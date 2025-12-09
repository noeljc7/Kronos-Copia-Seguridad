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
        Log.d("KRONOS", "🎬 Iniciando Operación Búsqueda: '$title' (Original: '$originalTitle') Año: $year")
        
        // --- FASE 1: Búsqueda en Español (Latino) ---
        // Ejemplo: Busca "Plan Familiar"
        var results = search(title)
        
        // --- FASE 2: Búsqueda en Inglés (Fallback) ---
        // Si la lista está vacía Y el título original es diferente
        // Ejemplo: Si "Plan Familiar" dio 0 resultados, busca "The Family Plan"
        if (results.isEmpty() && title != originalTitle) {
            Log.w("KRONOS", "⚠️ Sin resultados en español. Activando Protocolo Inglés: '$originalTitle'")
            results = search(originalTitle)
        }

        // --- FASE 3: Búsqueda "Cortada" (Último recurso) ---
        // Para cosas como "Misión Imposible: Sentencia Mortal..." -> Busca solo "Misión Imposible"
        if (results.isEmpty() && title.contains(":")) {
            val shortTitle = title.substringBefore(":").trim()
            if (shortTitle.length > 3) { 
                Log.w("KRONOS", "⚠️ Reintentando con título corto: '$shortTitle'")
                results = search(shortTitle)
            }
        }

        if (results.isEmpty()) {
            Log.e("KRONOS", "❌ Rendición: No se encontró la película.")
            return emptyList()
        }

        // ... (código anterior de búsqueda) ...

        Log.d("KRONOS", "✅ Candidatos encontrados: ${results.size}")

        // --- SELECCIÓN DEL MEJOR CANDIDATO ---
        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)
        
        val bestMatch = results.filter { it.type == "movie" }.minByOrNull { candidate ->
            val currentTitle = normalize(candidate.title ?: "")
            val candidateYear = candidate.year?.toIntOrNull() ?: 0
            
            // 1. Nombre
            var nameScore = 1000
            if (currentTitle == targetEs || currentTitle == targetEn) nameScore = 0
            else if (currentTitle.contains(targetEs) || targetEs.contains(currentTitle)) nameScore = 10
            else if (currentTitle.contains(targetEn) || targetEn.contains(currentTitle)) nameScore = 10
            
            // 2. Año
            var yearScore = 0
            if (year > 0 && candidateYear > 0) {
                val diff = abs(year - candidateYear)
                yearScore = when (diff) {
                    0 -> 0 
                    1 -> 5 
                    else -> 500 // Penalización enorme
                }
            }
            nameScore + yearScore
        }

        // --- FILTRO DE SEGURIDAD (ANTI-PREDATOR 1987) ---
        if (bestMatch == null) return emptyList()

        // Calcular la diferencia de año del ganador
        val winnerYear = bestMatch.year?.toIntOrNull() ?: 0
        if (year > 0 && winnerYear > 0) {
            val diff = abs(year - winnerYear)
            // Si la diferencia es mayor a 2 años, ES OTRA PELÍCULA.
            if (diff > 2) {
                Log.w("KRONOS", "⛔ BLOQUEADO: Se encontró '${bestMatch.title}' ($winnerYear) pero buscabas ($year). Diferencia: $diff años.")
                return emptyList() // Retornamos vacío, no reproducimos la equivocada.
            }
        }
        // ------------------------------------------------

        Log.d("KRONOS", "🎯 Ganador Elegido: '${bestMatch.title}' (${bestMatch.year})")

        // ... (resto del código loadStream) ...

        // --- EXTRACCIÓN DE SERVIDORES ---
        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        val links = mutableListOf<SourceLink>()
        
        try {
            val array = JSONArray(jsonServers)
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                
                // Filtro extra de seguridad anti-descargas
                val sName = s.optString("server", "Server")
                if (sName.lowercase().contains("download")) continue

                links.add(SourceLink(
                    name = sName,
                    url = s.optString("url"),
                    quality = "HD",
                    language = s.optString("lang", "Latino"),
                    provider = displayName,
                    isDirect = true
                ))
            }
        } catch (e: Exception) { Log.e("KRONOS", "Error servers: $e") }

        return links
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        return emptyList()
    }

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
                    year = obj.optString("year") // ¡Este campo es vital para el filtro de año!
                ))
            }
        } catch (e: Exception) { }
        return list
    }
}
