package com.kronos.tv.providers

import android.util.Base64
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern
import java.text.Normalizer
import java.util.Locale

// Aseguramos que usamos los modelos correctos del paquete actual
import com.kronos.tv.providers.KronosProvider
import com.kronos.tv.providers.SourceLink

class SoloLatinoProvider : KronosProvider {
    override val name = "SoloLatino"
    // Propiedad 'language' requerida si tu interfaz KronosProvider nueva la pide.
    // Si tu interfaz KronosProvider NO tiene 'val language', borra esta línea.
    // Si SÍ la tiene (como en el Provider.kt que hicimos), déjala.
    val language = "Latino" 

    private val client = OkHttpClient()
    
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Referer" to "https://sololatino.net/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    // --- PELÍCULAS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                // 1. Obtener IMDB ID
                val response = RetrofitInstance.api.getMovieExternalIds(tmdbId, RetrofitInstance.getApiKey())
                val imdbId = response.imdb_id

                if (imdbId != null) {
                    AppLogger.log("SoloLatino", "IMDB encontrado: $imdbId")
                    // Estrategias Directas (Xupalace / Embed69)
                    links.addAll(processUrl("https://xupalace.org/video/$imdbId"))
                    links.addAll(processUrl("https://embed69.org/f/$imdbId"))
                } else {
                    AppLogger.log("SoloLatino", "⚠️ Sin IMDB ID, intentando slug...")
                }

                // Estrategia de Respaldo: Slug Web
                if (links.isEmpty()) {
                    val slug = cleanTitle(title)
                    // ... lógica de respaldo ...
                }
            } catch (e: Exception) {
                AppLogger.log("SoloLatino", "Error Película: ${e.message}")
            }
            links
        }
    }

    // --- SERIES ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                // 1. Obtener IMDB ID
                val response = RetrofitInstance.api.getTvExternalIds(tmdbId, RetrofitInstance.getApiKey())
                val imdbId = response.imdb_id
                
                // Formato episodio: 1x01 (Relleno con ceros)
                val epPad = "${season}x${episode.toString().padStart(2, '0')}"
                
                if (imdbId != null) {
                    AppLogger.log("SoloLatino", "Buscando S${season}E${episode} ($imdbId)")
                    
                    // Estrategia A: Xupalace (IMDB)
                    links.addAll(processUrl("https://xupalace.org/video/$imdbId-$epPad"))
                    
                    // Estrategia B: Embed69 (IMDB)
                    links.addAll(processUrl("https://embed69.org/f/$imdbId-$epPad"))
                }

                // Estrategia C: Web Slug (Respaldo si falla IMDB o no hay links)
                if (links.isEmpty()) {
                    val slug = cleanTitle(showTitle)
                    val webUrlPad = "https://sololatino.net/episodios/$slug-$epPad/"
                    val webUrlNoPad = "https://sololatino.net/episodios/$slug-${season}x${episode}/"
                    
                    AppLogger.log("SoloLatino", "Intentando Web: $slug")
                    links.addAll(processUrl(webUrlPad))
                    if (links.isEmpty()) links.addAll(processUrl(webUrlNoPad))
                }

            } catch (e: Exception) {
                AppLogger.log("SoloLatino", "Error Serie: ${e.message}")
            }
            links
        }
    }

    // --- PROCESADOR DE URLS ---
    private fun processUrl(url: String): List<SourceLink> {
        val foundLinks = mutableListOf<SourceLink>()
        try {
            val currentHeaders = headers.toMutableMap()
            if (url.contains("embed69") || url.contains("xupalace")) {
                currentHeaders["Referer"] = "https://xupalace.org/"
            }

            val request = Request.Builder().url(url).headers(currentHeaders.toHeaders()).build()
            val response = client.newCall(request).execute()
            
            if (response.code == 404) return emptyList()
            val html = response.body?.string() ?: ""

            // 1. JSON Embed69 (dataLink = [...])
            if (html.contains("dataLink")) {
                foundLinks.addAll(scrapeEmbed69Json(html))
            }

            // 2. Iframes / Embed.php
            val iframeMatcher = Pattern.compile("src\\s*=\\s*[\"']([^\"']*embed\\.php\\?id=\\d+)[^\"']*[\"']").matcher(html)
            while (iframeMatcher.find()) {
                var iframeUrl = iframeMatcher.group(1)
                if (iframeUrl != null) {
                    if (iframeUrl.startsWith("//")) iframeUrl = "https:$iframeUrl"
                    if (!iframeUrl.startsWith("http")) iframeUrl = "https://sololatino.net$iframeUrl"
                    foundLinks.addAll(scrapeDoubleHop(iframeUrl))
                }
            }

        } catch (e: Exception) { }
        return foundLinks
    }

    private fun scrapeEmbed69Json(html: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val matcher = Pattern.compile("let\\s+dataLink\\s*=\\s*(\\[.*?\\]);", Pattern.DOTALL).matcher(html)
            if (matcher.find()) {
                val jsonArray = JSONArray(matcher.group(1))
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val lang = item.optString("video_language", "UNK")
                    val prettyLang = mapLanguage(lang)
                    
                    val embeds = item.optJSONArray("sortedEmbeds")
                    if (embeds != null) {
                        for (j in 0 until embeds.length()) {
                            val embed = embeds.getJSONObject(j)
                            val server = embed.optString("servername")
                            val token = embed.optString("link")
                            
                            if (server != "download" && token.isNotEmpty()) {
                                val decodedUrl = decodeJwt(token)
                                if (decodedUrl != null) {
                                    addLinkOptimized(links, server, decodedUrl, prettyLang)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return links
    }

    private fun scrapeDoubleHop(url: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val html = client.newCall(request).execute().body?.string() ?: ""
            
            val matcher = Pattern.compile("onclick=\"go_to_player\\('([^']+)'\\)\"[^>]*>.*?<span>(.*?)</span>", Pattern.DOTALL).matcher(html)
            while (matcher.find()) {
                val rawLink = matcher.group(1) ?: continue
                val serverName = matcher.group(2)?.trim() ?: "Server"
                
                // Filtramos enlaces http válidos
                if (rawLink.startsWith("http")) {
                    addLinkOptimized(links, serverName, rawLink, "🇲🇽 Latino")
                }
            }
        } catch (e: Exception) { }
        return links
    }

    // --- HELPERS ---

    private fun addLinkOptimized(links: MutableList<SourceLink>, serverName: String, url: String, lang: String) {
        val isDirect = url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains("stream.php")
        val prettyServer = serverName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // AQUÍ ESTABA EL PROBLEMA POTENCIAL: Ajustamos al nuevo constructor de SourceLink
        links.add(SourceLink(
            name = "SL - $prettyServer",
            url = url,
            quality = "HD",
            language = lang,
            isDirect = isDirect,       // Usamos el nombre del parámetro explícito
            requiresWebView = !isDirect, // Usamos el nombre del parámetro explícito
            provider = name            // Añadimos el nombre del proveedor
        ))
    }

    private fun mapLanguage(code: String): String {
        return when (code.uppercase()) {
            "LAT", "LATINO" -> "🇲🇽 Latino"
            "SUB", "SUBTITULADO" -> "🇺🇸 Subtitulado"
            "ESP", "CASTELLANO" -> "🇪🇸 Castellano"
            else -> "❓ $code"
        }
    }

    private fun decodeJwt(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            var payload = parts[1]
            while (payload.length % 4 != 0) payload += "="
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val json = JSONObject(String(decodedBytes))
            json.optString("link")
        } catch (e: Exception) { null }
    }

    private fun cleanTitle(title: String): String {
        var clean = title.lowercase()
        clean = clean.replace(Regex("\\s\\(\\d{4}\\).*$"), "")
        val nfd = Normalizer.normalize(clean, Normalizer.Form.NFD)
        clean = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("")
        clean = clean.replace(Regex("[^a-z0-9\\s-]"), "")
        clean = clean.replace(Regex("[\\s]+"), "-")
        return clean.trim('-')
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
}
