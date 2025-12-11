package com.kronos.tv.engine

import android.util.Base64
import com.kronos.tv.ScreenLogger
import com.kronos.tv.providers.SourceLink
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern
import java.util.concurrent.TimeUnit

object NativeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    fun extract(iframeUrl: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            ScreenLogger.log("NATIVE", "🕵️ Analizando: $iframeUrl")

            val request = Request.Builder()
                .url(iframeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://sololatino.net/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // ESTRATEGIA A: JSON (Embed69 Moderno)
            // Busca: let dataLink = [...];
            if (html.contains("dataLink")) {
                links.addAll(extractEmbed69Json(html))
            }

            // ESTRATEGIA B: HTML Parsing (XuPalace / Legacy)
            // Busca: go_to_playerVast('url') + data-lang="0"
            if (links.isEmpty() || html.contains("go_to_player")) {
                links.addAll(extractXuPalaceHtml(html))
            }

        } catch (e: Exception) {
            ScreenLogger.log("NATIVE_ERROR", e.message ?: "Error desconocido")
        }
        
        ScreenLogger.log("NATIVE", "✅ Total enlaces extraídos: ${links.size}")
        return links
    }

    // --- LÓGICA EMBED69 (JSON) ---
    private fun extractEmbed69Json(html: String): List<SourceLink> {
        val found = mutableListOf<SourceLink>()
        try {
            val matcher = Pattern.compile("let\\s+dataLink\\s*=\\s*(\\[.*?\\]);", Pattern.DOTALL).matcher(html)
            if (matcher.find()) {
                val jsonArray = JSONArray(matcher.group(1))
                for (i in 0 until jsonArray.length()) {
                    val langGroup = jsonArray.getJSONObject(i)
                    val langCode = langGroup.optString("video_language", "UNK")
                    val prettyLang = mapLanguage(langCode)
                    
                    val embeds = langGroup.optJSONArray("sortedEmbeds") ?: continue
                    for (j in 0 until embeds.length()) {
                        val embed = embeds.getJSONObject(j)
                        val server = embed.optString("servername", "Server")
                        val link = embed.optString("link", "")
                        
                        if (!server.equals("download", true) && link.isNotEmpty()) {
                            // Embed69 usa JWT en el JSON
                            val realUrl = decodeJwt(link)
                            if (realUrl != null) {
                                found.add(createLink(server, realUrl, prettyLang))
                            }
                        }
                    }
                }
                ScreenLogger.log("NATIVE", "🔹 Embed69 JSON detectado")
            }
        } catch (e: Exception) {}
        return found
    }

    // --- LÓGICA XUPALACE (HTML + Mapeo de Idiomas) ---
    private fun extractXuPalaceHtml(html: String): List<SourceLink> {
        val found = mutableListOf<SourceLink>()
        try {
            // 1. Crear Mapa de Idiomas (ID -> Nombre)
            // Busca: <li ... data-lang="0"> <img src=".../LAT.png">
            val langMap = mutableMapOf<String, String>()
            val langMatcher = Pattern.compile("data-lang=\"(\\d+)\"[^>]*>\\s*<img[^>]+src=\"[^\"]*/([A-Z]{3})\\.png\"", Pattern.CASE_INSENSITIVE).matcher(html)
            
            while (langMatcher.find()) {
                val id = langMatcher.group(1) // "0"
                val code = langMatcher.group(2) // "LAT"
                if (id != null && code != null) {
                    langMap[id] = mapLanguage(code)
                }
            }

            // 2. Extraer Servidores
            // Busca: onclick="go_to_playerVast('URL',...)" ... data-lang="0" ... <span>ServerName</span>
            // Regex flexible para go_to_player O go_to_playerVast
            val playerMatcher = Pattern.compile("onclick=\"go_to_player(?:Vast)?\\('([^']+)'[^>]*data-lang=\"(\\d+)\"[^>]*>.*?<span>(.*?)</span>", Pattern.DOTALL).matcher(html)
            
            while (playerMatcher.find()) {
                val rawUrl = playerMatcher.group(1) // URL
                val langId = playerMatcher.group(2) // "0"
                val serverName = playerMatcher.group(3)?.trim() ?: "Server" // "streamwish"

                if (rawUrl != null) {
                    val lang = langMap[langId] ?: "Latino 🇲🇽" // Default si falla el mapa
                    
                    // A veces XuPalace pone la URL directa, a veces en Base64 o codificada
                    // Pero en tu ejemplo (source 72) viene limpia: https://hglink.to/...
                    
                    // Filtrar enlaces basura
                    if (rawUrl.startsWith("http")) {
                        found.add(createLink(serverName, rawUrl, lang))
                    }
                }
            }
            if (found.isNotEmpty()) ScreenLogger.log("NATIVE", "🔸 XuPalace HTML detectado")

        } catch (e: Exception) {}
        return found
    }

    // --- UTILIDADES ---

    private fun decodeJwt(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val json = JSONObject(String(decodedBytes))
            json.optString("link")
        } catch (e: Exception) { null }
    }

    private fun mapLanguage(code: String): String {
        return when(code.uppercase()) {
            "LAT", "LATINO" -> "Latino 🇲🇽"
            "ESP", "CASTELLANO" -> "Castellano 🇪🇸"
            "SUB", "SUBTITULADO" -> "Subtitulado 🇺🇸"
            "JAP" -> "Japonés 🇯🇵"
            else -> code
        }
    }

    private fun createLink(host: String, url: String, lang: String): SourceLink {
        // Limpiar nombre del servidor (Ej: "streamwish" -> "Streamwish")
        var prettyHost = host.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        
        // Detectar si requiere WebView (casi todos los de XuPalace/Embed lo requieren hoy día)
        // Excepto si encontramos un .mp4 directo
        val isDirect = url.endsWith(".mp4") || url.endsWith(".m3u8")

        return SourceLink(
            name = prettyHost,
            url = url,
            quality = "HD",
            language = lang,
            provider = "SoloLatino",
            isDirect = isDirect,
            requiresWebView = !isDirect
        )
    }
}
