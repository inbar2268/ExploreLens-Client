package com.example.explorelens.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import com.example.explorelens.R

class ThreeDotsAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private var animatorSet: AnimatorSet? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.three_dots_loading, this, true)
        setupViews()
    }

    private fun setupViews() {
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
    }

    fun startAnimation() {
        stopAnimation()

        // Create individual repeating animations for each dot
        val alpha1 = ObjectAnimator.ofFloat(dot1, "alpha", 0.3f, 1.0f, 0.3f)
        alpha1.duration = 1200
        alpha1.startDelay = 0
        alpha1.repeatCount = ObjectAnimator.INFINITE
        alpha1.repeatMode = ObjectAnimator.RESTART

        val alpha2 = ObjectAnimator.ofFloat(dot2, "alpha", 0.3f, 1.0f, 0.3f)
        alpha2.duration = 1200
        alpha2.startDelay = 400
        alpha2.repeatCount = ObjectAnimator.INFINITE
        alpha2.repeatMode = ObjectAnimator.RESTART

        val alpha3 = ObjectAnimator.ofFloat(dot3, "alpha", 0.3f, 1.0f, 0.3f)
        alpha3.duration = 1200
        alpha3.startDelay = 800
        alpha3.repeatCount = ObjectAnimator.INFINITE
        alpha3.repeatMode = ObjectAnimator.RESTART

        // Store references to stop later
        this.animatorSet = AnimatorSet().apply {
            playTogether(alpha1, alpha2, alpha3)
        }

        // Start individual animations
        alpha1.start()
        alpha2.start()
        alpha3.start()
    }

    // Remove the helper methods since we're not using them anymore

    fun stopAnimation() {
        animatorSet?.cancel()
        animatorSet = null

        // Reset all dots to normal state
        dot1.alpha = 0.3f
        dot2.alpha = 0.3f
        dot3.alpha = 0.3f
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        when (visibility) {
            View.VISIBLE -> startAnimation()
            View.GONE, View.INVISIBLE -> stopAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}