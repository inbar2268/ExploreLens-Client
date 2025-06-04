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
import java.util.Locale
import android.os.Build

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

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
                    fetchUserData()
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

        // Fetch user data when the fragment is created
        fetchUserData()
        loadUserStatistics()
        loadContinentData()
    }

    private fun setupUserObserver() {
        viewLifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewLifecycleOwner.lifecycleScope.launch {
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
                        ToastHelper.showShortToast(requireContext(), "User not found. Please login again")
                    }
                )
            }
        }
    }

    private fun setupObservers() {
        userState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UserState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.errorMessage.visibility = View.GONE
                }
                is UserState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.GONE

                    // Update UI with user data
                    binding.usernameText.text = state.user.username
                    binding.emailText.text = state.user.email

                    // Load profile picture with Glide
                    if (!state.user.profilePictureUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(state.user.profilePictureUrl)
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.drawable.ic_default_profile)
                            .error(R.drawable.ic_default_profile)
                            .into(binding.profileImage)
                    } else {
                        binding.profileImage.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
                is UserState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.VISIBLE
                    binding.errorMessage.text = state.message

                    ToastHelper.showShortToast(context, state.message)
                }
                is UserState.Logout -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorMessage.visibility = View.GONE
                    findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                }
            }
        }
    }

    private fun setupRefreshListener() {
        binding.swipeRefresh.setOnRefreshListener {
            fetchUserData()
            loadUserStatistics()
            loadContinentData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadUserStatistics() {
        // Show loading indicator
        binding.statisticsProgressBar.visibility = View.VISIBLE
        binding.statisticsContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = userStatisticsRepository.getCurrentUserStatistics()

                result.fold(
                    onSuccess = { statistics ->
                        // Update UI with statistics
                        binding.percentageValue.text = statistics.percentageVisited
                        binding.countryValue.text = statistics.countryCount.toString()

                        // Hide loading and show statistics
                        binding.statisticsProgressBar.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.VISIBLE
                    },
                    onFailure = { error ->
                        Log.e("ProfileFragment", "Failed to load user statistics", error)

                        // Hide loading indicator
                        binding.statisticsProgressBar.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.VISIBLE

                        // Show default values
                        binding.percentageValue.text = "--"
                        binding.countryValue.text = "--"

                        ToastHelper.showShortToast(requireContext(), "Failed to load statistics")
                    }
                )
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Exception loading statistics", e)
                binding.statisticsProgressBar.visibility = View.GONE
                binding.statisticsContainer.visibility = View.VISIBLE
                binding.percentageValue.text = "--"
                binding.countryValue.text = "--"
            }
        }
    }

    private fun setupWorldMap() {
        binding.worldMapWebView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            // Add JavaScript interface for communication
            addJavascriptInterface(WebAppInterface(), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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
                ToastHelper.showShortToast(requireContext(), "Clicked: $continentName")
                // You can add navigation or other actions here
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
        <style>
            body { margin: 0; padding: 4px; background: #f0f8ff; }
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
            svg path.visited { fill: #4CAF50 !important; }
            svg path.unvisited { fill: #E0E0E0 !important; }
            svg { max-width: 100%; height: auto; background: #87CEEB; }
        </style>
    </head>
    <body>
        <div>$svgContent</div>
        
        <script>
            console.log('Map script starting');
            
            var countryNameToCode = {
                'Afghanistan': 'AF', 'Albania': 'AL', 'Algeria': 'DZ', 'Argentina': 'AR',
                'Armenia': 'AM', 'Australia': 'AU', 'Austria': 'AT', 'Azerbaijan': 'AZ',
                'Bahrain': 'BH', 'Bangladesh': 'BD', 'Belarus': 'BY', 'Belgium': 'BE',
                'Bolivia': 'BO', 'Bosnia and Herzegovina': 'BA', 'Brazil': 'BR', 'Bulgaria': 'BG',
                'Cambodia': 'KH', 'Cameroon': 'CM', 'Canada': 'CA', 'Chile': 'CL',
                'China': 'CN', 'Colombia': 'CO', 'Croatia': 'HR', 'Cuba': 'CU',
                'Czech Republic': 'CZ', 'Denmark': 'DK', 'Ecuador': 'EC', 'Egypt': 'EG',
                'Estonia': 'EE', 'Ethiopia': 'ET', 'Finland': 'FI', 'France': 'FR',
                'Germany': 'DE', 'Ghana': 'GH', 'Greece': 'GR', 'Hungary': 'HU',
                'Iceland': 'IS', 'India': 'IN', 'Indonesia': 'ID', 'Iran': 'IR',
                'Iraq': 'IQ', 'Ireland': 'IE', 'Israel': 'IL', 'Italy': 'IT',
                'Japan': 'JP', 'Jordan': 'JO', 'Kazakhstan': 'KZ', 'Kenya': 'KE',
                'South Korea': 'KR', 'Kuwait': 'KW', 'Latvia': 'LV', 'Lebanon': 'LB',
                'Libya': 'LY', 'Lithuania': 'LT', 'Luxembourg': 'LU', 'Malaysia': 'MY',
                'Mexico': 'MX', 'Mongolia': 'MN', 'Morocco': 'MA', 'Myanmar': 'MM',
                'Nepal': 'NP', 'Netherlands': 'NL', 'New Zealand': 'NZ', 'Nigeria': 'NG',
                'North Korea': 'KP', 'Norway': 'NO', 'Pakistan': 'PK', 'Peru': 'PE',
                'Philippines': 'PH', 'Poland': 'PL', 'Portugal': 'PT', 'Romania': 'RO',
                'Russia': 'RU', 'Saudi Arabia': 'SA', 'Serbia': 'RS', 'Singapore': 'SG',
                'Slovakia': 'SK', 'Slovenia': 'SI', 'South Africa': 'ZA', 'Spain': 'ES',
                'Sri Lanka': 'LK', 'Sweden': 'SE', 'Switzerland': 'CH', 'Syria': 'SY',
                'Taiwan': 'TW', 'Thailand': 'TH', 'Turkey': 'TR', 'Ukraine': 'UA',
                'United Arab Emirates': 'AE', 'United Kingdom': 'GB', 'United States': 'US',
                'Uruguay': 'UY', 'Venezuela': 'VE', 'Vietnam': 'VN', 'Yemen': 'YE',
                'Zimbabwe': 'ZW', 'USA': 'US', 'UK': 'GB', 'UAE': 'AE'
            };

            function findCountryElement(countryName) {
                console.log('Searching for country:', countryName);
                
                var countryCode = countryNameToCode[countryName];
                console.log('Mapped to code:', countryCode);
                
                if (!countryCode) {
                    console.log('No country code found for:', countryName);
                    return null;
                }
                
                // Method 1: Try getElementById
                var element = document.getElementById(countryCode);
                if (element) {
                    console.log('Found by getElementById:', countryCode);
                    return element;
                }
                
                // Method 2: Try lowercase
                element = document.getElementById(countryCode.toLowerCase());
                if (element) {
                    console.log('Found by getElementById (lowercase):', countryCode.toLowerCase());
                    return element;
                }
                
                // Method 3: Let's see what IDs actually exist
                console.log('getElementById failed, checking all IDs...');
                var allPaths = document.querySelectorAll('svg path');
                var foundIds = [];
                
                for (var i = 0; i < allPaths.length; i++) {
                    var path = allPaths[i];
                    if (path.id && path.id.length <= 3) {
                        foundIds.push(path.id);
                        
                        // Check if this is our target
                        if (path.id === countryCode || path.id === countryCode.toLowerCase()) {
                            console.log('Found target country by manual search:', path.id);
                            return path;
                        }
                    }
                }
                
                console.log('First 20 country IDs found:', foundIds.slice(0, 20).join(', '));
                console.log('Does', countryCode, 'exist in IDs?', foundIds.includes(countryCode));
                console.log('Does', countryCode.toLowerCase(), 'exist in IDs?', foundIds.includes(countryCode.toLowerCase()));
                
                return null;
            }

            function debugSVGStructure() {
                console.log('=== DEBUG: Analyzing SVG Structure ===');
                var allPaths = document.querySelectorAll('svg path');
                console.log('Total paths:', allPaths.length);
                
                // Find ALL elements with IDs
                var allIds = [];
                var allDataIds = [];
                var allDataNames = [];
                
                for (var i = 0; i < allPaths.length; i++) {
                    var path = allPaths[i];
                    
                    if (path.id) {
                        allIds.push(path.id);
                    }
                    
                    var dataId = path.getAttribute('data-id');
                    if (dataId) {
                        allDataIds.push(dataId);
                    }
                    
                    var dataName = path.getAttribute('data-name');
                    if (dataName) {
                        allDataNames.push(dataName);
                    }
                }
                
                console.log('=== ALL IDs FOUND ===');
                console.log('Total IDs:', allIds.length);
                console.log('All IDs:', allIds.sort().join(', '));
                
                console.log('=== ALL DATA-IDs FOUND ===');
                console.log('Total data-ids:', allDataIds.length);
                if (allDataIds.length > 0) {
                    console.log('All data-ids:', allDataIds.sort().join(', '));
                }
                
                console.log('=== ALL DATA-NAMEs FOUND ===');
                console.log('Total data-names:', allDataNames.length);
                if (allDataNames.length > 0) {
                    console.log('First 20 data-names:', allDataNames.slice(0, 20).join(', '));
                }
                
                // Look for anything that might be Italy
                console.log('=== SEARCHING FOR ITALY VARIATIONS ===');
                var italyFound = false;
                
                for (var i = 0; i < allPaths.length; i++) {
                    var path = allPaths[i];
                    var id = path.id || '';
                    var dataId = path.getAttribute('data-id') || '';
                    var dataName = path.getAttribute('data-name') || '';
                    
                    if (id.toLowerCase().includes('it') || 
                        dataId.toLowerCase().includes('it') || 
                        dataName.toLowerCase().includes('italy')) {
                        
                        console.log('POTENTIAL ITALY FOUND at index', i, ':');
                        console.log('  id:', id);
                        console.log('  data-id:', dataId);
                        console.log('  data-name:', dataName);
                        italyFound = true;
                    }
                }
                
                if (!italyFound) {
                    console.log('NO ITALY-LIKE ELEMENTS FOUND');
                    console.log('Checking for alternative codes...');
                    
                    // Check for common Italy alternatives
                    var alternatives = ['ITA', 'ITALY', 'Italia'];
                    for (var j = 0; j < alternatives.length; j++) {
                        var alt = alternatives[j];
                        var element = document.getElementById(alt);
                        if (element) {
                            console.log('FOUND ITALY WITH ALTERNATIVE ID:', alt);
                            break;
                        }
                    }
                }
            }

            function updateCountryColors(visitedCountries) {
                console.log('updateCountryColors called with:', visitedCountries);
                
                var allPaths = document.querySelectorAll('svg path');
                console.log('Found', allPaths.length, 'SVG paths');
                
                // Reset all to gray
                for (var i = 0; i < allPaths.length; i++) {
                    allPaths[i].className = 'unvisited';
                    allPaths[i].style.fill = '#E0E0E0';
                }
                
                var coloredCount = 0;
                
                // Color visited countries
                if (visitedCountries && visitedCountries.length > 0) {
                    for (var i = 0; i < visitedCountries.length; i++) {
                        var countryName = visitedCountries[i];
                        console.log('Processing country:', countryName);
                        
                        var element = findCountryElement(countryName);
                        if (element) {
                            console.log('SUCCESS: Coloring', countryName);
                            element.className = 'visited';
                            element.style.fill = '#4CAF50';
                            coloredCount++;
                        } else {
                            console.log('FAILED: Could not find', countryName);
                        }
                    }
                }
                
                console.log('Coloring completed. Total colored:', coloredCount);
                return coloredCount;
            }

            // Make functions available globally
            window.updateMap = updateCountryColors;
            window.updateCountryColors = updateCountryColors;
            
            console.log('Map script loaded. updateMap available:', typeof window.updateMap);
            
            // Test on load
            setTimeout(function() {
                console.log('=== RUNNING DIAGNOSTIC TESTS ===');
                debugSVGStructure();
                
                console.log('=== TESTING ISRAEL COLORING ===');
                updateCountryColors(['Israel']);
            }, 1000);
        </script>
    </body>
    </html>
    """.trimIndent()
    }

    private fun loadContinentData() {
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(1000)

                // Get user statistics which now includes countries list
                val result = userStatisticsRepository.getCurrentUserStatistics()

                result.fold(
                    onSuccess = { statistics ->
                        // Use the countries list from the server
                        val visitedCountries = statistics.countries ?: emptyList()
                        Log.d("ProfileFragment", "Visited countries from server: $visitedCountries")

                        updateWorldMapCountries(visitedCountries)
                    },
                    onFailure = { error ->
                        Log.e("ProfileFragment", "Failed to load user statistics", error)

                        // Fallback to test data
                        val testCountries = listOf("Spain", "Japan", "Germany")
                        updateWorldMapCountries(testCountries)
                    }
                )

            } catch (e: Exception) {
                Log.e("ProfileFragment", "Exception loading continent statistics", e)
                updateWorldMapCountries(emptyList())
            }
        }
    }

    private fun updateWorldMapCountries(visitedCountries: List<String>) {
        if (visitedCountries.isEmpty()) {
            Log.d("ProfileFragment", "No countries to display")
            return
        }

        val countriesArray = visitedCountries.joinToString("\",\"", "[\"", "\"]")
        val javascript = "if(typeof updateMap === 'function') { updateMap($countriesArray); } else { console.log('updateMap not available'); }"

        binding.worldMapWebView.post {
            binding.worldMapWebView.evaluateJavascript(javascript) { result ->
                Log.d("ProfileFragment", "Map update result: $result")
            }
        }
    }

}