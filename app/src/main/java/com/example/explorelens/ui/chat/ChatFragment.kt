package com.example.explorelens.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.BuildConfig
import com.example.explorelens.R
import com.example.explorelens.adapters.chat.ChatAdapter
import com.example.explorelens.databinding.FragmentChatBinding
import com.example.explorelens.data.model.chat.ChatMessage
import com.example.explorelens.data.model.chat.ChatCompletionRequest
import com.example.explorelens.data.repository.ChatRepository
import com.example.explorelens.data.repository.UserRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.util.UUID

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var userRepository: UserRepository
    private lateinit var chatRepository: ChatRepository

    // Chat history for OpenAI API
    private val chatHistory = mutableListOf<ChatCompletionRequest.Message>()

    private var siteName: String? = null
    private var typingMessageId: String? = null

    // Keyboard detection variables
    private var bottomNavigationView: BottomNavigationView? = null
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val defaultWelcomeMessage = "Welcome to HistoryGuide! I can provide information about historical sites and monuments. What would you like to know about?"
    private val siteSpecificWelcomeMessage = "Welcome to HistoryGuide! I'm your virtual guide for %s. What would you like to know about this historical site?"
    private val personalizedWelcomeMessage = "Hi %s! Welcome to HistoryGuide. I'm your virtual guide for %s. What would you like to know about this historical site?"

    private val defaultResponse = "I don't have specific information about that aspect of this historical site. Would you like to know about its history, architecture, cultural significance, or interesting facts instead?"

    // Define system prompt based on site
    private fun getSystemPrompt(): ChatCompletionRequest.Message {
        return if (!siteName.isNullOrEmpty()) {
            ChatCompletionRequest.Message(
                role = "system",
                content = "You are HistoryGuide, a virtual tour guide and expert on $siteName. Provide accurate, engaging, and concise information about this historical site. Focus on history, architecture, cultural significance, and interesting facts. Keep responses friendly and educational."
            )
        } else {
            ChatCompletionRequest.Message(
                role = "system",
                content = "You are HistoryGuide, a virtual tour guide expert on historical sites and monuments worldwide. Provide accurate, engaging, and concise information. Focus on history, architecture, cultural significance, and interesting facts. Keep responses friendly and educational."
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)

        // Initialize ChatRepository with API key
        chatRepository = ChatRepository(BuildConfig.OPENAI_API_KEY)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userRepository = UserRepository(requireActivity().application)

        siteName = arguments?.getString("SITE_NAME_KEY")

        Log.d("ChatFragment", "Site name: $siteName")

        // Set title based on site name
        if (!siteName.isNullOrEmpty()) {
            binding.titleText.text = siteName
        } else {
            binding.titleText.text = "Chat"
        }

        // Setup keyboard detection and bottom navigation handling
        setupKeyboardDetection()

        setupRecyclerView()
        setupClickListeners()

        loadUserDataAndShowWelcome()

        // Force a layout pass to ensure everything is sized correctly
        view.post {
            binding.root.requestLayout()
        }
    }

    private fun setupKeyboardDetection() {
        bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Debug: Check if bottom nav is found
        Log.d("ChatFragment", "Bottom nav found: ${bottomNavigationView != null}")
        Log.d("ChatFragment", "Bottom nav initial visibility: ${bottomNavigationView?.visibility}")

        // Force show bottom navigation when fragment starts
        bottomNavigationView?.visibility = View.VISIBLE

        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (_binding == null) return@OnGlobalLayoutListener

            val rootView = binding.root
            val heightDiff = rootView.rootView.height - rootView.height

            // Debug: Log height difference
            Log.d("ChatFragment", "Height diff: $heightDiff")

            // Increase threshold and add more robust detection
            if (heightDiff > 300) { // Increased from 200 to 300
                // Keyboard is shown
                Log.d("ChatFragment", "Keyboard shown - hiding bottom nav")
                bottomNavigationView?.visibility = View.GONE
                (binding.inputContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).apply {
                    bottomMargin = 0
                    binding.inputContainer.layoutParams = this
                }
            } else {
                // Keyboard is hidden
                Log.d("ChatFragment", "Keyboard hidden - showing bottom nav")
                bottomNavigationView?.visibility = View.VISIBLE
                (binding.inputContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).apply {
                    bottomMargin = (56 * resources.displayMetrics.density).toInt()
                    binding.inputContainer.layoutParams = this
                }
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }    private fun loadUserDataAndShowWelcome() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = userRepository.getUserFromDb()
                chatAdapter.setUserData(user?.profilePictureUrl, user?.username)

                val welcomeMessage = if (!siteName.isNullOrEmpty() && user != null && !user.username.isNullOrEmpty()) {
                    String.format(personalizedWelcomeMessage, user.username, siteName)
                } else if (!siteName.isNullOrEmpty()) {
                    String.format(siteSpecificWelcomeMessage, siteName)
                } else {
                    defaultWelcomeMessage
                }

                val initialMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = welcomeMessage,
                    sentByUser = false
                )

                chatMessages.add(initialMessage)
                chatAdapter.submitList(chatMessages.toList())

                // Use post to ensure the UI has updated before scrolling
                binding.recyclerView.post {
                    scrollToBottomImmediately()
                }

                // Add assistant welcome message to chat history
                chatHistory.add(ChatCompletionRequest.Message(
                    role = "assistant",
                    content = welcomeMessage
                ))

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error loading user data: ${e.message}")

                val welcomeMessage = if (!siteName.isNullOrEmpty()) {
                    String.format(siteSpecificWelcomeMessage, siteName)
                } else {
                    defaultWelcomeMessage
                }

                val initialMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = welcomeMessage,
                    sentByUser = false
                )

                chatMessages.add(initialMessage)
                chatAdapter.submitList(chatMessages.toList())

                // Use post to ensure the UI has updated before scrolling
                binding.recyclerView.post {
                    scrollToBottomImmediately()
                }

                // Add assistant welcome message to chat history
                chatHistory.add(ChatCompletionRequest.Message(
                    role = "assistant",
                    content = welcomeMessage
                ))
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()

        // Set up RecyclerView with LinearLayoutManager
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.stackFromEnd = true

        // Ensure we're using clipToPadding false to show content under the padding area
        binding.recyclerView.clipToPadding = false

        // Add a small bottom padding for aesthetics
        binding.recyclerView.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.recycler_bottom_padding))

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = chatAdapter

        // Add scroll state change listener to detect when scrolling stops
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkIfAtBottom()
                }
            }
        })
    }

    private fun checkIfAtBottom() {
        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        val itemCount = chatAdapter.itemCount

        // If we're not at the bottom, we might want to show a "scroll to bottom" button
        // You can add this feature in the future if needed
        val atBottom = lastVisibleItemPosition >= itemCount - 2 // Give a bit of buffer
        Log.d("ChatFragment", "At bottom: $atBottom, Last visible: $lastVisibleItemPosition, Count: $itemCount")
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            // Ensure bottom navigation is visible when going back
            bottomNavigationView?.visibility = View.VISIBLE
            requireActivity().onBackPressed()
        }

        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.editTextMessage.text.clear()
            }
        }
    }

    private fun showTypingIndicator() {
        val typingMessage = ChatMessage.createTypingMessage()
        typingMessageId = typingMessage.id
        chatMessages.add(typingMessage)
        chatAdapter.submitList(ArrayList(chatMessages))

        // Scroll to show typing indicator
        binding.recyclerView.post {
            scrollToBottomImmediately()
        }
    }

    private fun hideTypingIndicator() {
        typingMessageId?.let { id ->
            chatMessages.removeAll { it.id == id }
            chatAdapter.submitList(ArrayList(chatMessages))
            typingMessageId = null
        }
    }

    private fun sendMessage(message: String) {
        // Add user message to UI immediately
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            sentByUser = true
        )
        chatMessages.add(userMessage)
        chatAdapter.submitList(ArrayList(chatMessages))

        // Ensure we scroll after the list has been updated
        binding.recyclerView.post {
            scrollToBottomImmediately()
        }

        // Show typing indicator
        showTypingIndicator()

        // Process with ChatGPT
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Start with system prompt if it's the first message
                val basePrompt = if (chatHistory.isEmpty()) listOf(getSystemPrompt()) else emptyList()

                // Add new user message to the temporary list for this request
                val currentUserMessage = ChatCompletionRequest.Message(
                    role = "user",
                    content = message
                )

                // Call the repository with complete message history
                val result = chatRepository.sendMessage(
                    basePrompt + chatHistory,
                    message
                )

                // Hide typing indicator
                hideTypingIndicator()

                if (result.isSuccess) {
                    // Get the response text
                    val botResponse = result.getOrNull() ?: defaultResponse

                    // Add user message to history
                    chatHistory.add(currentUserMessage)

                    // Add bot response to history
                    chatHistory.add(ChatCompletionRequest.Message(
                        role = "assistant",
                        content = botResponse
                    ))

                    // Display the response by splitting into parts
                    val responseParts = splitIntoParts(botResponse)

                    for (part in responseParts) {
                        val botMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            message = part.trim(),
                            sentByUser = false
                        )
                        chatMessages.add(botMessage)
                        chatAdapter.submitList(ArrayList(chatMessages))

                        // Ensure we scroll after each message is added
                        binding.recyclerView.post {
                            scrollToBottomImmediately()
                        }
                    }
                } else {
                    // Handle error
                    val errorMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        message = "Sorry, I'm having trouble connecting right now. Let's try again in a moment.",
                        sentByUser = false
                    )
                    chatMessages.add(errorMessage)
                    chatAdapter.submitList(ArrayList(chatMessages))

                    // Ensure we scroll after the error message is added
                    binding.recyclerView.post {
                        scrollToBottomImmediately()
                    }

                    Log.e("ChatFragment", "Error getting response: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                // Hide typing indicator
                hideTypingIndicator()

                // Show error message
                val errorMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = "Sorry, I'm having trouble connecting right now. Let's try again in a moment.",
                    sentByUser = false
                )
                chatMessages.add(errorMessage)
                chatAdapter.submitList(ArrayList(chatMessages))

                // Ensure we scroll after the error message is added
                binding.recyclerView.post {
                    scrollToBottomImmediately()
                }

                Log.e("ChatFragment", "Exception in sendMessage: ${e.message}")
            }
        }
    }

    // Regular smooth scrolling for normal interactions
    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.recyclerView.post {
                binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    // Immediate scrolling for when we need to ensure visibility
    private fun scrollToBottomImmediately() {
        if (chatAdapter.itemCount > 0) {
            try {
                val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(chatAdapter.itemCount - 1, 0)

                // For extra assurance, also use scrollBy
                binding.recyclerView.postDelayed({
                    binding.recyclerView.scrollBy(0, 1000)
                }, 100)
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error scrolling to bottom: ${e.message}")
            }
        }
    }

    private fun splitIntoParts(text: String): List<String> {
        val paragraphs = text.split("\n\n")
        if (paragraphs.size > 1) {
            return paragraphs
        }

        val sentences = text.split(". ")
        if (sentences.size > 1) {
            return sentences.map {
                if (!it.endsWith(".")) "$it." else it
            }
        }

        return listOf(text)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Remove the keyboard listener BEFORE setting binding to null
        keyboardListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        keyboardListener = null

        // Ensure bottom navigation is visible when leaving the fragment
        bottomNavigationView?.visibility = View.VISIBLE
        bottomNavigationView = null

        _binding = null
    }

    companion object {
        fun newInstance(siteName: String): ChatFragment {
            val fragment = ChatFragment()
            val args = Bundle().apply {
                putString("SITE_NAME_KEY", siteName)
            }
            fragment.arguments = args
            return fragment
        }
    }
}