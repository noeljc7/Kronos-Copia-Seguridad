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
import java.util.Locale

// IMPORTS NECESARIOS
import com.kronos.tv.providers.KronosProvider
import com.kronos.tv.providers.SourceLink
// import com.kronos.tv.providers.LinkResolver // Descomenta si LinkResolver está en otro paquete

class SoloLatinoBleachProvider : KronosProvider {
    override val name = "SoloLatino (Bleach)"
    private val client = OkHttpClient()
    
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    )

    // --- MAPA DE TEMPORADAS ---
    private val seasonMap = mapOf(
        1 to (1 to 20),
        2 to (21 to 41),
        3 to (42 to 63),
        4 to (64 to 91),
        5 to (92 to 109),
        6 to (110 to 131),
        7 to (132 to 151),
        8 to (152 to 167),
        9 to (168 to 189),
        10 to (190 to 205),
        11 to (206 to 212),
        12 to (213 to 229),
        13 to (230 to 265),
        14 to (266 to 316),
        15 to (317 to 342),
        16 to (343 to 366)
    )

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return emptyList() // Solo series
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                // 1. Verificar si es Bleach
                val response = RetrofitInstance.api.getTvExternalIds(tmdbId, RetrofitInstance.getApiKey())
                val imdbId = response.imdb_id

                if (imdbId != "tt0434665") return@withContext emptyList()

                AppLogger.log("Bleach", "Detectado Bleach: S${season}E${episode}")

                // 2. ESTRATEGIA PARA "THOUSAND-YEAR BLOOD WAR" (Temporadas 17+)
                if (season >= 17) {
                    val newImdbId = "tt14986406" 
                    var targetSeason = "1"
                    var targetEp = episode
                    
                    if (season == 18) { targetSeason = "2" }
                    if (season == 19) { targetSeason = "3" }
                    
                    val epPad = targetEp.toString().padStart(2, '0')
                    val url = "https://embed69.org/f/$newImdbId-${targetSeason}x$epPad"
                    AppLogger.log("Bleach", "Buscando TYBW: $url")
                    links.addAll(processUrl(url))
                } 
                // 3. ESTRATEGIA CLÁSICA (Temporadas 1-16)
                else {
                    val range = seasonMap[season]
                    if (range != null) {
                        val epPad = episode.toString().padStart(2, '0')
                        val urlSuffix = "${season}x$epPad"
                        
                        // NOTA: Si Xupalace usa IDs raros, aquí iría esa lógica. 
                        // Por ahora usamos el estándar IMDB-SxE
                        val url = "https://xupalace.org/video/$imdbId-$urlSuffix"
                        AppLogger.log("Bleach", "Buscando Clásico: $url")
                        links.addAll(processUrl(url))
                    }
                }

            } catch (e: Exception) {
                AppLogger.log("Bleach", "Error: ${e.message}")
            }
            links
        }
    }

    // --- REUTILIZAMOS LA LÓGICA DE EXTRACCIÓN ---
    private fun processUrl(url: String): List<SourceLink> {
        val foundLinks = mutableListOf<SourceLink>()
        try {
            val currentHeaders = headers.toMutableMap()
            if (url.contains("embed69") || url.contains("xupalace")) {
                currentHeaders["Referer"] = "https://xupalace.org/"
            }

            val request = Request.Builder().url(url).headers(currentHeaders.toHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // 1. Embed69 JSON
            if (html.contains("dataLink")) {
                foundLinks.addAll(scrapeEmbed69Json(html))
            }
            
            // 2. VAST
            if (html.contains("go_to_playerVast")) {
                foundLinks.addAll(scrapeVast(html))
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
                    val embeds = item.optJSONArray("sortedEmbeds")
                    
                    if (embeds != null) {
                        for (j in 0 until embeds.length()) {
                            val embed = embeds.getJSONObject(j)
                            val server = embed.optString("servername")
                            val token = embed.optString("link")
                            
                            if (server != "download" && token.isNotEmpty()) {
                                val decodedUrl = decodeJwt(token)
                                if (decodedUrl != null) {
                                    addLinkOptimized(links, server, decodedUrl, lang)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return links
    }

    private fun scrapeVast(html: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val matcher = Pattern.compile("onclick=\"go_to_playerVast\\('([^']+)'[^>]*>.*?<span>(.*?)</span>", Pattern.DOTALL).matcher(html)
            while (matcher.find()) {
                val rawLink = matcher.group(1) ?: continue
                val serverName = matcher.group(2)?.trim() ?: "Server"
                addLinkOptimized(links, serverName, rawLink, "LAT/SUB")
            }
        } catch (e: Exception) { }
        return links
    }

    private fun addLinkOptimized(links: MutableList<SourceLink>, serverName: String, url: String, lang: String) {
        // Intenta usar LinkResolver si existe, sino usa la URL original
        // Si LinkResolver da error de compilación, elimina esa línea y usa solo 'url'
        var finalUrl = url
        try {
             val resolved = LinkResolver.resolve(url)
             if (resolved != null) finalUrl = resolved
        } catch (e: Exception) { /* LinkResolver no existe o falló */ }

        val isDirect = finalUrl.endsWith(".mp4") || finalUrl.endsWith(".m3u8")

        links.add(SourceLink(
            name = "Bleach - ${serverName.capitalize()}",
            url = finalUrl,
            quality = "HD",
            language = lang.uppercase(),
            isDirect = isDirect,
            requiresWebView = !isDirect,
            provider = name // <-- Importante
        ))
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

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
    
    private fun String.capitalize(): String = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
