package com.example.explorelens.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.explorelens.R
import com.example.explorelens.adapters.ChatAdapter
import com.example.explorelens.databinding.FragmentChatBinding
import com.example.explorelens.data.model.ChatMessage
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.UUID

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Site name passed from SiteDetailsFragment
    private var siteName: String? = null

    // Mock data for historical site information
    private val defaultWelcomeMessage = "Welcome to HistoryGuide! I can provide information about historical sites and monuments. What would you like to know about?"
    private val siteSpecificWelcomeMessage = "Welcome to HistoryGuide! I'm your virtual guide for %s. What would you like to know about this historical site?"

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

        // Get site name from arguments
        siteName = arguments?.getString("SITE_NAME_KEY")

        // Log the site name for debugging
        Log.d("ChatFragment", "Site name: $siteName")

        // Set up title
        if (!siteName.isNullOrEmpty()) {
            binding.titleText.text = siteName
        } else {
            binding.titleText.text = "Chat"
        }

        // Set up site image if relevant
        setupSiteImage()

        // Set up back button
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        setupRecyclerView()
        setupClickListeners()

        // Load initial welcome message
        val welcomeMessage = if (siteName.isNullOrEmpty()) {
            defaultWelcomeMessage
        } else {
            String.format(siteSpecificWelcomeMessage, siteName)
        }

        val initialMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = welcomeMessage,
            sentByUser = false
        )
        chatMessages.add(initialMessage)
        chatAdapter.submitList(chatMessages.toList())

        // Scroll to the bottom to show the initial message
        binding.recyclerView.post {
            binding.recyclerView.scrollToPosition(chatMessages.size - 1)
        }

        // Ensure the bottom navigation bar is visible but doesn't overlap with the chat input
        adjustBottomNavigationVisibility()
    }

    private fun adjustBottomNavigationVisibility() {
        // Find the bottom navigation and ensure it's visible
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.VISIBLE

        // Input layout already has margin in the XML to prevent overlap
    }

    private fun setupSiteImage() {
        // Show site image based on site name if available
        val imageResId = when {
            siteName?.contains("eiffel", ignoreCase = true) == true -> R.drawable.eiffel
            else -> 0
        }

        if (imageResId != 0) {
            binding.siteImageView.apply {
                setImageResource(imageResId)
                visibility = View.VISIBLE
            }
        } else {
            binding.siteImageView.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true  // To show newest messages at the bottom
            }
            adapter = chatAdapter
        }
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
        // Add user message
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            sentByUser = true
        )
        chatMessages.add(userMessage)
        chatAdapter.submitList(chatMessages.toList())

        // Get bot response based on user message
        val botResponse = getHistoricalSiteResponse(message)

        // Split bot response into separate messages by paragraphs or sentences
        binding.root.postDelayed({
            val responseParts = splitIntoParts(botResponse)

            for (part in responseParts) {
                val botMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    message = part.trim(),
                    sentByUser = false
                )
                chatMessages.add(botMessage)
                chatAdapter.submitList(chatMessages.toList())

                // Scroll to the bottom
                binding.recyclerView.scrollToPosition(chatMessages.size - 1)
            }
        }, 800) // 800ms delay
    }

    private fun splitIntoParts(text: String): List<String> {
        // Split by paragraphs first
        val paragraphs = text.split("\n\n")
        if (paragraphs.size > 1) {
            return paragraphs
        }

        // If no paragraphs, split by sentences
        val sentences = text.split(". ")
        if (sentences.size > 1) {
            return sentences.map {
                if (!it.endsWith(".")) "$it." else it
            }
        }

        // If it's a short text, return it as is
        return listOf(text)
    }

    private fun getHistoricalSiteResponse(message: String): String {
        // Convert to lowercase for case-insensitive matching
        val lowerMessage = message.lowercase()

        // Check if the user is asking about a specific site
        for ((key, value) in historicalSiteResponses) {
            if (lowerMessage.contains(key)) {
                return value
            }
        }

        // Process site-specific questions if we have a site name
        if (!siteName.isNullOrEmpty()) {
            val siteKey = identifySiteKeyFromName(siteName!!)

            // General information or history
            if (lowerMessage.contains("what is") ||
                lowerMessage.contains("tell me about") ||
                lowerMessage.contains("history") ||
                lowerMessage.contains("information")) {

                // Return site-specific information if available
                historicalSiteResponses[siteKey]?.let { return it }
            }

            // Visiting hours
            if (lowerMessage.contains("hour") ||
                lowerMessage.contains("visit") ||
                lowerMessage.contains("open") ||
                lowerMessage.contains("time")) {

                return "Visiting hours for ${siteName} typically vary by season. Most historical sites are open from 9 AM to 5 PM, with last entry about an hour before closing. Ticket prices range from $10-30 for adults, with discounts for students and seniors. Would you like more specific information?"
            }

            // Tickets and pricing
            if (lowerMessage.contains("ticket") ||
                lowerMessage.contains("price") ||
                lowerMessage.contains("cost") ||
                lowerMessage.contains("fee")) {

                return "Ticket prices for ${siteName} range from $10-30 for adults, with discounts for students and seniors. Many sites offer online booking options with potential discounts. Would you like to know about guided tour options?"
            }

            // Architecture
            if (lowerMessage.contains("built") ||
                lowerMessage.contains("architecture") ||
                lowerMessage.contains("design") ||
                lowerMessage.contains("construct")) {

                return "The architecture of ${siteName} represents the distinctive style of its period. The construction techniques used were innovative for their time, using locally sourced materials and advanced engineering methods. Would you like to know more specific architectural details?"
            }

            // Facts
            if (lowerMessage.contains("fact") ||
                lowerMessage.contains("interest") ||
                lowerMessage.contains("did you know")) {

                return "Here's an interesting fact about ${siteName}: Many historical sites were built without modern machinery, using only manual labor and ingenious engineering techniques. The precision achieved in construction often amazes modern architects and engineers."
            }

            // Site-specific response based on site key if available
            historicalSiteResponses[siteKey]?.let { return it }

            // If we don't have specific info about this site
            return "This is ${siteName}, a fascinating historical site. What specific aspect would you like to know about?"
        }

        // General questions about available sites
        if (lowerMessage.contains("what sites") ||
            lowerMessage.contains("which places") ||
            lowerMessage.contains("show me") ||
            lowerMessage.contains("list")) {
            return "I can provide information about: Eiffel Tower, Colosseum, and Taj Mahal. Which one would you like to learn about?"
        }

        // Default response if no match found
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
}