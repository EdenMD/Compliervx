package com.nastytech.eden2

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech // Added for TTS
import android.util.Base64 // Added for Blob Downloads
import android.util.Log // Added for logging Ad Blocker and TTS
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface // Added for Blob Downloads
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest // Added for Ad Blocker
import android.webkit.WebResourceResponse // Added for Ad Blocker
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nastytech.eden2.db.AppDatabase
import com.nastytech.eden2.db.HistoryItem
import com.nastytech.eden2.db.HistoryDao // Import HistoryDao
import kotlinx.coroutines.Dispatchers // Added for Blob Downloads
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Added for Blob Downloads
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection // For guessing MIME type in blob download
import java.util.Locale // Added for TTS

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var textToSpeech: TextToSpeech // Added for TTS

    private val STORAGE_PERMISSION_CODE = 1
    private var initialUrl: String = "about:blank"
    private lateinit var historyDao: HistoryDao // Initialized in onCreate

    // Ad blocking list (a simple example)
    private val AD_HOSTS = listOf(
        "adservice.google.com",
        "doubleclick.net",
        "admob.com",
        "googlesyndication.com",
        "adnxs.com",
        "facebook.com/ads",
        "app-measurement.com" // Basic analytics/ad tracking
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialUrl = it.getString(ARG_URL) ?: "about:blank"
        }
        historyDao = AppDatabase.getDatabase(requireContext()).historyDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.webview_fragment)
        progressBar = view.findViewById(R.id.progress_bar_fragment)

        // --- Initialize Text-to-Speech ---
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US) // Set default language
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported or missing data")
                } else {
                    Log.d("TTS", "TextToSpeech initialized successfully")
                }
            } else {
                Log.e("TTS", "TextToSpeech Initialization failed!")
            }
        }

        // --- WebView Settings ---
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && view?.title != null && url != "about:blank") {
                    lifecycleScope.launch {
                        val existingItem = historyDao.getHistoryItemByUrl(url)
                        if (existingItem == null) {
                            historyDao.insert(HistoryItem(url = url, title = view.title!!))
                        } else {
                            historyDao.insert(existingItem.copy(timestamp = System.currentTimeMillis()))
                        }
                    }
                }
                (activity as? MainActivity)?.let { mainActivity ->
                    val tabAdapter = mainActivity.viewPager.adapter as? TabAdapter
                    val currentPosition = mainActivity.viewPager.currentItem
                    tabAdapter?.notifyItemChanged(currentPosition)
                }
            }

            // --- Ad Blocker Implementation (shouldInterceptRequest) ---
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString()?.toLowerCase(Locale.ROOT)
                if (url != null) {
                    // Check if the URL contains any of the ad hosts
                    if (AD_HOSTS.any { url.contains(it) }) {
                        Log.d("AdBlocker", "Blocked ad request: ${request.url}")
                        // Return an empty/null response to block the ad
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream("".toByteArray())
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        // --- Progress Bar Handler ---
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                (activity as? MainActivity)?.let { mainActivity ->
                    val tabAdapter = mainActivity.viewPager.adapter as? TabAdapter
                    val currentPosition = mainActivity.viewPager.currentItem
                    tabAdapter?.notifyItemChanged(currentPosition)
                }
            }
        }

        // --- Download Handler (Enhanced for Blob Downloads) ---
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
                // Handle Blob downloads using JavaScript injection
                handleBlobDownload(url, contentDisposition, mimetype)
            } else {
                // Handle standard file downloads
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    downloadFile(url, userAgent, contentDisposition, mimetype)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                }
            }
        }

        // --- Add JavaScript Interface for Blob Downloads ---
        webView.addJavascriptInterface(JavaScriptBlobHandler(), "AndroidBlobHandler")

        // --- Load Initial URL ---
        webView.loadUrl(initialUrl)

        // --- Handle back press within the Fragment's WebView ---
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Handles the file download process using Android's system DownloadManager.
     */
    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(requireContext(), "Downloading File...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error downloading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Injects JavaScript to handle blob URL downloads.
     */
    private fun handleBlobDownload(blobUrl: String, contentDisposition: String, mimeType: String) {
        val fileName = URLUtil.guessFileName(blobUrl, contentDisposition, mimeType)
        val js = """
            (function() {
                fetch('$blobUrl')
                    .then(response => response.blob())
                    .then(blob => {
                        const reader = new FileReader();
                        reader.onloadend = function() {
                            const base64data = reader.result.split(',')[1];
                            const actualMimeType = blob.type || '$mimeType';
                            const actualFilename = '$fileName';
                            AndroidBlobHandler.receiveBase64Blob(base64data, actualMimeType, actualFilename);
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(e => console.error('Blob fetch error', e));
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        Toast.makeText(requireContext(), "Processing blob download: $fileName", Toast.LENGTH_SHORT).show()
    }

    /**
     * JavaScript Interface for receiving blob data (Base64 encoded) from the WebView.
     */
    private inner class JavaScriptBlobHandler {
        @JavascriptInterface
        fun receiveBase64Blob(base64Data: String, mimeType: String, filename: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs() // Ensure directory exists

                    val file = File(downloadsDir, filename)
                    FileOutputStream(file).use { it.write(bytes) }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Blob downloaded: $filename", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error downloading blob: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("BlobDownload", "Error saving blob file: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * This is called after the user responds to the permission request dialog.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permission granted. Please try the download again.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot download file.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Public method to load a URL into this fragment's WebView
    fun loadUrl(url: String) {
        if (::webView.isInitialized) {
            webView.loadUrl(url)
        } else {
            initialUrl = url
        }
    }

    // Public method to get the current URL of the WebView
    fun getCurrentUrl(): String? {
        return if (::webView.isInitialized) webView.url else null
    }

    // Public method to get the WebView instance
    fun getWebView(): WebView? {
        return if (::webView.isInitialized) webView else null
    }

    // Public method to speak text (can be triggered from MainActivity/menu later)
    fun speakText(text: String) {
        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        if (::textToSpeech.isInitialized && text.isNotBlank()) {
            val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            if (result == TextToSpeech.ERROR) {
                Log.e("TTS", "Error speaking text.")
                Toast.makeText(requireContext(), "Error speaking text.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Reading aloud...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "TTS not ready or no text to speak.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause() // Pause WebView activity
    }

    override fun onResume() {
        super.onResume()
        webView.onResume() // Resume WebView activity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // It's good practice to destroy the WebView when the fragment's view is destroyed
        // to prevent memory leaks, especially in a ViewPager scenario.
        webView.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release TextToSpeech resources
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    companion object {
        private const val ARG_URL = "url"

        fun newInstance(url: String): BrowserFragment {
            val fragment = BrowserFragment()
            val args = Bundle()
            args.putString(ARG_URL, url)
            fragment.arguments = args
            return fragment
        }
    }
}