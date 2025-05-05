
package com.example.explorelens.common.helpers;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Helper to manage the sample snackbar. Hides the Android boilerplate code, and exposes simpler
 * methods.
 */
public final class SnackbarHelper {
  private static final int BACKGROUND_COLOR = 0xCCEEEEEE;
  private Snackbar messageSnackbar;
  private enum DismissBehavior { HIDE, SHOW, FINISH };
  private int maxLines = 2;
  private String lastMessage = "";
  private View snackbarView;

  public boolean isShowing() {
    return messageSnackbar != null;
  }

  /** Shows a snackbar with a given message. */
  public void showMessage(Activity activity, String message) {
    if (!message.isEmpty() && (!isShowing() || !lastMessage.equals(message))) {
      lastMessage = message;
      show(activity, message, DismissBehavior.HIDE);
    }
  }

  /** Shows a snackbar with a given message, and a dismiss button. */
  public void showMessageWithDismiss(Activity activity, String message) {
    show(activity, message, DismissBehavior.SHOW);
  }

  /**
   * Shows a snackbar with a given error message. When dismissed, will finish the activity. Useful
   * for notifying errors, where no further interaction with the activity is possible.
   */
  public void showError(Activity activity, String errorMessage) {
    show(activity, errorMessage, DismissBehavior.FINISH);
  }

  /**
   * Hides the currently showing snackbar, if there is one. Safe to call from any thread. Safe to
   * call even if snackbar is not shown.
   */
  public void hide(Activity activity) {
    if (!isShowing()) {
      return;
    }
    lastMessage = "";
    Snackbar messageSnackbarToHide = messageSnackbar;
    messageSnackbar = null;
    activity.runOnUiThread(() -> messageSnackbarToHide.dismiss());
  }

  public void setMaxLines(int lines) {
    maxLines = lines;
  }


  public void setParentView(View snackbarView) {
    this.snackbarView = snackbarView;
  }

  private void show(
      final Activity activity, final String message, final DismissBehavior dismissBehavior) {
    activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            messageSnackbar =
                Snackbar.make(
                    snackbarView == null
                        ? activity.findViewById(android.R.id.content)
                        : snackbarView,
                    message,
                    Snackbar.LENGTH_LONG);

            messageSnackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);
            messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
            TextView textView = messageSnackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            textView.setMaxLines(maxLines);
            textView.setTextColor(Color.BLACK);
            if (dismissBehavior != DismissBehavior.HIDE) {
              messageSnackbar.setAction(
                  "Dismiss",
                  v -> messageSnackbar.dismiss());
              messageSnackbar.setActionTextColor(Color.parseColor("#1976D2"));
              if (dismissBehavior == DismissBehavior.FINISH) {
                messageSnackbar.addCallback(
                    new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                      @Override
                      public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                      }
                    });
              }
            }
            messageSnackbar.show();

            View snackbarViewObj = messageSnackbar.getView();
            ViewGroup.LayoutParams layoutParams = snackbarViewObj.getLayoutParams();

            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
              CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) layoutParams;
              params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
              params.topMargin = 50;
              snackbarViewObj.setLayoutParams(params);
            }
            }


        });
  }
}