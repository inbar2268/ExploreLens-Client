package com.example.explorelens.model
import com.google.ar.core.Anchor

data class ARLabeledAnchor(
    val anchor: Anchor,
    val label: String,
    val siteName: String? = null, // Site name for DetailActivity
    var fullDescription: String? = null, // Full description for DetailActivity
    var siteId: String? = null,
)