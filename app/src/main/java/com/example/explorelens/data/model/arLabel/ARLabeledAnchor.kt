package com.example.explorelens.data.model.arLabel
import com.google.ar.core.Anchor

data class ARLabeledAnchor(
    val anchor: Anchor,
    val label: String,
    val siteName: String? = null,
    var fullDescription: String? = null,
    var siteId: String? = null,
)