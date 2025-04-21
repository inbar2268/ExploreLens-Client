package com.example.explorelens

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import com.example.explorelens.data.network.auth.AuthClient
import com.example.explorelens.utils.LoadingManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.ui.onNavDestinationSelected

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        AuthClient.init(applicationContext)

        bottomNavigationView = findViewById(R.id.bottom_navigation)


        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener(this)

        bottomNavigationView.setupWithNavController(navController)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.arActivity -> {
                    // Start AR activity
                    navController.navigate(R.id.arActivity)
                    true
                }
                else -> {
                    // For all other items, let the NavigationUI handle it
                    item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
                }
            }
        }
        handleNavigationIntents(intent)
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val fragmentsWithoutBottomNav = listOf(

            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment
        )

        if (destination.id in fragmentsWithoutBottomNav) {
            bottomNavigationView.visibility = View.GONE
        } else {
            bottomNavigationView.visibility = View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        LoadingManager.cleanup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // Store the new intent
        // Only handle navigation if already resumed
        if (hasWindowFocus()) {
            handleNavigationIntents(intent)
        }
    }

    private fun handleNavigationIntents(intent: Intent) {
        if (intent.hasExtra("NAVIGATE_TO")) {
            // Get the navigation target
            val navigateTo = intent.getStringExtra("NAVIGATE_TO") ?: return

            // Use a post-delayed handler to ensure the activity is fully ready
            Handler(Looper.getMainLooper()).postDelayed({
                when (navigateTo) {
                    "SITE_DETAILS_FRAGMENT" -> {
                        val label = intent.getStringExtra("LABEL_KEY") ?: return@postDelayed
                        val bundle = Bundle().apply {
                            putString("LABEL_KEY", label)
                            if (intent.hasExtra("DESCRIPTION_KEY")) {
                                putString("DESCRIPTION_KEY", intent.getStringExtra("DESCRIPTION_KEY"))
                            }
                        }

                        try {
                            // Ensure NavController is ready
                            navController.navigate(R.id.siteDetailsFragment, bundle)

                            // Clear the intent data to prevent reprocessing
                            intent.removeExtra("NAVIGATE_TO")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Navigation failed", e)
                        }
                    }
                }
            }, 100) // Small delay to ensure UI is ready
        }
    }
    override fun onResume() {
        super.onResume()
        // Handle navigation intents after the activity is fully resumed
        handleNavigationIntents(intent)
    }
}