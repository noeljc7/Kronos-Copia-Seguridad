package com.kronos.tv.providers

import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// IMPORTS CLAVE QUE FALTABAN O PODRÍAN CAUSAR CONFUSIÓN
import com.kronos.tv.providers.KronosProvider
import com.kronos.tv.providers.SourceLink

class SoloLatinoOnePieceProvider : KronosProvider {
    override val name = "SoloLatino (One Piece)"
    private val client = OkHttpClient()
    
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "https://sololatino.net/",
        "Origin" to "https://embed69.org",
        "X-Requested-With" to "XMLHttpRequest"
    )

    // MAPA DE EPISODIOS
    private val seasonStartMap = mapOf(
        1 to 1, 2 to 62, 3 to 78, 4 to 93, 5 to 131, 6 to 144, 7 to 196, 8 to 207, 
        9 to 229, 10 to 264, 11 to 385, 12 to 408, 13 to 422, 14 to 459, 15 to 517, 
        16 to 575, 17 to 629, 18 to 747, 19 to 783, 20 to 892, 21 to 1089
    )

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        return emptyList() // Solo para series
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return withContext(Dispatchers.IO) {
            val links = mutableListOf<SourceLink>()
            try {
                // 1. Verificar si es One Piece (IMDB: tt0388629)
                val response = RetrofitInstance.api.getTvExternalIds(tmdbId, RetrofitInstance.getApiKey())
                val imdbId = response.imdb_id

                if (imdbId != "tt0388629") return@withContext emptyList()

                AppLogger.log("OnePiece", "Detectado One Piece S${season}E${episode}")

                // 2. Calcular Episodio Absoluto
                val absoluteEpisode = getAbsoluteEpisode(season, episode)
                if (absoluteEpisode == null) {
                    AppLogger.log("OnePiece", "No se pudo calcular el episodio absoluto.")
                    return@withContext emptyList()
                }

                // 3. Construir URL
                val targetUrl = "https://embed69.org/f/$imdbId-1x$absoluteEpisode"
                AppLogger.log("OnePiece", "Objetivo: $targetUrl")

                // 4. Extraer
                links.addAll(extractFromEmbed(targetUrl))

            } catch (e: Exception) {
                AppLogger.log("OnePiece", "Error: ${e.message}")
            }
            links
        }
    }

    private fun getAbsoluteEpisode(season: Int, episode: Int): String? {
        if (seasonStartMap.containsKey(season)) {
            val start = seasonStartMap[season]!!
            val abs = start + (episode - 1)
            return abs.toString()
        }
        if (season == 1) return episode.toString()
        return null
    }

    private fun extractFromEmbed(url: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            // A) Obtener HTML
            val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()

            // B) Buscar dataLink JSON
            val matcher = java.util.regex.Pattern.compile("let\\s+dataLink\\s*=\\s*(\\[.*?\\]);", java.util.regex.Pattern.DOTALL).matcher(html)
            if (!matcher.find()) return emptyList()

            val jsonArray = JSONArray(matcher.group(1))
            val encryptedTokens = mutableListOf<String>()
            val metadataMap = mutableMapOf<Int, Map<String, String>>()
            var tokenIndex = 0

            // C) Recolectar tokens encriptados
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val lang = item.optString("video_language", "UNK")
                val embeds = item.optJSONArray("sortedEmbeds") ?: continue

                for (j in 0 until embeds.length()) {
                    val embed = embeds.getJSONObject(j)
                    val link = embed.optString("link")
                    val server = embed.optString("servername", "Server")

                    if (link.isNotEmpty() && server != "download") {
                        encryptedTokens.add(link)
                        metadataMap[tokenIndex] = mapOf("lang" to lang, "server" to server)
                        tokenIndex++
                    }
                }
            }

            if (encryptedTokens.isEmpty()) return emptyList()

            // D) Llamar a la API de desencriptado (POST)
            val decryptUrl = "https://embed69.org/api/decrypt"
            val jsonBody = JSONObject().put("links", JSONArray(encryptedTokens))
            
            val postRequest = Request.Builder()
                .url(decryptUrl)
                .headers(headers.toHeaders())
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val apiResponse = client.newCall(postRequest).execute()
            val apiJson = JSONObject(apiResponse.body?.string() ?: "{}")

            if (apiJson.optBoolean("success")) {
                val decodedLinks = apiJson.optJSONArray("links") ?: return emptyList()
                
                for (i in 0 until decodedLinks.length()) {
                    val item = decodedLinks.getJSONObject(i)
                    val idx = item.optInt("index")
                    val realUrl = item.optString("link").replace("`", "").trim()
                    
                    if (realUrl.startsWith("http") && metadataMap.containsKey(idx)) {
                        val info = metadataMap[idx]!!
                        val lang = mapLanguage(info["lang"] ?: "UNK")
                        val serverName = info["server"]?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Server"
                        
                        val isDirect = realUrl.endsWith(".mp4") || realUrl.endsWith(".m3u8")

                        // CORRECCIÓN CLAVE AQUÍ: Usamos el constructor completo de SourceLink
                        links.add(SourceLink(
                            name = "OP - $serverName",
                            url = realUrl,
                            quality = "HD",
                            language = lang,
                            isDirect = isDirect,
                            requiresWebView = !isDirect,
                            provider = name // Añadimos el nombre del proveedor
                        ))
                    }
                }
            }

        } catch (e: Exception) {
            AppLogger.log("OnePiece", "Error extracción: ${e.message}")
        }
        return links
    }

    private fun mapLanguage(code: String): String {
        val l = code.lowercase()
        return when {
            "latino" in l || "lat" in l -> "🇲🇽 Latino"
            "castellano" in l || "esp" in l -> "🇪🇸 Castellano"
            "sub" in l -> "🇺🇸 Subtitulado"
            "japanese" in l -> "🇯🇵 Japonés"
            else -> "❓ $code"
        }
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        for ((k, v) in this) builder.add(k, v)
        return builder.build()
    }
}
