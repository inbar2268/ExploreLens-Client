package com.example.explorelens.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.BuildConfig
import com.example.explorelens.R
import com.example.explorelens.adapters.ChatAdapter
import com.example.explorelens.databinding.FragmentChatBinding
import com.example.explorelens.data.model.ChatMessage
import com.example.explorelens.data.model.chat.ChatCompletionRequest
import com.example.explorelens.data.repository.ChatRepository
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.data.repository.UserRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.util.UUID
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var userRepository: UserRepository
    private lateinit var siteDetailsRepository: SiteDetailsRepository
    private lateinit var chatRepository: ChatRepository

    // Chat history for OpenAI API
    private val chatHistory = mutableListOf<ChatCompletionRequest.Message>()

    private var siteName: String? = null
    private var siteImageUrl: String? = null

    private val defaultWelcomeMessage = "Welcome to HistoryGuide! I can provide information about historical sites and monuments. What would you like to know about?"
    private val siteSpecificWelcomeMessage = "Welcome to HistoryGuide! I'm your virtual guide for %s. What would you like to know about this historical site?"
    private val personalizedWelcomeMessage = "Hi %s! Welcome to HistoryGuide. I'm your virtual guide for %s. What would you like to know about this historical site?"

    private val siteImages = mapOf(
        "eiffel" to R.drawable.eiffel,
        "colosseum" to R.drawable.eiffel,
        "taj_mahal" to R.drawable.eiffel
    )

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

        // Pre-set image container height as early as possible
        _binding?.siteImageView?.layoutParams?.height = resources.getDimensionPixelSize(R.dimen.site_image_height)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userRepository = UserRepository(requireActivity().application)
        siteDetailsRepository = SiteDetailsRepository(requireContext())

        siteName = arguments?.getString("SITE_NAME_KEY")
        siteImageUrl = arguments?.getString("SITE_IMAGE_URL_KEY")

        Log.d("ChatFragment", "Site name: $siteName")
        Log.d("ChatFragment", "Site image URL: $siteImageUrl")

        if (!siteName.isNullOrEmpty()) {
            binding.titleText.text = siteName
        } else {
            binding.titleText.text = "Chat"
        }

        // Fix image size in various ways
        fixImageContainerSize()

        // Keep bottom navigation visible but ensure our input is above it
        adjustInputForBottomNavigation()

        setupRecyclerView()
        setupClickListeners()

        // Load image after RecyclerView is set up
        if (!siteImageUrl.isNullOrEmpty()) {
            loadSiteImage(siteImageUrl)
        } else {
            fetchSiteDetails()
        }

        loadUserDataAndShowWelcome()

        // Force a layout pass to ensure everything is sized correctly
        view.post {
            binding.root.requestLayout()
        }

        // Add another delayed layout pass for good measure
        view.postDelayed({
            binding.root.requestLayout()
        }, 300)
    }

    private fun fixImageContainerSize() {
        // Make sure image view has the right sizing
        binding.siteImageView.layoutParams.height = resources.getDimensionPixelSize(R.dimen.site_image_height)
        binding.siteImageView.minimumHeight = resources.getDimensionPixelSize(R.dimen.site_image_height)
        binding.siteImageView.maxHeight = resources.getDimensionPixelSize(R.dimen.site_image_height)

        // Request layout to apply changes
        binding.siteImageView.requestLayout()

        // Make sure card view wraps content properly
        binding.imageCardView.requestLayout()
    }

    private fun adjustInputForBottomNavigation() {
        // Find the bottom navigation view
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Make sure navigation stays visible
        bottomNav?.visibility = View.VISIBLE

        // Make sure navigation is below our input by setting a higher elevation on input
        binding.editTextLayout.elevation = 10f
    }

    private fun fetchSiteDetails() {
        if (!siteName.isNullOrEmpty()) {
            val siteKey = identifySiteKeyFromName(siteName!!)

            val imageResId = siteImages[siteKey]
            if (imageResId != null) {
                binding.siteImageView.apply {
                    setImageResource(imageResId)
                    visibility = View.VISIBLE
                }

                // Make sure the site name is displayed in the label
                binding.siteImageLabel.text = siteName
                binding.siteImageLabel.visibility = View.VISIBLE

                // Apply sizing constraints
                fixImageContainerSize()

            } else {
                val siteId = siteName?.replace(" ", "")
                if (!siteId.isNullOrEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val result = siteDetailsRepository.syncSiteDetails(siteId)
                            if (result.isSuccess) {
                                siteDetailsRepository.getSiteDetailsLiveData(siteId)
                                    .observe(viewLifecycleOwner) { siteDetails ->
                                        if (siteDetails != null && !siteDetails.imageUrl.isNullOrEmpty()) {
                                            siteImageUrl = siteDetails.imageUrl
                                            loadSiteImage(siteDetails.imageUrl)

                                            // Update label with site name
                                            binding.siteImageLabel.text = siteName
                                            binding.siteImageLabel.visibility = View.VISIBLE
                                        } else {
                                            binding.siteImageView.visibility = View.GONE
                                            binding.siteImageLabel.visibility = View.GONE
                                        }
                                    }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatFragment", "Error fetching site details: ${e.message}")
                            binding.siteImageView.visibility = View.GONE
                            binding.siteImageLabel.visibility = View.GONE
                        }
                    }
                }
            }
        } else {
            binding.siteImageView.visibility = View.GONE
            binding.siteImageLabel.visibility = View.GONE
        }
    }

    private fun loadSiteImage(imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            try {
                binding.siteImageView.visibility = View.VISIBLE

                // Make sure site name is displayed in the label (add this line)
                if (!siteName.isNullOrEmpty()) {
                    binding.siteImageLabel.text = siteName
                    binding.siteImageLabel.visibility = View.VISIBLE
                }

                // Apply fixed height constraints
                fixImageContainerSize()

                // Create request options with exactly how we want the image to fit
                val requestOptions = RequestOptions()
                    .placeholder(R.drawable.eiffel)
                    .error(R.drawable.eiffel)
                    .dontTransform()  // Don't apply any automatic transformations
                    .override(Target.SIZE_ORIGINAL, resources.getDimensionPixelSize(R.dimen.site_image_height))
                    .fitCenter()      // Try fitCenter instead of centerCrop

                // Load the image with Glide
                Glide.with(requireContext())
                    .load(imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .apply(requestOptions)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            // Keep correct height even on failure
                            fixImageContainerSize()

                            // Make sure site name is still shown even if image fails to load
                            if (!siteName.isNullOrEmpty()) {
                                binding.siteImageLabel.text = siteName
                                binding.siteImageLabel.visibility = View.VISIBLE
                            }
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("ChatFragment", "Image loaded successfully - fixing size again")
                            // Force layout updates after image is ready
                            fixImageContainerSize()

                            // Ensure site name is displayed after image is loaded
                            if (!siteName.isNullOrEmpty()) {
                                binding.siteImageLabel.text = siteName
                                binding.siteImageLabel.visibility = View.VISIBLE
                            }

                            // Use a delayed post to ensure image scaling is correct
                            binding.root.post {
                                fixImageContainerSize()
                                scrollToBottom()
                            }

                            // Try one more time with a longer delay
                            binding.root.postDelayed({
                                fixImageContainerSize()
                            }, 100)

                            return false
                        }
                    })
                    .into(binding.siteImageView)

                // Apply a backup sizing fix after the into() call
                binding.root.postDelayed({
                    fixImageContainerSize()

                    // One last check to make sure site name is displayed
                    if (!siteName.isNullOrEmpty()) {
                        binding.siteImageLabel.text = siteName
                        binding.siteImageLabel.visibility = View.VISIBLE
                    }
                }, 500)

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error loading image: ${e.message}")
                binding.siteImageView.visibility = View.GONE

                // Hide the label if there's no image
                binding.siteImageLabel.visibility = View.GONE
            }
        } else {
            binding.siteImageView.visibility = View.GONE
            binding.siteImageLabel.visibility = View.GONE
        }
    }

    private fun loadUserDataAndShowWelcome() {
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
            // Use requireActivity().onBackPressed() for older versions of Android
            // or navigation for newer versions based on your app's setup
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

        // Show a loading indicator
        binding.messageLoadingIndicator.visibility = View.VISIBLE

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

                // Hide loading indicator
                binding.messageLoadingIndicator.visibility = View.GONE

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
                // Hide loading indicator
                binding.messageLoadingIndicator.visibility = View.GONE

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

    private fun identifySiteKeyFromName(siteName: String): String {
        val lowerSiteName = siteName.lowercase()

        return when {
            lowerSiteName.contains("eiffel") -> "eiffel"
            lowerSiteName.contains("colosseum") -> "colosseum"
            lowerSiteName.contains("taj") -> "taj_mahal"
            else -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // No need to restore bottom navigation as we're not hiding it anymore

        _binding = null
    }

    companion object {
        fun newInstance(siteName: String, siteImageUrl: String?): ChatFragment {
            val fragment = ChatFragment()
            val args = Bundle().apply {
                putString("SITE_NAME_KEY", siteName)
                putString("SITE_IMAGE_URL_KEY", siteImageUrl)
            }
            fragment.arguments = args
            return fragment
        }
    }
}