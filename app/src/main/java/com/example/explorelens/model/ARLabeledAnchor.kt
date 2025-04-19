package com.example.explorelens.model
import com.google.ar.core.Anchor

data class ARLabeledAnchor(
    val anchor: Anchor,
    val label: String
)