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
import com.example.explorelens.ui.site.SiteDetailsFragment
import androidx.activity.OnBackPressedCallback

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
       // handleNavigationIntents(intent)
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

}