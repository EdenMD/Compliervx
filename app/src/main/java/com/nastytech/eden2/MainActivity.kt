package com.nastytech.eden2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabAdapter: TabAdapter // Declare, but won't initialize fully yet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to our new tab-managing layout
        setContentView(R.layout.activity_main)

        // Initialize the TabLayout and ViewPager2
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)

        // For now, we'll initialize a basic adapter.
        // In the next phase (Phase 2.1), we'll create a full TabAdapter.
        // For now, we'll use a placeholder or an empty list of fragments.
        tabAdapter = TabAdapter(this) // This will be properly implemented in Phase 2.1
        viewPager.adapter = tabAdapter

        // Link the TabLayout and ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // For now, tabs will just be numbered. We'll improve this in Phase 2.2.
            tab.text = "Tab ${position + 1}"
        }.attach()

        // Add an initial tab for demonstration or a default homepage
        // This method will be properly implemented in Phase 2.2
        addTab(BrowserFragment.newInstance("https://www.google.com"))

        // TODO: In Phase 2.2, we'll implement logic for adding/removing tabs
        // TODO: In Phase 4.1, we'll integrate menu for "New Tab", "Split View", etc.
    }

    // Placeholder method for adding tabs
    // This will be fleshed out in Phase 2.2
    fun addTab(fragment: BrowserFragment) {
        // This is a simplified placeholder.
        // The actual TabAdapter will manage a list of fragments.
        // For now, if we don't have a real list, this might need more robust handling
        // in TabAdapter or directly setting a single fragment.
        // We will make this fully functional in Phase 2.2.
        // For the immediate update, this will prevent crashes and demonstrate the new structure.
        tabAdapter.addFragment(fragment)
        viewPager.setCurrentItem(tabAdapter.itemCount - 1, true) // Go to the new tab
        tabLayout.getTabAt(tabAdapter.itemCount - 1)?.text = "New Tab" // Placeholder title
    }

    override fun onBackPressed() {
        val currentFragment = tabAdapter.getFragment(viewPager.currentItem)
        if (currentFragment != null && currentFragment.getWebView()?.canGoBack() == true) {
            currentFragment.getWebView()?.goBack()
        } else if (tabAdapter.itemCount > 1) {
            // Close the current tab if not the last one
            val currentTabPosition = viewPager.currentItem
            tabAdapter.removeFragment(currentTabPosition)
            tabLayout.removeTabAt(currentTabPosition)
        } else {
            super.onBackPressed()
        }
    }
}