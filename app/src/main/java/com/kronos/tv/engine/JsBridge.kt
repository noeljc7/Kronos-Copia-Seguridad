package com.kronos.tv.engine

import android.util.Log
import org.jsoup.Jsoup
import java.io.IOException

/**
 * EL PUENTE (JsBridge)
 * Esta clase será inyectada dentro del motor JavaScript.
 * Permite que los scripts externos usen el poder de Jsoup (Kotlin) para descargar webs.
 */
class JsBridge {

    /**
     * Descarga el código fuente (HTML) de una URL.
     * Simula ser un navegador real (Chrome en Windows) para evitar bloqueos simples.
     * * @param url La dirección web a descargar.
     * @return El código HTML en formato String, o vacío si falla.
     */
    fun fetchHtml(url: String): String {
        return try {
            Log.d("KRONOS_ENGINE", "JsBridge solicitando: $url")
            
            Jsoup.connect(url)
                // Usamos un User-Agent genérico de PC para pasar desapercibidos
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .timeout(15000) // 15 segundos de espera máxima
                .ignoreContentType(true) // Descargar aunque no sea text/html puro
                .referrer("https://google.com")
                .followRedirects(true)
                .execute()
                .body()
        } catch (e: IOException) {
            log("Error en fetchHtml para $url: ${e.message}")
            ""
        } catch (e: Exception) {
            log("Error crítico en fetchHtml: ${e.message}")
            ""
        }
    }

    /**
     * Herramienta de depuración.
     * Permite que el script JS escriba en el Logcat de Android Studio.
     * Uso en JS: bridge.log("Hola mundo");
     */
    fun log(msg: String) {
        Log.d("KRONOS_JS_LOG", msg)
    }
}

