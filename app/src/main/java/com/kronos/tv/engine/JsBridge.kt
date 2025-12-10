package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import org.jsoup.Jsoup
import android.util.Log
import com.kronos.tv.ScreenLogger // <--- IMPORTANTE: Para ver los logs en pantalla

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
            // JSOUP CONFIGURADO COMO UN NAVEGADOR REAL
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest") // <--- ¡LA LLAVE MAESTRA!
                .header("Referer", "https://zonaaps.com/")    // <--- CORTESÍA NECESARIA
                .ignoreContentType(true)                      // <--- PERMITE DESCARGAR JSON
                .timeout(15000)                               // <--- MÁS TIEMPO DE ESPERA
                .execute()                                    // <--- EJECUTAR PETICIÓN PURA
                .body()                                       // <--- OBTENER TEXTO CRUDO (HTML O JSON)
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo al conectar: ${e.message}")
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
                .requestBody(data)
                .ignoreContentType(true)
                .post()
            doc.body().text()
        } catch (e: Exception) { "{}" }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("KRONOS_JS", message)
        // AHORA LOS LOGS DEL JS SE VERÁN EN TU PANTALLA NEGRA
        ScreenLogger.log("JS_LOG", message) 
    }
}
