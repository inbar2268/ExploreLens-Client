package com.example.explorelens.ui.site

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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.explorelens.R
import com.example.explorelens.data.network.AnalyzedResultsClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.explorelens.ui.site.CommentsAdapter
import com.example.explorelens.ui.site.CommentItem

class SiteDetailsFragment : Fragment() {

    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var commentsButton: ImageButton

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

        // Set up comments button click listener
        commentsButton.setOnClickListener {
            showCommentsDialog()
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
            val bottomSheetDialog = BottomSheetDialog(ctx, R.style.CustomBottomSheetDialogTheme)
            val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)
            bottomSheetDialog.setContentView(dialogView)

            // Make dialog background transparent to show the rounded corners
            bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
                setBackgroundResource(android.R.color.transparent)
            }

            // Set up RecyclerView for comments
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.commentsRecyclerView)
            recyclerView.layoutManager = LinearLayoutManager(ctx)

            // Create some mock comments for demonstration
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
                    // Here you would normally send this to your backend
                    // For now, just show a toast and clear the input
                    Toast.makeText(ctx, "Comment submitted", Toast.LENGTH_SHORT).show()
                    commentInput.text.clear()

                    // Could also add the comment to the recyclerView adapter if desired
                    // (In a real app you would update the adapter with the new comment)
                }
            }

            bottomSheetDialog.show()
        }
    }
}