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

    // Descarga un script y lo guarda en /data/.../files/plugins/nombre.py
    suspend fun downloadPlugin(context: Context, pluginName: String, url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.log("REPO", "⬇️ Iniciando descarga de: $pluginName")
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    AppLogger.log("REPO", "❌ Error HTTP: ${response.code}")
                    return@withContext false
                }

                val scriptContent = response.body?.string()
                if (scriptContent.isNullOrEmpty()) {
                    AppLogger.log("REPO", "❌ El archivo descargado está vacío")
                    return@withContext false
                }

                // Crear carpeta plugins si no existe
                val pluginsDir = File(context.filesDir, "plugins")
                if (!pluginsDir.exists()) pluginsDir.mkdirs()

                // Guardar el archivo .py
                val file = File(pluginsDir, "$pluginName.py")
                file.writeText(scriptContent)

                AppLogger.log("REPO", "✅ Plugin guardado en: ${file.absolutePath}")
                return@withContext true

            } catch (e: Exception) {
                AppLogger.log("REPO", "🔥 Excepción al descargar: ${e.message}")
                e.printStackTrace()
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
