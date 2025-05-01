package com.example.explorelens.common.helpers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

public class ToastHelper {

    public static void showCustomToast(Context context, String message, int duration) {
        Toast toast = Toast.makeText(context, message, duration);


        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextColor(Color.parseColor("#1976D2"));
        textView.setTextSize(16);
        textView.setPadding(30, 20, 30, 20);
        textView.setGravity(Gravity.CENTER);

        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setColor(0xCCEEEEEE);
        backgroundDrawable.setCornerRadius(15);
        textView.setLayerType(TextView.LAYER_TYPE_SOFTWARE, null);
        textView.getPaint().setShadowLayer(8, 0, 4, Color.argb(80, 0, 0, 0)); // צל

        textView.setBackground(backgroundDrawable);
        toast.setView(textView);
        toast.show();
    }

    public static void showShortToast(Context context, String message) {
        showCustomToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLongToast(Context context, String message) {
        showCustomToast(context, message, Toast.LENGTH_LONG);
    }
}
