package com.kronos.tv.providers

import com.kronos.tv.ui.AppLogger
import com.kronos.tv.utils.JsUnpacker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object LinkResolver {
    private val client = OkHttpClient()
    
    // Headers que imitan un navegador real (Vital para que no te bloqueen)
    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1"
    )

    fun resolve(url: String): String? {
        // Si ya es un video, retornarlo
        if (url.endsWith(".mp4") || url.endsWith(".m3u8")) return url

        val host = try { java.net.URI(url).host ?: "" } catch (e: Exception) { "" }
        AppLogger.log("Resolver", "Analizando host: $host")

        return when {
            // Grupo Packer (Vidhide, Streamwish, Voe, etc)
            host.contains("dintezuvio") || host.contains("vidhide") -> resolvePacked(url)
            host.contains("hglink") || host.contains("streamwish") || host.contains("wishembed") -> resolvePacked(url)
            host.contains("voe") -> resolvePacked(url) // Voe a veces usa packer también
            
            // Grupo Iframe (Filemoon)
            host.contains("filemoon") || host.contains("moon") -> resolveFilemoon(url)
            
            // Fallback
            else -> resolveGeneric(url)
        }
    }

    // --- ESTRATEGIA 1: PACKER (Para Vidhide, Streamwish) ---
    private fun resolvePacked(url: String): String? {
        try {
            val html = getHtml(url, mapOf("Referer" to url)) ?: return null
            
            // 1. Buscar el bloque encriptado 'eval(function(p,a,c,k,e,d)...'
            val unpacked = JsUnpacker.unpack(html)
            
            // Si no estaba encriptado, usamos el HTML original
            val sourceToSearch = unpacked ?: html 

            // 2. Buscar patrón JWPlayer: file:"https://..." o sources:[{file:"..."}]
            return extractVideoUrl(sourceToSearch)

        } catch (e: Exception) {
            AppLogger.log("Resolver", "Error Packer: ${e.message}")
        }
        return null
    }

    // --- ESTRATEGIA 2: FILEMOON (Iframe + Packer) ---
    private fun resolveFilemoon(url: String): String? {
        try {
            // Paso A: Obtener la página principal (la que me pasaste)
            val htmlWrapper = getHtml(url, mapOf("Referer" to url)) ?: return null

            // Paso B: Buscar el Iframe oculto
            // Tu código: <iframe src="https://ico3c.com/bkg/j38t6ba8qbk9" ...>
            val iframeMatcher = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']").matcher(htmlWrapper)
            
            if (iframeMatcher.find()) {
                val iframeUrl = iframeMatcher.group(1)
                AppLogger.log("Resolver", "Filemoon: Saltando a $iframeUrl")
                
                // Paso C: Ir al iframe y aplicar lógica Packer normal
                return resolvePacked(iframeUrl)
            } else {
                // Si no hay iframe, intentamos resolver directamente (a veces Filemoon cambia)
                return resolvePacked(url)
            }

        } catch (e: Exception) {
            AppLogger.log("Resolver", "Error Filemoon: ${e.message}")
        }
        return null
    }

    // --- UTILS ---

    private fun extractVideoUrl(content: String): String? {
        // Regex 1: file:"https://..." (Común en JWPlayer)
        var matcher = Pattern.compile("file\\s*:\\s*[\"'](https?://[^\"']+(?:\\.m3u8|\\.mp4)[^\"']*)[\"']").matcher(content)
        if (matcher.find()) return matcher.group(1)

        // Regex 2: sources: [{file: "..."}]
        matcher = Pattern.compile("sources\\s*:\\s*\\[.*?file\\s*:\\s*[\"'](https?://[^\"']+)[\"']", Pattern.DOTALL).matcher(content)
        if (matcher.find()) return matcher.group(1)
        
        // Regex 3: hls: "..." (Voe usa esto a veces)
        matcher = Pattern.compile("['\"]hls['\"]\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(content)
        if (matcher.find()) return matcher.group(1)

        return null
    }

    private fun resolveGeneric(url: String): String? {
        // Intento básico
        val html = getHtml(url) ?: return null
        return extractVideoUrl(html)
    }

    private fun getHtml(url: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return try {
            val headersBuilder = baseHeaders.toMutableMap()
            headersBuilder.putAll(extraHeaders)
            
            val request = Request.Builder().url(url).headers(headersBuilder.toHeaders()).build()
            val response = client.newCall(request).execute()
            response.body?.string()
        } catch (e: Exception) {
            AppLogger.log("Resolver", "Error de Red: ${e.message}")
            null
        }
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
}