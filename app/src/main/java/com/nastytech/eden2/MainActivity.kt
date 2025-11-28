package com.nastytech.eden2

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // A constant for the storage permission request code
    private val STORAGE_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the views from the layout
        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progress_bar)

        // --- WebView Settings ---
        webView.webViewClient = WebViewClient() // Ensures links open within the WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // --- Progress Bar Handler ---
        // This client handles events related to the browser UI, like progress updates.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // --- Download Handler ---
        // This listener is triggered whenever the WebView needs to download a file.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            // Check if we already have permission to write to storage
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                // If not, request the permission from the user
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
            }
        }

        // --- Back Press Handler ---
        // This is the modern way to handle the back button.
        // It checks if the WebView can go back before closing the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // If the WebView can't go back, disable this callback and
                    // let the system handle the back press (which usually closes the activity).
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // --- Load Initial URL ---
        webView.loadUrl("https://github.com/amirisback") // You can change this to any start page
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
            // Show a notification while downloading and when complete.
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // Save the file to the public "Downloads" folder.
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request) // Start the download

            Toast.makeText(applicationContext, "Downloading File...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Show an error message if something goes wrong
            Toast.makeText(applicationContext, "Error downloading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * This is called after the user responds to the permission request dialog.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted. Inform the user to try again.
                Toast.makeText(this, "Permission granted. Please try the download again.", Toast.LENGTH_LONG).show()
            } else {
                // Permission was denied.
                Toast.makeText(this, "Permission denied. Cannot download file.", Toast.LENGTH_LONG).show()
            }
        }
    }
}