package com.kronos.tv.providers

import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern
import java.util.Locale

// IMPORTS NECESARIOS
import com.kronos.tv.providers.KronosProvider
import com.kronos.tv.providers.SourceLink

class ZonaApsProvider : KronosProvider {
    override val name = "ZonaAps"
    private val client = OkHttpClient()
    private val baseUrl = "https://zonaaps.com"
    
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Referer" to "https://zonaaps.com/",
        "X-Requested-With" to "XMLHttpRequest"
    )

    // --- PELÍCULAS ---
    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                AppLogger.log("ZonaAps", "Buscando: $title")
                val nonce = getNonce()
                
                // Búsqueda por título exacto y original
                var contentUrl = searchContent(title, true, nonce)
                if (contentUrl == null && title != originalTitle) {
                    contentUrl = searchContent(originalTitle, true, nonce)
                }

                if (contentUrl != null) {
                    // Extraer servidores
                    val candidates = extractServers(contentUrl)
                    
                    for (candidate in candidates) {
                        // Obtener el enlace del iframe (embed)
                        val iframeUrl = getApiEmbedLink(candidate.postId, candidate.type, candidate.nume)
                        
                        if (iframeUrl != null) {
                            addLinkOptimized(links, candidate.name, iframeUrl, candidate.lang)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("ZonaAps", "Error: ${e.message}")
            }
            links
        }
    }

    // --- SERIES ---
    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                AppLogger.log("ZonaAps", "Buscando Serie: $showTitle")
                val nonce = getNonce()
                
                // 1. Buscar la serie base
                val showUrl = searchContent(showTitle, false, nonce)
                
                if (showUrl != null) {
                    // 2. Construir URL del episodio (Slug Magic)
                    // Ej: /tvshows/el-pingino/ -> /episodes/el-pingino-1x1/
                    val showSlug = showUrl.trimEnd('/').substringAfterLast('/')
                    val episodeUrl = "$baseUrl/episodes/$showSlug-$season" + "x" + episode + "/"
                    
                    val candidates = extractServers(episodeUrl)
                    if (candidates.isNotEmpty()) {
                        for (candidate in candidates) {
                            val iframeUrl = getApiEmbedLink(candidate.postId, candidate.type, candidate.nume)
                            if (iframeUrl != null) {
                                addLinkOptimized(links, candidate.name, iframeUrl, candidate.lang)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("ZonaAps", "Error Serie: ${e.message}")
            }
            links
        }
    }

    // --- HELPERS ---

    private fun addLinkOptimized(links: MutableList<SourceLink>, serverName: String, url: String, lang: String) {
        // Si es mp4/m3u8 es directo. Si no (ej: zonaaps-player.xyz/embed.php), es WEB.
        val isDirect = url.endsWith(".mp4") || url.endsWith(".m3u8")
        
        // Limpiar nombre
        val prettyServer = serverName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // AQUÍ ESTÁ LA CORRECCIÓN: Constructor Completo
        links.add(SourceLink(
            name = "ZA - $prettyServer",
            url = url,
            quality = "HD",
            language = lang,
            isDirect = isDirect,
            requiresWebView = !isDirect, // El Sniffer se encargará de extraer el video del PHP
            provider = name // Añadimos el nombre del proveedor
        ))
    }

    // --- LÓGICA DE EXTRACCIÓN ---
    
    data class ServerCandidate(val name: String, val postId: String, val type: String, val nume: String, val lang: String)

    private fun getNonce(): String {
        try {
            val request = Request.Builder().url(baseUrl).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            val matcher = Pattern.compile("\"nonce\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
            return if (matcher.find()) matcher.group(1) ?: "7f353cc3d9" else "7f353cc3d9"
        } catch (e: Exception) { return "7f353cc3d9" }
    }

    private fun searchContent(query: String, isMovie: Boolean, nonce: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchApi = "$baseUrl/wp-json/dooplay/search/?keyword=$encodedQuery&nonce=$nonce"
            val request = Request.Builder().url(searchApi).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return null

            if (jsonStr.trim() == "[]" || jsonStr.contains("\"error\"")) return null
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            
            while (keys.hasNext()) {
                val key = keys.next()
                val item = json.getJSONObject(key)
                val url = item.getString("url")
                if (isMovie && "/movies/" in url) return url
                if (!isMovie && "/tvshows/" in url) return url
            }
        } catch (e: Exception) { }
        return null
    }

    private fun extractServers(url: String): List<ServerCandidate> {
        val candidates = mutableListOf<ServerCandidate>()
        try {
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()

            // Regex flexible para encontrar <li> con data-post
            val liMatcher = Pattern.compile("<li[^>]+data-post=['\"](\\d+)['\"][^>]*>(.*?)</li>", Pattern.DOTALL).matcher(html)

            while (liMatcher.find()) {
                val tag = liMatcher.group(0) ?: ""
                val content = liMatcher.group(2) ?: ""
                
                val postId = extractAttr(tag, "data-post")
                val type = extractAttr(tag, "data-type")
                val nume = extractAttr(tag, "data-nume")

                if (postId != null && type != null && nume != null && nume != "trailer") {
                    // Detectar idioma
                    val lang = when {
                        "latino" in content.lowercase() || "mx.png" in content -> "🇲🇽 Latino"
                        "castellano" in content.lowercase() || "es.png" in content -> "🇪🇸 Castellano"
                        "sub" in content.lowercase() -> "🇺🇸 Subtitulado"
                        else -> "🇲🇽 Latino"
                    }
                    
                    // Nombre del servidor
                    var serverName = "Server"
                    val titleMatcher = Pattern.compile("<span class=['\"]title['\"]>(.*?)</span>").matcher(content)
                    if (titleMatcher.find()) serverName = titleMatcher.group(1).replace("🔒", "").trim()

                    candidates.add(ServerCandidate(serverName, postId, type, nume, lang))
                }
            }
        } catch (e: Exception) { }
        return candidates
    }

    private fun extractAttr(text: String, attr: String): String? {
        val matcher = Pattern.compile("$attr=['\"]([^'\"]+)['\"]").matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun getApiEmbedLink(postId: String, type: String, nume: String): String? {
        try {
            val apiUrl = "$baseUrl/wp-json/dooplayer/v2/$postId/$type/$nume"
            val request = Request.Builder().url(apiUrl).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return null
            val json = JSONObject(jsonStr)
            return json.optString("embed_url").ifEmpty { json.optString("u") }
        } catch (e: Exception) { return null }
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
}
