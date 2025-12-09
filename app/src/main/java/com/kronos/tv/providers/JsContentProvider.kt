package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer

class JsContentProvider(
    override val name: String,
    private val displayName: String
) : KronosProvider {

    override val language = "Multi"

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        Log.d("KRONOS", "🎬 Buscando película: $title")
        
        // 1. Obtener resultados de búsqueda del JS
        val results = search(title)
        if (results.isEmpty()) return emptyList()

        // 2. LÓGICA DE SELECCIÓN INTELIGENTE (Tu corrección)
        // Buscamos el título que más se parezca al original
        val target = normalize(title)
        
        val bestMatch = results.filter { it.type == "movie" }.minByOrNull { candidate ->
            val current = normalize(candidate.title ?: "")
            when {
                current == target -> 0 // ¡Coincidencia Exacta! (Prioridad Máxima)
                current.contains(target) -> current.length - target.length // Contiene el título (Prioridad Media)
                else -> 1000 // No se parece (Prioridad Baja)
            }
        }

        if (bestMatch == null || normalize(bestMatch.title ?: "") != target) {
             // Opcional: Si no hay match exacto, podrías decidir no mostrar nada o mostrar el más cercano.
             // Por ahora dejamos el más cercano pero logeamos la diferencia
             Log.w("KRONOS", "⚠️ No hubo match exacto. Usando: ${bestMatch?.title}")
        } else {
             Log.d("KRONOS", "✅ Match Exacto: ${bestMatch.title}")
        }

        if (bestMatch == null) return emptyList()

        // 3. EXTRAER SERVIDORES (Soporte Multi-Opción)
        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        
        val links = mutableListOf<SourceLink>()
        try {
            // El JS ahora nos devuelve una lista de objetos [{"server":"Vidhide", "url":"..."}, ...]
            val array = JSONArray(jsonServers)
            
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                links.add(SourceLink(
                    name = s.optString("server", "Server $i"),
                    url = s.optString("url"),
                    quality = "720p", 
                    language = s.optString("lang", "Latino"),
                    provider = displayName,
                    isDirect = true // El JS ya nos da el link del video final
                ))
            }
            Log.d("KRONOS", "🔗 Enlaces extraídos: ${links.size}")
        } catch (e: Exception) {
            Log.e("KRONOS", "Error parseando servidores: ${e.message}")
            // Fallback por si el JS devolvió un string simple (versión vieja)
            if (jsonServers.startsWith("http")) {
                links.add(SourceLink("Opcion 1", jsonServers, "720p", "Latino", displayName, true))
            }
        }

        return links
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        // ... (Lógica de series pendiente, retorna vacío por seguridad)
        return emptyList()
    }

    // --- HERRAMIENTAS ---

    // Normaliza el texto para comparar (quita acentos, mayúsculas y símbolos)
    // Ejemplo: "Batman: El Caballero" -> "batmanelcaballero"
    private fun normalize(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }
    
    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        // Limpiamos comillas extras si es necesario
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

    override suspend fun loadEpisodes(url: String): List<Episode> = emptyList() 

    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val cleanJson = if (json.trim().startsWith("{")) "[]" else json
            val array = JSONArray(cleanJson)
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
        } catch (e: Exception) { Log.e("KRONOS", "JSON Error: $json") }
        return list
    }
}
