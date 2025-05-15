package com.example.explorelens.adapters.siteHistory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.databinding.ItemSiteHistoryBinding

class SiteHistoryAdapter(
    private val onItemClick: (SiteHistory) -> Unit,
    private val siteRepository: SiteDetailsRepository,
    private val lifecycleOwner: LifecycleOwner
) :
    ListAdapter<SiteHistory, SiteHistoryViewHolder>(SiteHistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteHistoryViewHolder {
        val binding = ItemSiteHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SiteHistoryViewHolder(binding, onItemClick,siteRepository,lifecycleOwner)
    }

    override fun onBindViewHolder(holder: SiteHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SiteHistoryDiffCallback : DiffUtil.ItemCallback<SiteHistory>() {
        override fun areItemsTheSame(oldItem: SiteHistory, newItem: SiteHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SiteHistory, newItem: SiteHistory): Boolean {
            return oldItem == newItem
        }
    }
}