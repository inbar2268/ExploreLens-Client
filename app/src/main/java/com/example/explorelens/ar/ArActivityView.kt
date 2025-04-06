
package com.example.explorelens.ar
import android.opengl.GLSurfaceView
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.ArActivity
import com.example.explorelens.common.helpers.SnackbarHelper
import com.example.explorelens.common.samplerender.SampleRender
import com.example.explorelens.R

/**
 * Wraps [R.layout.activity_main] and controls lifecycle operations for [GLSurfaceView].
 */
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

  override fun onResume(owner: LifecycleOwner) {
    super.onResume(owner)
    surfaceView.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    surfaceView.onPause()
  }

  fun post(action: Runnable) = root.post(action)

  /**
   * Toggles the scan button depending on if scanning is in progress.
   */
  fun setScanningActive(active: Boolean) = when(active) {
    true -> {
      snapshotButton.isEnabled = false
//      snapshotButton.setText(activity.getString(R.string.scan_busy))
    }
    false -> {
      snapshotButton.isEnabled = true
//      snapshotButton.setText(activity.getString(R.string.scan_available))
    }
  }
}