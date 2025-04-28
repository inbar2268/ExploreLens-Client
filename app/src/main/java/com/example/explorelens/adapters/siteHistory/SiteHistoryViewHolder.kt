package com.example.explorelens.adapters.siteHistory

import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.databinding.ItemSiteHistoryBinding

class SiteHistoryViewHolder(
    private val binding: ItemSiteHistoryBinding,
    private val onItemClick: (SiteHistory) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(siteHistory: SiteHistory) {
        binding.apply {
            tvSiteName.text = siteHistory.siteInfoId

            root.setOnClickListener {
                onItemClick(siteHistory)
            }
        }
    }
}