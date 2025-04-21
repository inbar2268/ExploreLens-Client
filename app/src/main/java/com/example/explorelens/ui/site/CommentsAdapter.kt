package com.example.explorelens.ui.site

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.network.Comment

/**
 * Adapter for displaying comments in a RecyclerView
 */
class CommentsAdapter(private val comments: List<Comment>) :
    RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

        // Add date display if needed
        // If you have a date TextView in your item_comment layout:
        // val dateView = holder.itemView.findViewById<TextView>(R.id.dateTextView)
        // dateView?.text = comment.date ?: ""
    }

    override fun getItemCount() = comments.size
}