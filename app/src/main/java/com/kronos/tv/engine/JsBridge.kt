package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import org.jsoup.Jsoup
import android.util.Log
import com.kronos.tv.ScreenLogger // IMPORTANTE: Para ver los logs en la pantalla negra

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
            // JSOUP CONFIGURADO COMO UN NAVEGADOR REAL (NIVEL DIOS)
            val connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                // 👇 ESTAS SON LAS 3 LÍNEAS QUE ARREGLAN ZONAAPS 👇
                .header("X-Requested-With", "XMLHttpRequest") // La llave para la API
                .header("Referer", "https://zonaaps.com/")    // La cortesía necesaria
                .ignoreContentType(true)                      // Permite descargar JSON (no solo HTML)
                // 👆 ------------------------------------------ 👆
                .timeout(15000)
                .execute()

            connection.body()
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo al conectar a $url: ${e.message}")
            "" 
        }
    }

    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://zonaaps.com/")
                .requestBody(data)
                .ignoreContentType(true)
                .post()
            doc.body().text()
        } catch (e: Exception) { "{}" }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("KRONOS_JS", message)
        // ESTO ENVÍA EL LOG A TU PANTALLA NEGRA
        ScreenLogger.log("JS_LOG", message) 
    }
}
