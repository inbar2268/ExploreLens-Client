package com.example.explorelens.ui.site

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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.network.AnalyzedResultsClient
import com.example.explorelens.ui.site.RatingView
import com.example.explorelens.ui.site.CommentsAdapter
import com.example.explorelens.ui.site.CommentItem
import com.example.explorelens.ui.site.SiteRating
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SiteDetailsFragment : Fragment() {

    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var commentsButton: View
    private lateinit var ratingContainer: LinearLayout
    private lateinit var ratingView: RatingView
    private var siteRating: SiteRating? = null

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

        // Set up comments button click listener
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
                // Use the passed description directly
                Log.d("SiteDetailsFragment", "Using passed description")
                loadingIndicator.visibility = View.GONE
                descriptionTextView.text = passedDescription
            } else {
                // Need to fetch the description
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
        val call = AnalyzedResultsClient.siteDetailsApiClient.getSiteDetails(labelWithoutSpaces)

        call.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (!isAdded) return  // Check if fragment is still attached

                loadingIndicator.visibility = View.GONE

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // Update UI with the string response
                        descriptionTextView.text = responseBody
                    } else {
                        Log.e("SiteDetailsFragment", "Response body is null")
                        showError("No data returned from server")
                    }
                } else {
                    Log.e("SiteDetailsFragment", "Error: ${response.code()}")
                    showError("Failed to load details: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                if (!isAdded) return  // Check if fragment is still attached

                loadingIndicator.visibility = View.GONE
                Log.e("SiteDetailsFragment", "Network error: ${t.message}", t)
                showError("Network error: ${t.message}")
            }
        })
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCommentsDialog() {
        context?.let { ctx ->
            // Replace BottomSheetDialog with AlertDialog
            val builder = AlertDialog.Builder(ctx, R.style.RoundedDialog)
            val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)
            val dialog = builder.setView(dialogView).create()

            // Make dialog background transparent to show rounded corners
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Set up RecyclerView for comments
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.commentsRecyclerView)
            recyclerView.layoutManager = LinearLayoutManager(ctx)

            // Create mock comments
            val comments = listOf(
                CommentItem("John Doe", "This place is amazing! I visited last summer and the architecture is stunning.", null),
                CommentItem("Jane Smith", "The historical significance of this site cannot be overstated. A must-visit!", null),
                CommentItem("Mark Johnson", "Great place to take photos. The lighting in the evening is perfect.", null),
                CommentItem("Sarah Williams", "I was disappointed by how crowded it was. Maybe visit during off-season if you can.", null),
                CommentItem("David Brown", "The tour guides are very knowledgeable and friendly. Definitely take a guided tour if available.", null)
            )

            // Set adapter
            recyclerView.adapter = CommentsAdapter(comments)

            // Set up comment submission
            val commentInput = dialogView.findViewById<EditText>(R.id.commentInput)
            val submitButton = dialogView.findViewById<Button>(R.id.submitCommentButton)

            submitButton.setOnClickListener {
                val commentText = commentInput.text.toString().trim()
                if (commentText.isNotEmpty()) {
                    Toast.makeText(ctx, "Comment submitted", Toast.LENGTH_SHORT).show()
                    commentInput.text.clear()
                }
            }

            dialog.show()

            // Set dialog size - make it taller
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.8).toInt() // 80% of screen height
            )
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
                    Toast.makeText(context, "Rating submitted: $rating", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
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
}