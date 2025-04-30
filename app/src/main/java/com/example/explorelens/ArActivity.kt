package com.example.explorelens
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
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

  val TAG = "MainActivity"
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var renderer: AppRenderer
  lateinit var view: ArActivityView
  private lateinit var geoLocationUtils: GeoLocationUtils
  private val LOCATION_PERMISSION_REQUEST = 1001
  private val CAMERA_PERMISSION_REQUEST = 1002
  private lateinit var siteHistoryViewModel: SiteHistoryViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    geoLocationUtils = GeoLocationUtils(applicationContext)
    val siteRepository = SiteHistoryRepository(applicationContext)
    siteHistoryViewModel = ViewModelProvider(
      this,
      SiteHistoryViewModel.Factory(siteRepository, geoLocationUtils)
    )[SiteHistoryViewModel::class.java]

    when {
      !hasLocationPermissions() -> {
        requestLocationPermissions()
      }
      !hasCameraPermission() -> {
        requestCameraPermission()
      }
      else -> {
        setupAr()
      }
    }

  }

  private fun setupAr(){
    // Setup ARCore session lifecycle helper and configuration.
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // When session creation or session.resume fails, we display a message and log detailed information.
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


    // Configure session features, including: Lighting Estimation, Depth mode, Instant Placement.
    arCoreSessionHelper.beforeSessionResume = { session ->
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

    // Set up AR UI
    view = ArActivityView(this, renderer)
    setContentView(view.root)
    renderer.bindView(view)
    lifecycle.addObserver(view)

    val closeButton = findViewById<ImageButton>(R.id.closeButton)
    closeButton?.setOnClickListener {
      finish()
    }
  }

  private fun hasLocationPermissions(): Boolean {
    return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
  }

  private fun hasCameraPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
  }


  private fun requestLocationPermissions() {
    ActivityCompat.requestPermissions(
      this,
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ),
      LOCATION_PERMISSION_REQUEST
    )
  }

  private fun requestCameraPermission() {
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

    when (requestCode) {
      LOCATION_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          if (!hasCameraPermission()) {
            requestCameraPermission()
          } else {
            setupAr()
          }
        } else {
          Toast.makeText(this, "Location permissions are required for this feature.", Toast.LENGTH_LONG).show()
          finish()
        }
      }
      CAMERA_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          // Camera permission granted, we can proceed
          setupAr()
        } else {
          // Camera permission denied
          Toast.makeText(this, "Camera permission is required for AR features.", Toast.LENGTH_LONG).show()
          finish()
        }
      }
      else -> {
        if (::arCoreSessionHelper.isInitialized) {
          arCoreSessionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
      val x = event.x
      val y = event.y
      renderer.handleTouch(x, y)
    }
    return super.onTouchEvent(event)
  }

}