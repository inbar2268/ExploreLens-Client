package com.example.explorelens.ui.site

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.ui.site.CommentItem

/**
 * Adapter for displaying comments in a RecyclerView
 */
class CommentsAdapter(private val comments: List<CommentItem>) :
    RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //val profilePic: ImageView = view.findViewById(R.id.profileImageView)
        val username: TextView = view.findViewById(R.id.usernameTextView)
        val commentText: TextView = view.findViewById(R.id.commentTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        holder.username.text = comment.user
        holder.commentText.text = comment.content

        // Set default profile picture
       // holder.profilePic.setImageResource(R.drawable.ic_default_profile)

        // In a real app, you would load the profile image using Glide or similar
        // if (comment.profilePicUrl != null) {
        //     Glide.with(holder.itemView.context)
        //         .load(comment.profilePicUrl)
        //         .circleCrop()
        //         .into(holder.profilePic)
        // }
    }

    override fun getItemCount() = comments.size
}