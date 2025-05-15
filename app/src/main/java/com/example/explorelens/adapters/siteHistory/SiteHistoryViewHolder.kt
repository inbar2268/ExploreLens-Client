package com.example.explorelens.adapters.siteHistory

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.databinding.ItemSiteHistoryBinding
import java.text.SimpleDateFormat
import java.util.*
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

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
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.siteImageView)

        // Set click listener even if API call fails
        binding.root.setOnClickListener {
            onItemClick(siteHistory)
        }

        // Fetch site details to get correct name and image using callback-based method
        val cleanSiteId = siteHistory.siteInfoId.replace(" ", "")
        siteRepository.fetchSiteDetails(
            siteId = cleanSiteId,
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
            onError = {
                Log.e("SiteHistoryViewHolder", "Failed to fetch site details")
                // Keep default values in case of error
            }
        )
    }
}