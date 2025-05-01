package com.example.explorelens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.utils.LoadingManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.explorelens.ui.site.SiteDetailsFragment
import androidx.activity.OnBackPressedCallback
import com.example.explorelens.utils.FragmentNavigationManager

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        ExploreLensApiClient.init(applicationContext)

        bottomNavigationView = findViewById(R.id.bottom_navigation)


        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            FragmentNavigationManager.setCurrentFragmentId(destination.id)
            updateBottomNavigationVisibility(destination.id)
        }

        bottomNavigationView.setupWithNavController(navController)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.arActivity -> {
                    launchArActivity()
                    false
                }
                else -> {
                    FragmentNavigationManager.setCurrentFragmentId(item.itemId)
                    item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
                    true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    ?.childFragmentManager?.fragments?.firstOrNull()

                if (currentFragment is SiteDetailsFragment && intent.hasExtra("NAVIGATE_TO")) {
                    // Create a new intent to return to ArActivity
                    val arIntent = Intent(this@MainActivity, ArActivity::class.java)
                    startActivity(arIntent)
                    finish()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        val currentFragmentId = navController.currentDestination?.id
        currentFragmentId?.let {
            updateBottomNavigationVisibility(it)
        }
    }

    private fun updateBottomNavigationVisibility(fragmentId: Int) {
        val fragmentsWithoutBottomNav = listOf(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment
        )

        if (fragmentId in fragmentsWithoutBottomNav) {
            bottomNavigationView.visibility = View.GONE
        } else {
            bottomNavigationView.visibility = View.VISIBLE
        }
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

        Log.d("MainActivity111", "Destination changed to: ${destination.id}")


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

    override fun onResume() {
        super.onResume()

        if (intent.getBooleanExtra("RETURNED_FROM_AR", false)) {
            intent.removeExtra("RETURNED_FROM_AR")

            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            val fragmentId = FragmentNavigationManager.getLastFragmentId()

            if (navController.currentDestination?.id != fragmentId) {
                navController.navigate(fragmentId)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val destinationId = FragmentNavigationManager.getLastFragmentId()

        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId)
        }
    }


    fun launchArActivity() {
        val intent = Intent(this, ArActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
    }

}