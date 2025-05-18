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
import com.example.explorelens.R
import com.example.explorelens.adapters.ChatAdapter
import com.example.explorelens.databinding.FragmentChatBinding
import com.example.explorelens.data.model.ChatMessage
import com.example.explorelens.data.repository.SiteDetailsRepository
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
    private lateinit var siteDetailsRepository: SiteDetailsRepository

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

    private val historicalSiteResponses = mapOf(
        "eiffel" to "The Eiffel Tower is a world-famous iron structure located in Paris, France. Built by engineer Gustave Eiffel for the 1889 World's Fair, it stands 330 meters tall and was once the tallest man-made structure in the world. Today, it remains one of the most iconic landmarks globally, attracting millions of visitors each year who admire its unique design and panoramic views of Paris.",
        "colosseum" to "The Colosseum is an ancient amphitheater in Rome, Italy, built during the Roman Empire. Completed around 80 AD, it could hold up to 80,000 spectators who came to watch gladiatorial contests, executions, animal hunts, and dramas. It's considered one of the greatest achievements of Roman architecture and engineering.",
        "taj_mahal" to "The Taj Mahal is a magnificent white marble mausoleum located in Agra, India. It was commissioned in 1632 by the Mughal emperor Shah Jahan to house the tomb of his favorite wife, Mumtaz Mahal. The Taj Mahal is renowned for its perfect symmetry, intricate carvings, and the changing colors of its marble as the light shifts throughout the day."
    )

    private val defaultResponse = "I don't have specific information about that aspect of this historical site. Would you like to know about its history, architecture, cultural significance, or interesting facts instead?"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
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

        if (!siteImageUrl.isNullOrEmpty()) {
            loadSiteImage(siteImageUrl)
        } else {
            fetchSiteDetails()
        }

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        setupRecyclerView()
        setupClickListeners()
        loadUserDataAndShowWelcome()
        adjustBottomNavigationVisibility()
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
                                        } else {
                                            binding.siteImageView.visibility = View.GONE
                                        }
                                    }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatFragment", "Error fetching site details: ${e.message}")
                            binding.siteImageView.visibility = View.GONE
                        }
                    }
                }
            }
        } else {
            binding.siteImageView.visibility = View.GONE
        }
    }

    private fun loadSiteImage(imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            try {
                binding.siteImageView.visibility = View.VISIBLE

                Glide.with(requireContext())
                    .load(imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .apply(RequestOptions()
                        .placeholder(R.drawable.eiffel)
                        .error(R.drawable.eiffel)
                        .centerCrop())
                    .into(binding.siteImageView)

            } catch (e: Exception) {
                Log.e("ChatFragment", "Error loading image: ${e.message}")
                binding.siteImageView.visibility = View.GONE
            }
        } else {
            binding.siteImageView.visibility = View.GONE
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
                scrollToBottom()

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
                scrollToBottom()
            }
        }
    }

    private fun adjustBottomNavigationVisibility() {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.VISIBLE
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()

        // Set up RecyclerView with LinearLayoutManager
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.stackFromEnd = true

        // Add padding to the bottom to prevent messages from being hidden behind input
        val bottomInputHeight = resources.getDimensionPixelSize(R.dimen.chat_input_height)
        binding.recyclerView.setPadding(
            binding.recyclerView.paddingLeft,
            binding.recyclerView.paddingTop,
            binding.recyclerView.paddingRight,
            binding.recyclerView.paddingBottom + bottomInputHeight
        )
        binding.recyclerView.clipToPadding = false

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
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.editTextMessage.text.clear()
            }
        }
    }

    private fun sendMessage(message: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            sentByUser = true
        )
        chatMessages.add(userMessage)
        chatAdapter.submitList(ArrayList(chatMessages))
        scrollToBottomImmediately()

        val botResponse = getHistoricalSiteResponse(message)

        binding.root.postDelayed({
            val responseParts = splitIntoParts(botResponse)

            // Use a standard for loop with index
            for (i in responseParts.indices) {
                val part = responseParts[i]
                val botMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = part.trim(),
                    sentByUser = false
                )
                chatMessages.add(botMessage)
                chatAdapter.submitList(ArrayList(chatMessages))

                // Use immediate scroll for all messages
                scrollToBottomImmediately()
            }
        }, 800)
    }

    // Regular smooth scrolling for normal interactions
    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    // Immediate scrolling for when we need to ensure visibility
    private fun scrollToBottomImmediately() {
        if (chatAdapter.itemCount > 0) {
            // First jump to near the bottom
            val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(chatAdapter.itemCount - 1, 0)

            // Then ensure we're fully at the bottom
            binding.recyclerView.post {
                binding.recyclerView.scrollBy(0, 1000) // Scroll down by a large amount to ensure bottom
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

    private fun getHistoricalSiteResponse(message: String): String {
        val lowerMessage = message.lowercase()

        for ((key, value) in historicalSiteResponses) {
            if (lowerMessage.contains(key)) {
                return value
            }
        }

        if (!siteName.isNullOrEmpty()) {
            val siteKey = identifySiteKeyFromName(siteName!!)

            if (lowerMessage.contains("what is") ||
                lowerMessage.contains("tell me about") ||
                lowerMessage.contains("history") ||
                lowerMessage.contains("information")) {

                historicalSiteResponses[siteKey]?.let { return it }
            }

            if (lowerMessage.contains("hour") ||
                lowerMessage.contains("visit") ||
                lowerMessage.contains("open") ||
                lowerMessage.contains("time")) {

                return "Visiting hours for ${siteName} typically vary by season. Most historical sites are open from 9 AM to 5 PM, with last entry about an hour before closing. Ticket prices range from $10-30 for adults, with discounts for students and seniors. Would you like more specific information?"
            }

            if (lowerMessage.contains("ticket") ||
                lowerMessage.contains("price") ||
                lowerMessage.contains("cost") ||
                lowerMessage.contains("fee")) {

                return "Ticket prices for ${siteName} range from $10-30 for adults, with discounts for students and seniors. Many sites offer online booking options with potential discounts. Would you like to know about guided tour options?"
            }

            if (lowerMessage.contains("built") ||
                lowerMessage.contains("architecture") ||
                lowerMessage.contains("design") ||
                lowerMessage.contains("construct")) {

                return "The architecture of ${siteName} represents the distinctive style of its period. The construction techniques used were innovative for their time, using locally sourced materials and advanced engineering methods. Would you like to know more specific architectural details?"
            }

            if (lowerMessage.contains("fact") ||
                lowerMessage.contains("interest") ||
                lowerMessage.contains("did you know")) {

                return "Here's an interesting fact about ${siteName}: Many historical sites were built without modern machinery, using only manual labor and ingenious engineering techniques. The precision achieved in construction often amazes modern architects and engineers."
            }

            historicalSiteResponses[siteKey]?.let { return it }

            return "This is ${siteName}, a fascinating historical site. What specific aspect would you like to know about?"
        }

        if (lowerMessage.contains("what sites") ||
            lowerMessage.contains("which places") ||
            lowerMessage.contains("show me") ||
            lowerMessage.contains("list")) {
            return "I can provide information about: Eiffel Tower, Colosseum, and Taj Mahal. Which one would you like to learn about?"
        }

        return defaultResponse
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