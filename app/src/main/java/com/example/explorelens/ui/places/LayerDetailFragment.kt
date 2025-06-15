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
            Log.d("LayerDetailFragment", "Setting placeId: $placeId")
            viewModel.setPlaceId(placeId)
        } else {
            Log.e("LayerDetailFragment", "No place ID provided")
            showError("No place ID provided")
        }
    }

    private fun initializeComponents() {
        Log.d("LayerDetailFragment", "initializeComponents called")

        try {
            viewModel = ViewModelProvider(this)[LayerDetailViewModel::class.java]
            Log.d("LayerDetailFragment", "ViewModel created successfully")

            // Setup reviews RecyclerView
            reviewsAdapter = ReviewsAdapter()
            _binding?.let { binding ->
                binding.reviewsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = reviewsAdapter
                }
                Log.d("LayerDetailFragment", "RecyclerView setup complete")
            } ?: Log.e("LayerDetailFragment", "Binding is null in initializeComponents")

        } catch (e: Exception) {
            Log.e("LayerDetailFragment", "Error in initializeComponents", e)
        }
    }

    private fun setupObservers() {
        Log.d("LayerDetailFragment", "Setting up observers")

        // Place detail state observer
        viewModel.placeDetailState.observe(viewLifecycleOwner) { state ->
            Log.d("LayerDetailFragment", "PlaceDetailState changed: $state")

            _binding?.let { binding ->
                when (state) {
                    is LayerDetailViewModel.PlaceDetailState.Loading -> {
                        Log.d("LayerDetailFragment", "Showing loading state")
                        showLoading()
                    }
                    is LayerDetailViewModel.PlaceDetailState.Success -> {
                        Log.d("LayerDetailFragment", "Showing success state for place: ${state.place.name}")
                        hideLoading()
                        displayPlaceDetails(state.place)
                        showCacheIndicator(state.isFromCache)
                    }
                    is LayerDetailViewModel.PlaceDetailState.Error -> {
                        Log.e("LayerDetailFragment", "Showing error state: ${state.message}")
                        hideLoading()
                        showError(state.message)
                        ToastHelper.showShortToast(requireContext(), state.message)
                    }
                }
            } ?: run {
                Log.e("LayerDetailFragment", "Binding is null when trying to update UI")
            }
        }

        // Refresh state observer
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            Log.d("LayerDetailFragment", "Refresh state: $isRefreshing")
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
        Log.d("LayerDetailFragment", "displayPlaceDetails called for: ${place.name}")

        _binding?.let { binding ->
            binding.apply {
                // Show content container FIRST
                contentContainer.visibility = View.VISIBLE
                Log.d("LayerDetailFragment", "ContentContainer visibility set to VISIBLE")

                // Debug: Check all container visibilities immediately
                Log.d("LayerDetailFragment", "=== VISIBILITY CHECK ===")
                Log.d("LayerDetailFragment", "contentContainer.visibility = ${contentContainer.visibility} (should be 0)")
                Log.d("LayerDetailFragment", "progressBar.visibility = ${progressBar.visibility} (should be 8)")
                Log.d("LayerDetailFragment", "errorMessage.visibility = ${errorMessage.visibility} (should be 8)")
                Log.d("LayerDetailFragment", "swipeRefresh.visibility = ${swipeRefresh.visibility} (should be 0)")

                // Basic information
                placeName.text = place.name
                placeType.text = formatPlaceType(place.type)
                ratingValue.text = String.format("%.1f", place.rating)

                Log.d("LayerDetailFragment", "=== TEXT SET ===")
                Log.d("LayerDetailFragment", "placeName.text = '${placeName.text}'")
                Log.d("LayerDetailFragment", "placeType.text = '${placeType.text}'")
                Log.d("LayerDetailFragment", "ratingValue.text = '${ratingValue.text}'")

                // Editorial summary
                if (!place.editorialSummary.isNullOrEmpty()) {
                    editorialSummary.text = place.editorialSummary
                    editorialSummary.visibility = View.VISIBLE
                    Log.d("LayerDetailFragment", "editorialSummary.text = '${editorialSummary.text}'")
                    Log.d("LayerDetailFragment", "editorialSummary.visibility = ${editorialSummary.visibility}")
                } else {
                    editorialSummary.visibility = View.GONE
                    Log.d("LayerDetailFragment", "No editorial summary")
                }

                // Contact information
                setupContactInfo(place)

                // Opening hours
                setupOpeningHours(place)

                // Reviews
                setupReviews(place)

                // POST LAYOUT DEBUG - Check dimensions after layout
                contentContainer.post {
                    Log.d("LayerDetailFragment", "=== POST-LAYOUT DIMENSIONS ===")
                    Log.d("LayerDetailFragment", "contentContainer: ${contentContainer.width}x${contentContainer.height}")
                    Log.d("LayerDetailFragment", "contentContainer.visibility = ${contentContainer.visibility}")
                    Log.d("LayerDetailFragment", "swipeRefresh: ${swipeRefresh.width}x${swipeRefresh.height}")
                    Log.d("LayerDetailFragment", "root: ${root.width}x${root.height}")

                    // Check if any parent views are hiding content
                    var parent = contentContainer.parent
                    var level = 1
                    while (parent != null && level < 5) {
                        if (parent is View) {
                            Log.d("LayerDetailFragment", "Parent $level: ${parent.javaClass.simpleName} - ${parent.width}x${parent.height}, visibility=${parent.visibility}")
                        }
                        parent = parent.parent
                        level++
                    }
                }

                Log.d("LayerDetailFragment", "Finished displaying place details")
            }
        } ?: Log.e("LayerDetailFragment", "Binding is null in displayPlaceDetails")
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
        Log.d("LayerDetailFragment", "showLoading called")
        _binding?.let { binding ->
            binding.apply {
                progressBar.visibility = View.VISIBLE
                contentContainer.visibility = View.GONE
                errorMessage.visibility = View.GONE
                Log.d("LayerDetailFragment", "Loading state set - progress visible, content/error gone")
            }
        } ?: Log.e("LayerDetailFragment", "Binding is null in showLoading")
    }

    private fun hideLoading() {
        Log.d("LayerDetailFragment", "hideLoading called")
        _binding?.let { binding ->
            binding.progressBar.visibility = View.GONE
            Log.d("LayerDetailFragment", "Progress bar hidden")
        } ?: Log.e("LayerDetailFragment", "Binding is null in hideLoading")
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