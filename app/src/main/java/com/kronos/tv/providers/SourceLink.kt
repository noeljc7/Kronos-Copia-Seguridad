package com.kronos.tv.providers

data class SourceLink(
    val name: String,
    val url: String,
    val quality: String,
    val language: String,
    val iconUrl: String? = null,
    val isDirect: Boolean = false,
    val requiresWebView: Boolean = false,
    val provider: String = ""
)
