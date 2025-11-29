package com.nastytech.eden2

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TabAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private val fragments: MutableList<BrowserFragment> = mutableListOf()

    init {
        // Initially, start with one blank tab
        addFragment(BrowserFragment.newInstance("about:blank"))
    }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun addFragment(fragment: BrowserFragment) {
        fragments.add(fragment)
        notifyItemInserted(fragments.size - 1)
    }

    fun removeFragment(position: Int) {
        if (position >= 0 && position < fragments.size) {
            fragments.removeAt(position)
            notifyItemRemoved(position)
            // If the last tab was removed and there are still tabs, select the new last tab
            if (fragments.isNotEmpty() && position == fragments.size) {
                notifyItemChanged(fragments.size - 1) // Inform ViewPager2 to update the current item
            }
        }
    }

    fun getFragment(position: Int): BrowserFragment? {
        return if (position >= 0 && position < fragments.size) fragments[position] else null
    }

    fun getFragments(): List<BrowserFragment> = fragments

    // Method to get the title for a tab (e.g., from fragment's current URL or custom title)
    fun getTabTitle(position: Int): String {
        return getFragment(position)?.getWebView()?.title ?: "New Tab"
    }
}