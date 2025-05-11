// app/src/main/java/com/example/explorelens/ui/map/CustomInfoWindowAdapter.kt
package com.example.explorelens.ui.map

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.explorelens.R
import com.example.explorelens.data.network.ExploreLensApiClient
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.material.imageview.ShapeableImageView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    private val TAG = "CustomInfoWindowAdapter"
    private val windowView: View = LayoutInflater.from(context)
        .inflate(R.layout.custom_map_info_window, null)

    // Store the current marker to update it when image loads
    private var currentMarker: Marker? = null

    override fun getInfoWindow(marker: Marker): View {
        currentMarker = marker
        renderWindow(marker)
        return windowView
    }

    override fun getInfoContents(marker: Marker): View? {
        return null // Using custom window, not just contents
    }

    private fun renderWindow(marker: Marker) {
        val siteImageView = windowView.findViewById<ShapeableImageView>(R.id.siteImageView)
        val siteNameTextView = windowView.findViewById<TextView>(R.id.siteNameTextView)
        val visitDateTextView = windowView.findViewById<TextView>(R.id.visitDateTextView)

        // Set name and date
        siteNameTextView.text = marker.title ?: "Unknown Site"
        visitDateTextView.text = marker.snippet ?: "Unknown date"

        // Set default image
        Glide.with(context)
            .load(R.drawable.noimage)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(siteImageView)

        // Get the site ID from marker tag
        val siteId = when (val tag = marker.tag) {
            is String -> tag
            is Map<*, *> -> tag["siteId"] as? String
            else -> null
        }

        if (siteId.isNullOrEmpty()) {
            Log.e(TAG, "No site ID available for marker")
            return
        }

        // Clean the site ID
        val cleanSiteId = siteId.replace(" ", "")

        // Fetch site details directly - similar to your SiteHistoryViewHolder
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
            .enqueue(object : Callback<com.example.explorelens.data.model.SiteDetails.SiteDetails> {
                override fun onResponse(
                    call: Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    response: Response<com.example.explorelens.data.model.SiteDetails.SiteDetails>
                ) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()

                        // Update site name
                        siteDetails?.name?.let {
                            siteNameTextView.text = it
                            marker.title = it
                        }

                        // Load image from site details
                        siteDetails?.imageUrl?.let { imageUrl ->
                            Log.d(TAG, "Loading image from URL: $imageUrl")
                            try {
                                Glide.with(context)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.noimage)
                                    .error(R.drawable.noimage)
                                    .centerCrop()
                                    .transition(DrawableTransitionOptions.withCrossFade())
                                    .into(siteImageView)

                                // Store the image URL in marker tag for future use
                                val newTag = when (marker.tag) {
                                    is Map<*, *> -> {
                                        val map = (marker.tag as Map<*, *>).toMutableMap()
                                        map["imageUrl"] = imageUrl
                                        map
                                    }
                                    else -> mapOf("siteId" to siteId, "imageUrl" to imageUrl)
                                }
                                marker.tag = newTag

                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load image", e)
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch site details: ${response.code()}")
                    }
                }

                override fun onFailure(
                    call: Call<com.example.explorelens.data.model.SiteDetails.SiteDetails>,
                    t: Throwable
                ) {
                    Log.e(TAG, "Error fetching site details", t)
                }
            })
    }
}