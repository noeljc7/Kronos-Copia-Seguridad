package com.kronos.tv.providers // Ojo al paquete

data class SourceLink(
    val name: String,
    val url: String,
    val quality: String,
    val language: String,
    val isDirect: Boolean = false,
    val requiresWebView: Boolean = false
)
