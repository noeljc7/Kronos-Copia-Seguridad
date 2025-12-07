package com.kronos.tv.providers

import com.kronos.tv.engine.ScriptEngine
import org.json.JSONArray
import org.json.JSONObject

class JsContentProvider(override val name: String) : KronosProvider {

    override suspend fun getMovieLinks(tmdbId: Int, title: String, originalTitle: String, year: Int): List<SourceLink> {
        // Empaquetamos los datos de TMDB en un JSON para el script JS
        val metadata = JSONObject()
        metadata.put("tmdbId", tmdbId)
        metadata.put("title", title)
        metadata.put("originalTitle", originalTitle)
        metadata.put("year", year)
        metadata.put("type", "movie")

        // Llamamos al script remoto
        val jsonResult = ScriptEngine.queryProvider(name, "getLinks", arrayOf(metadata.toString()))
        return parseJsonToLinks(jsonResult)
    }

    override suspend fun getEpisodeLinks(tmdbId: Int, showTitle: String, season: Int, episode: Int): List<SourceLink> {
        val metadata = JSONObject()
        metadata.put("tmdbId", tmdbId)
        metadata.put("title", showTitle)
        metadata.put("season", season)
        metadata.put("episode", episode)
        metadata.put("type", "tv")

        val jsonResult = ScriptEngine.queryProvider(name, "getLinks", arrayOf(metadata.toString()))
        return parseJsonToLinks(jsonResult)
    }

    private fun parseJsonToLinks(jsonStr: String?): List<SourceLink> {
        val list = mutableListOf<SourceLink>()
        if (jsonStr.isNullOrBlank()) return list

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SourceLink(
                    name = obj.optString("name", name),
                    url = obj.getString("url"),
                    quality = obj.optString("quality", "HD"),
                    language = obj.optString("language", "Unknown"),
                    isDirect = obj.optBoolean("isDirect", false),
                    requiresWebView = !obj.optBoolean("isDirect", false)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
