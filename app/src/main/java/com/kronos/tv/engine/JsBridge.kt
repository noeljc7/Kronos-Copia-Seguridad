package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import org.jsoup.Jsoup
import android.util.Log

class JsBridge {
    
    // --- SISTEMA DE CALLBACK (EL TELÉFONO ROJO) ---
    var onResultCallback: ((String) -> Unit)? = null

    @JavascriptInterface
    fun onResult(result: String) {
        onResultCallback?.invoke(result)
    }
    // ----------------------------------------------

    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
                .html()
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .requestBody(data)
                .ignoreContentType(true)
                .post()
            doc.body().text()
        } catch (e: Exception) { "{}" }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("KRONOS_JS", message)
    }
}
