package com.example.explorelens.ar

import android.content.Context
import android.content.Intent
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.MainActivity
import com.example.explorelens.R
import com.example.explorelens.common.helpers.FullScreenHelper
import com.example.explorelens.common.helpers.SnackbarHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.data.model.FilterOption
import com.example.explorelens.databinding.ActivityMainBinding
import com.example.explorelens.databinding.FilterSideSheetBinding
import com.example.explorelens.adapters.filterOptions.FilterOptionAdapter
import com.example.explorelens.ui.site.SiteDetailsFragment
import com.google.android.material.sidesheet.SideSheetDialog

/**
 * View class for AR activity that manages UI components and interactions
 */
class ArActivityView(
  private val activity: FragmentActivity,
  private val renderer: AppRenderer,
  private val filterOptions: Array<FilterOption>
) : DefaultLifecycleObserver {

  companion object {
    private const val TAG = "ArActivityView"
  }

  // View bindings
  val binding: ActivityMainBinding = ActivityMainBinding.inflate(
    LayoutInflater.from(activity)
  )

  val root: View = binding.root

  // Helper classes
  val snackbarHelper = SnackbarHelper().apply {
    setParentView(binding.coordinatorLayout)
    setMaxLines(6)
  }

  // Selected filters
  private val selectedFilters = mutableSetOf<String>()

  // Site details management
  private var currentSiteDetailsFragment: SiteDetailsFragment? = null

  init {
    setupSurfaceView()
    setupUIComponents()
    setupBackButtonHandler()
  }

  private fun setupSurfaceView() {
    binding.surfaceview.apply {
      SampleRender(this, renderer, activity.assets)
    }
  }

  private fun setupUIComponents() {
    // Setup close button
    binding.closeButton.setOnClickListener {
      Log.d(TAG, "Close button clicked")
      navigateToMainActivity()
    }

    // Setup layers/filter button
    binding.layersButton.setOnClickListener {
      Log.d(TAG, "Layers button clicked")
      showFilterSideSheet()
    }

  }

  private fun setupBackButtonHandler() {
    // Handle back button when site details are showing
    activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (binding.siteDetailsContainer.visibility == View.VISIBLE) {
          hideSiteDetails()
        } else {
          isEnabled = false
          activity.onBackPressedDispatcher.onBackPressed()
          isEnabled = true
        }
      }
    })
  }

  /**
   * Show the filter side sheet dialog with all available filtering options
   */
  private fun showFilterSideSheet() {
    Log.d(TAG, "All Filter Options in showFilterSideSheet: ${filterOptions.joinToString()}")
    val sideSheetDialog = SideSheetDialog(activity)
    val sideSheetBinding = FilterSideSheetBinding.inflate(activity.layoutInflater)

    sideSheetDialog.setContentView(sideSheetBinding.root)

    // Configure fullscreen appearance
    configureDialogFullscreen(sideSheetDialog)

    // Create filter options list
    val allFilterOptions = filterOptions.map { option ->
      FilterOption(
        name = option.name,
        isChecked = selectedFilters.contains(option.name),
        iconResId = option.iconResId
      )
    }.toMutableList()

    // Setup filter adapter
    var adapter = FilterOptionAdapter(allFilterOptions) { _, _ -> }

    val recyclerView = sideSheetBinding.filterOptionsRecyclerViewSideSheet
    recyclerView.layoutManager = LinearLayoutManager(activity)
    recyclerView.adapter = adapter
    recyclerView.addItemDecoration(createDividerDecoration())

    // Setup button click listeners
    setupSideSheetButtons(sideSheetBinding, adapter, allFilterOptions, sideSheetDialog)

    // Handle dismissal to restore UI state
    sideSheetDialog.setOnDismissListener {
      FullScreenHelper.setFullScreenOnWindowFocusChanged(activity, true)
    }

    sideSheetDialog.setCanceledOnTouchOutside(true)
    sideSheetDialog.show()
  }

  /**
   * Configure dialog to appear in fullscreen mode
   */
  private fun configureDialogFullscreen(dialog: SideSheetDialog) {
    dialog.window?.apply {
      setFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
      )

      // Apply edge-to-edge display
      WindowCompat.setDecorFitsSystemWindows(this, false)

      // Using the system UI controller approach
      decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Listen for dialog events to maintain fullscreen
    dialog.setOnShowListener {
      FullScreenHelper.setFullScreenOnWindowFocusChanged(activity, true)
    }
  }

  /**
   * Create divider decoration for recycler view
   */
  private fun createDividerDecoration(): DividerItemDecoration {
    val dividerHeightPx = 3
    val dividerColor = ContextCompat.getColor(activity, R.color.light_gray)
    val dividerMarginPx = 50

    val divider = ShapeDrawable(RectShape()).apply {
      intrinsicHeight = dividerHeightPx
    }

    DrawableCompat.setTint(divider, dividerColor)
    val insetDivider = InsetDrawable(divider, dividerMarginPx, 0, dividerMarginPx, 0)

    return DividerItemDecoration(
      activity,
      LinearLayoutManager.VERTICAL
    ).apply {
      setDrawable(insetDivider)
    }
  }

  /**
   * Setup buttons for the filter side sheet
   */
  private fun setupSideSheetButtons(
    binding: FilterSideSheetBinding,
    adapter: FilterOptionAdapter,
    allFilterOptions: MutableList<FilterOption>,
    dialog: SideSheetDialog
  ) {
    // Close button
    binding.btnCloseSideSheet.setOnClickListener {
      dialog.dismiss()
    }

    // Apply button
    binding.applyButtonSideSheet.setOnClickListener {
      selectedFilters.clear()
      selectedFilters.addAll(adapter.getCurrentChoices())
      Log.d(TAG, "Selected Filters (on Apply): $selectedFilters")

      // Apply filters to renderer
      //renderer.applyFilters(selectedFilters)

      Toast.makeText(activity,
        "Filters Applied: ${selectedFilters.joinToString(", ")}",
        Toast.LENGTH_SHORT
      ).show()

      dialog.dismiss()
    }

    // Clear all button
    binding.clearAllButtonSideSheet.setOnClickListener {
      selectedFilters.clear()
      allFilterOptions.forEach { it.isChecked = false }
      adapter.notifyDataSetChanged()
    }
  }

  /**
   * Show site details in a fragment
   */
  fun showSiteDetails(siteId: String, description: String?, siteName: String? = null) {
    // Create and show the site details fragment
    val fragment = SiteDetailsFragment().apply {
      arguments = Bundle().apply {
        putString("LABEL_KEY", siteId)  // Site ID for API calls
        putString("SITE_NAME_KEY", siteName)  // Site name for initial display
        description?.let { putString("DESCRIPTION_KEY", it) }
      }
    }

    // Add fragment to the container
    activity.supportFragmentManager.beginTransaction()
      .replace(R.id.siteDetailsContainer, fragment)
      .commit()

    currentSiteDetailsFragment = fragment
    binding.siteDetailsContainer.visibility = View.VISIBLE

    // Hide camera button when showing details
    binding.cameraButtonContainer.visibility = View.GONE
  }

  /**
   * Hide the currently displayed site details
   */
  fun hideSiteDetails() {
    // Remove the fragment
    currentSiteDetailsFragment?.let {
      activity.supportFragmentManager.beginTransaction()
        .remove(it)
        .commit()
    }

    currentSiteDetailsFragment = null
    binding.siteDetailsContainer.visibility = View.GONE

    // Show camera button again
    binding.cameraButtonContainer.visibility = View.VISIBLE
  }

  /**
   * Navigate back to main activity
   */
  private fun navigateToMainActivity() {
    val intent = Intent(activity, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    intent.putExtra("RETURNED_FROM_AR", true)
    activity.startActivity(intent)
    activity.finish()
  }

  /**
   * Post a runnable to the UI thread
   */
  fun post(action: Runnable) = root.post(action)

  /**
   * Set the scanning state visually
   */
  fun setScanningActive(active: Boolean) {
    if (active) {
      binding.cameraInnerCircle.animate()
        .scaleX(0.93f)
        .scaleY(0.93f)
        .alpha(0.5f)
        .setDuration(250)
        .start()
      binding.cameraButtonContainer.isEnabled = false
    } else {
      binding.cameraInnerCircle.animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setDuration(200)
        .start()
      binding.cameraButtonContainer.isEnabled = true
    }
  }

  override fun onResume(owner: LifecycleOwner) {
    binding.surfaceview.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    binding.surfaceview.onPause()
  }
}