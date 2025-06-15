package com.example.explorelens.ui.places

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.explorelens.ArActivity
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.db.places.Place
import com.example.explorelens.databinding.FragmentLayerDetailBinding

class LayerDetailFragment : Fragment() {

    private var _binding: FragmentLayerDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LayerDetailViewModel
    private lateinit var reviewsAdapter: ReviewsAdapter

    // Get placeId from arguments manually until Safe Args is properly set up
    private val placeId: String by lazy {
        arguments?.getString("placeId") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeComponents()
        setupObservers()
        setupListeners()
        setupArActivityCommunication()

        // Set the place ID from navigation arguments
        if (placeId.isNotEmpty()) {
            viewModel.setPlaceId(placeId)
        } else {
            showError("No place ID provided")
        }
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[LayerDetailViewModel::class.java]

        // Setup reviews RecyclerView
        reviewsAdapter = ReviewsAdapter()
        binding.reviewsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reviewsAdapter
        }
    }

    private fun setupObservers() {
        // Place detail state observer
        viewModel.placeDetailState.observe(viewLifecycleOwner) { state ->
            _binding?.let {
                when (state) {
                    is LayerDetailViewModel.PlaceDetailState.Loading -> {
                        showLoading()
                    }
                    is LayerDetailViewModel.PlaceDetailState.Success -> {
                        hideLoading()
                        displayPlaceDetails(state.place)
                        showCacheIndicator(state.isFromCache)
                    }
                    is LayerDetailViewModel.PlaceDetailState.Error -> {
                        hideLoading()
                        showError(state.message)
                        ToastHelper.showShortToast(requireContext(), state.message)
                    }
                }
            }
        }

        // Refresh state observer
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            _binding?.let {
                binding.swipeRefresh.isRefreshing = isRefreshing
            }
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshPlaceDetails()
        }

        // Click listeners for interactive elements
        binding.websiteContainer.setOnClickListener {
            val website = binding.website.text.toString()
            if (website.isNotEmpty()) {
                openWebsite(website)
            }
        }

        binding.phoneContainer.setOnClickListener {
            val phone = binding.phoneNumber.text.toString()
            if (phone.isNotEmpty()) {
                dialPhone(phone)
            }
        }

        binding.addressContainer.setOnClickListener {
            val address = binding.address.text.toString()
            if (address.isNotEmpty()) {
                openMaps(address)
            }
        }
    }

    private fun displayPlaceDetails(place: Place) {
        binding.apply {
            // Show content container
            contentContainer.visibility = View.VISIBLE

            // Basic information
            placeName.text = place.name
            placeType.text = formatPlaceType(place.type)
            ratingValue.text = String.format("%.1f", place.rating)

            // Editorial summary
            if (!place.editorialSummary.isNullOrEmpty()) {
                editorialSummary.text = place.editorialSummary
                editorialSummary.visibility = View.VISIBLE
            } else {
                editorialSummary.visibility = View.GONE
            }

            // Contact information
            setupContactInfo(place)

            // Opening hours
            setupOpeningHours(place)

            // Reviews
            setupReviews(place)
        }
    }

    private fun setupContactInfo(place: Place) {
        binding.apply {
            // Address
            if (!place.address.isNullOrEmpty()) {
                address.text = place.address
                addressContainer.visibility = View.VISIBLE
            } else {
                addressContainer.visibility = View.GONE
            }

            // Website
            if (!place.website.isNullOrEmpty()) {
                website.text = place.website
                websiteContainer.visibility = View.VISIBLE
            } else {
                websiteContainer.visibility = View.GONE
            }

            // Phone
            if (!place.phoneNumber.isNullOrEmpty()) {
                phoneNumber.text = place.phoneNumber
                phoneContainer.visibility = View.VISIBLE
            } else {
                phoneContainer.visibility = View.GONE
            }

            // Status
            if (place.openNow != null) {
                openStatus.text = if (place.openNow == true) "Open now" else "Closed"
                openStatus.setTextColor(
                    if (place.openNow == true)
                        requireContext().getColor(R.color.success_color)
                    else
                        requireContext().getColor(R.color.error_color)
                )
                statusContainer.visibility = View.VISIBLE
            } else {
                statusContainer.visibility = View.GONE
            }

            // Price level
            if (place.priceLevel != null) {
                priceLevel.text = formatPriceLevel(place.priceLevel)
                priceLevelContainer.visibility = View.VISIBLE
            } else {
                priceLevelContainer.visibility = View.GONE
            }

            // Show contact card only if we have any contact info
            contactCard.visibility = if (
                !place.address.isNullOrEmpty() ||
                !place.website.isNullOrEmpty() ||
                !place.phoneNumber.isNullOrEmpty() ||
                place.openNow != null ||
                place.priceLevel != null
            ) View.VISIBLE else View.GONE
        }
    }

    private fun setupOpeningHours(place: Place) {
        if (!place.weekdayText.isNullOrEmpty()) {
            binding.hoursContainer.removeAllViews()

            place.weekdayText.forEach { hourText ->
                val textView = TextView(requireContext()).apply {
                    text = hourText
                    textSize = 15f
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                }
                binding.hoursContainer.addView(textView)
            }

            binding.openingHoursCard.visibility = View.VISIBLE
        } else {
            binding.openingHoursCard.visibility = View.GONE
        }
    }

    private fun setupReviews(place: Place) {
        if (!place.reviews.isNullOrEmpty()) {
            reviewsAdapter.submitList(place.reviews.take(5)) // Show top 5 reviews
            binding.reviewsCard.visibility = View.VISIBLE
        } else {
            binding.reviewsCard.visibility = View.GONE
        }
    }

    private fun showLoading() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            contentContainer.visibility = View.GONE
            errorMessage.visibility = View.GONE
        }
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.apply {
            errorMessage.text = message
            errorMessage.visibility = View.VISIBLE
            contentContainer.visibility = View.GONE
        }
    }

    private fun showCacheIndicator(isFromCache: Boolean) {
        binding.cacheIndicator.visibility = if (isFromCache) View.VISIBLE else View.GONE
    }

    private fun formatPlaceType(type: String): String {
        return type.split("_")
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    private fun formatPriceLevel(level: Int): String {
        return when (level) {
            0 -> "Free"
            1 -> "$"
            2 -> "$$"
            3 -> "$$$"
            4 -> "$$$$"
            else -> "Price level $level"
        }
    }

    private fun openWebsite(url: String) {
        try {
            val formattedUrl = if (!url.startsWith("http")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
            startActivity(intent)
        } catch (e: Exception) {
            ToastHelper.showShortToast(requireContext(), "Cannot open website")
        }
    }

    private fun dialPhone(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(intent)
        } catch (e: Exception) {
            ToastHelper.showShortToast(requireContext(), "Cannot dial number")
        }
    }

    private fun openMaps(address: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback to browser
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://maps.google.com/?q=${Uri.encode(address)}")
                )
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            ToastHelper.showShortToast(requireContext(), "Cannot open maps")
        }
    }
    companion object {
        /**
         * Helper function to create a new instance with placeId
         */
        fun newInstance(placeId: String): LayerDetailFragment {
            return LayerDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("placeId", placeId)
                }
            }
        }
    }
    private fun setupArActivityCommunication() {
        // If you don't have a toolbar, add a close button to your layout
        binding.closeButton?.setOnClickListener {
            closeFragment()
        }

        // Handle system back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeFragment()
                }
            }
        )
    }

    /**
     * Close the fragment and return to AR view
     */
    private fun closeFragment() {
        try {
            // Send result to ArActivity
            setFragmentResult("layer_detail_closed", Bundle.EMPTY)

            // Remove this fragment
            parentFragmentManager.beginTransaction()
                .remove(this)
                .commit()

            Log.d("LayerDetailFragment", "Fragment closed successfully")

        } catch (e: Exception) {
            Log.e("LayerDetailFragment", "Error closing fragment", e)
            // Fallback: just pop back stack
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Ensure AR view is shown when fragment is destroyed
        (activity as? ArActivity)?.showArViewSafely()

        _binding = null
    }
}