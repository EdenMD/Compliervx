package com.nastytech.eden2

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplitViewActivity : AppCompatActivity() {

    private lateinit var leftPaneContainer: FrameLayout
    private lateinit var rightPaneContainer: FrameLayout
    private lateinit var dividerView: View
    private lateinit var rootLayout: LinearLayout

    private var initialX: Float = 0f
    private var initialLeftWidth: Int = 0
    private var initialRightWidth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split_view) // Our split view layout

        rootLayout = findViewById(R.id.root_layout) // Ensure root layout has an ID if used directly
        leftPaneContainer = findViewById(R.id.left_pane_container)
        rightPaneContainer = findViewById(R.id.right_pane_container)
        dividerView = findViewById(R.id.divider_view)

        // Add the BrowserFragments to each pane
        val leftFragment = BrowserFragment.newInstance("https://www.google.com") // Default URL for left
        val rightFragment = BrowserFragment.newInstance("https://www.bing.com") // Default URL for right

        supportFragmentManager.beginTransaction()
            .replace(R.id.left_pane_container, leftFragment)
            .replace(R.id.right_pane_container, rightFragment)
            .commit()

        // Make the divider draggable
        setupDividerDragListener()
    }

    private fun setupDividerDragListener() {
        dividerView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialLeftWidth = leftPaneContainer.width
                    initialRightWidth = rightPaneContainer.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialX
                    val newLeftWidth = (initialLeftWidth + dx).toInt()
                    val newRightWidth = (initialRightWidth - dx).toInt()

                    // Ensure widths are not too small (e.g., min 100 pixels)
                    if (newLeftWidth > 100 && newRightWidth > 100) {
                        val leftParams = leftPaneContainer.layoutParams as LinearLayout.LayoutParams
                        val rightParams = rightPaneContainer.layoutParams as LinearLayout.LayoutParams

                        leftParams.width = newLeftWidth
                        leftParams.weight = 0f // Set weight to 0 when using fixed width
                        rightParams.width = newRightWidth
                        rightParams.weight = 0f

                        leftPaneContainer.layoutParams = leftParams
                        rightPaneContainer.layoutParams = rightParams
                        leftPaneContainer.requestLayout()
                        rightPaneContainer.requestLayout()
                    }
                    true
                }
                else -> false
            }
        }
    }
}