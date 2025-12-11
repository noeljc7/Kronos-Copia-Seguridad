package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import com.kronos.tv.ScreenLogger
import org.jsoup.Jsoup

class JsBridge {

    // MAPA DE CALLBACKS: Permite manejar múltiples peticiones a la vez
    private val callbacks = mutableMapOf<String, (String) -> Unit>()

    fun addCallback(id: String, callback: (String) -> Unit) {
        synchronized(callbacks) {
            callbacks[id] = callback
        }
    }

    // El JS nos devuelve el ID de la petición + el resultado
    @JavascriptInterface
    fun onResult(requestId: String, result: String) {
        synchronized(callbacks) {
            callbacks[requestId]?.invoke(result)
            callbacks.remove(requestId) // Limpiamos memoria
        }
    }

    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            // Configuración "Nivel Dios" para saltar protecciones básicas
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://google.com/")
                .ignoreContentType(true)
                .timeout(20000) // 20 segundos de timeout
                .execute()
                .body()
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo en $url: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .requestBody(data)
                .ignoreContentType(true)
                .post()
                .body()
                .text()
        } catch (e: Exception) { "{}" }
    }

    @JavascriptInterface
    fun log(message: String) {
        ScreenLogger.log("JS_LOG", message)
    }
}
