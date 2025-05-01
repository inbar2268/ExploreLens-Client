package com.example.explorelens
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import androidx.navigation.fragment.findNavController
import com.example.explorelens.common.helpers.FullScreenHelper
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.ar.ARCoreSessionLifecycleHelper
import com.example.explorelens.ar.AppRenderer
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.utils.GeoLocationUtils

class ArActivity : AppCompatActivity() {

  val TAG = "ArActivity111"
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var renderer: AppRenderer
  lateinit var view: ArActivityView
  private lateinit var geoLocationUtils: GeoLocationUtils
  private val LOCATION_PERMISSION_REQUEST = 1001
  private val CAMERA_PERMISSION_REQUEST = 1002
  private lateinit var siteHistoryViewModel: SiteHistoryViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate called")

    geoLocationUtils = GeoLocationUtils(applicationContext)
    val siteRepository = SiteHistoryRepository(applicationContext)
    siteHistoryViewModel = ViewModelProvider(
      this,
      SiteHistoryViewModel.Factory(siteRepository, geoLocationUtils)
    )[SiteHistoryViewModel::class.java]

    Log.d(TAG, "ViewModel initialized")
  }

  override fun onStart() {
    super.onStart()
    Log.d(TAG, "onStart called - Activity becoming visible")
    checkAndRequestPermissions()
  }

  private fun checkAndRequestPermissions() {
    Log.d(TAG, "checkAndRequestPermissions called")
    when {
      !hasLocationPermissions() -> {
        Log.d(TAG, "Location permissions not granted. Requesting...")
        requestLocationPermissions()
      }
      !hasCameraPermission() -> {
        Log.d(TAG, "Camera permissions not granted. Requesting...")
        requestCameraPermission()
      }
      else -> {
        Log.d(TAG, "Permissions granted. Setting up AR...")
        setupAr()
      }
    }
  }

  private fun setupAr() {
    Log.d(TAG, "setupAr called")

    // Setup ARCore session lifecycle helper and configuration.
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    Log.d(TAG, "ARCoreSessionLifecycleHelper initialized")

    arCoreSessionHelper.exceptionCallback = { exception ->
      val message = when (exception) {
        is UnavailableArcoreNotInstalledException,
        is UnavailableUserDeclinedInstallationException -> "Please install ARCore"
        is UnavailableApkTooOldException -> "Please update ARCore"
        is UnavailableSdkTooOldException -> "Please update this app"
        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
        else -> "Failed to create AR session: $exception"
      }
      Log.e(TAG, message, exception)
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    Log.d(TAG, "ARCore session helper setup complete")

    // Configure session features, including: Lighting Estimation, Depth mode, Instant Placement.
    arCoreSessionHelper.beforeSessionResume = { session ->
      Log.d(TAG, "beforeSessionResume called")

      session.configure(
        session.config.apply {
          // To get the best image of the object in question, enable autofocus.
          focusMode = Config.FocusMode.AUTO
          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            depthMode = Config.DepthMode.AUTOMATIC
          }
        }
      )

      val filter = CameraConfigFilter(session)
        .setFacingDirection(CameraConfig.FacingDirection.BACK)
      val configs = session.getSupportedCameraConfigs(filter)
      val sort = compareByDescending<CameraConfig> { it.imageSize.width }
        .thenByDescending { it.imageSize.height }
      session.cameraConfig = configs.sortedWith(sort)[0]
    }

    lifecycle.addObserver(arCoreSessionHelper)

    // Set up AR renderer
    renderer = AppRenderer(this, geoLocationUtils, siteHistoryViewModel)
    lifecycle.addObserver(renderer)
    Log.d(TAG, "AR renderer setup complete")

    // Set up AR UI
    view = ArActivityView(this, renderer)
    setContentView(view.root)
    renderer.bindView(view)
    lifecycle.addObserver(view)

    setupCloseButton()
    Log.d(TAG, "AR UI setup complete")
  }

  private fun showPermissionDeniedDialog() {
    Log.d(TAG, "showPermissionDeniedDialog called")
    AlertDialog.Builder(this)
      .setTitle("Permissions Required")
      .setMessage("Please enable location and camera permissions in settings to use this feature.")
      .setPositiveButton("Go to Settings") { _, _ ->
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
      }
      .setNegativeButton("Cancel") { _, _ ->
        Log.d(TAG, "Cancel button clicked")
        navigateToProfile()  // Call the function to navigate to the profile
      }
      .show()
  }

  private fun hasLocationPermissions(): Boolean {
    val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    Log.d(TAG, "hasLocationPermissions: $granted")
    return granted
  }

  private fun hasCameraPermission(): Boolean {
    val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    Log.d(TAG, "hasCameraPermission: $granted")
    return granted
  }

  private fun requestLocationPermissions() {
    Log.d(TAG, "requestLocationPermissions called")
    ActivityCompat.requestPermissions(
      this,
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
      LOCATION_PERMISSION_REQUEST
    )
  }

  private fun requestCameraPermission() {
    Log.d(TAG, "requestCameraPermission called")
    ActivityCompat.requestPermissions(
      this,
      arrayOf(Manifest.permission.CAMERA),
      CAMERA_PERMISSION_REQUEST
    )
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    Log.d(TAG, "onRequestPermissionsResult called with requestCode: $requestCode")

    when (requestCode) {
      LOCATION_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          Log.d(TAG, "Location permissions granted")
          if (!hasCameraPermission()) {
            requestCameraPermission()
          } else {
            setupAr()
          }
        } else {
          Log.d(TAG, "Location permissions denied")
          showPermissionDeniedDialog()
        }
      }

      CAMERA_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(TAG, "Camera permission granted")
          setupAr()
        } else {
          Log.d(TAG, "Camera permission denied")
          showPermissionDeniedDialog()
        }
      }

      else -> {
        if (::arCoreSessionHelper.isInitialized) {
          arCoreSessionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
      }
    }
  }

  private fun navigateToProfile() {
    Log.d(TAG, "navigateToProfile called")
    val intent = Intent(this, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    intent.putExtra("RETURNED_FROM_AR", true)
    startActivity(intent)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    Log.d(TAG, "onWindowFocusChanged called - hasFocus: $hasFocus")
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
      val x = event.x
      val y = event.y
      Log.d(TAG, "onTouchEvent called - x: $x, y: $y")
      renderer.handleTouch(x, y)
    }
    return super.onTouchEvent(event)
  }

  private fun setupCloseButton() {
    val closeButton = findViewById<ImageButton>(R.id.closeButton)
    closeButton?.setOnClickListener {
      Log.d(TAG, "Close button clicked")
      val intent = Intent(this, MainActivity::class.java)
      intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
      intent.putExtra("RETURNED_FROM_AR", true)
      startActivity(intent)
    }
  }

  override fun onResume() {
    super.onResume()
    Log.d(TAG, "onResume called - Activity resumed and interactive")
  }

  override fun onPause() {
    super.onPause()
    Log.d(TAG, "onPause called - Activity paused but still visible")
  }

  override fun onStop() {
    super.onStop()
    Log.d(TAG, "onStop called - Activity no longer visible")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "onDestroy called - Activity being destroyed")
  }
}