package com.example.explorelens.adapters.chat

import android.animation.ObjectAnimator
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
import com.example.explorelens.databinding.ItemMessageBotTypingBinding
import com.example.explorelens.databinding.ItemMessageUserBinding
import com.example.explorelens.data.model.chat.ChatMessage

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
        private const val VIEW_TYPE_BOT_TYPING = 3
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
        val message = getItem(position)
        return when {
            message.sentByUser -> VIEW_TYPE_USER
            message.isTyping -> VIEW_TYPE_BOT_TYPING
            else -> VIEW_TYPE_BOT
        }
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
            VIEW_TYPE_BOT_TYPING -> {
                val binding = ItemMessageBotTypingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                BotTypingViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message, userProfilePictureUrl)
            is BotMessageViewHolder -> holder.bind(message)
            is BotTypingViewHolder -> holder.bind(message)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is BotTypingViewHolder) {
            holder.stopAnimation()
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
            binding.imageViewBotAvatar.setImageResource(R.drawable.logo_chat)
        }
    }

    class BotTypingViewHolder(private val binding: ItemMessageBotTypingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var animators: List<ObjectAnimator> = emptyList()

        fun bind(message: ChatMessage) {
            // Set bot avatar image
            binding.imageViewBotAvatar.setImageResource(R.drawable.logo_chat)

            // Start typing animation
            startTypingAnimation()
        }

        private fun startTypingAnimation() {
            stopAnimation()

            val dot1 = binding.dot1
            val dot2 = binding.dot2
            val dot3 = binding.dot3

            // Create alpha animations for each dot with staggered delays
            val alpha1 = ObjectAnimator.ofFloat(dot1, "alpha", 0.3f, 1.0f, 0.3f)
            alpha1.duration = 1200
            alpha1.startDelay = 0
            alpha1.repeatCount = ObjectAnimator.INFINITE
            alpha1.repeatMode = ObjectAnimator.RESTART

            val alpha2 = ObjectAnimator.ofFloat(dot2, "alpha", 0.3f, 1.0f, 0.3f)
            alpha2.duration = 1200
            alpha2.startDelay = 400
            alpha2.repeatCount = ObjectAnimator.INFINITE
            alpha2.repeatMode = ObjectAnimator.RESTART

            val alpha3 = ObjectAnimator.ofFloat(dot3, "alpha", 0.3f, 1.0f, 0.3f)
            alpha3.duration = 1200
            alpha3.startDelay = 800
            alpha3.repeatCount = ObjectAnimator.INFINITE
            alpha3.repeatMode = ObjectAnimator.RESTART

            animators = listOf(alpha1, alpha2, alpha3)

            // Start all animations
            animators.forEach { it.start() }
        }

        fun stopAnimation() {
            animators.forEach { it.cancel() }
            animators = emptyList()

            // Reset dots to default state
            binding.dot1.alpha = 0.3f
            binding.dot2.alpha = 0.3f
            binding.dot3.alpha = 0.3f
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