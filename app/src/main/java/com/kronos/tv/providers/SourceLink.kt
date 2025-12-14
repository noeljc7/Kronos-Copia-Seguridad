package com.kronos.tv.providers

// Este archivo define qué es un enlace para toda la app
data class SourceLink(
    val name: String,
    val url: String,
    val quality: String,
    val language: String,
    val provider: String = "",
    val isDirect: Boolean = false,
    val requiresWebView: Boolean = false
)
