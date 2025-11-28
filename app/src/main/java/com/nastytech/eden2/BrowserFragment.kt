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
import androidx.lifecycle.lifecycleScope
import com.nastytech.eden2.db.AppDatabase
import com.nastytech.eden2.db.HistoryItem
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val STORAGE_PERMISSION_CODE = 1

    private var initialUrl: String = "about:blank"

    // Reference to the database DAO
    private lateinit var historyDao: HistoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialUrl = it.getString(ARG_URL) ?: "about:blank"
        }
        // Initialize DAO here
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

        // --- WebView Settings ---
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // When a page finishes loading, save it to history
                if (url != null && view?.title != null && url != "about:blank") {
                    lifecycleScope.launch {
                        val existingItem = historyDao.getHistoryItemByUrl(url)
                        if (existingItem == null) { // Only add if not already in history (or update timestamp)
                            historyDao.insert(HistoryItem(url = url, title = view.title!!))
                        } else {
                            // Update timestamp if the page is visited again
                            historyDao.insert(existingItem.copy(timestamp = System.currentTimeMillis()))
                        }
                    }
                }
                // Update the tab title in MainActivity via its adapter if available
                (activity as? MainActivity)?.let { mainActivity ->
                    val tabAdapter = mainActivity.viewPager.adapter as? TabAdapter
                    val currentPosition = mainActivity.viewPager.currentItem
                    tabAdapter?.notifyItemChanged(currentPosition) // Trigger title update in TabLayoutMediator
                }
            }
            // Add other overrides as needed, e.g., shouldOverrideUrlLoading for custom handling
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
                // If the title is received, also notify the main activity to update the tab title
                (activity as? MainActivity)?.let { mainActivity ->
                    val tabAdapter = mainActivity.viewPager.adapter as? TabAdapter
                    val currentPosition = mainActivity.viewPager.currentItem
                    tabAdapter?.notifyItemChanged(currentPosition) // This will cause TabLayoutMediator to re-query the title
                }
            }
        }

        // --- Download Handler ---
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
            }
        }

        // --- Load Initial URL ---
        webView.loadUrl(initialUrl)

        // --- Handle back press within the Fragment's WebView ---
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // If the WebView can't go back, this tab is "done".
                    // The MainActivity will handle closing the tab or exiting the app.
                    isEnabled = false // Disable this fragment's callback
                    requireActivity().onBackPressedDispatcher.onBackPressed() // Let the Activity's callback handle it
                }
            }
        })
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

    fun loadUrl(url: String) {
        if (::webView.isInitialized) {
            webView.loadUrl(url)
        } else {
            initialUrl = url
        }
    }

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