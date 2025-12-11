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

            // 1. Detección de ID Universal (MEJORADA)
            // Captura: /f/ID, /video/ID, ?id=ID, &id=ID
            val matcher = Pattern.compile("(?<=\\/f\\/|\\/video\\/|[?&]id=)([a-zA-Z0-9-]+)").matcher(iframeUrl)
            
            if (matcher.find()) {
                val videoId = matcher.group(1)
                // ScreenLogger.log("NATIVE", "🆔 ID Detectado: $videoId") // Debug opcional
                
                val request = Request.Builder()
                    .url(iframeUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://sololatino.net/")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                // ESTRATEGIA A: JSON (Embed69)
                if (html.contains("dataLink")) {
                    links.addAll(extractEmbed69Json(html))
                }

                // ESTRATEGIA B: HTML Parsing (XuPalace / Otros)
                if (links.isEmpty() || html.contains("go_to_player")) {
                    links.addAll(extractXuPalaceHtml(html))
                }
            } else {
                ScreenLogger.log("NATIVE", "⚠️ No se pudo extraer ID de la URL")
            }

        } catch (e: Exception) {
            ScreenLogger.log("NATIVE_ERROR", e.message ?: "Error desconocido")
        }
        
        ScreenLogger.log("NATIVE", "✅ Total enlaces extraídos: ${links.size}")
        return links
    }

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
                            val realLink = decodeJwt(link)
                            if (realLink != null) {
                                found.add(createLink(server, realLink, prettyLang))
                            }
                        }
                    }
                }
                ScreenLogger.log("NATIVE", "🔹 Embed69 JSON procesado")
            }
        } catch (e: Exception) {}
        return found
    }

    private fun extractXuPalaceHtml(html: String): List<SourceLink> {
        val found = mutableListOf<SourceLink>()
        try {
            val langMap = mutableMapOf<String, String>()
            val langMatcher = Pattern.compile("data-lang=\"(\\d+)\"[^>]*>\\s*<img[^>]+src=\"[^\"]*/([A-Z]{3})\\.png\"", Pattern.CASE_INSENSITIVE).matcher(html)
            while (langMatcher.find()) {
                langMap[langMatcher.group(1)] = mapLanguage(langMatcher.group(2))
            }

            val playerMatcher = Pattern.compile("onclick=\"go_to_player(?:Vast)?\\('([^']+)'[^>]*data-lang=\"(\\d+)\"[^>]*>.*?<span>(.*?)</span>", Pattern.DOTALL).matcher(html)
            while (playerMatcher.find()) {
                val rawUrl = playerMatcher.group(1)
                val langId = playerMatcher.group(2)
                val serverName = playerMatcher.group(3)?.trim() ?: "Server"

                if (rawUrl != null && rawUrl.startsWith("http")) {
                    val lang = langMap[langId] ?: "Latino 🇲🇽"
                    found.add(createLink(serverName, rawUrl, lang))
                }
            }
            if (found.isNotEmpty()) ScreenLogger.log("NATIVE", "🔸 XuPalace HTML procesado")
        } catch (e: Exception) {}
        return found
    }

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
        var prettyHost = host.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val isDirect = url.endsWith(".mp4") || url.endsWith(".m3u8")
        return SourceLink(name = prettyHost, url = url, quality = "HD", language = lang, provider = "SoloLatino", isDirect = isDirect, requiresWebView = !isDirect)
    }
}
