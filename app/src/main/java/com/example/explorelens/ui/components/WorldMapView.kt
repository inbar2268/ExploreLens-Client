package com.example.explorelens.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.example.explorelens.R

class WorldMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val visitedPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.continent_visited)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val unvisitedPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.continent_unvisited)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
      //  color = ContextCompat.getColor(context, R.color.continent_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        //color = ContextCompat.getColor(context, R.color.map_background)
        style = Paint.Style.FILL
    }

    private var visitedContinents: Set<String> = emptySet()
    private lateinit var continentPaths: Map<String, Path>

    init {
        setupContinentPaths()
    }

    fun setVisitedContinents(continents: List<String>) {
        visitedContinents = continents.map { normalizeContinentName(it) }.toSet()
        invalidate()
    }

    private fun normalizeContinentName(continent: String): String {
        return when (continent.trim().lowercase()) {
            "north america", "northamerica" -> "north_america"
            "south america", "southamerica" -> "south_america"
            else -> continent.trim().lowercase()
        }
    }

    private fun setupContinentPaths() {
        continentPaths = mapOf(
            "asia" to createAsiaPath(),
            "africa" to createAfricaPath(),
            "north_america" to createNorthAmericaPath(),
            "south_america" to createSouthAmericaPath(),
            "europe" to createEuropePath(),
            "australia" to createAustraliaPath(),
            "antarctica" to createAntarcticaPath()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val scaleX = width / 360f  // World longitude range
        val scaleY = height / 180f // World latitude range

        canvas.save()
        canvas.translate(width / 2f, height / 2f) // Center the map
        canvas.scale(scaleX, scaleY)

        // Draw each continent
        continentPaths.forEach { (continentName, path) ->
            val isVisited = visitedContinents.contains(continentName)
            val paint = if (isVisited) visitedPaint else unvisitedPaint

            canvas.drawPath(path, paint)
            canvas.drawPath(path, strokePaint)
        }

        canvas.restore()
    }

    // Simplified continent shapes using approximate coordinates
    private fun createAsiaPath(): Path {
        return Path().apply {
            moveTo(60f, -40f)   // Russia
            lineTo(140f, -60f)  // Eastern Russia
            lineTo(150f, -20f)  // Eastern Asia
            lineTo(140f, 0f)    // Southeast Asia
            lineTo(120f, 20f)   // Indonesia area
            lineTo(100f, 10f)   // India
            lineTo(80f, 0f)     // Middle East
            lineTo(70f, -20f)   // Central Asia
            close()
        }
    }

    private fun createAfricaPath(): Path {
        return Path().apply {
            moveTo(10f, -20f)   // North Africa
            lineTo(40f, -30f)   // Northeast Africa
            lineTo(50f, 0f)     // East Africa
            lineTo(45f, 30f)    // Southeast Africa
            lineTo(20f, 40f)    // South Africa
            lineTo(0f, 30f)     // Southwest Africa
            lineTo(-10f, 0f)    // West Africa
            lineTo(-5f, -20f)   // Northwest Africa
            close()
        }
    }

    private fun createNorthAmericaPath(): Path {
        return Path().apply {
            moveTo(-140f, -60f) // Alaska
            lineTo(-60f, -70f)  // Northern Canada
            lineTo(-50f, -40f)  // Eastern Canada
            lineTo(-70f, -30f)  // Eastern US
            lineTo(-100f, -25f) // Southern US
            lineTo(-120f, -30f) // Western US
            lineTo(-130f, -50f) // Western Canada
            close()
        }
    }

    private fun createSouthAmericaPath(): Path {
        return Path().apply {
            moveTo(-70f, -10f)  // Northern South America
            lineTo(-50f, -5f)   // Northeast Brazil
            lineTo(-40f, 20f)   // Eastern Brazil
            lineTo(-50f, 40f)   // Southeast Brazil
            lineTo(-70f, 50f)   // Southern Argentina
            lineTo(-80f, 30f)   // Chile
            lineTo(-85f, 0f)    // Peru/Ecuador
            close()
        }
    }

    private fun createEuropePath(): Path {
        return Path().apply {
            moveTo(0f, -50f)    // Scandinavia
            lineTo(30f, -55f)   // Northern Russia
            lineTo(40f, -40f)   // Western Russia
            lineTo(30f, -30f)   // Eastern Europe
            lineTo(10f, -35f)   // Central Europe
            lineTo(-5f, -40f)   // Western Europe
            lineTo(-10f, -45f)  // Atlantic Europe
            close()
        }
    }

    private fun createAustraliaPath(): Path {
        return Path().apply {
            moveTo(120f, 30f)   // Northern Australia
            lineTo(140f, 25f)   // Northeast Australia
            lineTo(150f, 35f)   // East Australia
            lineTo(140f, 45f)   // Southeast Australia
            lineTo(120f, 40f)   // South Australia
            lineTo(110f, 35f)   // West Australia
            close()
        }
    }

    private fun createAntarcticaPath(): Path {
        return Path().apply {
            moveTo(-180f, 70f)  // Western Antarctica
            lineTo(0f, 65f)     // Central Antarctica
            lineTo(180f, 70f)   // Eastern Antarctica
            lineTo(180f, 85f)   // Southern edge
            lineTo(-180f, 85f)  // Southern edge
            close()
        }
    }
}