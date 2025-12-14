package com.kronos.tv.providers

import android.content.Context
import com.kronos.tv.ui.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object PluginRepository {

    private val client = OkHttpClient()
    
    // URL RAW de GitHub donde alojarás tu script (cámbiala por la tuya real luego)
    // Por ahora usaremos un ejemplo o una variable vacía para que tú la pongas
    private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/TU_USUARIO/TU_REPO/main/scrapers/sololatino.py"

    suspend fun updatePlugin(context: Context, pluginName: String, url: String = GITHUB_RAW_URL): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.log("REPO", "⬇️ Descargando plugin: $pluginName desde $url")
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    AppLogger.log("REPO", "❌ Error descarga: ${response.code}")
                    return@withContext false
                }

                val code = response.body?.string() ?: return@withContext false
                
                // Guardar en /files/plugins/nombre.py
                val pluginsDir = File(context.filesDir, "plugins")
                if (!pluginsDir.exists()) pluginsDir.mkdirs()
                
                val scriptFile = File(pluginsDir, "$pluginName.py")
                scriptFile.writeText(code)
                
                AppLogger.log("REPO", "✅ Plugin instalado/actualizado: ${scriptFile.absolutePath}")
                return@withContext true
                
            } catch (e: Exception) {
                AppLogger.log("REPO", "🔥 Excepción actualizando: ${e.message}")
                return@withContext false
            }
        }
    }
    
    // Verifica si el plugin ya existe localmente
    fun isPluginInstalled(context: Context, pluginName: String): Boolean {
        val file = File(context.filesDir, "plugins/$pluginName.py")
        return file.exists() && file.length() > 0
    }
}
