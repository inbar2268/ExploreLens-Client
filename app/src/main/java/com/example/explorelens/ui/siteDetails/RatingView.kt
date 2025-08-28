package com.example.explorelens.ui.siteDetails

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.explorelens.R

/**
 * Custom view that displays a rating as stars with a numeric value
 */
class RatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val starViews = mutableListOf<ImageView>()
    private val ratingTextView: TextView
    private var currentRating: Float = 0f
    private val MAX_STARS = 5

    init {
        orientation = HORIZONTAL

        // Inflate the layout
        LayoutInflater.from(context).inflate(R.layout.view_rating, this, true)

        // Find star ImageViews
        starViews.add(findViewById(R.id.star1))
        starViews.add(findViewById(R.id.star2))
        starViews.add(findViewById(R.id.star3))
        starViews.add(findViewById(R.id.star4))
        starViews.add(findViewById(R.id.star5))

        // Find rating TextView
        ratingTextView = findViewById(R.id.ratingText)

        // Set default rating
        setRating(0f)
    }

    /**
     * Sets the rating and updates the star icons and text accordingly
     * @param rating The rating value (0-5)
     */
    fun setRating(rating: Float) {
        currentRating = rating.coerceIn(0f, 5f)
        updateStars()
        updateRatingText()
    }

    private fun updateStars() {
        val fullStars = currentRating.toInt()
        val hasHalfStar = currentRating - fullStars >= 0.5f

        // Update each star
        for (i in 0 until MAX_STARS) {
            val starView = starViews[i]
            when {
                i < fullStars -> starView.setImageResource(R.drawable.ic_star_full)
                i == fullStars && hasHalfStar -> starView.setImageResource(R.drawable.ic_star_half)
                else -> starView.setImageResource(R.drawable.ic_star_empty)
            }
        }
    }

    private fun updateRatingText() {
        // Format rating to one decimal place
        val formattedRating = String.format("%.1f", currentRating)
        ratingTextView.text = formattedRating
    }

    /**
     * Returns the current rating value
     */
    fun getRating(): Float = currentRating
}