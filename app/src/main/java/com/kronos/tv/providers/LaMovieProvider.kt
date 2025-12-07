package com.kronos.tv.providers

import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.net.URI

class LaMovieProvider : KronosProvider {
    override val name = "LaMovie"
    private val client = OkHttpClient()
    
    // Headers copiados de tu la_movie.py
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Referer" to "https://la.movie/",
        "X-Requested-With" to "XMLHttpRequest"
    )

    private val API_URL = "https://la.movie/wp-api/v1"

    // --- PELÍCULAS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                AppLogger.log("LaMovie", "Buscando: $title")
                // 1. Buscar ID del post
                val postId = getPostId(title, year, "movies")
                
                if (postId != null) {
                    links.addAll(getEmbeds(postId))
                } else {
                    // Intento con título original si falla
                    if (title != originalTitle) {
                        val postIdOrg = getPostId(originalTitle, year, "movies")
                        if (postIdOrg != null) links.addAll(getEmbeds(postIdOrg))
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("LaMovie", "Error: ${e.message}")
            }
            links
        }
    }

    // --- SERIES ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                AppLogger.log("LaMovie", "Buscando serie: $showTitle")
                // 1. Buscar ID de la serie
                val serieId = getPostId(showTitle, 0, "tvshows")
                
                if (serieId != null) {
                    // 2. Buscar el episodio específico en la lista
                    val episodeId = getEpisodeId(serieId, season, episode)
                    
                    if (episodeId != null) {
                        links.addAll(getEmbeds(episodeId))
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("LaMovie", "Error Serie: ${e.message}")
            }
            links
        }
    }

    // --- LÓGICA PRIVADA (API) ---

    private fun getPostId(title: String, year: Int, type: String): String? {
        try {
            // Limpieza básica del título (quitar año si viene pegado)
            val cleanTitle = title.replace(Regex("\\(\\d{4}\\)"), "").trim()
            val url = "$API_URL/search?postType=any&q=${URLEncoder.encode(cleanTitle, "UTF-8")}&postsPerPage=5"
            
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            
            val posts = json.optJSONObject("data")?.optJSONArray("posts") ?: return null
            
            for (i in 0 until posts.length()) {
                val item = posts.getJSONObject(i)
                val itemType = item.optString("type")
                
                // Filtro por tipo exacto (movies o tvshows)
                if (itemType == type) {
                    return item.optString("_id")
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun getEpisodeId(serieId: String, season: Int, episode: Int): String? {
        try {
            // LaMovie tiene un endpoint para listar episodios de una serie
            val url = "$API_URL/single/episodes/list?_id=$serieId&season=$season&page=1&postsPerPage=50"
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            
            val posts = json.optJSONObject("data")?.optJSONArray("posts") ?: return null
            
            for (i in 0 until posts.length()) {
                val item = posts.getJSONObject(i)
                // Comparamos números
                if (item.optInt("episode_number") == episode) {
                    return item.optString("_id")
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun getEmbeds(postId: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val url = "$API_URL/player?postId=$postId&demo=0"
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            
            val embeds = json.optJSONObject("data")?.optJSONArray("embeds") ?: return emptyList()
            
            for (i in 0 until embeds.length()) {
                val item = embeds.getJSONObject(i)
                val urlLink = item.optString("url")
                val rawServer = item.optString("server", "Online")
                val rawQuality = item.optString("quality", "HD")
                val rawLang = item.optString("lang", "")

                if (urlLink.isNotEmpty()) {
                    // 1. OBTENER NOMBRE REAL (Lógica portada de Python)
                    val realServerName = getServerName(urlLink, rawServer)
                    
                    // 2. MAPEAR IDIOMA
                    val prettyLang = mapLanguage(rawLang)
                    
                    // 3. MAPEAR CALIDAD (Tu lógica de downgrade para ser honestos)
                    val prettyQuality = if (rawQuality.contains("4k", true)) "4K" else "HD"

                    // 4. CLASIFICAR TIPO DE ENLACE
                    // Si es MP4/M3U8 es directo. Si es Voe/Filemoon/etc, es Web (Sniffer).
                    val isDirect = urlLink.endsWith(".mp4") || urlLink.endsWith(".m3u8")

                    links.add(SourceLink(
                        name = "LM - $realServerName",
                        url = urlLink,
                        quality = prettyQuality,
                        language = prettyLang,
                        isDirect = isDirect,
                        requiresWebView = !isDirect // Si no es directo, el PlayerScreen usará el Sniffer
                    ))
                }
            }
        } catch (e: Exception) { }
        return links
    }

    // --- LA LÓGICA DE TU PYTHON TRADUCIDA (Identificador de Servidor) ---
    private fun getServerName(url: String, rawServer: String): String {
        val u = url.lowercase()
        
        // Detección por URL (Prioritaria)
        if (u.contains("vimeos")) return "Vimeos"
        if (u.contains("goodstream")) return "Goodstream"
        if (u.contains("voe")) return "Voe"
        if (u.contains("filemoon") || u.contains("moon")) return "Filemoon"
        if (u.contains("wish")) return "StreamWish"
        if (u.contains("dood")) return "Doodstream"
        if (u.contains("ok.ru")) return "Ok.ru"
        
        // Si el nombre es genérico ("Online", "Server"), intentamos sacar el dominio
        if (rawServer.equals("Online", true) || rawServer.equals("Server", true)) {
            try {
                val host = URI(url).host ?: ""
                // Ejemplo: "www.fembed.com" -> "Fembed"
                val domain = host.replace("www.", "").split(".")[0]
                if (domain.isNotEmpty()) {
                    return domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            } catch (e: Exception) { }
        }
        
        return rawServer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun mapLanguage(code: String): String {
        val l = code.lowercase()
        return when {
            "latino" in l -> "🇲🇽 Latino"
            "castellano" in l || "esp" in l -> "🇪🇸 Castellano"
            "sub" in l -> "🇺🇸 Subtitulado"
            "ing" in l || "eng" in l -> "🇬🇧 Inglés"
            else -> "❓ $code"
        }
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
}