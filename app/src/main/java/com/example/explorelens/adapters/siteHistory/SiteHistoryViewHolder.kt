package com.example.explorelens.adapters.siteHistory

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.databinding.ItemSiteHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class SiteHistoryViewHolder(
    private val binding: ItemSiteHistoryBinding,
    private val onItemClick: (SiteHistory) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(siteHistory: SiteHistory) {
        binding.apply {
            // Set site name (initially with site ID, to be updated later)
            siteNameTextView.text = formatSiteId(siteHistory.siteInfoId)

            // Format and set date
            val date = Date(siteHistory.createdAt)
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            siteDateTextView.text = formatter.format(date)

            // Load a placeholder or generated image
            val siteName = formatSiteId(siteHistory.siteInfoId)
            val imageUrl = "https://source.unsplash.com/800x600/?landmark,${siteName.replace(" ", "+")}"

            Glide.with(root.context)
                .load(imageUrl)
                .placeholder(R.drawable.eiffel)
                .error(R.drawable.eiffel)
                .centerCrop()
                .into(siteImageView)

            // Set click listener
            root.setOnClickListener {
                onItemClick(siteHistory)
            }
        }
    }

//    private fun formatSiteId(siteInfoId: String): String {
//        // Format the site ID to be more readable
//        return siteInfoId.replace(Regex("[^A-Za-z]"), " ")
//            .split(" ")
//            .filter { it.isNotEmpty() }
//            .joinToString(" ") { it.capitalize(Locale.getDefault()) }
//    }
        private fun formatSiteId(siteInfoId: String): String {
        return siteInfoId
    }
}