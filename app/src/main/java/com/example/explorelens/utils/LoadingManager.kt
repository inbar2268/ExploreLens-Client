package com.example.explorelens.utils

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.example.explorelens.R
import java.lang.ref.WeakReference

object LoadingManager {
    private var activityWeakRef: WeakReference<Activity>? = null
    private var overlayView: WeakReference<FrameLayout>? = null

    fun showLoading(activity: Activity) {
        // Clean up previous references if activity changed
        if (activityWeakRef?.get() != activity) {
            cleanup()
        }

        // Set activity reference
        activityWeakRef = WeakReference(activity)

        val currentOverlay = overlayView?.get()
        if (currentOverlay == null) {
            // Create the overlay dynamically
            val newOverlay = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(activity, R.color.semi_transparent_background))
                isClickable = true
                isFocusable = true

                // Add ProgressBar to center
                val progressBar = ProgressBar(activity)
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                params.gravity = android.view.Gravity.CENTER
                addView(progressBar, params)
            }

            // Add overlay to the activity's root view
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(newOverlay)

            // Store weak reference to overlay
            overlayView = WeakReference(newOverlay)
        } else {
            currentOverlay.visibility = View.VISIBLE
        }
    }

    fun hideLoading() {
        overlayView?.get()?.visibility = View.GONE
    }

    fun cleanup() {
        overlayView?.get()?.let { overlay ->
            val parent = overlay.parent as? ViewGroup
            parent?.removeView(overlay)
        }
        overlayView = null
        activityWeakRef = null
    }
}