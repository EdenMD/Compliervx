package com.nastytech.eden2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabAdapter: TabAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Use the new tab-managing layout

        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)

        tabAdapter = TabAdapter(this) // Initialize our custom TabAdapter
        viewPager.adapter = tabAdapter

        // Link the TabLayout and ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabAdapter.getTabTitle(position) // Set initial tab titles
        }.attach()

        // Listener for tab selection changes to update titles or other UI
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Not much needed here initially, but useful for future features
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Not much needed here initially
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Can be used to refresh the tab or scroll to top
            }
        })

        // TODO: In Phase 4.1, we'll integrate menu for "New Tab", "Split View", etc.
        // For now, the TabAdapter starts with one blank tab.
        // You can add more tabs here if needed for initial testing, e.g.:
        // addTab("https://www.google.com")
    }

    // Method to add a new tab to the browser
    fun addTab(url: String) {
        tabAdapter.addFragment(BrowserFragment.newInstance(url))
        viewPager.setCurrentItem(tabAdapter.itemCount - 1, true) // Switch to the new tab
        tabLayout.getTabAt(tabAdapter.itemCount - 1)?.text = "Loading..." // Initial tab title
        // The BrowserFragment's WebChromeClient will update the title later
    }

    // Method to remove a tab
    fun removeCurrentTab() {
        if (tabAdapter.itemCount > 1) { // Only remove if more than one tab exists
            val currentPosition = viewPager.currentItem
            tabAdapter.removeFragment(currentPosition)
            // TabLayoutMediator handles tab removal automatically if linked properly
        } else {
            // If only one tab left, closing it should exit the app (or go to homepage)
            finish()
        }
    }

    override fun onBackPressed() {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem)
        if (currentFragment != null && currentFragment.getWebView()?.canGoBack() == true) {
            currentFragment.getWebView()?.goBack()
        } else if (tabAdapter.itemCount > 1) {
            removeCurrentTab() // Close the current tab
        } else {
            super.onBackPressed() // Exit the app
        }
    }
}