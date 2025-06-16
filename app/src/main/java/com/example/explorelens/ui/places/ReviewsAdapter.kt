package com.example.explorelens.ui.places

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.data.db.places.Review
import com.example.explorelens.databinding.ItemReviewBinding

class ReviewsAdapter : ListAdapter<Review, ReviewsAdapter.ReviewViewHolder>(ReviewDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReviewViewHolder(
        private val binding: ItemReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(review: Review) {
            binding.apply {
                authorName.text = review.authorName
                reviewRating.text = String.format("%.0f", review.rating)
                reviewTime.text = review.relativeTimeDescription
                reviewText.text = review.text
            }
        }
    }

    private class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem.authorName == newItem.authorName && oldItem.time == newItem.time
        }

        override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem == newItem
        }
    }
}