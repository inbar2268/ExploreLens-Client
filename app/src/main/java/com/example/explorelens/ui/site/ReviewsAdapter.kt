package com.example.explorelens.ui.site

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.explorelens.R
import com.example.explorelens.data.model.comments.ReviewWithUser

/**
 * Adapter for displaying reviews in a RecyclerView
 */
class ReviewsAdapter(private val reviewsWithUser: List<ReviewWithUser>) :
    RecyclerView.Adapter<ReviewsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePic: ImageView = view.findViewById(R.id.profileImageView)
        val username: TextView = view.findViewById(R.id.usernameTextView)
        val reviewText: TextView = view.findViewById(R.id.commentTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reviewWithUser = reviewsWithUser[position]
        holder.username.text = reviewWithUser.user?.username ?: reviewWithUser.review.user
        holder.reviewText.text = reviewWithUser.review.content


        val profilePicUrl = reviewWithUser.user?.profilePictureUrl
        if (!profilePicUrl.isNullOrEmpty()) {
            Glide.with(holder.profilePic.context)
                .load(profilePicUrl)
                .placeholder(R.drawable.avatar_placeholder)
                .error(R.drawable.avatar_placeholder)
                .into(holder.profilePic)
        } else {
            holder.profilePic.setImageResource(R.drawable.avatar_placeholder)
        }


        // Add date display if needed
        // If you have a date TextView in your item_review layout:
        // val dateView = holder.itemView.findViewById<TextView>(R.id.dateTextView)
        // dateView?.text = review.date ?: ""
    }

    override fun getItemCount() = reviewsWithUser.size
}