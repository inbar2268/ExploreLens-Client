package com.example.explorelens.ui.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.explorelens.R
import com.example.explorelens.common.helpers.ToastHelper
import com.example.explorelens.data.db.User
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.data.repository.UserStatisticsRepository
import com.example.explorelens.databinding.FragmentProfileBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.Locale
import android.os.Build

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Job references to cancel when fragment is destroyed
    private var statisticsJob: Job? = null
    private var mapDataJob: Job? = null
    private var userObserverJob: Job? = null

    // UserState sealed class for handling UI states
    sealed class UserState {
        object Loading : UserState()
        data class Success(val user: User) : UserState()
        data class Error(val message: String) : UserState()
        object Logout : UserState()
    }

    // ViewModel code now in the Fragment
    private val _userState = MutableLiveData<UserState>()
    val userState: LiveData<UserState> = _userState

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userStatisticsRepository: UserStatisticsRepository

    // Track loading states separately
    private var isLoadingStatistics = false
    private var isLoadingMapData = false

    // Continent to country mapping for accurate coloring
    private val continentCountries = mapOf(
        "north-america" to listOf(
            "US", "CA", "MX", "GT", "BZ", "SV", "HN", "NI", "CR", "PA",
            "CU", "JM", "HT", "DO", "GL", "BS"
        ),
        "south-america" to listOf(
            "BR", "AR", "CO", "PE", "VE", "CL", "EC", "BO", "PY", "UY",
            "GY", "SR", "GF"
        ),
        "europe" to listOf(
            "RU", "NO", "SE", "FI", "EE", "LV", "LT", "PL", "DE", "DK",
            "NL", "BE", "LU", "FR", "CH", "AT", "CZ", "SK", "HU", "SI",
            "HR", "BA", "RS", "ME", "AL", "MK", "BG", "RO", "MD", "UA",
            "BY", "IE", "GB", "IS", "PT", "ES", "IT", "MT", "GR", "CY"
        ),
        "africa" to listOf(
            "DZ", "LY", "EG", "SD", "TD", "NE", "ML", "MR", "SN", "GM",
            "GW", "GN", "SL", "LR", "CI", "GH", "TG", "BJ", "BF", "NG",
            "CM", "CF", "GQ", "GA", "CG", "CD", "AO", "ZM", "MW", "TZ",
            "KE", "UG", "RW", "BI", "SO", "ET", "ER", "DJ", "ZA", "NA",
            "BW", "ZW", "MZ", "SZ", "LS", "MG", "MA", "TN", "SS", "EH"
        ),
        "asia" to listOf(
            "CN", "IN", "ID", "PK", "BD", "JP", "PH", "VN", "TR", "IR",
            "TH", "MM", "KR", "IQ", "AF", "UZ", "MY", "NP", "YE", "KP",
            "LK", "KH", "JO", "AZ", "AE", "TJ", "SY", "LA", "LB", "SG",
            "OM", "KW", "GE", "MN", "AM", "QA", "BH", "BT", "BN", "MV",
            "KZ", "KG", "TM", "TL", "PS", "IL", "SA"
        ),
        "oceania" to listOf(
            "AU", "PG", "NZ", "FJ", "SB", "NC", "PF", "VU", "WS", "KI",
            "FM", "TO", "PW", "MH", "NR", "TV"
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun setupBottomNavigation() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemReselectedListener { menuItem ->
            if (menuItem.itemId == R.id.profileFragment) {
                val navController = findNavController()
                val currentDestinationId = navController.currentDestination?.id
                if (currentDestinationId != R.id.profileFragment) {
                    navController.popBackStack(R.id.profileFragment, false)
                } else {
                    // Refresh data when user taps profile tab again
                    refreshAllData()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repositories
        userRepository = UserRepository(requireActivity().application)
        authRepository = AuthRepository(requireActivity().application)
        userStatisticsRepository = UserStatisticsRepository(requireContext())

        // Initialize user data observer
        setupUserObserver()

        setupObservers()
        setupRefreshListener()
        setupBottomNavigation()

        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }

        // Setup the world map
        setupWorldMap()

        // Load all data when the fragment is created
        loadInitialData()
    }

    private fun loadInitialData() {
        Log.d("ProfileFragment", "Loading initial data")
        fetchUserData()
        loadUserStatistics()
        loadContinentData()
    }

    private fun refreshAllData() {
        Log.d("ProfileFragment", "Refreshing all data")
        fetchUserData()
        loadUserStatistics()
        loadContinentData()
    }

    private fun setupUserObserver() {
        viewLifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                userObserverJob = viewLifecycleOwner.lifecycleScope.launch {
                    userRepository.observeUser().collectLatest { user ->
                        if (user != null) {
                            _userState.value = UserState.Success(user)
                        }
                    }
                }
            }
        })
    }

    // Fetch user data from server
    fun fetchUserData() {
        _userState.value = UserState.Loading

        viewLifecycleOwner.lifecycleScope.launch {
            // First, try to get user from local database
            val localUser = userRepository.getUserFromDb()

            if (localUser != null) {
                // If user is found locally, update UI with local data
                _userState.value = UserState.Success(localUser)
            } else {
                // If no user is found locally, fetch user data from server
                val result = userRepository.fetchAndSaveUser()

                result.fold(
                    onSuccess = {
                        // The user data will be updated through the observer once the repository saves it
                    },
                    onFailure = { exception ->
                        // Instead of using Error state, directly use Logout state
                        _userState.value = UserState.Logout

                        // Show toast and navigate to login page when user ID is not found
                        if (_binding != null) {
                            ToastHelper.showShortToast(requireContext(), "User not found. Please login again")
                        }
                    }
                )
            }
        }
    }

    private fun setupObservers() {
        userState.observe(viewLifecycleOwner) { state ->
            // Check if binding is still available before updating UI
            _binding?.let { safeBinding ->
                when (state) {
                    is UserState.Loading -> {
                        safeBinding.progressBar.visibility = View.VISIBLE
                        safeBinding.errorMessage.visibility = View.GONE
                    }
                    is UserState.Success -> {
                        safeBinding.progressBar.visibility = View.GONE
                        safeBinding.errorMessage.visibility = View.GONE

                        // Update UI with user data
                        safeBinding.usernameText.text = state.user.username
                        safeBinding.emailText.text = state.user.email

                        // Load profile picture with Glide
                        if (!state.user.profilePictureUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(state.user.profilePictureUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.drawable.ic_default_profile)
                                .error(R.drawable.ic_default_profile)
                                .into(safeBinding.profileImage)
                        } else {
                            safeBinding.profileImage.setImageResource(R.drawable.avatar_placeholder)
                        }
                    }
                    is UserState.Error -> {
                        safeBinding.progressBar.visibility = View.GONE
                        safeBinding.errorMessage.visibility = View.VISIBLE
                        safeBinding.errorMessage.text = state.message

                        ToastHelper.showShortToast(context, state.message)
                    }
                    is UserState.Logout -> {
                        safeBinding.progressBar.visibility = View.GONE
                        safeBinding.errorMessage.visibility = View.GONE
                        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                    }
                }
            }
        }
    }

    private fun setupRefreshListener() {
        binding.swipeRefresh.setOnRefreshListener {
            Log.d("ProfileFragment", "Pull-to-refresh triggered")
            refreshAllData()
            // Don't set isRefreshing to false immediately - let the loading finish
        }
    }

    private fun updateSwipeRefreshState() {
        // Only hide refresh indicator when both statistics and map data are done loading
        // AND the binding is still available
        _binding?.let { safeBinding ->
            if (!isLoadingStatistics && !isLoadingMapData) {
                safeBinding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Cancel all running jobs to prevent accessing destroyed view
        statisticsJob?.cancel()
        mapDataJob?.cancel()
        userObserverJob?.cancel()

        _binding = null
    }

    private fun loadUserStatistics() {
        if (isLoadingStatistics) {
            Log.d("ProfileFragment", "Statistics already loading, skipping...")
            return
        }

        isLoadingStatistics = true
        Log.d("ProfileFragment", "Starting to load user statistics (always syncing with server)")

        // Check if binding is available before starting
        _binding?.let { safeBinding ->
            // Show loading indicator
            safeBinding.statisticsProgressBar.visibility = View.VISIBLE
            safeBinding.statisticsContainer.visibility = View.GONE
        }

        statisticsJob = lifecycleScope.launch {
            try {
                // This now always syncs with server first thanks to the updated repository
                val result = userStatisticsRepository.getCurrentUserStatistics()

                result.fold(
                    onSuccess = { statistics ->
                        Log.d("ProfileFragment", "Successfully loaded statistics: ${statistics.countryCount} countries, ${statistics.percentageVisited}% visited")

                        // Check if binding is still available before updating UI
                        _binding?.let { safeBinding ->
                            // Update UI with statistics
                            safeBinding.percentageValue.text = statistics.percentageVisited
                            safeBinding.countryValue.text = statistics.countryCount.toString()

                            // Hide loading and show statistics
                            safeBinding.statisticsProgressBar.visibility = View.GONE
                            safeBinding.statisticsContainer.visibility = View.VISIBLE
                        }
                    },
                    onFailure = { error ->
                        Log.e("ProfileFragment", "Failed to load user statistics", error)

                        // Check if binding is still available before updating UI
                        _binding?.let { safeBinding ->
                            // Hide loading indicator
                            safeBinding.statisticsProgressBar.visibility = View.GONE
                            safeBinding.statisticsContainer.visibility = View.VISIBLE

                            // Show default values
                            safeBinding.percentageValue.text = "--"
                            safeBinding.countryValue.text = "--"
                        }

                        // Show different messages based on error type
                        val errorMessage = when {
                            error.message?.contains("User ID not found") == true -> "Please log in again"
                            error.message?.contains("network") == true || error.message?.contains("internet") == true -> "Check your internet connection"
                            else -> "Failed to load statistics"
                        }

                        // Only show toast if context is still available
                        if (_binding != null) {
                            ToastHelper.showShortToast(requireContext(), errorMessage)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Exception loading statistics", e)

                // Check if binding is still available before updating UI
                _binding?.let { safeBinding ->
                    safeBinding.statisticsProgressBar.visibility = View.GONE
                    safeBinding.statisticsContainer.visibility = View.VISIBLE
                    safeBinding.percentageValue.text = "--"
                    safeBinding.countryValue.text = "--"
                }

                // Only show toast if context is still available
                if (_binding != null) {
                    ToastHelper.showShortToast(requireContext(), "Error loading statistics")
                }
            } finally {
                isLoadingStatistics = false
                updateSwipeRefreshState()
            }
        }
    }

    private fun setupWorldMap() {
        binding.worldMapWebView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false // Hide default zoom controls
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            // Add JavaScript interface for communication
            addJavascriptInterface(WebAppInterface(), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("ProfileFragment", "WebView page finished loading")
                    // Map is ready, load continent data
                    loadContinentData()
                }
            }

            // Load the exact world map HTML
            loadDataWithBaseURL(null, getWorldMapHTML(), "text/html", "UTF-8", null)
        }
    }

    // JavaScript Interface for WebView communication
    inner class WebAppInterface {
        @JavascriptInterface
        fun onContinentClicked(continentName: String) {
            requireActivity().runOnUiThread {
                if (_binding != null) {
                    ToastHelper.showShortToast(requireContext(), "Clicked: $continentName")
                    // You can add navigation or other actions here
                }
            }
        }
    }

    private fun loadWorldMapFromAssets(): String {
        return try {
            requireContext().assets.open("world.svg").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error loading world map from assets", e)
            // Return fallback simple SVG if file not found
            getFallbackWorldMap()
        }
    }

    private fun getFallbackWorldMap(): String {
        return """
        <svg viewBox="0 0 1000 500" xmlns="http://www.w3.org/2000/svg">
            <rect width="1000" height="500" fill="#87CEEB"/>
            <!-- Simplified world map for fallback -->
            <g id="north-america">
                <path id="US" class="country" d="M200,200 L350,190 L340,250 L320,260 L280,270 L250,265 L220,255 L200,240 Z"/>
                <path id="CA" class="country" d="M180,120 L380,110 L370,180 L200,190 Z"/>
                <path id="MX" class="country" d="M200,270 L320,270 L310,320 L220,320 Z"/>
            </g>
            <g id="south-america">
                <path id="BR" class="country" d="M280,320 L380,310 L390,420 L300,430 Z"/>
                <path id="AR" class="country" d="M260,430 L320,420 L310,480 L250,480 Z"/>
            </g>
            <g id="europe">
                <path id="RU" class="country" d="M520,80 L900,70 L890,180 L510,190 Z"/>
                <path id="DE" class="country" d="M480,140 L520,135 L515,170 L475,175 Z"/>
                <path id="FR" class="country" d="M450,150 L485,145 L480,180 L445,185 Z"/>
            </g>
            <g id="africa">
                <path id="DZ" class="country" d="M470,230 L520,225 L515,280 L465,285 Z"/>
                <path id="EG" class="country" d="M525,225 L560,220 L555,270 L520,275 Z"/>
                <path id="ZA" class="country" d="M480,350 L530,345 L525,380 L475,385 Z"/>
            </g>
            <g id="asia">
                <path id="CN" class="country" d="M700,180 L850,170 L840,250 L690,260 Z"/>
                <path id="IN" class="country" d="M650,250 L750,240 L740,320 L640,330 Z"/>
                <path id="RU" class="country" d="M520,80 L900,70 L890,180 L510,190 Z"/>
            </g>
            <g id="oceania">
                <path id="AU" class="country" d="M750,380 L880,370 L870,430 L740,440 Z"/>
                <path id="NZ" class="country" d="M890,420 L920,415 L915,445 L885,450 Z"/>
            </g>
        </svg>
        """
    }

    private fun getWorldMapHTML(): String {
        val svgContent = loadWorldMapFromAssets()

        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <style>
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body { 
                margin: 0; 
                padding: 0;
                background: #f0f8ff;
                overflow: hidden;
                border-radius: 12px;
                user-select: none;
                -webkit-user-select: none;
                -webkit-touch-callout: none;
            }
            
            .map-container {
                width: 100%;
                height: 100vh;
                border-radius: 12px;
                overflow: hidden;
                background: #87CEEB;
                position: relative;
            }
            
            .zoom-controls {
                position: absolute;
                top: 10px;
                right: 10px;
                display: flex;
                flex-direction: column;
                z-index: 100;
                gap: 5px;
            }
            
            .zoom-btn {
                width: 40px;
                height: 40px;
                background: rgba(255, 255, 255, 0.9);
                border: 1px solid #ccc;
                border-radius: 6px;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                font-size: 18px;
                font-weight: bold;
                color: #333;
                transition: all 0.2s ease;
                user-select: none;
                -webkit-user-select: none;
            }
            
            .zoom-btn:hover {
                background: rgba(255, 255, 255, 1);
                transform: scale(1.05);
            }
            
            .zoom-btn:active {
                transform: scale(0.95);
            }
            
            .map-content {
                width: 100%;
                height: 100%;
                transition: transform 0.3s ease;
                transform-origin: center center;
            }
            
            svg { 
                width: 100%;
                height: 100%;
                max-width: none;
                max-height: none;
                border-radius: 12px;
                background: #87CEEB;
                cursor: grab;
                touch-action: none;
            }
            
            svg:active {
                cursor: grabbing;
            }
            
            svg path { 
                stroke: #fff; 
                stroke-width: 0.3; 
                cursor: pointer;
                fill: #E0E0E0;
                transition: all 0.2s ease;
            }
            
            svg path:hover {
                stroke-width: 1;
                stroke: #333;
                filter: brightness(1.1);
            }
            
            svg path.visited { 
                fill: #4CAF50 !important; 
            }
            
            svg path.unvisited { 
                fill: #E0E0E0 !important; 
            }
            
            @media (max-width: 768px) {
                svg path {
                    stroke-width: 0.25;
                }
                
                svg path:hover {
                    stroke-width: 0.8;
                }
                
                .zoom-btn {
                    width: 35px;
                    height: 35px;
                    font-size: 16px;
                }
            }
        </style>
    </head>
    <body>
        <div class="map-container">
            <div class="zoom-controls">
                <div class="zoom-btn" id="zoomIn">+</div>
                <div class="zoom-btn" id="zoomOut">−</div>
                <div class="zoom-btn" id="zoomReset">⌂</div>
            </div>
            
            <div class="map-content" id="mapContent">
                $svgContent
            </div>
        </div>
        
        <script>
            // Global variables
            var currentZoom = 1;
            var minZoom = 0.5;
            var maxZoom = 3;
            var zoomStep = 0.3;
            var mapContent = null;
            var isDragging = false;
            var startX = 0;
            var startY = 0;
            var translateX = 0;
            var translateY = 0;
            
            // Touch gesture variables
            var initialDistance = 0;
            var initialZoom = 1;
            var isZooming = false;
            var lastTouchTime = 0;
            var touchStartTime = 0;
            
            // Country mapping
            var countryNameToCode = {
                'Afghanistan': 'AF', 'Albania': 'AL', 'Algeria': 'DZ', 'Anguilla': 'AI', 
                'Armenia': 'AM', 'Aruba': 'AW', 'Austria': 'AT', 'Bahrain': 'BH', 
                'Bangladesh': 'BD', 'Barbados': 'BB', 'Belarus': 'BY', 'Belgium': 'BE', 
                'Belize': 'BZ', 'Benin': 'BJ', 'Bermuda': 'BM', 'Bhutan': 'BT', 
                'Bolivia': 'BO', 'Bosnia and Herzegovina': 'BA', 'Botswana': 'BW', 
                'Brazil': 'BR', 'British Virgin Islands': 'VG', 'Brunei Darussalam': 'BN', 
                'Bulgaria': 'BG', 'Burkina Faso': 'BF', 'Burundi': 'BI', 'Cambodia': 'KH', 
                'Cameroon': 'CM', 'Central African Republic': 'CF', 'Chad': 'TD', 
                'Colombia': 'CO', 'Costa Rica': 'CR', 'Croatia': 'HR', 'Cuba': 'CU', 
                'Curaçao': 'CW', 'Czech Republic': 'CZ', 'Côte d\'Ivoire': 'CI', 
                'Dem. Rep. Korea': 'KP', 'Democratic Republic of the Congo': 'CD', 
                'Djibouti': 'DJ', 'Dominica': 'DM', 'Dominican Republic': 'DO', 
                'Ecuador': 'EC', 'Egypt': 'EG', 'El Salvador': 'SV', 'Equatorial Guinea': 'GQ', 
                'Eritrea': 'ER', 'Estonia': 'EE', 'Ethiopia': 'ET', 'Finland': 'FI', 
                'French Guiana': 'GF', 'Gabon': 'GA', 'Georgia': 'GE', 'Germany': 'DE', 
                'Ghana': 'GH', 'Greenland': 'GL', 'Grenada': 'GD', 'Guam': 'GU', 
                'Guatemala': 'GT', 'Guinea': 'GN', 'Guinea-Bissau': 'GW', 'Guyana': 'GY', 
                'Haiti': 'HT', 'Honduras': 'HN', 'Hungary': 'HU', 'Iceland': 'IS', 
                'India': 'IN', 'Iran': 'IR', 'Iraq': 'IQ', 'Ireland': 'IE', 'Israel': 'IL', 
                'Jamaica': 'JM', 'Jordan': 'JO', 'Kazakhstan': 'KZ', 'Kenya': 'KE', 
                'Kosovo': 'XK', 'Kuwait': 'KW', 'Kyrgyzstan': 'KG', 'Lao PDR': 'LA', 
                'Latvia': 'LV', 'Lebanon': 'LB', 'Lesotho': 'LS', 'Liberia': 'LR', 
                'Libya': 'LY', 'Lithuania': 'LT', 'Luxembourg': 'LU', 'Macedonia': 'MK', 
                'Madagascar': 'MG', 'Malawi': 'MW', 'Maldives': 'MV', 'Mali': 'ML', 
                'Marshall Islands': 'MH', 'Martinique': 'MQ', 'Mauritania': 'MR', 
                'Mayotte': 'YT', 'Mexico': 'MX', 'Moldova': 'MD', 'Mongolia': 'MN', 
                'Montenegro': 'ME', 'Montserrat': 'MS', 'Morocco': 'MA', 'Mozambique': 'MZ', 
                'Myanmar': 'MM', 'Namibia': 'NA', 'Nauru': 'NR', 'Nepal': 'NP', 
                'Netherlands': 'NL', 'Nicaragua': 'NI', 'Niger': 'NE', 'Nigeria': 'NG', 
                'Pakistan': 'PK', 'Palau': 'PW', 'Palestine': 'PS', 'Panama': 'PA', 
                'Paraguay': 'PY', 'Peru': 'PE', 'Poland': 'PL', 'Portugal': 'PT', 
                'Qatar': 'QA', 'Republic of Congo': 'CG', 'Republic of Korea': 'KR', 
                'Reunion': 'RE', 'Romania': 'RO', 'Rwanda': 'RW', 'Saint Lucia': 'LC', 
                'Saint Vincent and the Grenadines': 'VC', 'Saint-Barthélemy': 'BL', 
                'Saint-Martin': 'MF', 'Saudi Arabia': 'SA', 'Senegal': 'SN', 'Serbia': 'RS', 
                'Sierra Leone': 'SL', 'Sint Maarten': 'SX', 'Slovakia': 'SK', 'Slovenia': 'SI', 
                'Somalia': 'SO', 'South Africa': 'ZA', 'South Sudan': 'SS', 'Spain': 'ES', 
                'Sri Lanka': 'LK', 'Sudan': 'SD', 'Suriname': 'SR', 'Swaziland': 'SZ', 
                'Sweden': 'SE', 'Switzerland': 'CH', 'Syria': 'SY', 'Taiwan': 'TW', 
                'Tajikistan': 'TJ', 'Tanzania': 'TZ', 'Thailand': 'TH', 'The Gambia': 'GM', 
                'Timor-Leste': 'TL', 'Togo': 'TG', 'Tunisia': 'TN', 'Turkmenistan': 'TM', 
                'Tuvalu': 'TV', 'Uganda': 'UG', 'Ukraine': 'UA', 'United Arab Emirates': 'AE', 
                'Uruguay': 'UY', 'Uzbekistan': 'UZ', 'Venezuela': 'VE', 'Vietnam': 'VN', 
                'Western Sahara': 'EH', 'Yemen': 'YE', 'Zambia': 'ZM', 'Zimbabwe': 'ZW',
                
                // Countries not available in this SVG
                'UK': null, 'United Kingdom': null, 'Great Britain': null, 'Britain': null,
                'USA': null, 'United States': null, 'America': null,
                'South Korea': 'KR', 'North Korea': 'KP',
                'Russia': null, 'China': null, 'France': null, 'Italy': null,
                'Japan': null, 'Australia': null, 'Canada': null, 'Turkey': null,
                'Argentina': null, 'Chile': null, 'Norway': null, 'Denmark': null,
                'New Zealand': null, 'Singapore': null, 'Malaysia': null, 'Indonesia': null,
                'Philippines': null, 'Gambia': 'GM'
            };

            // Helper functions for touch gestures
            function getTouchDistance(touch1, touch2) {
                var dx = touch1.clientX - touch2.clientX;
                var dy = touch1.clientY - touch2.clientY;
                return Math.sqrt(dx * dx + dy * dy);
            }

            function findCountryElement(countryName) {
                var countryCode = countryNameToCode[countryName];
                
                if (countryCode === null) {
                    console.log('⚠ Country not available in this SVG map:', countryName);
                    return null;
                }
                
                if (!countryCode) {
                    console.log('No country code mapping found for:', countryName);
                    return null;
                }
                
                var element = document.getElementById(countryCode);
                if (element) {
                    console.log('✓ Found country element:', countryCode);
                    return element;
                }
                
                element = document.getElementById(countryCode.toLowerCase());
                if (element) {
                    console.log('✓ Found country element (lowercase):', countryCode.toLowerCase());
                    return element;
                }
                
                console.log('✗ Country code exists but element not found:', countryName, 'Code:', countryCode);
                return null;
            }

            function updateCountryColors(visitedCountries) {
                console.log('Updating map with countries:', visitedCountries);
                
                var allPaths = document.querySelectorAll('svg path');
                console.log('Total map elements:', allPaths.length);
                
                // Reset all countries
                for (var i = 0; i < allPaths.length; i++) {
                    var path = allPaths[i];
                    path.classList.remove('visited');
                    path.classList.add('unvisited');
                }
                
                var coloredCount = 0;
                var notAvailableCount = 0;
                
                if (visitedCountries && visitedCountries.length > 0) {
                    for (var i = 0; i < visitedCountries.length; i++) {
                        var countryName = visitedCountries[i];
                        var element = findCountryElement(countryName);
                        
                        if (element) {
                            console.log('✓ Coloring country:', countryName);
                            element.classList.remove('unvisited');
                            element.classList.add('visited');
                            coloredCount++;
                        } else {
                            var countryCode = countryNameToCode[countryName];
                            if (countryCode === null) {
                                notAvailableCount++;
                            }
                        }
                    }
                }
                
                console.log('Map update complete. Colored:', coloredCount, '| Not available:', notAvailableCount);
                return coloredCount;
            }

            function zoom(delta) {
                var newZoom = currentZoom + delta;
                if (newZoom >= minZoom && newZoom <= maxZoom) {
                    currentZoom = newZoom;
                    updateTransform();
                    console.log('Zoomed to:', currentZoom);
                }
            }

            function resetZoom() {
                currentZoom = 1;
                translateX = 0;
                translateY = 0;
                updateTransform();
                console.log('Zoom reset');
            }

            function updateTransform() {
                if (mapContent) {
                    mapContent.style.transform = 'translate(' + translateX + 'px, ' + translateY + 'px) scale(' + currentZoom + ')';
                }
            }

            function setupZoomControls() {
                var zoomInBtn = document.getElementById('zoomIn');
                var zoomOutBtn = document.getElementById('zoomOut');
                var zoomResetBtn = document.getElementById('zoomReset');
                
                if (zoomInBtn) {
                    zoomInBtn.onclick = function() { zoom(zoomStep); };
                }
                
                if (zoomOutBtn) {
                    zoomOutBtn.onclick = function() { zoom(-zoomStep); };
                }
                
                if (zoomResetBtn) {
                    zoomResetBtn.onclick = function() { resetZoom(); };
                }
                
                console.log('✓ Zoom controls initialized');
            }

            function setupTouchControls() {
                var container = document.querySelector('.map-container');
                if (!container) return;
                
                container.ontouchstart = function(e) {
                    touchStartTime = new Date().getTime();
                    
                    if (e.touches.length === 1) {
                        // Single touch - pan
                        if (!isZooming) {
                            isDragging = true;
                            var touch = e.touches[0];
                            startX = touch.clientX - translateX;
                            startY = touch.clientY - translateY;
                        }
                        e.preventDefault();
                    } else if (e.touches.length === 2) {
                        // Two touches - pinch zoom
                        isDragging = false;
                        isZooming = true;
                        
                        var touch1 = e.touches[0];
                        var touch2 = e.touches[1];
                        
                        initialDistance = getTouchDistance(touch1, touch2);
                        initialZoom = currentZoom;
                        
                        console.log('Pinch zoom started');
                        e.preventDefault();
                    }
                };
                
                container.ontouchmove = function(e) {
                    if (e.touches.length === 1 && isDragging && !isZooming) {
                        // Single touch pan
                        var touch = e.touches[0];
                        translateX = touch.clientX - startX;
                        translateY = touch.clientY - startY;
                        updateTransform();
                        e.preventDefault();
                    } else if (e.touches.length === 2 && isZooming) {
                        // Two finger pinch zoom
                        var touch1 = e.touches[0];
                        var touch2 = e.touches[1];
                        
                        var currentDistance = getTouchDistance(touch1, touch2);
                        var scaleChange = currentDistance / initialDistance;
                        var newZoom = initialZoom * scaleChange;
                        
                        // Apply zoom with limits
                        if (newZoom >= minZoom && newZoom <= maxZoom) {
                            currentZoom = newZoom;
                            updateTransform();
                        }
                        
                        e.preventDefault();
                    }
                };
                
                container.ontouchend = function(e) {
                    var touchEndTime = new Date().getTime();
                    var touchDuration = touchEndTime - touchStartTime;
                    
                    if (e.touches.length === 0) {
                        // All touches ended
                        var wasQuickTap = touchDuration < 200 && !isDragging && !isZooming;
                        
                        isDragging = false;
                        isZooming = false;
                        
                        // Double tap to reset zoom (quick successive taps)
                        if (wasQuickTap && touchEndTime - lastTouchTime < 400) {
                            resetZoom();
                            console.log('Double tap zoom reset');
                        }
                        
                        lastTouchTime = touchEndTime;
                    } else if (e.touches.length === 1) {
                        // One touch remaining, switch back to pan mode
                        isZooming = false;
                        if (!isDragging) {
                            isDragging = true;
                            var touch = e.touches[0];
                            startX = touch.clientX - translateX;
                            startY = touch.clientY - translateY;
                        }
                    }
                    
                    e.preventDefault();
                };
                
                console.log('✓ Touch controls initialized (pinch-to-zoom enabled)');
            }

            function setupMouseControls() {
                var svg = document.querySelector('svg');
                if (!svg) return;
                
                // Mouse drag for desktop
                svg.onmousedown = function(e) {
                    isDragging = true;
                    startX = e.clientX - translateX;
                    startY = e.clientY - translateY;
                    svg.style.cursor = 'grabbing';
                    e.preventDefault();
                };
                
                document.onmousemove = function(e) {
                    if (!isDragging) return;
                    translateX = e.clientX - startX;
                    translateY = e.clientY - startY;
                    updateTransform();
                    e.preventDefault();
                };
                
                document.onmouseup = function() {
                    if (isDragging) {
                        isDragging = false;
                        svg.style.cursor = 'grab';
                    }
                };
                
                // Mouse wheel zoom
                svg.onwheel = function(e) {
                    e.preventDefault();
                    var delta = e.deltaY > 0 ? -zoomStep : zoomStep;
                    zoom(delta);
                };
                
                console.log('✓ Mouse controls initialized');
            }

            function initializeMap() {
                console.log('Initializing map...');
                
                mapContent = document.getElementById('mapContent');
                
                setupZoomControls();
                setupTouchControls();
                setupMouseControls();
                
                // Add country click listeners
                var allPaths = document.querySelectorAll('svg path');
                for (var i = 0; i < allPaths.length; i++) {
                    allPaths[i].onclick = function(e) {
                        if (isDragging || isZooming) {
                            e.preventDefault();
                            return;
                        }
                        
                        var countryId = this.id || 'Unknown';
                        console.log('Country clicked:', countryId);
                        
                        if (window.Android && window.Android.onContinentClicked) {
                            window.Android.onContinentClicked(countryId);
                        }
                    };
                }
                
                console.log('✓ Map initialization complete');
            }

            // Make functions globally available
            window.updateMap = updateCountryColors;
            window.updateCountryColors = updateCountryColors;
            window.initializeMap = initializeMap;
            
            // Initialize when ready
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', initializeMap);
            } else {
                initializeMap();
            }
            
            console.log('✓ Map script loaded successfully');
        </script>
    </body>
    </html>
    """.trimIndent()
    }

    private fun loadContinentData() {
        if (isLoadingMapData) {
            Log.d("ProfileFragment", "Map data already loading, skipping...")
            return
        }

        isLoadingMapData = true
        Log.d("ProfileFragment", "Starting to load continent data (always syncing with server)")

        mapDataJob = lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(1000) // Allow WebView to fully load

                // This now always syncs with server first thanks to the updated repository
                val result = userStatisticsRepository.getCurrentUserStatistics()

                result.fold(
                    onSuccess = { statistics ->
                        // Use the actual countries list from the server
                        val visitedCountries = statistics.countries ?: emptyList()
                        Log.d("ProfileFragment", "Loaded ${visitedCountries.size} visited countries from server: $visitedCountries")

                        updateWorldMapCountries(visitedCountries)
                    },
                    onFailure = { error ->
                        Log.e("ProfileFragment", "Failed to load user statistics for map", error)

                        // Check if we have cached data as fallback
                        try {
                            val cachedResult = userStatisticsRepository.getCachedUserStatistics()
                            cachedResult.fold(
                                onSuccess = { cachedStats ->
                                    Log.w("ProfileFragment", "Using cached data for map")
                                    val cachedCountries = cachedStats.countries ?: emptyList()
                                    updateWorldMapCountries(cachedCountries)
                                },
                                onFailure = {
                                    Log.w("ProfileFragment", "No cached data available, using empty map")
                                    updateWorldMapCountries(emptyList())
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error loading cached data", e)
                            updateWorldMapCountries(emptyList())
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e("ProfileFragment", "Exception loading continent statistics", e)
                updateWorldMapCountries(emptyList())
            } finally {
                isLoadingMapData = false
                updateSwipeRefreshState()
            }
        }
    }

    private fun updateWorldMapCountries(visitedCountries: List<String>) {
        if (visitedCountries.isEmpty()) {
            Log.d("ProfileFragment", "No countries to display on map")
            return
        }

        // Check if binding is still available before accessing WebView
        _binding?.let { safeBinding ->
            val countriesArray = visitedCountries.joinToString("\",\"", "[\"", "\"]")
            val javascript = "if(typeof updateMap === 'function') { updateMap($countriesArray); } else { console.log('updateMap not available'); }"

            safeBinding.worldMapWebView.post {
                // Double-check binding is still available in the post block
                _binding?.let {
                    safeBinding.worldMapWebView.evaluateJavascript(javascript) { result ->
                        Log.d("ProfileFragment", "Map update completed. Countries colored: $result")
                    }
                }
            }
        }
    }
}