package com.example.explorelens.ar
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.ArActivity
import com.example.explorelens.common.helpers.SnackbarHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.R
import com.example.explorelens.ui.site.SiteDetailsFragment

class ArActivityView(val activity: ArActivity, renderer: AppRenderer) : DefaultLifecycleObserver {
  val root = View.inflate(activity, R.layout.activity_main, null)
  val surfaceView = root.findViewById<GLSurfaceView>(R.id.surfaceview).apply {
    SampleRender(this, renderer, activity.assets)
  }
  val snapshotButton = root.findViewById<AppCompatButton>(R.id.cameraButton)
  val snackbarHelper = SnackbarHelper().apply {
    setParentView(root.findViewById(R.id.coordinatorLayout))
    setMaxLines(6)
  }

  // Site details overlay
  private val siteDetailsContainer = root.findViewById<FrameLayout>(R.id.siteDetailsContainer)
  private var currentSiteDetailsFragment: SiteDetailsFragment? = null

  init {
    // Handle back button when site details are showing
    activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (siteDetailsContainer.visibility == View.VISIBLE) {
          hideSiteDetails()
        } else {
          isEnabled = false
          activity.onBackPressedDispatcher.onBackPressed()
          isEnabled = true
        }
      }
    })
  }

  override fun onResume(owner: LifecycleOwner) {
    super.onResume(owner)
    surfaceView.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    surfaceView.onPause()
  }

  fun showSiteDetails(siteName: String, description: String?) {
    // Create and show the site details fragment
    val fragment = SiteDetailsFragment().apply {
      arguments = Bundle().apply {
        putString("LABEL_KEY", siteName)
        description?.let { putString("DESCRIPTION_KEY", it) }
      }
    }

    // Add fragment to the container
    activity.supportFragmentManager.beginTransaction()
      .replace(R.id.siteDetailsContainer, fragment)
      .commit()

    currentSiteDetailsFragment = fragment
    siteDetailsContainer.visibility = View.VISIBLE
  }

  fun hideSiteDetails() {
    // Remove the fragment
    currentSiteDetailsFragment?.let {
      activity.supportFragmentManager.beginTransaction()
        .remove(it)
        .commit()
    }
    currentSiteDetailsFragment = null
    siteDetailsContainer.visibility = View.GONE
    activity.findViewById<View>(R.id.cameraButton)?.visibility = View.VISIBLE
  }

  fun post(action: Runnable) = root.post(action)

  /**
   * Toggles the scan button depending on if scanning is in progress.
   */
  fun setScanningActive(active: Boolean) = when(active) {
    true -> {
      snapshotButton.isEnabled = false
    }
    false -> {
      snapshotButton.isEnabled = true
    }
  }
}