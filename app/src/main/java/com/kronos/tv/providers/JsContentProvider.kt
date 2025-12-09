package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class JsContentProvider(
    override val name: String,
    private val displayName: String
) : KronosProvider {

    override val language = "Multi"

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        Log.d("KRONOS", "🎬 Iniciando búsqueda de PELÍCULA: $title")
        val results = search(title)
        
        Log.d("KRONOS", "🔢 Resultados encontrados: ${results.size}")
        
        val bestMatch = results.firstOrNull { it.type == "movie" } 
        if (bestMatch == null) {
            Log.e("KRONOS", "❌ No se encontró ninguna película coincidente")
            return emptyList()
        }

        Log.d("KRONOS", "✅ Coincidencia encontrada: ${bestMatch.title} (${bestMatch.id})")
        val videoUrl = loadStream(bestMatch.id, "movie")
        
        return if (videoUrl != null) {
            Log.d("KRONOS", "🔗 Link final resuelto: $videoUrl")
            listOf(SourceLink(
                name = "$displayName (JS)",
                url = videoUrl,
                quality = "720p",
                language = "Latino",
                provider = displayName,
                isDirect = true,
                requiresWebView = false
            ))
        } else {
            Log.e("KRONOS", "❌ El JS devolvió URL nula")
            emptyList()
        }
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        Log.d("KRONOS", "📺 Iniciando búsqueda de SERIE: $showTitle $season x $episode")
        // Construimos una query inteligente: "Serie Temporada x Episodio"
        // SoloLatino suele funcionar bien buscando solo el nombre, pero probemos específico
        val query = "$showTitle $season" 
        val results = search(showTitle) // Buscamos la serie primero
        
        // Aquí falta lógica avanzada de JS para navegar episodios, 
        // por ahora retornamos vacío para no romper nada.
        return emptyList()
    }

    // --- MÉTODOS PUENTE CON LOGS ---

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        Log.d("KRONOS", "⚡ Llamando a JS search('$query')...")
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        
        // ¡AQUÍ ESTÁ LA CLAVE! Vamos a ver qué nos escupe el JS
        Log.d("KRONOS", "📦 Respuesta RAW del JS: $json") 
        
        return@withContext parseResults(json ?: "[]")
    }
    
    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        Log.d("KRONOS", "⚡ Llamando a JS resolveVideo('$id')...")
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        Log.d("KRONOS", "📦 Respuesta RAW Resolve: $res")
        return@withContext if (res != "null" && res != null) res.replace("\"", "").trim() else null
    }

    override suspend fun loadEpisodes(url: String): List<Episode> = emptyList() 

    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            // Si el JS devuelve HTML de error en vez de JSON, esto explotará y lo veremos en los logs
            val array = if (json.trim().startsWith("{")) JSONArray("[]") else JSONArray(json)
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
        } catch (e: Exception) {
            Log.e("KRONOS", "💥 Error parseando JSON: ${e.message}")
            Log.e("KRONOS", "💥 JSON Culpable: $json")
        }
        return list
    }
}
