package com.example.explorelens.ui.site

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.ArActivity
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.model.comments.Comment
import com.example.explorelens.data.model.SiteDetails
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.bumptech.glide.Glide
import com.example.explorelens.data.repository.CommentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SiteDetailsFragment : Fragment() {

    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var commentsButton: View
    private lateinit var ratingContainer: CardView
    private lateinit var ratingView: RatingView
    private var siteRating: SiteRating? = null
    private var SiteDetails: SiteDetails? = null
    private var fetchedComments: List<Comment> = emptyList()
    private lateinit var headerBackground: ImageView
    private lateinit var commentsRepository: CommentsRepository


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_site_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        labelTextView = view.findViewById(R.id.labelTextView)
        descriptionTextView = view.findViewById(R.id.descriptionTextView)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        commentsButton = view.findViewById(R.id.commentsButton)
        ratingContainer = view.findViewById(R.id.ratingContainer)
        ratingView = view.findViewById(R.id.ratingView)
        headerBackground = view.findViewById(R.id.headerBackground)

        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)
        closeButton.setOnClickListener {
            // Dismiss the fragment
            dismissSiteDetails()
        }
        // Set up comments button click listener
        commentsRepository = CommentsRepository(requireContext())
        commentsButton.setOnClickListener {
            showCommentsDialog()
        }

        ratingContainer.setOnClickListener {
            showRatingDialog()
        }

        arguments?.let { args ->
            val label = args.getString("LABEL_KEY", "Unknown")
            Log.d("SiteDetailsFragment", "Received label: $label")

            labelTextView.text = label

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
            // In a real app, you would fetch this from the server
            siteRating = SiteRating(label, 4.2f, 128)
            ratingView.setRating(siteRating?.averageRating ?: 0f)
        }
    }

    private fun fetchSiteDetails(label: String) {
        val labelWithoutSpaces = label.replace(" ", "")
        Log.d("SiteDetailsFragment", "Sending request with label: $labelWithoutSpaces")

        // Use the client from our networking package
        val call = ExploreLensApiClient.siteDetailsApi.getSiteDetails(labelWithoutSpaces)

        call.enqueue(object : Callback<SiteDetails> {
            override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                if (!isAdded) return  // Check if fragment is still attached

                loadingIndicator.visibility = View.GONE

                if (response.isSuccessful) {
                    val siteDetailsResponse = response.body()
                    Log.d("SiteDetailsFragment", "Raw response: ${response.raw()}")
                    Log.d("SiteDetailsFragment", "Response received: $siteDetailsResponse")
                    Log.d("SiteDetailsFragment", "Image URL: ${siteDetailsResponse?.imageUrl}")

                    if (siteDetailsResponse != null) {
                        // Store the complete SiteDetails object
                        this@SiteDetailsFragment.SiteDetails = siteDetailsResponse

                        // Only update description if we don't already have one
                        val hasPassedDescription =
                            arguments?.getString("DESCRIPTION_KEY")?.isNotEmpty() == true
                        if (!hasPassedDescription) {
                            descriptionTextView.text = siteDetailsResponse.description
                        }

                        // Load image if available
                        if (!siteDetailsResponse.imageUrl.isNullOrEmpty()) {
                            Log.d(
                                "SiteDetailsFragment",
                                "Loading image from URL: ${siteDetailsResponse.imageUrl}"
                            )
                            try {
                                Glide.with(requireContext())
                                    .load(siteDetailsResponse.imageUrl)
                                    .placeholder(R.drawable.eiffel) // This shows temporarily while loading
                                    .error(R.drawable.eiffel) // This shows only if loading fails
                                    .into(headerBackground)
                            } catch (e: Exception) {
                                Log.e("SiteDetailsFragment", "Error loading image: ${e.message}", e)
                            }
                        } else {
                            Log.d("SiteDetailsFragment", "No image URL provided")
                        }

                        Log.d(
                            "SiteDetailsFragment",
                            "Rating from server: ${siteDetailsResponse.averageRating}, count: ${siteDetailsResponse.ratingCount}"
                        )

                        val siteId = siteDetailsResponse.id
                        if (!siteId.isNullOrBlank()) {
                            fetchSiteComments(siteId)
                        } else {
                            Log.e("SiteDetailsFragment", "siteId is null or blank")
                        }

                        // Update rating if available
                        if (siteDetailsResponse.ratingCount > 0) {
                            this@SiteDetailsFragment.siteRating = SiteRating(
                                labelTextView.text.toString(),
                                siteDetailsResponse.averageRating,
                                siteDetailsResponse.ratingCount
                            )
                            ratingView.setRating(siteDetailsResponse.averageRating)
                            Log.d(
                                "SiteDetailsFragment",
                                "Updated rating view to: ${siteDetailsResponse.averageRating}"
                            )
                        } else {
                            Log.d("SiteDetailsFragment", "No ratings available, using default")
                        }
                    } else {
                        Log.e("SiteDetailsFragment", "Response body is null")
                        showError("No data returned from server")
                    }
                } else {
                    Log.e("SiteDetailsFragment", "Error: ${response.code()}")
                    showError("Failed to load details: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                if (!isAdded) return  // Check if fragment is still attached

                loadingIndicator.visibility = View.GONE
                Log.e("SiteDetailsFragment", "Network error: ${t.message}", t)
                showError("Network error: ${t.message}")
            }
        })
    }

    private fun fetchSiteComments(siteId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = commentsRepository.fetchSiteComments(siteId)
            if (result.isSuccess) {
                val comments = result.getOrNull()
                if (!isAdded) return@launch
                fetchedComments = comments ?: emptyList()
                Log.d("SiteDetailsFragment", "Loaded ${fetchedComments.size} comments")
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
    private fun showCommentsDialog() {
        Log.d("SiteDetailsFragment", "showCommentsDialog called")
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

                val comments = fetchedComments ?: emptyList()

                if (comments.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                    emptyView?.text = " No comments yet"
                } else {
                    recyclerView.adapter = CommentsAdapter(comments)
                    recyclerView.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                }

                submitButton.setOnClickListener {
                    val commentText = commentInput.text.toString().trim()
                    if (commentText.isNotEmpty()) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = commentsRepository.createComment(
                                siteId = SiteDetails?.id ?: "",
                                content = commentText
                            )

                            if (result.isSuccess) {
                                ToastHelper.showShortToast(ctx, "comment submit")
                                commentInput.text.clear()

                                val newComment = result.getOrNull()
                                if (newComment != null) {
                                    fetchedComments = (fetchedComments ?: emptyList()) + newComment
                                    recyclerView.adapter = CommentsAdapter(fetchedComments!!)
                                    recyclerView.scrollToPosition(fetchedComments!!.lastIndex)

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
                    // Here you would normally send this to your backend
                    // For now, just update the UI and dismiss
                    updateRatingView(rating)
                    ToastHelper.showShortToast(context, "Rating submitted: $rating")
                    dialog.dismiss()
                } else {
                    ToastHelper.showShortToast(context, "Please select a rating")
                }
            }
        }
    }

    // Add this method to update the rating display
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
            activity.findViewById<View>(R.id.cameraButton)?.visibility = View.VISIBLE
        } else {
            // For regular fragment navigation
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
    }
}