package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import org.jsoup.Jsoup

class JsBridge {
    
    // --- ESTO ES LO NUEVO QUE NECESITAMOS AGREGAR ---
    // Un "buzón" donde guardaremos la función para avisar a Kotlin
    var onResultCallback: ((String) -> Unit)? = null

    // Esta función la llamará el JS cuando termine: bridge.onResult(...)
    @JavascriptInterface
    fun onResult(result: String) {
        onResultCallback?.invoke(result)
    }
    // ------------------------------------------------

    // GET (Compatible con scripts viejos)
    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
                .html()
        } catch (e: Exception) {
            ""
        }
    }

    // POST (Compatible con scripts viejos)
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
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("KRONOS_JS", message)
    }
}
