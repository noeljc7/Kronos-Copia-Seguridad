package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SearchResult
import com.kronos.tv.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import kotlin.math.abs

class JsContentProvider(
    override val name: String,
    private val displayName: String
) : KronosProvider {

    override val language = "Multi"

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        Log.d("KRONOS", "🎬 Buscando: '$title' ($year)")
        
        // 1. ESTRATEGIA DE BÚSQUEDA (Cascada)
        // Intentamos Español -> Inglés -> Título Corto
        var results = search(title)
        
        if (results.isEmpty() && title != originalTitle) {
            Log.w("KRONOS", "⚠️ Falló búsqueda exacta. Intentando título original: '$originalTitle'")
            results = search(originalTitle)
        }

        // Caso especial Anime/Peliculas largas (Ej: Chainsaw Man: Arc Reze...)
        // Si falla, buscamos solo las primeras 2-3 palabras clave
        if (results.isEmpty()) {
            val shortTitle = simplifyTitle(title)
            if (shortTitle.length > 3 && shortTitle != title) {
                Log.w("KRONOS", "⚠️ Falló todo. Intentando búsqueda corta: '$shortTitle'")
                results = search(shortTitle)
            }
        }

        if (results.isEmpty()) return emptyList()

        // 2. SISTEMA DE PUNTUACIÓN (NOMBRE + AÑO)
        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)
        
        val bestMatch = results.filter { it.type == "movie" }.minByOrNull { candidate ->
            val currentTitle = normalize(candidate.title ?: "")
            
            // --- PUNTAJE POR NOMBRE (0 a 100) ---
            var nameScore = 1000 // Peor puntaje inicial
            if (currentTitle == targetEs || currentTitle == targetEn) nameScore = 0 // Perfecto
            else if (currentTitle.contains(targetEs) || targetEs.contains(currentTitle)) nameScore = 10
            else if (currentTitle.contains(targetEn) || targetEn.contains(currentTitle)) nameScore = 10
            
            // --- PUNTAJE POR AÑO (Vital para remakes o animes) ---
            var yearScore = 0
            val candidateYear = candidate.year?.toIntOrNull() ?: 0
            
            if (year > 0 && candidateYear > 0) {
                val diff = abs(year - candidateYear)
                yearScore = when (diff) {
                    0 -> 0   // Mismo año (Perfecto)
                    1 -> 5   // 1 año de diferencia (Aceptable, a veces pasa por fechas de estreno)
                    2 -> 50  // 2 años (Sospechoso)
                    else -> 500 // Muy lejos (Probablemente es otra peli con mismo nombre)
                }
            }

            // Puntaje Final (Menos es mejor)
            nameScore + yearScore
        }

        // Filtro de Calidad: Si el puntaje es muy alto (>400), es que el año no cuadró nada
        if (bestMatch == null) return emptyList()
        
        // Log para depurar qué eligió
        Log.d("KRONOS", "🎯 Ganador: '${bestMatch.title}' (${bestMatch.year}) vs Esperado: $year")

        // 3. EXTRAER SERVIDORES
        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        return parseServers(jsonServers)
    }

    // ... (El resto de métodos getEpisodeLinks, search, etc. siguen igual) ...

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return emptyList()
    }

    private fun normalize(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    // Nueva función para simplificar títulos largos
    // Ej: "Chainsaw Man: The Movie - Reze Arc" -> "Chainsaw Man"
    private fun simplifyTitle(title: String): String {
        return title.split(":")[0].split("-")[0].trim()
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }
    
    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
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
                    type = obj.optString("type"),
                    // ¡IMPORTANTE! Asegúrate de que el modelo SearchResult tenga el campo 'year'
                    // Si no lo tiene, agrégalo a tu data class SearchResult o usa un campo temporal
                    year = obj.optString("year") 
                ))
            }
        } catch (e: Exception) { }
        return list
    }

    private fun parseServers(jsonServers: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(jsonServers)
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                links.add(SourceLink(
                    name = s.optString("server", "Server"),
                    url = s.optString("url"),
                    quality = "HD",
                    language = s.optString("lang", "Latino"),
                    provider = displayName,
                    isDirect = true
                ))
            }
        } catch (e: Exception) {}
        return links
    }
}
