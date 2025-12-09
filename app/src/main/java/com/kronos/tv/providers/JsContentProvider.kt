package com.kronos.tv.providers

import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// Este proveedor sirve para CUALQUIER script remoto (Cinemitas, AnimeFLV, etc.)
class JsContentProvider(
    override val name: String,     // El ID en el JS (ej: "Cinemitas")
    private val displayName: String // El nombre bonito (ej: "Cinemitas Movies")
) : Provider() {

    override val language = "Multi"

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        // Llama a: KronosEngine.providers['Cinemitas'].search('Batman')
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }

    override suspend fun loadEpisodes(url: String): List<Episode> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "getEpisodes", arrayOf(url))
        return@withContext parseEpisodes(json ?: "[]")
    }

    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        // El queryProvider devuelve un string JSON ("url"), limpiamos comillas
        return@withContext if (res != "null" && res != null) res.replace("\"", "") else null
    }

    // --- PARSERS GENÉRICOS ---
    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(SearchResult(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    posterUrl = obj.optString("img"),
                    id = obj.optString("id"),
                    type = obj.optString("type")
                ))
            }
        } catch (e: Exception) { }
        return list
    }

    private fun parseEpisodes(json: String): List<Episode> {
        val list = mutableListOf<Episode>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Episode(
                    name = obj.optString("title"),
                    url = obj.optString("url")
                ))
            }
        } catch (e: Exception) { }
        return list
    }
}
