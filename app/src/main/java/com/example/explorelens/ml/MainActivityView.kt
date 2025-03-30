
package com.example.explorelens.ml
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.explorelens.common.helpers.SnackbarHelper
import com.example.explorelens.common.samplerender.SampleRender
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import com.example.explorelens.ml.AppRenderer.Companion.TAG

/**
 * Wraps [R.layout.activity_main] and controls lifecycle operations for [GLSurfaceView].
 */
class MainActivityView(val activity: MainActivity, renderer: AppRenderer) : DefaultLifecycleObserver {
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