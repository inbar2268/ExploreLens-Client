package com.example.explorelens.adapters.siteHistory

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.databinding.ItemSiteHistoryBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SiteHistoryViewHolder(
    private val binding: ItemSiteHistoryBinding,
    private val onItemClick: (SiteHistory) -> Unit
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
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
            .enqueue(object : Callback<com.example.explorelens.data.model.SiteDetails.SiteDetails> {
                override fun onResponse(
                    call: Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    response: Response<com.example.explorelens.data.model.SiteDetails.SiteDetails>
                ) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()

                        // Update site name
                        siteDetails?.name?.let {
                            binding.siteNameTextView.text = it
                        }

                        // Load image from site details
                        siteDetails?.imageUrl?.let { imageUrl ->
                            Glide.with(binding.root.context)
                                .load(imageUrl)
                                .placeholder(R.drawable.noimage)
                                .error(R.drawable.noimage)
                                .centerCrop()
                                .into(binding.siteImageView)
                        }
                    } else {
                        Log.e("SiteHistoryViewHolder", "Failed to fetch site details: ${response.code()}")
                    }

                    // Set click listener
                    binding.root.setOnClickListener {
                        onItemClick(siteHistory)
                    }
                }

                override fun onFailure(
                    call: Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    t: Throwable
                ) {
                    Log.e("SiteHistoryViewHolder", "Error fetching site details", t)

                    // Set click listener even if API call fails
                    binding.root.setOnClickListener {
                        onItemClick(siteHistory)
                    }
                }
            })
    }
}