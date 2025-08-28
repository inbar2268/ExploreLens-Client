package com.example.explorelens.ui.siteDetails
/**
 * ARAnchorRepository class for site ratings
 */
data class SiteRating(
    val siteId: String,
    val averageRating: Float,
    val totalRatings: Int
) {
    /**
     * Calculates what the new average rating would be if a new rating was added
     *
     * @param newRating The new rating value to add (0-5)
     * @return The new average rating
     */
    fun calculateNewAverage(newRating: Float): Float {
        val totalScore = averageRating * totalRatings
        val newTotalScore = totalScore + newRating
        val newTotalRatings = totalRatings + 1
        return newTotalScore / newTotalRatings
    }

    /**
     * Creates a new SiteRating object with the updated average and count after adding a new rating
     *
     * @param newRating The new rating value to add (0-5)
     * @return A new SiteRating object with updated values
     */
    fun withNewRating(newRating: Float): SiteRating {
        val newAverage = calculateNewAverage(newRating)
        return copy(
            averageRating = newAverage,
            totalRatings = totalRatings + 1
        )
    }
}