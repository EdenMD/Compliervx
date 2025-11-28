package com.nastytech.eden2

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // A constant for the storage permission request code, specific to this fragment if needed
    // For now, assume MainActivity handles initial permission, but Fragment can also request.
    private val STORAGE_PERMISSION_CODE = 1

    // This property will be set by the newInstance factory method
    private var initialUrl: String = "about:blank"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve the initial URL passed as an argument
        arguments?.let {
            initialUrl = it.getString(ARG_URL) ?: "about:blank"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment (fragment_browser.xml)
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize views from the fragment's layout
        webView = view.findViewById(R.id.webview_fragment)
        progressBar = view.findViewById(R.id.progress_bar_fragment)

        // --- WebView Settings (moved from old MainActivity) ---
        webView.webViewClient = object : WebViewClient() {
            // Optional: You can override onPageStarted, onPageFinished, etc. here
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // TODO: In Phase 2.4, add logic here to save history to Room DB
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false // Hide zoom buttons

        // --- Progress Bar Handler (moved from old MainActivity) ---
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
                // Optionally update the tab title here
                // (e.g., if Fragment is part of an interface that displays tab titles)
            }
        }

        // --- Download Handler (moved from old MainActivity) ---
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                // Request permission if not granted. Use requestPermissions from Fragment.
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
            }
        }

        // --- Load Initial URL ---
        webView.loadUrl(initialUrl)

        // --- Handle back press within the Fragment's WebView ---
        // This ensures back navigation works per-tab
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // If the WebView can't go back, the Fragment should ideally
                    // inform its hosting Activity/ViewPager to close the tab,
                    // or let the Activity handle the global back press if it's the only tab.
                    // For now, we'll disable this callback and let the Activity handle it.
                    // The MainActivity will then manage tab closing if multiple tabs are open.
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
            // If WebView not initialized, store for when it is (e.g., during onCreate)
            initialUrl = url
        }
    }

    // Provide access to the WebView instance
    fun getWebView(): WebView? {
        return if (::webView.isInitialized) webView else null
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