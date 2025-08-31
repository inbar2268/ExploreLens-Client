package com.example.explorelens.ui.user

import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.data.db.user.User
import com.example.explorelens.databinding.FragmentProfileBinding

class ProfileUIHelper(private val binding: FragmentProfileBinding) {

    fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorMessage.visibility = View.GONE
    }

    fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorMessage.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }

    fun hideError() {
        binding.errorMessage.visibility = View.GONE
    }

    fun updateUserInfo(user: User, fragment: ProfileFragment) {
        hideLoading()
        hideError()

        binding.usernameText.text = user.username
        binding.emailText.text = user.email

        // Load profile picture with Glide
        if (!user.profilePictureUrl.isNullOrEmpty()) {
            Glide.with(fragment)
                .load(user.profilePictureUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.avatar_placeholder)
                .error(R.drawable.avatar_placeholder)
                .into(binding.profileImage)
        } else {
            binding.profileImage.setImageResource(R.drawable.avatar_placeholder)
        }
    }

    // ========== PERCENTAGE METHODS ==========
    fun showPercentageLoading() {
        binding.percentageProgressBar.visibility = View.VISIBLE
        binding.percentageContent.visibility = View.GONE
    }

    fun hidePercentageLoading() {
        binding.percentageProgressBar.visibility = View.GONE
        binding.percentageContent.visibility = View.VISIBLE
    }

    fun updatePercentage(percentage: String) {
        hidePercentageLoading()
        binding.percentageValue.text = percentage
    }

    fun showPercentageError() {
        hidePercentageLoading()
        binding.percentageValue.text = "--"
    }

    // ========== COUNTRY METHODS ==========
    fun showCountryLoading() {
        binding.countryProgressBar.visibility = View.VISIBLE
        binding.countryContent.visibility = View.GONE
    }

    fun hideCountryLoading() {
        binding.countryProgressBar.visibility = View.GONE
        binding.countryContent.visibility = View.VISIBLE
    }

    fun updateCountryCount(countryCount: Int) {
        hideCountryLoading()
        binding.countryValue.text = countryCount.toString()
    }

    fun showCountryError() {
        hideCountryLoading()
        binding.countryValue.text = "--"
    }

    // ========== COMBINED METHODS (for backward compatibility) ==========
    fun showStatisticsLoading() {
        binding.statisticsProgressBar.visibility = View.VISIBLE
        binding.statisticsContainer.visibility = View.GONE
        // Also show individual loading
        showPercentageLoading()
        showCountryLoading()
    }

    fun hideStatisticsLoading() {
        binding.statisticsProgressBar.visibility = View.GONE
        binding.statisticsContainer.visibility = View.VISIBLE
        // Also hide individual loading
        hidePercentageLoading()
        hideCountryLoading()
    }

    fun updateStatistics(percentage: String, countryCount: Int) {
        hideStatisticsLoading()
        updatePercentage(percentage)
        updateCountryCount(countryCount)
    }

    fun showStatisticsError() {
        hideStatisticsLoading()
        showPercentageError()
        showCountryError()
    }

    // ========== REFRESH METHODS ==========
    fun setRefreshing(isRefreshing: Boolean) {
        binding.swipeRefresh.isRefreshing = isRefreshing
    }
}