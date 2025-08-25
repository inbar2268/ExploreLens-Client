package com.example.explorelens.ui.user

import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.data.db.User
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

    // Individual loading states for percentage card
    fun showPercentageLoading() {
        binding.percentageProgressBar.visibility = View.VISIBLE
        binding.percentageValue.visibility = View.GONE
    }

    fun hidePercentageLoading() {
        binding.percentageProgressBar.visibility = View.GONE
        binding.percentageValue.visibility = View.VISIBLE
    }

    // Individual loading states for country card
    fun showCountryLoading() {
        binding.countryProgressBar.visibility = View.VISIBLE
        binding.countryValue.visibility = View.GONE
    }

    fun hideCountryLoading() {
        binding.countryProgressBar.visibility = View.GONE
        binding.countryValue.visibility = View.VISIBLE
    }

    // Show loading for both statistics (keeps your existing code working)
    fun showStatisticsLoading() {
        binding.statisticsProgressBar.visibility = View.VISIBLE
        binding.statisticsContainer.visibility = View.GONE
        // Also show individual loading if you want
        // showPercentageLoading()
        // showCountryLoading()
    }

    // Hide loading for both statistics
    fun hideStatisticsLoading() {
        binding.statisticsProgressBar.visibility = View.GONE
        binding.statisticsContainer.visibility = View.VISIBLE
        // Also hide individual loading
        // hidePercentageLoading()
        // hideCountryLoading()
    }

    // Update individual statistics
    fun updatePercentage(percentage: String) {
        hidePercentageLoading()
        binding.percentageValue.text = percentage
    }

    fun updateCountryCount(countryCount: Int) {
        hideCountryLoading()
        binding.countryValue.text = countryCount.toString()
    }

    // Update both statistics at once (for backward compatibility)
    fun updateStatistics(percentage: String, countryCount: Int) {
        hideStatisticsLoading()
        binding.percentageValue.text = percentage
        binding.countryValue.text = countryCount.toString()
    }

    // Show error states for individual cards
    fun showPercentageError() {
        hidePercentageLoading()
        binding.percentageValue.text = "--"
    }

    fun showCountryError() {
        hideCountryLoading()
        binding.countryValue.text = "--"
    }

    // Show error for both statistics
    fun showStatisticsError() {
        hideStatisticsLoading()
        binding.percentageValue.text = "--"
        binding.countryValue.text = "--"
    }

    fun setRefreshing(isRefreshing: Boolean) {
        binding.swipeRefresh.isRefreshing = isRefreshing
    }
}