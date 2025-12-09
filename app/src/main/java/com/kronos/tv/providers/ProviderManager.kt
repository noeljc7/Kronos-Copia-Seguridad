package com.kronos.tv.providers

import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import com.kronos.tv.models.SourceLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class JsContentProvider(
    override val name: String,
    private val displayName: String
) : Provider() {

    override val language = "Multi"

    // --- IMPLEMENTACIÓN ANTIGUA (Compatibilidad con tu App) ---

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        // 1. Buscamos la película en el sitio JS usando el título
        val results = search(title)
        
        // 2. Filtramos para encontrar la correcta (coincidencia de año o título exacto)
        // (Por simplicidad tomamos la primera que parezca película)
        val bestMatch = results.firstOrNull { it.type == "movie" } ?: return emptyList()

        // 3. Extraemos el link de video
        val videoUrl = loadStream(bestMatch.id, "movie")
        
        return if (videoUrl != null) {
            listOf(SourceLink(
                name = "$displayName (JS)",
                url = videoUrl,
                quality = "720p",
                language = "Latino",
                isDirect = true, // Es m3u8 directo
                requiresWebView = false
            ))
        } else {
            emptyList()
        }
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        // 1. Buscamos la serie
        val results = search(showTitle)
        val series = results.firstOrNull { it.type == "tv" } ?: return emptyList()

        // 2. Cargamos episodios (necesitamos un método nuevo en JS para esto, o asumir URL)
        // Para simplificar, intentaremos resolver asumiendo que el script JS maneja la lógica
        // Si tu script JS tiene "getEpisodes", deberíamos llamarlo aquí.
        // Por ahora, retornamos vacío hasta que implementemos la lógica completa de series.
        return emptyList()
    }

    // --- MÉTODOS INTERNOS NUEVOS (Llamadas al JS) ---

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }
    
    // Añadimos este para cumplir con la clase abstracta
    override suspend fun loadEpisodes(url: String): List<Episode> = emptyList() 

    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        return@withContext if (res != "null" && res != null) res.replace("\"", "") else null
    }

    // --- PARSERS ---
    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SearchResult(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    img = obj.optString("img"),
                    id = obj.optString("id"),
                    type = obj.optString("type")
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }
}
