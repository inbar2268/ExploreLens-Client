package com.example.explorelens.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.databinding.ItemMessageBotBinding
import com.example.explorelens.databinding.ItemMessageUserBinding
import com.example.explorelens.data.model.ChatMessage

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    // Profile picture URL to be set from the fragment
    private var userProfilePictureUrl: String? = null
    private var username: String? = null

    // Method to set user data
    fun setUserData(profilePictureUrl: String?, username: String?) {
        this.userProfilePictureUrl = profilePictureUrl
        this.username = username
        notifyDataSetChanged() // Update existing viewholders with new profile data
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).sentByUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_BOT -> {
                val binding = ItemMessageBotBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                BotMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message, userProfilePictureUrl)
            is BotMessageViewHolder -> holder.bind(message)
        }
    }

    class UserMessageViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage, profilePictureUrl: String?) {
            binding.textViewUserMessage.text = message.message
            binding.textViewUserTime.text = message.getFormattedTime()

            // Load profile picture if available
            if (!profilePictureUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(profilePictureUrl)
                    .apply(RequestOptions().transform(CircleCrop()))
                    .placeholder(R.drawable.avatar_placeholder)
                    .error(R.drawable.avatar_placeholder)
                    .into(binding.imageViewUserAvatar)
            } else {
                // Use default avatar if no profile picture
                binding.imageViewUserAvatar.setImageResource(R.drawable.avatar_placeholder)
            }
        }
    }

    class BotMessageViewHolder(private val binding: ItemMessageBotBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.textViewBotMessage.text = message.message
            binding.textViewBotTime.text = message.getFormattedTime()

            // Set bot avatar image
            binding.imageViewBotAvatar.setImageResource(R.drawable.ic_bot_avatar)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}