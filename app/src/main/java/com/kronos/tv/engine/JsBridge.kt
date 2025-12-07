package com.kronos.tv.engine

import org.jsoup.Jsoup

class JsBridge {
    // Permite al JS descargar HTML
    fun fetchHtml(url: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
                .html()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    // Permite al JS imprimir logs en tu consola
    fun log(message: String) {
        println("KRONOS_JS: $message")
    }
}
