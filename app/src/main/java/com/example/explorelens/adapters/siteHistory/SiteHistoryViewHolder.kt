package com.example.explorelens.adapters.siteHistory

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.databinding.ItemSiteHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SiteHistoryViewHolder(
    private val binding: ItemSiteHistoryBinding,
    private val onItemClick: (SiteHistory) -> Unit,
    private val siteRepository: SiteDetailsRepository,
    private val lifecycleOwner: LifecycleOwner
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(siteHistory: SiteHistory) {
        // Format and set date
        val date = Date(siteHistory.createdAt)
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        binding.siteDateTextView.text = formatter.format(date)

        // Set default values
        binding.siteNameTextView.text = ""

        Glide.with(binding.root.context)
            .load(R.drawable.noimage)
            .into(binding.siteImageView)

        // Fetch site details to get correct name and image
        val cleanSiteId = siteHistory.siteInfoId.replace(" ", "")
        lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                siteRepository.fetchSiteDetails(cleanSiteId)
            }

            result.fold(
                onSuccess = { siteDetails ->
                    binding.siteNameTextView.text = siteDetails.name

                    siteDetails.imageUrl?.let { imageUrl ->
                        Glide.with(binding.root.context)
                            .load(imageUrl)
                            .placeholder(R.drawable.noimage)
                            .error(R.drawable.noimage)
                            .centerCrop()
                            .into(binding.siteImageView)
                    }
                },
                onFailure = { error ->
                    Log.e("SiteHistoryViewHolder", "Failed to fetch site details: ${error.message}")
                }
            )
                    // Set click listener even if API call fails
                    binding.root.setOnClickListener {
                        onItemClick(siteHistory)
                    }
                }
    }
}