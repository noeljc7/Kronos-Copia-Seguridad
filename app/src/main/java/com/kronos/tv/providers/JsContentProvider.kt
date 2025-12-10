package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.ScreenLogger // Importante para ver logs en pantalla
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
        val bestMatch = findBestMatch(title, originalTitle, year, "movie")
        
        if (bestMatch == null) {
            ScreenLogger.log("KRONOS", "⛔ ${name}: Ningún candidato pasó el filtro de año/nombre.")
            return emptyList()
        }

        ScreenLogger.log("KRONOS", "🎯 ${name}: Ganador '${bestMatch.title}' (${bestMatch.year})")
        val jsonServers = loadStream(bestMatch.id, "movie") ?: "[]"
        return parseServers(jsonServers)
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        // En series no filtramos tan estricto por año porque a veces TMDB tiene la fecha del primer cap y la web la del último
        val bestMatch = findBestMatch(showTitle, showTitle, 0, "tv") 
        
        if (bestMatch == null) {
            ScreenLogger.log("KRONOS", "❌ ${name}: No se encontró la serie '$showTitle'")
            return emptyList()
        }

        ScreenLogger.log("KRONOS", "📺 ${name}: Serie encontrada. Buscando ${season}x${episode}...")
        val jsonServers = loadEpisodeStream(bestMatch.id, season, episode) ?: "[]"
        return parseServers(jsonServers)
    }

    private suspend fun findBestMatch(title: String, originalTitle: String, year: Int, type: String): SearchResult? {
        // 1. Obtener resultados crudos
        var results = search(title)
        if (results.isEmpty() && title != originalTitle) results = search(originalTitle)
        if (results.isEmpty() && title.contains(":")) results = search(title.substringBefore(":").trim())

        if (results.isEmpty()) return null

        val targetEs = normalize(title)
        val targetEn = normalize(originalTitle)

        // 2. FILTRADO ESTRICTO
        // Filtramos la lista para quedarnos solo con los que valen la pena
        val candidates = results.filter { it.type == type }
        
        ScreenLogger.log("KRONOS", "🧐 ${name}: Analizando ${candidates.size} candidatos para '$title' ($year)")

        val bestMatch = candidates.minByOrNull { cand ->
            val currentTitle = normalize(cand.title ?: "")
            val candYear = cand.year?.toIntOrNull() ?: 0
            
            // PUNTUACIÓN DE NOMBRE (Menos es mejor)
            var score = 1000
            if (currentTitle == targetEs || currentTitle == targetEn) score = 0
            else if (currentTitle.contains(targetEs) || targetEs.contains(currentTitle)) score = 10
            else score = 100 // No se parece mucho

            // PUNTUACIÓN DE AÑO (CRÍTICO) 🛡️
            if (year > 0) {
                if (candYear > 0) {
                    val diff = abs(year - candYear)
                    if (diff > 2) score += 5000 // Penalización MÁXIMA (Bloqueo)
                    else if (diff == 1) score += 5
                    // Si diff == 0, score no cambia (perfecto)
                } else {
                    // Si el resultado NO tiene año (0), penalizamos un poco por sospechoso,
                    // pero no lo bloqueamos totalmente si el nombre es idéntico.
                    score += 50 
                }
            }
            score
        }

        // 3. VEREDICTO FINAL
        if (bestMatch != null) {
            val candYear = bestMatch.year?.toIntOrNull() ?: 0
            
            // Si el mejor candidato tiene una puntuación altísima, es que falló en año o nombre
            // Score > 4000 significa que el año difiere en más de 2
            if (year > 0 && candYear > 0 && abs(year - candYear) > 2) {
                ScreenLogger.log("ALERTA", "⛔ ${name}: Bloqueado '${bestMatch.title}' ($candYear). Buscabas ($year).")
                return null
            }
        }

        return bestMatch
    }

    // ... (El resto de funciones: loadEpisodeStream, loadStream, search, parseServers, normalize, parseResults SIGUEN IGUAL) ...
    // Asegúrate de copiar el resto del archivo que ya tenías o usar el del mensaje anterior para estas funciones auxiliares.
    // Solo cambiamos la lógica de getMovieLinks y findBestMatch.
    
    // --- COPIA AQUÍ EL RESTO DE FUNCIONES DEL ARCHIVO ANTERIOR ---
    private suspend fun loadEpisodeStream(url: String, season: Int, episode: Int): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveEpisode", arrayOf(url, season, episode))
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

    override suspend fun loadStream(id: String, type: String): String? = withContext(Dispatchers.IO) {
        val res = ScriptEngine.queryProvider(name, "resolveVideo", arrayOf(id, type))
        return@withContext if (res != "null" && res != null) res.trim() else null
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val json = ScriptEngine.queryProvider(name, "search", arrayOf(query))
        return@withContext parseResults(json ?: "[]")
    }

    private fun parseServers(json: String): List<SourceLink> {
        val links = mutableListOf<SourceLink>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                val rawName = s.optString("server", "Server")
                val sName = rawName.replaceFirstChar { it.uppercase() } 
                val isWeb = sName.contains("Waaw") || sName.contains("Filemoon") || sName.contains("Voe") || sName.contains("Streamwish") || s.optBoolean("requiresWebView", false)
                
                links.add(SourceLink(
                    name = sName,
                    url = s.optString("url"),
                    quality = "HD",
                    language = s.optString("lang", "Latino"),
                    provider = displayName,
                    isDirect = !isWeb, 
                    requiresWebView = isWeb
                ))
            }
        } catch (e: Exception) { ScreenLogger.log("ERROR", "JS Parse: ${e.message}") }
        return links
    }

    private fun normalize(str: String): String = Normalizer.normalize(str, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    override suspend fun loadEpisodes(url: String): List<Episode> = emptyList() 

    private fun parseResults(json: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        try {
            val clean = if (json.trim().startsWith("{")) "[]" else json
            val array = JSONArray(clean)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(SearchResult(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    img = o.optString("img"),
                    id = o.optString("id"),
                    type = o.optString("type"),
                    year = o.optString("year")
                ))
            }
        } catch (e: Exception) {}
        return list
    }
}
