package com.nastytech.eden2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    lateinit var viewPager: ViewPager2
    private lateinit var tabAdapter: TabAdapter
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)

        tabAdapter = TabAdapter(this)
        viewPager.adapter = tabAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabAdapter.getFragment(position)?.getWebView()?.title ?: "New Tab"
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val currentFragment = tabAdapter.getFragment(tab?.position ?: 0)
                supportActionBar?.title = currentFragment?.getWebView()?.title ?: "EdenX Browser"
                invalidateOptionsMenu()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tabAdapter.getFragment(tab?.position ?: 0)?.getWebView()?.reload()
            }
        })

        if (tabAdapter.itemCount == 0) {
            addTab("https://www.google.com")
        }

        supportActionBar?.title = tabAdapter.getFragment(viewPager.currentItem)?.getWebView()?.title ?: "EdenX Browser"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem)
        val desktopModeItem = menu?.findItem(R.id.action_desktop_mode)
        desktopModeItem?.isChecked = currentFragment?.getWebView()?.settings?.userAgentString?.contains("Mobile", ignoreCase = true)?.not() ?: false
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem)
        return when (item.itemId) {
            R.id.action_new_tab -> {
                showNewTabDialog()
                true
            }
            R.id.action_split_view -> {
                val intent = Intent(this, SplitViewActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_history -> {
                Toast.makeText(this, "History feature coming soon!", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_bookmarks -> {
                Toast.makeText(this, "Bookmarks feature coming soon!", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_desktop_mode -> {
                currentFragment?.toggleDesktopMode()
                invalidateOptionsMenu()
                true
            }
            R.id.action_find_in_page -> {
                currentFragment?.showFindInPage()
                true
            }
            R.id.action_speak_page -> {
                val title = currentFragment?.getWebView()?.title
                val url = currentFragment?.getWebView()?.url
                if (!title.isNullOrBlank()) {
                    currentFragment?.speakText("The current page title is: $title")
                } else if (!url.isNullOrBlank()) {
                    currentFragment?.speakText("The current page URL is: $url")
                } else {
                    Toast.makeText(this, "No content to speak.", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_anesha_chat -> {
                addTab("https://xbvercel.vercel.app")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun addTab(url: String) {
        tabAdapter.addFragment(BrowserFragment.newInstance(url))
        viewPager.setCurrentItem(tabAdapter.itemCount - 1, true)
        tabLayout.getTabAt(tabAdapter.itemCount - 1)?.text = "Loading..."
    }

    fun removeCurrentTab() {
        if (tabAdapter.itemCount > 1) {
            val currentPosition = viewPager.currentItem
            tabAdapter.removeFragment(currentPosition)
            if (currentPosition < tabAdapter.itemCount) {
                viewPager.setCurrentItem(currentPosition, true)
            } else if (tabAdapter.itemCount > 0) {
                viewPager.setCurrentItem(tabAdapter.itemCount - 1, true)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun showNewTabDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Open New Tab")

        val input = EditText(this)
        input.hint = "Enter URL (e.g., google.com)"
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        builder.setView(input)

        builder.setPositiveButton("Go") { dialog, _ ->
            var url = input.text.toString().trim()
            if (url.isNotBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                addTab(url)
            } else {
                addTab("https://www.google.com")
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    override fun onBackPressed() {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem)
        if (currentFragment != null && currentFragment.getWebView()?.canGoBack() == true) {
            currentFragment.getWebView()?.goBack()
        } else if (tabAdapter.itemCount > 1) {
            removeCurrentTab()
        } else {
            super.onBackPressed()
        }
    }
}