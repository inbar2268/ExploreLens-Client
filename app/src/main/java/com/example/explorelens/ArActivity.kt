package com.example.explorelens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.ar.ARCoreSessionLifecycleHelper
import com.example.explorelens.ar.AppRenderer
import com.example.explorelens.ar.ArActivityView
import com.example.explorelens.common.helpers.FullScreenHelper
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.model.siteHistory.FilterOption
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.databinding.ActivityMainBinding
import com.example.explorelens.utils.GeoLocationUtils
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.exceptions.*
import java.util.EnumSet

/**
 * Main AR activity that handles camera preview, AR rendering, and location-based services
 */
class ArActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ArActivity"
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CAMERA_PERMISSION_REQUEST = 1002
        private const val ALL_PERMISSIONS_REQUEST = 1003

        // Permission states
        private const val PERMISSIONS_GRANTED = 0
        private const val LOCATION_PERMISSION_REQUIRED = 1
        private const val CAMERA_PERMISSION_REQUIRED = 2
        private const val ALL_PERMISSIONS_REQUIRED = 3
    }

    // View binding
    private lateinit var binding: ActivityMainBinding

    // Core AR components
    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    private lateinit var renderer: AppRenderer
    lateinit var view: ArActivityView

    // Utils and data
    private lateinit var geoLocationUtils: GeoLocationUtils
    private lateinit var siteHistoryViewModel: SiteHistoryViewModel

    // Filter options data
    private val filterOptionsArray = arrayOf(
        FilterOption("restaurant", iconResId = R.drawable.ic_restaurant),
        FilterOption("cafe", iconResId = R.drawable.ic_cafe),
        FilterOption("bar", iconResId = R.drawable.ic_bar),
        FilterOption("bakery", iconResId = R.drawable.ic_bakery),
        FilterOption("lodging", iconResId = R.drawable.ic_hotel),
        FilterOption("pharmacy", iconResId = R.drawable.ic_pharmacy),
        FilterOption("gym", iconResId = R.drawable.ic_gym)
    )

    // Permission state tracking
    private var permissionState = ALL_PERMISSIONS_REQUIRED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        configureWindowSettings()
    }

    private fun initializeComponents() {
        geoLocationUtils = GeoLocationUtils(applicationContext)
        val siteRepository = SiteHistoryRepository(applicationContext)
        siteHistoryViewModel = ViewModelProvider(
            this,
            SiteHistoryViewModel.Factory(siteRepository, geoLocationUtils)
        )[SiteHistoryViewModel::class.java]

        Log.d(TAG, "ViewModel initialized")
    }

    private fun configureWindowSettings() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called - Activity becoming visible")
        checkAndRequestPermissions()
    }

    /**
     * Check permission status and request missing permissions
     */
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "checkAndRequestPermissions called")

        // Determine permission state
        updatePermissionState()

        when (permissionState) {
            PERMISSIONS_GRANTED -> {
                Log.d(TAG, "All permissions granted. Setting up AR...")
                setupAr()
            }

            LOCATION_PERMISSION_REQUIRED -> {
                Log.d(TAG, "Location permissions not granted. Requesting...")
                showPermissionRationale(
                    "Location Access Needed",
                    "ExploreLens needs your location to identify points of interest around you.",
                    LOCATION_PERMISSION_REQUEST
                )
            }

            CAMERA_PERMISSION_REQUIRED -> {
                Log.d(TAG, "Camera permissions not granted. Requesting...")
                showPermissionRationale(
                    "Camera Access Needed",
                    "ExploreLens needs camera access to provide the AR experience.",
                    CAMERA_PERMISSION_REQUEST
                )
            }

            ALL_PERMISSIONS_REQUIRED -> {
                Log.d(TAG, "Multiple permissions not granted. Requesting all...")
                showPermissionRationale(
                    "Permissions Required",
                    "ExploreLens needs camera and location access to provide the AR experience.",
                    ALL_PERMISSIONS_REQUEST
                )
            }
        }
    }

    /**
     * Update the current permission state based on granted permissions
     */
    private fun updatePermissionState() {
        val hasLocationPermission = hasLocationPermissions()
        val hasCameraPermission = hasCameraPermission()

        permissionState = when {
            hasLocationPermission && hasCameraPermission -> PERMISSIONS_GRANTED
            hasLocationPermission -> CAMERA_PERMISSION_REQUIRED
            hasCameraPermission -> LOCATION_PERMISSION_REQUIRED
            else -> ALL_PERMISSIONS_REQUIRED
        }

        Log.d(TAG, "Permission state updated: $permissionState")
    }

    /**
     * Shows rationale before requesting permissions
     */
    private fun showPermissionRationale(title: String, message: String, requestCode: Int) {
        // Check if we should show rationale for any permission
        val shouldShowLocationRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val shouldShowCameraRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.CAMERA
        )

        if (shouldShowLocationRationale || shouldShowCameraRationale) {
            // User has denied before, show rationale
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Grant") { _, _ ->
                    requestPermissionsBasedOnCode(requestCode)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Log.d(TAG, "Permission rationale denied by user")
                    showPermissionRequiredDialog()
                }
                .show()
        } else {
            // First time asking or user selected "Don't ask again"
            requestPermissionsBasedOnCode(requestCode)
        }
    }

    /**
     * Request permissions based on request code
     */
    private fun requestPermissionsBasedOnCode(requestCode: Int) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> requestLocationPermissions()
            CAMERA_PERMISSION_REQUEST -> requestCameraPermission()
            ALL_PERMISSIONS_REQUEST -> requestAllPermissions()
        }
    }

    /**
     * Setup AR components after permissions are granted
     */
    private fun setupAr() {
        Log.d(TAG, "setupAr called")

        // Setup ARCore session lifecycle helper
        setupARCoreSession()

        // Set up AR renderer
        setupRenderer()

        // Set up AR UI
        setupArView()
    }

    private fun setupARCoreSession() {
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        Log.d(TAG, "ARCoreSessionLifecycleHelper initialized")

        arCoreSessionHelper.exceptionCallback = { exception ->
            val message = getARExceptionMessage(exception)
            Log.e(TAG, message, exception)
            ToastHelper.showShortToast(this, message)
        }
        arCoreSessionHelper.beforeSessionResume = { session ->
            Log.d(TAG, "beforeSessionResume called")

            session.configure(
                session.config.apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                    }
                    if (session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                        geospatialMode = Config.GeospatialMode.ENABLED
                    } else {
                        Log.w(TAG, "GeospatialMode is not supported on this device.")
                    }
                }
            )

            val filter = CameraConfigFilter(session)
                .setFacingDirection(CameraConfig.FacingDirection.BACK)
                .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
            val configs = session.getSupportedCameraConfigs(filter)
            val sort = compareByDescending<CameraConfig> { it.imageSize.width }
                .thenByDescending { it.imageSize.height }
            session.cameraConfig = configs.sortedWith(sort)[0]
        }

        lifecycle.addObserver(arCoreSessionHelper)
    }

    private fun getARExceptionMessage(exception: Exception): String {
        return when (exception) {
            is UnavailableArcoreNotInstalledException,
            is UnavailableUserDeclinedInstallationException -> "Please install ARCore"
            is UnavailableApkTooOldException -> "Please update ARCore"
            is UnavailableSdkTooOldException -> "Please update this app"
            is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
            is CameraNotAvailableException -> "Camera not available. Try restarting the app."
            else -> "Failed to create AR session: $exception"
        }
    }

    private fun setupRenderer() {
        renderer = AppRenderer(this, geoLocationUtils, siteHistoryViewModel)
        lifecycle.addObserver(renderer)
        Log.d(TAG, "AR renderer setup complete")
    }

    private fun setupArView() {
        view = ArActivityView(this, renderer, filterOptionsArray)
        setContentView(view.root)
        renderer.bindView(view)
        lifecycle.addObserver(view)
        Log.d(TAG, "AR UI setup complete")
    }

    /**
     * Show dialog when permissions are permanently denied
     */
    private fun showPermissionRequiredDialog() {
        Log.d(TAG, "showPermissionRequiredDialog called")

        // Determine which permissions are permanently denied
        val locationPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) &&
                !hasLocationPermissions()

        val cameraPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.CAMERA
        ) &&
                !hasCameraPermission()

        // Create appropriate message based on denied permissions
        val message = when {
            locationPermanentlyDenied && cameraPermanentlyDenied ->
                "Both location and camera permissions are required for ExploreLens to work. " +
                        "Please enable them in Settings."

            locationPermanentlyDenied ->
                "Location permission is required to identify points of interest around you. " +
                        "Please enable it in Settings."

            cameraPermanentlyDenied ->
                "Camera permission is required for the AR experience. " +
                        "Please enable it in Settings."

            else ->
                "Required permissions were denied. ExploreLens needs these permissions to function properly."
        }

        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Exit") { _, _ ->
                Log.d(TAG, "User declined to grant permissions through settings")
                navigateToProfile()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Open app settings so user can enable permissions
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val granted = fineLocationGranted && coarseLocationGranted
        Log.d(TAG, "hasLocationPermissions: $granted")
        return granted
    }

    private fun hasCameraPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "hasCameraPermission: $granted")
        return granted
    }

    private fun requestLocationPermissions() {
        Log.d(TAG, "requestLocationPermissions called")
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
        Log.d(TAG, "requestCameraPermission called")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    private fun requestAllPermissions() {
        Log.d(TAG, "requestAllPermissions called")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            ),
            ALL_PERMISSIONS_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "onRequestPermissionsResult called with requestCode: $requestCode")

        if (grantResults.isEmpty()) {
            Log.d(TAG, "Permission request was cancelled")
            updatePermissionState()
            return
        }

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Location permissions granted")
                    updatePermissionState()
                    checkAndRequestPermissions() // Continue with permission flow
                } else {
                    Log.d(TAG, "Location permissions denied")
                    updatePermissionState()
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            this, Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    ) {
                        // User selected "Don't ask again"
                        showPermissionRequiredDialog()
                    } else {
                        // User simply denied
                        checkAndRequestPermissions() // Continue with permission flow
                    }
                }
            }

            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted")
                    updatePermissionState()
                    checkAndRequestPermissions() // Continue with permission flow
                } else {
                    Log.d(TAG, "Camera permission denied")
                    updatePermissionState()
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            this, Manifest.permission.CAMERA
                        )
                    ) {
                        // User selected "Don't ask again"
                        showPermissionRequiredDialog()
                    } else {
                        // User simply denied
                        checkAndRequestPermissions() // Continue with permission flow
                    }
                }
            }

            ALL_PERMISSIONS_REQUEST -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Log.d(TAG, "All permissions granted")
                    updatePermissionState()
                    setupAr() // All permissions granted, proceed with setup
                } else {
                    Log.d(TAG, "Some permissions denied")
                    updatePermissionState()
                    checkAndRequestPermissions() // Continue with permission flow for remaining permissions
                }
            }

            else -> {
                if (::arCoreSessionHelper.isInitialized) {
                    arCoreSessionHelper.onRequestPermissionsResult(
                        requestCode,
                        permissions,
                        grantResults
                    )
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
        finish() // Finish this activity so it's removed from the stack
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

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - Activity resumed and interactive")

        // Re-check permissions if returning from settings
        if (permissionState != PERMISSIONS_GRANTED) {
            updatePermissionState()
            if (permissionState == PERMISSIONS_GRANTED) {
                setupAr()
            }
        }
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

    override fun onBackPressed() {
        // Check if LayerDetailFragment is currently showing
        val layerDetailFragment = supportFragmentManager.findFragmentByTag("LayerDetailFragment")

        if (layerDetailFragment != null && layerDetailFragment.isVisible) {
            Log.d(TAG, "Closing LayerDetailFragment and returning to AR")

            // Remove the fragment
            supportFragmentManager.beginTransaction()
                .remove(layerDetailFragment)
                .commit()

            // Show AR view again
            showArView()

            // Remove the fragment container if it was dynamically created
            removeFragmentContainer()

        } else if (supportFragmentManager.backStackEntryCount > 0) {
            Log.d(TAG, "Popping fragment from back stack")
            supportFragmentManager.popBackStack()

            // Show AR view again after any fragment is popped
            showArView()

        } else {
            super.onBackPressed()
        }
    }

    private fun showArView() {
        if (::view.isInitialized) {
            view.root.visibility = View.VISIBLE
            Log.d(TAG, "AR view is now visible")
        }
    }

    /**
     * Remove dynamically created fragment container
     */
    private fun removeFragmentContainer() {
        val fragmentContainer = findViewById<ViewGroup>(R.id.fragment_container)
            ?: findViewById<ViewGroup>(android.R.id.content)?.findViewWithTag<ViewGroup>("fragment_container")

        fragmentContainer?.let { container ->
            if (container.tag == "fragment_container") {
                (container.parent as? ViewGroup)?.removeView(container)
                Log.d(TAG, "Removed dynamic fragment container")
            }
        }
    }

    /**
     * Set up fragment result listener to handle fragment dismissal
     */
    private fun setupFragmentResultListener() {
        supportFragmentManager.setFragmentResultListener(
            "layer_detail_closed",
            this
        ) { _, bundle ->
            Log.d(TAG, "LayerDetailFragment closed via result listener")
            showArView()
            removeFragmentContainer()
        }
    }

    // Call this in your onCreate or setupAr method
    private fun initializeFragmentSupport() {
        setupFragmentResultListener()
    }
    fun showArViewSafely() {
        try {
            if (::view.isInitialized) {
                view.root.visibility = View.VISIBLE
                Log.d(TAG, "AR view restored to visible")
            } else {
                Log.w(TAG, "AR view not yet initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing AR view: ${e.message}")
        }
    }

}