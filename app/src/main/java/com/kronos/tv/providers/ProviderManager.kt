package com.kronos.tv.providers

import android.util.Log
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.models.SourceLink // Asegúrate de tener este modelo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object ProviderManager { // <--- AHORA ES UN SINGLETON

    // Lista de proveedores (Locales + Remotos)
    // Usamos 'mutableListOf' para poder agregar los que vienen de GitHub
    val providers = mutableListOf<Provider>(
        SoloLatinoOnePieceProvider(),
        SoloLatinoBleachProvider(),
        SoloLatinoProvider(),
        LaMovieProvider(),
        ZonaApsProvider()
        // Los de Javascript se agregarán aquí dinámicamente
    )

    private var isRemoteLoaded = false

    /**
     * PASO 1: Carga proveedores dinámicos desde GitHub.
     * Esto se llama DESDE MainActivity al iniciar la app.
     */
    suspend fun loadRemoteProviders(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isRemoteLoaded) return@withContext // Si ya cargaron, no hacemos nada

        try {
            Log.d("KRONOS_MANAGER", "Descargando configuración remota...")
            val jsonStr = URL(manifestUrl).readText()
            val json = JSONObject(jsonStr)
            
            // 1. Cargar Scripts JS en el Motor (WebView)
            if (json.has("scripts")) {
                val scripts = json.getJSONArray("scripts")
                for (i in 0 until scripts.length()) {
                    ScriptEngine.loadScriptFromUrl(scripts.getString(i))
                }
            }

            // 2. Registrar los Proveedores en Kotlin
            if (json.has("providers")) {
                val remoteList = json.getJSONArray("providers")
                for (i in 0 until remoteList.length()) {
                    // El JSON ahora debería ser objetos: {"id": "Cinemitas", "name": "Cinemitas HD"}
                    val p = remoteList.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.getString("name")

                    // Evitar duplicados
                    if (providers.none { it.name == id }) {
                        providers.add(JsContentProvider(id, name))
                        Log.d("KRONOS_MANAGER", "Proveedor remoto agregado: $name")
                    }
                }
            }
            isRemoteLoaded = true
        } catch (e: Exception) {
            Log.e("KRONOS_MANAGER", "Error cargando remotos: ${e.message}")
        }
    }

    /**
     * PASO 2: Buscar enlaces.
     * Ahora es ultrarrápido porque no descarga nada, solo itera la lista.
     */
    suspend fun getLinks(
        tmdbId: Int, 
        title: String, 
        isMovie: Boolean, 
        year: Int = 0, 
        season: Int = 0, 
        episode: Int = 0
    ): List<SourceLink> = withContext(Dispatchers.IO) {
        
        val allLinks = mutableListOf<SourceLink>()
        
        Log.d("KRONOS_MANAGER", "Buscando en ${providers.size} proveedores...")

        // Ejecutar búsqueda en paralelo o secuencial (aquí secuencial por seguridad)
        for (provider in providers) {
            try {
                // Decidir si buscar peli o episodio
                if (isMovie) {
                    // Asumiendo que tu interfaz Provider tiene getMovieLinks
                    // Si tu Provider base es distinto, ajusta esta llamada
                    val links = provider.search(title) // O el método que uses para buscar
                    // Convertir SearchResult a SourceLink si es necesario
                    // ... lógica de conversión ...
                } else {
                    // Lógica para episodios
                    if (provider is JsContentProvider) {
                       // Lógica especial JS si es necesario
                    }
                }
                
                // NOTA: Para simplificar, si tus providers ya retornan SourceLink:
                // allLinks.addAll(provider.getLinks(...))
                
            } catch (e: Exception) {
                Log.e("KRONOS_MANAGER", "Error en provider ${provider.name}: ${e.message}")
            }
        }

        return@withContext allLinks.sortedBy { it.quality } // Ordenar por calidad
    }
}
