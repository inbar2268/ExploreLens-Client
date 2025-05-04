package com.example.explorelens.ui.site

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.model.comments.Comment
import com.example.explorelens.data.model.comments.CommentWithUser
import com.squareup.picasso.Picasso

/**
 * Adapter for displaying comments in a RecyclerView
 */
class CommentsAdapter(private val commentsWithUser: List<CommentWithUser>) :
    RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePic: ImageView = view.findViewById(R.id.profileImageView)
        val username: TextView = view.findViewById(R.id.usernameTextView)
        val commentText: TextView = view.findViewById(R.id.commentTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val commentWithUser = commentsWithUser[position]
        holder.username.text = commentWithUser.user?.username ?: commentWithUser.comment.user
        holder.commentText.text = commentWithUser.comment.content

        val profilePicUrl = commentWithUser.user?.profilePictureUrl
                if (!profilePicUrl.isNullOrEmpty()) {
                    Picasso.get()
                        .load(profilePicUrl)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(holder.profilePic)
                } else {
                    holder.profilePic.setImageResource(R.drawable.avatar_placeholder)
                }

        // Add date display if needed
        // If you have a date TextView in your item_comment layout:
        // val dateView = holder.itemView.findViewById<TextView>(R.id.dateTextView)
        // dateView?.text = comment.date ?: ""
    }

    override fun getItemCount() = commentsWithUser.size
}