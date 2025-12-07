package com.kronos.tv.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Modelo de datos para el Historial
data class WatchProgress(
    val tmdbId: Int,
    val isMovie: Boolean,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val season: Int,
    val episode: Int,
    val position: Long,
    val duration: Long,
    val lastWatched: Long
) {
    fun getProgressPercent(): Float {
        if (duration <= 0) return 0f
        return position.toFloat() / duration.toFloat()
    }
}

// Modelo simple para Favoritos
data class FavoriteItem(
    val tmdbId: Int,
    val isMovie: Boolean,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String
)

class HistoryManager(context: Context) : SQLiteOpenHelper(context, "KronosHistory.db", null, 2) { // <--- CAMBIÉ LA VERSIÓN A 2

    override fun onCreate(db: SQLiteDatabase) {
        // TABLA HISTORIAL
        db.execSQL(
            "CREATE TABLE history (" +
                    "id TEXT PRIMARY KEY, " + // tmdbId_S_E
                    "tmdbId INTEGER, isMovie INTEGER, " +
                    "title TEXT, posterUrl TEXT, backdropUrl TEXT, " +
                    "season INTEGER, episode INTEGER, " +
                    "position INTEGER, duration INTEGER, " +
                    "lastWatched INTEGER)"
        )

        // TABLA FAVORITOS (NUEVA)
        db.execSQL(
            "CREATE TABLE favorites (" +
                    "tmdbId INTEGER PRIMARY KEY, " + 
                    "isMovie INTEGER, " +
                    "title TEXT, posterUrl TEXT, backdropUrl TEXT, " +
                    "addedAt INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Si actualizamos, borramos todo y creamos de nuevo (simple para desarrollo)
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        onCreate(db)
    }

    // --- FUNCIONES DE HISTORIAL ---

    fun saveProgress(item: WatchProgress) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", "${item.tmdbId}_${item.season}_${item.episode}")
            put("tmdbId", item.tmdbId)
            put("isMovie", if (item.isMovie) 1 else 0)
            put("title", item.title)
            put("posterUrl", item.posterUrl)
            put("backdropUrl", item.backdropUrl)
            put("season", item.season)
            put("episode", item.episode)
            put("position", item.position)
            put("duration", item.duration)
            put("lastWatched", System.currentTimeMillis())
        }
        db.replace("history", null, values)
        db.close()
    }

    fun getProgress(tmdbId: Int, season: Int, episode: Int): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT position FROM history WHERE id = ?", 
            arrayOf("${tmdbId}_${season}_${episode}")
        )
        var pos = 0L
        if (cursor.moveToFirst()) {
            pos = cursor.getLong(0)
        }
        cursor.close()
        db.close()
        return pos
    }

    fun getContinueWatching(): List<WatchProgress> {
        val list = mutableListOf<WatchProgress>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM history ORDER BY lastWatched DESC LIMIT 20", null)
        
        if (cursor.moveToFirst()) {
            do {
                list.add(WatchProgress(
                    tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow("tmdbId")),
                    isMovie = cursor.getInt(cursor.getColumnIndexOrThrow("isMovie")) == 1,
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    posterUrl = cursor.getString(cursor.getColumnIndexOrThrow("posterUrl")),
                    backdropUrl = cursor.getString(cursor.getColumnIndexOrThrow("backdropUrl")),
                    season = cursor.getInt(cursor.getColumnIndexOrThrow("season")),
                    episode = cursor.getInt(cursor.getColumnIndexOrThrow("episode")),
                    position = cursor.getLong(cursor.getColumnIndexOrThrow("position")),
                    duration = cursor.getLong(cursor.getColumnIndexOrThrow("duration")),
                    lastWatched = cursor.getLong(cursor.getColumnIndexOrThrow("lastWatched"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // Verificar si un episodio está visto (>90%)
    fun isEpisodeWatched(tmdbId: Int, season: Int, episode: Int): Boolean {
        val db = readableDatabase
        var isWatched = false
        try {
            val cursor = db.rawQuery(
                "SELECT position, duration FROM history WHERE id = ?", 
                arrayOf("${tmdbId}_${season}_${episode}")
            )
            if (cursor.moveToFirst()) {
                val pos = cursor.getLong(0)
                val dur = cursor.getLong(1)
                if (dur > 0 && (pos.toFloat() / dur.toFloat()) > 0.90f) {
                    isWatched = true
                }
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.close()
        }
        return isWatched
    }

    // --- NUEVAS FUNCIONES DE FAVORITOS ---

    // Agregar o Quitar de favoritos (Toggle)
    fun toggleFavorite(item: FavoriteItem): Boolean {
        val db = writableDatabase
        val exists = isFavorite(item.tmdbId)
        
        if (exists) {
            // Si ya existe, lo borramos
            db.delete("favorites", "tmdbId = ?", arrayOf(item.tmdbId.toString()))
            db.close()
            return false // Ya no es favorito
        } else {
            // Si no existe, lo agregamos
            val values = ContentValues().apply {
                put("tmdbId", item.tmdbId)
                put("isMovie", if (item.isMovie) 1 else 0)
                put("title", item.title)
                put("posterUrl", item.posterUrl)
                put("backdropUrl", item.backdropUrl)
                put("addedAt", System.currentTimeMillis())
            }
            db.insert("favorites", null, values)
            db.close()
            return true // Ahora es favorito
        }
    }

    // Verificar si es favorito (para pintar el corazón)
    fun isFavorite(tmdbId: Int): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM favorites WHERE tmdbId = ?", arrayOf(tmdbId.toString()))
        val exists = cursor.moveToFirst()
        cursor.close()
        // No cerramos db aquí si vamos a usarla inmediatamente después, pero por seguridad en hilos:
        // db.close() 
        return exists
    }

    // Obtener lista completa de favoritos
    fun getFavorites(): List<FavoriteItem> {
        val list = mutableListOf<FavoriteItem>()
        val db = readableDatabase
        // Ordenamos por fecha de agregado (los nuevos primero)
        val cursor = db.rawQuery("SELECT * FROM favorites ORDER BY addedAt DESC", null)
        
        if (cursor.moveToFirst()) {
            do {
                list.add(FavoriteItem(
                    tmdbId = cursor.getInt(cursor.getColumnIndexOrThrow("tmdbId")),
                    isMovie = cursor.getInt(cursor.getColumnIndexOrThrow("isMovie")) == 1,
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    posterUrl = cursor.getString(cursor.getColumnIndexOrThrow("posterUrl")),
                    backdropUrl = cursor.getString(cursor.getColumnIndexOrThrow("backdropUrl"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}