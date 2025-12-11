package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import com.kronos.tv.ScreenLogger
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

class JsBridge {

    // Usamos ConcurrentHashMap para evitar problemas de hilos si hay muchas peticiones
    private val callbacks = ConcurrentHashMap<String, (String) -> Unit>()

    fun addCallback(id: String, callback: (String) -> Unit) {
        callbacks[id] = callback
    }

    @JavascriptInterface
    fun onResult(requestId: String, result: String) {
        // Ejecutamos y removemos
        callbacks.remove(requestId)?.invoke(result)
    }

    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            ScreenLogger.log("HTTP", "Conectando a: $url")
            
            // --- CONFIGURACIÓN NIVEL "NINJA" PARA EVITAR BLOQUEOS 403 ---
            val connection = Jsoup.connect(url)
                // Usamos un User-Agent de PC (Windows) porque suelen bloquear menos que los móviles genéricos
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                // IMPORTANTE: Referer dinámico (se engaña a la web diciendo que venimos de ella misma)
                .referrer(if (url.contains("sololatino")) "https://sololatino.net/" else "https://www.google.com/")
                .ignoreHttpErrors(true) // <--- ESTO EVITA EL CRASH SI DA ERROR 403/404
                .ignoreContentType(true)
                .followRedirects(true)
                .timeout(25000) // 25 segundos de tolerancia
                .execute()

            // Si a pesar de todo nos da error 403, logueamos pero no crasheamos
            if (connection.statusCode() != 200) {
                ScreenLogger.log("HTTP_WARN", "Respuesta del servidor: ${connection.statusCode()}")
            }

            connection.body()
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo en $url: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://zonaaps.com") // Ajuste para ZonaAps
                .header("Referer", "https://zonaaps.com/")
                .requestBody(data)
                .ignoreHttpErrors(true)
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
