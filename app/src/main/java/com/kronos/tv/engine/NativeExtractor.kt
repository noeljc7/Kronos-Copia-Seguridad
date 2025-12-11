package com.kronos.tv.engine

import com.kronos.tv.ScreenLogger
import com.kronos.tv.providers.SourceLink
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern
import java.util.concurrent.TimeUnit

object NativeExtractor {

    // Cliente HTTP independiente para no mezclar cookies con el navegador
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Cabeceras que TU encontraste en los logs (User-Agent de Windows)
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    /**
     * Recibe: https://xupalace.org/video/tt0434665-14x37
     * Devuelve: Lista de SourceLink (Voe, Streamwish, etc)
     */
    fun extract(iframeUrl: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        
        try {
            ScreenLogger.log("NATIVE", "🔓 Desencriptando: $iframeUrl")

            // 1. Detección de Dominio y ID
            // Tu log mostró: /video/ID o /f/ID
            val matcher = Pattern.compile("/(?:f|video)/([a-zA-Z0-9-]+)").matcher(iframeUrl)
            if (!matcher.find()) {
                ScreenLogger.log("NATIVE", "⚠️ No se encontró ID en la URL")
                return emptyList()
            }
            
            val videoId = matcher.group(1) // Ej: tt0434665-14x37
            val domain = if (iframeUrl.contains("xupalace")) "https://xupalace.org" else "https://embed69.org"
            val apiUrl = "$domain/api/decrypt"

            // 2. Construir la Petición (Exactamente como en tu Log)
            val body = FormBody.Builder()
                .add("id", videoId!!)
                .build()

            val request = Request.Builder()
                .url(apiUrl)
                .post(body) // Método POST
                .header("User-Agent", USER_AGENT)
                .header("Referer", iframeUrl) // Referer es la misma URL del video
                .header("X-Requested-With", "XMLHttpRequest") // CRÍTICO: Sin esto falla
                .header("Origin", domain)
                .build()

            // 3. Disparar
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string() ?: "{}"

            // 4. Parsear el JSON que encontraste: {"success":true,"links":[{...}]}
            val json = JSONObject(jsonStr)
            
            if (json.optBoolean("success")) {
                val array = json.optJSONArray("links")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val finalUrl = item.optString("link").replace("\\", "") // Limpiar escapes
                        
                        if (finalUrl.isNotEmpty()) {
                            links.add(identifyServer(finalUrl))
                        }
                    }
                }
                ScreenLogger.log("NATIVE", "✅ Éxito: ${links.size} servidores extraídos")
            } else {
                ScreenLogger.log("NATIVE", "❌ API respondió false o error")
            }

        } catch (e: Exception) {
            ScreenLogger.log("NATIVE", "Error crítico: ${e.message}")
        }
        return links
    }

    private fun identifyServer(url: String): SourceLink {
        var name = "Server"
        val u = url.lowercase()
        
        if (u.contains("voe")) name = "Voe"
        else if (u.contains("dintezuvio") || u.contains("dood")) name = "Doodstream"
        else if (u.contains("filemoon")) name = "Filemoon"
        else if (u.contains("streamwish")) name = "Streamwish"
        else if (u.contains("vidhide")) name = "Vidhide"
        else if (u.contains("hglink")) name = "3Qi" // HgLink suele ser fast
        else if (u.contains("1fichier")) name = "1Fichier"

        // Detectar si es video directo (.mp4/.m3u8) o requiere web
        val isDirect = u.endsWith(".mp4") || u.endsWith(".m3u8")

        return SourceLink(
            name = name,
            url = url,
            quality = "HD",
            language = "Multi", 
            provider = "SoloLatino",
            isDirect = isDirect,
            requiresWebView = !isDirect
        )
    }
}
