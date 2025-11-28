package com.nastytech.eden2

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nastytech.eden2.db.AppDatabase
import com.nastytech.eden2.db.HistoryItem
import com.nastytech.eden2.db.HistoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var textToSpeech: TextToSpeech

    private var findInPageLayout: LinearLayout? = null
    private var findInPageEditText: EditText? = null
    private var findInPageNextButton: ImageButton? = null
    private var findInPagePrevButton: ImageButton? = null
    private var findInPageCloseButton: ImageButton? = null

    private val STORAGE_PERMISSION_CODE = 1
    private var initialUrl: String = "about:blank"
    private lateinit var historyDao: HistoryDao

    private var originalUserAgent: String? = null

    private val AD_HOSTS = listOf(
        "adservice.google.com",
        "doubleclick.net",
        "admob.com",
        "googlesyndication.com",
        "adnxs.com",
        "facebook.com/ads",
        "app-measurement.com"
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

        findInPageLayout = view.findViewById(R.id.find_in_page_layout)
        findInPageEditText = view.findViewById(R.id.find_in_page_edit_text)
        findInPageNextButton = view.findViewById(R.id.find_in_page_next_button)
        findInPagePrevButton = view.findViewById(R.id.find_in_page_prev_button)
        findInPageCloseButton = view.findViewById(R.id.find_in_page_close_button)

        findInPageNextButton?.setOnClickListener { findNext(true) }
        findInPagePrevButton?.setOnClickListener { findNext(false) }
        findInPageCloseButton?.setOnClickListener { hideFindInPage() }

        findInPageEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findNext(true)
                true
            } else {
                false
            }
        }

        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported or missing data")
                } else {
                    Log.d("TTS", "TextToSpeech initialized successfully")
                }
            } else {
                Log.e("TTS", "TextToSpeech Initialization failed!")
            }
        }

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
                (activity as? MainActivity)?.supportActionBar?.title = view?.title ?: "EdenX Browser"
                activity?.invalidateOptionsMenu()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString()?.toLowerCase(Locale.ROOT)
                if (url != null) {
                    if (AD_HOSTS.any { url.contains(it) }) {
                        Log.d("AdBlocker", "Blocked ad request: ${request.url}")
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

        originalUserAgent = webView.settings.userAgentString

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
                (activity as? MainActivity)?.supportActionBar?.title = title ?: "EdenX Browser"
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
                handleBlobDownload(url, contentDisposition, mimetype)
            } else {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    downloadFile(url, userAgent, contentDisposition, mimetype)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                }
            }
        }

        webView.addJavascriptInterface(JavaScriptBlobHandler(), "AndroidBlobHandler")

        webView.loadUrl(initialUrl)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (findInPageLayout?.visibility == View.VISIBLE) {
                    hideFindInPage()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun loadUrl(url: String) {
        if (::webView.isInitialized) {
            webView.loadUrl(url)
        } else {
            initialUrl = url
        }
    }

    fun getCurrentUrl(): String? {
        return if (::webView.isInitialized) webView.url else null
    }

    fun getWebView(): WebView? {
        return if (::webView.isInitialized) webView else null
    }

    fun toggleDesktopMode() {
        val settings = webView.settings
        if (settings.userAgentString == originalUserAgent) {
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36"
            Toast.makeText(requireContext(), "Desktop mode enabled", Toast.LENGTH_SHORT).show()
        } else {
            settings.userAgentString = originalUserAgent
            Toast.makeText(requireContext(), "Desktop mode disabled", Toast.LENGTH_SHORT).show()
        }
        webView.reload()
    }

    fun showFindInPage() {
        findInPageLayout?.visibility = View.VISIBLE
        findInPageEditText?.requestFocus()
    }

    private fun hideFindInPage() {
        webView.clearMatches()
        findInPageLayout?.visibility = View.GONE
        findInPageEditText?.text?.clear()
    }

    private fun findNext(forward: Boolean) {
        val searchText = findInPageEditText?.text.toString()
        if (searchText.isNotBlank()) {
            webView.findAllAsync(searchText)
            webView.findNext(forward)
        }
    }

    fun speakText(text: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            if (text.isNotBlank()) {
                val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                if (result == TextToSpeech.ERROR) {
                    Log.e("TTS", "Error speaking text.")
                    Toast.makeText(requireContext(), "Error speaking text.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Reading aloud...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No text to speak.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Text-to-Speech engine not ready.", Toast.LENGTH_SHORT).show()
        }
    }

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

    private inner class JavaScriptBlobHandler {
        @JavascriptInterface
        fun receiveBase64Blob(base64Data: String, mimeType: String, filename: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()

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

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
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