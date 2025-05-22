package com.example.explorelens.ui.site

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.ArActivity
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.bumptech.glide.Glide
import com.example.explorelens.data.model.comments.ReviewWithUser
import com.example.explorelens.data.repository.ReviewsRepository
import com.example.explorelens.data.repository.SiteDetailsRepository
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.fragments.ChatFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SiteDetailsFragment : Fragment(), TextToSpeech.OnInitListener {

    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var commentsButton: View
    private lateinit var ratingContainer: LinearLayout
    private lateinit var ratingView: RatingView
    private var siteRating: SiteRating? = null
    private var SiteDetails: SiteDetails? = null
    private var fetchedReviews: List<Review> = emptyList()
    private var fetchedReviewsWithUsers: List<ReviewWithUser> = emptyList()
    private lateinit var headerBackground: ImageView
    private lateinit var reviewRepository: ReviewsRepository
    private lateinit var siteDetailsRepository: SiteDetailsRepository
    private lateinit var userRepository: UserRepository

    // Simple Text-to-Speech components
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private lateinit var playStopButton: ImageButton

    // Store the text to be spoken
    private var currentTextToSpeak: String = ""

    // Simple TTS States
    private enum class TtsState {
        IDLE,       // Not playing, ready to start
        PLAYING,    // Currently speaking
        FINISHED    // Finished speaking
    }

    private var currentState = TtsState.IDLE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_site_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(requireContext(), this)

        // Initialize views
        labelTextView = view.findViewById(R.id.labelTextView)
        descriptionTextView = view.findViewById(R.id.descriptionTextView)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        commentsButton = view.findViewById(R.id.commentsButton)
        ratingContainer = view.findViewById(R.id.ratingContainer)
        ratingView = view.findViewById(R.id.ratingView)
        headerBackground = view.findViewById(R.id.headerBackground)

        val askAssistantCard = view.findViewById<CardView>(R.id.askAssistantCard)
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)

        // Initialize the play/stop button
        playStopButton = view.findViewById(R.id.playStopButton)

        setupClickListeners(closeButton, askAssistantCard)

        // Set up repositories
        reviewRepository = ReviewsRepository(requireContext())
        siteDetailsRepository = SiteDetailsRepository(requireContext())
        userRepository = UserRepository(requireContext())

        arguments?.let { args ->
            val label = args.getString("LABEL_KEY", "Unknown")
            val siteName = args.getString("SITE_NAME_KEY")

            labelTextView.text = siteName

            // Check if we already have the description from AR
            val passedDescription = args.getString("DESCRIPTION_KEY")
            if (passedDescription != null && passedDescription.isNotEmpty()) {
                // Use the passed description directly for UI
                Log.d("SiteDetailsFragment", "Using passed description for UI")
                descriptionTextView.text = passedDescription

                // Still fetch details to get ratings and comments
                Log.d("SiteDetailsFragment", "Fetching additional details for ratings and comments")
                fetchSiteDetails(label)
            } else {
                // Need to fetch everything including the description
                Log.d("SiteDetailsFragment", "No description passed, fetching all details")
                loadingIndicator.visibility = View.VISIBLE
                fetchSiteDetails(label)
            }
            // Set initial mock rating
            siteRating = SiteRating(label, 0f, 0)
            ratingView.setRating(siteRating?.averageRating ?: 0f)
        }
    }

    private fun setupClickListeners(closeButton: ImageButton, askAssistantCard: CardView) {
        closeButton.setOnClickListener {
            dismissSiteDetails()
        }

        // Set up comments button click listener
        commentsButton.setOnClickListener {
            showReviewsDialog()
        }

        ratingContainer.setOnClickListener {
            showRatingDialog()
        }

        askAssistantCard.setOnClickListener {
            navigateToChatFragment()
        }

        // Set up play/stop button
        playStopButton.setOnClickListener {
            handlePlayStop()
        }
    }

    // Text-to-Speech initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("SiteDetailsFragment", "Language not supported for TTS")
                ToastHelper.showShortToast(requireContext(), "Text-to-speech language not supported")
            } else {
                isTtsInitialized = true
                // Set speech rate (0.5 = half speed, 1.0 = normal, 2.0 = double speed)
                textToSpeech?.setSpeechRate(0.8f)
                // Set pitch (0.5 = lower pitch, 1.0 = normal, 2.0 = higher pitch)
                textToSpeech?.setPitch(1.0f)

                // Set up utterance progress listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Called when TTS starts speaking
                        activity?.runOnUiThread {
                            currentState = TtsState.PLAYING
                            updateButtonState()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        // Called when TTS finishes speaking
                        activity?.runOnUiThread {
                            currentState = TtsState.FINISHED
                            updateButtonState()
                            ToastHelper.showShortToast(requireContext(), "Finished reading")
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        // Called when TTS encounters an error
                        activity?.runOnUiThread {
                            currentState = TtsState.IDLE
                            updateButtonState()
                            ToastHelper.showShortToast(requireContext(), "Error in text-to-speech")
                        }
                    }
                })

                Log.d("SiteDetailsFragment", "Text-to-Speech initialized successfully")
                updateButtonState()
            }
        } else {
            Log.e("SiteDetailsFragment", "Text-to-Speech initialization failed")
            ToastHelper.showShortToast(requireContext(), "Text-to-speech initialization failed")
        }
    }

    private fun handlePlayStop() {
        if (!isTtsInitialized) {
            ToastHelper.showShortToast(requireContext(), "Text-to-speech not ready")
            return
        }

        when (currentState) {
            TtsState.IDLE, TtsState.FINISHED -> {
                startSpeaking()
            }
            TtsState.PLAYING -> {
                stopSpeaking()
            }
        }
    }

    private fun startSpeaking() {
        val description = descriptionTextView.text.toString()
        val siteName = labelTextView.text.toString()

        if (description.isEmpty()) {
            ToastHelper.showShortToast(requireContext(), "No description to read")
            return
        }

        // Create the text to speak with site name introduction
        currentTextToSpeak = "Here's information about $siteName. $description"

        textToSpeech?.speak(currentTextToSpeak, TextToSpeech.QUEUE_FLUSH, null, "site_description")
        currentState = TtsState.PLAYING
        updateButtonState()

        ToastHelper.showShortToast(requireContext(), "Reading site description...")
    }

    private fun stopSpeaking() {
        textToSpeech?.stop()
        currentState = TtsState.IDLE
        updateButtonState()

        ToastHelper.showShortToast(requireContext(), "Stopped reading")
    }


    private fun updateButtonState() {
        when (currentState) {
            TtsState.IDLE -> {
                playStopButton.setImageResource(R.drawable.ic_play)
                playStopButton.contentDescription = "Play description"
                playStopButton.isEnabled = true
            }
            TtsState.PLAYING -> {
                playStopButton.setImageResource(R.drawable.ic_stop)
                playStopButton.contentDescription = "Stop reading"
                playStopButton.isEnabled = true
            }
            TtsState.FINISHED -> {
                playStopButton.setImageResource(R.drawable.ic_play)
                playStopButton.contentDescription = "Play again"
                playStopButton.isEnabled = true
            }
        }
    }

    private fun fetchSiteDetails(label: String) {
        val siteId = label.replace(" ", "")

        Log.d("SiteDetailsFragment", "Fetching site details for: $siteId")

        loadingIndicator.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // First sync from server to Room
                Log.d("SiteDetailsFragment", "Syncing site details from server to Room...")
                val syncResult = siteDetailsRepository.syncSiteDetails(siteId)

                if (syncResult.isFailure) {
                    Log.w(
                        "SiteDetailsFragment",
                        "Sync failed: ${syncResult.exceptionOrNull()?.message}"
                    )
                    // Continue anyway - we might have cached data
                } else {
                    Log.d("SiteDetailsFragment", "Sync successful")
                }

                // Now observe Room data
                Log.d("SiteDetailsFragment", "Setting up Room observer...")
                siteDetailsRepository.getSiteDetailsLiveData(siteId)
                    .observe(viewLifecycleOwner) { siteDetails ->
                        loadingIndicator.visibility = View.GONE

                        if (siteDetails != null) {
                            Log.d(
                                "SiteDetailsFragment",
                                "Got site details from Room: ${siteDetails.name}"
                            )
                            handleSiteDetailsSuccess(siteDetails)
                        } else {
                            Log.e("SiteDetailsFragment", "No site details found in Room")
                            showError("Failed to load details")
                        }
                    }
            } catch (e: Exception) {
                loadingIndicator.visibility = View.GONE
                Log.e("SiteDetailsFragment", "Error in fetchSiteDetails: ${e.message}", e)
                showError("Failed to load details: ${e.message}")
            }
        }
    }

    private fun handleSiteDetailsSuccess(siteDetails: SiteDetails) {
        this.SiteDetails = siteDetails

        labelTextView.text = siteDetails.name
        setDescriptionIfNotPassed(siteDetails.description)
        loadImageIfAvailable(siteDetails.imageUrl)
        updateRatingView(siteDetails.averageRating, siteDetails.ratingCount)

        siteDetails.id?.let {
            fetchSiteReviews(it)
        } ?: Log.e("SiteDetailsFragment", "siteId is null or blank")
    }

    private fun setDescriptionIfNotPassed(description: String?) {
        val hasPassedDescription = arguments?.getString("DESCRIPTION_KEY")?.isNotEmpty() == true
        if (!hasPassedDescription) {
            descriptionTextView.text = description
        }
    }

    private fun loadImageIfAvailable(imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            try {
                Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.eiffel)
                    .error(R.drawable.eiffel)
                    .into(headerBackground)
            } catch (e: Exception) {
                Log.e("SiteDetailsFragment", "Error loading image: ${e.message}", e)
            }
        } else {
            Log.d("SiteDetailsFragment", "No image URL provided")
        }
    }

    private fun updateRatingView(averageRating: Float?, ratingCount: Int) {
        if (averageRating != null) {
            if (averageRating > 0) {
                ratingView.setRating(averageRating ?: 0f)
                Log.d("SiteDetailsFragment", "Updated rating view to: $averageRating")
            } else {
                Log.d("SiteDetailsFragment", "No ratings available, using default")
                ratingView.setRating(0f)
            }
        }
    }

    private fun fetchSiteReviews(siteId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = reviewRepository.fetchSiteReviews(siteId)
            if (result.isSuccess) {
                val comments = result.getOrNull()
                if (!isAdded) return@launch
                fetchedReviews = comments ?: emptyList()

                val enrichedReviews = withContext(Dispatchers.IO) {
                    comments?.map { comment ->
                        val userResult = userRepository.getUserById(comment.user)
                        val user = userResult.getOrNull()
                        ReviewWithUser(comment, user)
                    }
                }

                if (enrichedReviews != null) {
                    fetchedReviewsWithUsers = enrichedReviews
                }
                Log.d("SiteDetailsFragment", "Loaded ${fetchedReviews.size} comments")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("SiteDetailsFragment", "Failed to load comments: $error")
                if (!isAdded) return@launch
                showError("Failed to load comments: $error")
            }
        }
    }

    private fun showError(message: String) {
        context?.let {
            ToastHelper.showShortToast(it, message)
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showReviewsDialog() {
        Log.d("SiteDetailsFragment", "showReviewsDialog called")
        context?.let { ctx ->
            try {
                val builder = AlertDialog.Builder(ctx, R.style.RoundedDialog)
                val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)
                val dialog = builder.setView(dialogView).create()
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.commentsRecyclerView)
                val emptyView = dialogView.findViewById<TextView>(R.id.emptyCommentsText)
                val commentInput = dialogView.findViewById<EditText>(R.id.commentInput)
                val submitButton = dialogView.findViewById<Button>(R.id.submitCommentButton)
                val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

                if (recyclerView == null || submitButton == null || commentInput == null || cancelButton == null) {
                    Log.e("SiteDetailsFragment", "UI elements not found in dialog")
                    return@let
                }

                recyclerView.layoutManager = LinearLayoutManager(ctx)

                val comments = fetchedReviews ?: emptyList()

                if (comments.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                    emptyView?.text = " No comments yet"
                } else {
                    recyclerView.adapter = ReviewsAdapter(fetchedReviewsWithUsers)
                    recyclerView.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                }

                submitButton.setOnClickListener {
                    val commentText = commentInput.text.toString().trim()
                    if (commentText.isNotEmpty()) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = reviewRepository.createReview(
                                siteId = SiteDetails?.id ?: "",
                                content = commentText
                            )

                            if (result.isSuccess) {
                                ToastHelper.showShortToast(ctx, "comment submit")
                                commentInput.text.clear()

                                val newReview = result.getOrNull()
                                val user = userRepository.getUserFromDb()
                                val newReviewWithUser =
                                    newReview?.let { it1 -> ReviewWithUser(it1, user) }
                                if (newReviewWithUser != null) {
                                    fetchedReviewsWithUsers =
                                        (fetchedReviewsWithUsers ?: emptyList()) + newReviewWithUser
                                    recyclerView.adapter = ReviewsAdapter(fetchedReviewsWithUsers)
                                    recyclerView.scrollToPosition(fetchedReviewsWithUsers.lastIndex)

                                    recyclerView.visibility = View.VISIBLE
                                    emptyView?.visibility = View.GONE
                                }
                            } else {
                                Log.e(
                                    "SiteDetailsFragment",
                                    "Failed to submit comment: ${result.exceptionOrNull()?.message}"
                                )
                                ToastHelper.showShortToast(ctx, "error sending massage  ")
                            }
                        }
                    }
                }

                cancelButton.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.8).toInt()
                )
            } catch (e: Exception) {
                Log.e("SiteDetailsFragment", "Error showing dialog", e)
            }
        }
    }

    private fun showRatingDialog() {
        context?.let { ctx ->
            // Create and show dialog
            val dialog = AlertDialog.Builder(ctx, R.style.RoundedDialog)
                .setView(R.layout.dialog_rate_site)
                .create()

            // Make dialog background transparent to show rounded corners
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            dialog.show()

            // Get views from dialog
            val siteName = dialog.findViewById<TextView>(R.id.siteName)
            val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
            val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
            val submitButton = dialog.findViewById<Button>(R.id.submitRatingButton)

            // Set site name
            siteName?.text = labelTextView.text

            // Set up cancel button
            cancelButton?.setOnClickListener {
                dialog.dismiss()
            }

            // Set up submit button
            submitButton?.setOnClickListener {
                val rating = ratingBar?.rating ?: 0f
                if (rating > 0) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = siteDetailsRepository.addRating(
                            siteId = SiteDetails?.id ?: "",
                            rating = rating
                        )

                        if (result.isSuccess) {

                            ToastHelper.showShortToast(ctx, "Rating submitted: $rating")
                            val newSite = result.getOrNull()
                            if (newSite != null) {
                                Log.e("SiteDetailsFragment", " rating: ${newSite.averageRating}")
                                ratingView.setRating(newSite.averageRating ?: 0f)
                            }

                            dialog.dismiss()
                        } else {
                            Log.e(
                                "SiteDetailsFragment",
                                "Failed to submit rating: ${result.exceptionOrNull()?.message}"
                            )
                            ToastHelper.showShortToast(ctx, "Failed to submit rating")
                        }
                    }
                } else {
                    ToastHelper.showShortToast(ctx, "Please select a rating")
                }
            }
        }
    }

    private fun updateRatingView(newRating: Float? = null) {
        val currentRating = ratingView.getRating()
        val currentCount = siteRating?.totalRatings ?: 0

        if (newRating != null && currentCount > 0) {
            // Calculate new average if we have a current rating
            val totalScore = currentRating * currentCount
            val newTotalScore = totalScore + newRating
            val newCount = currentCount + 1
            val newAverage = newTotalScore / newCount

            // Update our model
            siteRating = SiteRating(labelTextView.text.toString(), newAverage, newCount)

            // Update the view
            ratingView.setRating(newAverage)
        } else if (newRating != null) {
            // First rating
            siteRating = SiteRating(labelTextView.text.toString(), newRating, 1)
            ratingView.setRating(newRating)
        }
    }

    private fun dismissSiteDetails() {
        // If using an overlay in AR activity
        val activity = activity as? ArActivity
        if (activity != null) {
            // Hide the site details container
            activity.view.hideSiteDetails()

            // Make sure the camera button is visible again
            activity.findViewById<View>(R.id.cameraButtonContainer)?.visibility = View.VISIBLE
        } else {
            // For regular fragment navigation, use back press behavior
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun navigateToChatFragment() {
        val siteName = labelTextView.text.toString()
        val siteImageUrl = SiteDetails?.imageUrl

        val bundle = Bundle().apply {
            putString("SITE_NAME_KEY", siteName)
            putString("SITE_IMAGE_URL_KEY", siteImageUrl)
        }

        // Check if we're in AR activity context
        val activity = activity
        if (activity is ArActivity) {
            try {
                // Hide the site details container
                val siteDetailsContainer = activity.findViewById<View>(R.id.siteDetailsContainer)
                siteDetailsContainer?.visibility = View.GONE

                // Show the fragment container
                val fragmentContainer = activity.findViewById<View>(R.id.fragment_container)
                fragmentContainer?.visibility = View.VISIBLE

                // Create the chat fragment with arguments
                val chatFragment = ChatFragment.newInstance(siteName)

                // Use the ArActivity to manage the fragment transaction
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, chatFragment)
                    .addToBackStack("chat")
                    .commit()

                // Hide the camera button container
                activity.findViewById<View>(R.id.cameraButtonContainer)?.visibility = View.GONE

            } catch (e: Exception) {
                Log.e("SiteDetailsFragment", "Error showing ChatFragment in AR: ${e.message}", e)
                showError("Failed to open chat assistant in AR mode")
            }
        } else {
            // Standard navigation using NavController
            try {
                findNavController().navigate(
                    R.id.action_siteDetailsFragment_to_chatFragment,
                    bundle
                )
            } catch (e: Exception) {
                Log.e("SiteDetailsFragment", "Error navigating to ChatFragment: ${e.message}", e)
                showError("Failed to open chat assistant")
            }
        }
    }

    override fun onDestroy() {
        // Clean up Text-to-Speech
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // Stop speaking when fragment is paused
        if (currentState == TtsState.PLAYING) {
            stopSpeaking()
        }
    }
}