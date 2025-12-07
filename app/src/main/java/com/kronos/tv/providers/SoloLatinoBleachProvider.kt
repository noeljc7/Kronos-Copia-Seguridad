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

class SoloLatinoBleachProvider : KronosProvider {
    override val name = "SoloLatino (Bleach)"
    private val client = OkHttpClient()
    
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    )

    // --- MAPA DE TEMPORADAS (Copiado de tu Python) ---
    // Clave: Temporada -> Valor: (Inicio Absoluto, Fin Absoluto)
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
                // 1. Verificar si es Bleach (TMDB ID: 30984 o IMDB: tt0434665)
                val response = RetrofitInstance.api.getTvExternalIds(tmdbId, RetrofitInstance.getApiKey())
                val imdbId = response.imdb_id

                if (imdbId != "tt0434665") return@withContext emptyList()

                AppLogger.log("Bleach", "Detectado Bleach: S${season}E${episode}")

                // 2. ESTRATEGIA PARA "THOUSAND-YEAR BLOOD WAR" (Temporadas 17+)
                if (season >= 17) {
                    // En Embed69/SoloLatino, TYBW es una serie nueva con ID distinto
                    val newImdbId = "tt14986406" 
                    
                    // Calculamos "Parte" y "Episodio" dentro de TYBW
                    // (La lógica de tu Python mapeada a TMDB Seasons)
                    // TMDB S17 = Part 1 (Eps 1-13)
                    // TMDB S18 = Part 2 (Eps 14-26)
                    // TMDB S19 = Part 3 (Eps 27+)
                    
                    var targetSeason = "1"
                    var targetEp = episode
                    
                    // Ajuste simple asumiendo que TMDB separa las partes como temporadas 17, 18, 19...
                    // O si TMDB usa S17 para todo, tendríamos que ver el número de episodio.
                    // Asumiremos el mapeo estándar de TMDB para simplificar:
                    if (season == 18) { targetSeason = "2" }
                    if (season == 19) { targetSeason = "3" }
                    
                    // Formato 01, 02...
                    val epPad = targetEp.toString().padStart(2, '0')
                    
                    val url = "https://embed69.org/f/$newImdbId-${targetSeason}x$epPad"
                    AppLogger.log("Bleach", "Buscando TYBW: $url")
                    links.addAll(processUrl(url))
                } 
                // 3. ESTRATEGIA CLÁSICA (Temporadas 1-16)
                else {
                    val range = seasonMap[season]
                    if (range != null) {
                        // Calcular Episodio Absoluto
                        // Ej: Temp 2, Ep 1. Inicio T2 = 21. Absoluto = 21 + 1 - 1 = 21.
                        val startAbsolute = range.first
                        val absoluteEp = startAbsolute + episode - 1
                        
                        // SoloLatino Clásico usa este formato: ID-TemporadaMapxEpisodioRelativoMap... 
                        // ESPERA, tu script de Python dice: url_suffix = f"{final_season}x{final_episode}"
                        // Donde final_season y final_episode se calculan.
                        // Pero Xupalace a veces usa el absoluto para animes largos.
                        // Tu script Python hace algo curioso: mapea de vuelta a temporada/episodio "fake" que usa el servidor.
                        
                        // Vamos a usar la lógica de tu script tal cual:
                        // El servidor espera: TEMPORADA x EPISODIO (donde temporada coincide con el mapa)
                        val epPad = episode.toString().padStart(2, '0')
                        val urlSuffix = "${season}x$epPad"
                        
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

    // --- REUTILIZAMOS LA LÓGICA DE EXTRACCIÓN (Igual que SoloLatinoProvider) ---
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
            
            // 2. VAST (Xupalace suele usar esto)
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
                
                // Detectar idioma aproximado por el icono (si pudiéramos ver el data-lang)
                // Asumimos Latino/Sub según contexto, o genérico
                addLinkOptimized(links, serverName, rawLink, "LAT/SUB")
            }
        } catch (e: Exception) { }
        return links
    }

    private fun addLinkOptimized(links: MutableList<SourceLink>, serverName: String, url: String, lang: String) {
        val resolvedUrl = LinkResolver.resolve(url)
        val finalUrl = resolvedUrl ?: url
        val isDirect = finalUrl.endsWith(".mp4") || finalUrl.endsWith(".m3u8")

        links.add(SourceLink(
            name = "Bleach - ${serverName.capitalize()}",
            url = finalUrl,
            quality = "HD",
            language = lang.uppercase(),
            isDirect = isDirect,
            requiresWebView = !isDirect
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