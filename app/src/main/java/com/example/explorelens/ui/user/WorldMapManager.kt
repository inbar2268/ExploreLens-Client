package com.example.explorelens.ui.user

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.explorelens.common.helpers.ToastHelper

class WorldMapManager(
    private val context: Context,
    private val webView: WebView
) {

    interface MapClickListener {
        fun onCountryClicked(countryId: String)
    }

    private var mapClickListener: MapClickListener? = null
    private var isMapReady = false
    private var pendingCountries: List<String>? = null // Store countries to color when map is ready

    fun setMapClickListener(listener: MapClickListener) {
        this.mapClickListener = listener
    }

    fun setupWorldMap(onMapReady: () -> Unit) {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            addJavascriptInterface(WebAppInterface(), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WorldMapManager", "WebView page finished loading")
                    isMapReady = true

                    // If we have pending countries to color, do it now
                    pendingCountries?.let { countries ->
                        Log.d(
                            "WorldMapManager",
                            "Applying pending countries after map ready: $countries"
                        )
                        updateCountriesInternal(countries)
                        pendingCountries = null
                    }

                    onMapReady()
                }
            }

            loadDataWithBaseURL(null, getWorldMapHTML(), "text/html", "UTF-8", null)
        }
    }

    fun updateCountries(visitedCountries: List<String>) {
        if (!isMapReady) {
            Log.d(
                "WorldMapManager",
                "Map not ready, storing countries for later: $visitedCountries"
            )
            pendingCountries = visitedCountries
            return
        }

        updateCountriesInternal(visitedCountries)
    }

    private fun updateCountriesInternal(visitedCountries: List<String>) {
        if (visitedCountries.isEmpty()) {
            Log.d("WorldMapManager", "No countries to display on map")
            return
        }

        val countriesArray = visitedCountries.joinToString("\",\"", "[\"", "\"]")
        val javascript = """
            if(typeof updateMap === 'function') { 
                console.log('Updating map with countries from Android: $visitedCountries');
                updateMap($countriesArray); 
            } else { 
                console.log('updateMap function not available yet'); 
            }
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(javascript) { result ->
                Log.d("WorldMapManager", "Map update completed. Countries colored: $result")
            }
        }
    }

    /**
     * Call this when the fragment is being destroyed to clean up
     */
    fun cleanup() {
        isMapReady = false
        pendingCountries = null
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun onContinentClicked(continentName: String) {
            // Post to main thread to ensure UI operations are safe
            webView.post {
                ToastHelper.showShortToast(context, "Clicked: $continentName")
                mapClickListener?.onCountryClicked(continentName)
            }
        }
    }

    private fun loadWorldMapFromAssets(): String {
        return try {
            context.assets.open("world.svg").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("WorldMapManager", "Error loading world map from assets", e)
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
                stroke: #2E7D32 !important;
                stroke-width: 0.5 !important;
            }
            
            svg path.unvisited { 
                fill: #E0E0E0 !important; 
                stroke: #fff !important;
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
            <div class="map-content" id="mapContent">
                $svgContent
            </div>
        </div>
        
        <script>
            var currentZoom = 1.2;  // Changed from 1 to 1.2 for slightly bigger default zoom
            var minZoom = 0.5;
            var maxZoom = 3;
            var zoomStep = 0.3;
            var mapContent = null;
            var isDragging = false;
            var startX = 0;
            var startY = 0;
            var translateX = 0;
            var translateY = 0;
            var initialDistance = 0;
            var initialZoom = 1.2;  // Changed from 1 to 1.2 to match currentZoom
            var isZooming = false;
            var lastTouchTime = 0;
            var touchStartTime = 0;
            
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
                'UK': null, 'United Kingdom': null, 'Great Britain': null, 'Britain': null,
                'USA': null, 'United States': null, 'America': null,
                'South Korea': 'KR', 'North Korea': 'KP',
                'Russia': null, 'China': null, 'France': null, 'Italy': null,
                'Japan': null, 'Australia': null, 'Canada': null, 'Turkey': null,
                'Argentina': null, 'Chile': null, 'Norway': null, 'Denmark': null,
                'New Zealand': null, 'Singapore': null, 'Malaysia': null, 'Indonesia': null,
                'Philippines': null, 'Gambia': 'GM'
            };

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
                    console.log('✓ Found country element:', countryCode, 'for country:', countryName);
                    return element;
                }
                
                element = document.getElementById(countryCode.toLowerCase());
                if (element) {
                    console.log('✓ Found country element (lowercase):', countryCode.toLowerCase(), 'for country:', countryName);
                    return element;
                }
                
                console.log('✗ Country code exists but element not found:', countryName, 'Code:', countryCode);
                
                // Debug: List all available IDs to help find the correct mapping
                var allElements = document.querySelectorAll('svg path[id]');
                var foundIds = [];
                for (var i = 0; i < Math.min(10, allElements.length); i++) {
                    foundIds.push(allElements[i].id);
                }
                console.log('Available element IDs (first 10):', foundIds);
                
                return null;
            }

            function updateCountryColors(visitedCountries) {
                console.log('Updating map with countries:', visitedCountries);
                
                var allPaths = document.querySelectorAll('svg path');
                console.log('Total map elements:', allPaths.length);
                
                // Reset all countries to unvisited first
                for (var i = 0; i < allPaths.length; i++) {
                    var path = allPaths[i];
                    path.classList.remove('visited');
                    path.classList.add('unvisited');
                    // Force style reset
                    path.style.fill = '#E0E0E0';
                    path.style.stroke = '#fff';
                    path.style.strokeWidth = '0.3';
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
                            // Force style application with stronger colors
                            element.style.fill = '#4CAF50';
                            element.style.stroke = '#2E7D32';
                            element.style.strokeWidth = '0.8';
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
                
                // Force a repaint
                if (typeof requestAnimationFrame !== 'undefined') {
                    requestAnimationFrame(function() {
                        console.log('Map repaint completed');
                    });
                }
                
                return coloredCount;
            }

            function zoom(delta) {
                var newZoom = currentZoom + delta;
                if (newZoom >= minZoom && newZoom <= maxZoom) {
                    currentZoom = newZoom;
                    updateTransform();
                }
            }

            function resetZoom() {
                currentZoom = 1.2;  // Changed from 1 to 1.2 for slightly bigger default zoom
                translateX = 0;
                translateY = 0;
                updateTransform();
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
            }

            function setupTouchControls() {
                var container = document.querySelector('.map-container');
                if (!container) return;
                
                container.ontouchstart = function(e) {
                    touchStartTime = new Date().getTime();
                    
                    if (e.touches.length === 1) {
                        if (!isZooming) {
                            isDragging = true;
                            var touch = e.touches[0];
                            startX = touch.clientX - translateX;
                            startY = touch.clientY - translateY;
                        }
                        e.preventDefault();
                    } else if (e.touches.length === 2) {
                        isDragging = false;
                        isZooming = true;
                        
                        var touch1 = e.touches[0];
                        var touch2 = e.touches[1];
                        
                        initialDistance = getTouchDistance(touch1, touch2);
                        initialZoom = currentZoom;
                        e.preventDefault();
                    }
                };
                
                container.ontouchmove = function(e) {
                    if (e.touches.length === 1 && isDragging && !isZooming) {
                        var touch = e.touches[0];
                        translateX = touch.clientX - startX;
                        translateY = touch.clientY - startY;
                        updateTransform();
                        e.preventDefault();
                    } else if (e.touches.length === 2 && isZooming) {
                        var touch1 = e.touches[0];
                        var touch2 = e.touches[1];
                        
                        var currentDistance = getTouchDistance(touch1, touch2);
                        var scaleChange = currentDistance / initialDistance;
                        var newZoom = initialZoom * scaleChange;
                        
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
                        var wasQuickTap = touchDuration < 200 && !isDragging && !isZooming;
                        
                        isDragging = false;
                        isZooming = false;
                        
                        if (wasQuickTap && touchEndTime - lastTouchTime < 400) {
                            resetZoom();
                        }
                        
                        lastTouchTime = touchEndTime;
                    } else if (e.touches.length === 1) {
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
            }

            function setupMouseControls() {
                var svg = document.querySelector('svg');
                if (!svg) return;
                
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
                
                svg.onwheel = function(e) {
                    e.preventDefault();
                    var delta = e.deltaY > 0 ? -zoomStep : zoomStep;
                    zoom(delta);
                };
            }

            function initializeMap() {
                mapContent = document.getElementById('mapContent');
                
                // Apply initial zoom
                updateTransform();
                
                setupZoomControls();
                setupTouchControls();
                setupMouseControls();
                
                var allPaths = document.querySelectorAll('svg path');
                for (var i = 0; i < allPaths.length; i++) {
                    allPaths[i].onclick = function(e) {
                        if (isDragging || isZooming) {
                            e.preventDefault();
                            return;
                        }
                        
                        var countryId = this.id || 'Unknown';
                        
                        if (window.Android && window.Android.onContinentClicked) {
                            window.Android.onContinentClicked(countryId);
                        }
                    };
                }
            }

            window.updateMap = updateCountryColors;
            window.updateCountryColors = updateCountryColors;
            window.initializeMap = initializeMap;
            
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', initializeMap);
            } else {
                initializeMap();
            }
        </script>
    </body>
    </html>
    """.trimIndent()
    }
}