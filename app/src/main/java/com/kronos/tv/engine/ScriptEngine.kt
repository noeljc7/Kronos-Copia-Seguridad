package com.kronos.tv.engine

import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScriptEngine {
    private var scope: Scriptable? = null
    
    // CAMBIA ESTO POR TU URL RAW DE GITHUB
    private const val REMOTE_JS_URL = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/sources.js"

    // 1. INICIALIZAR: Descarga el cerebro
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                // Descargamos el código
                val jsCode = URL(REMOTE_JS_URL).readText()
                
                val rhino = RhinoContext.enter()
                rhino.optimizationLevel = -1 // Modo intérprete para Android
                scope = rhino.initStandardObjects()
                
                // Inyectamos el puente
                val wrappedBridge = rhino.wrap(scope, JsBridge(), JsBridge::class.java)
                scope?.put("Android", scope, wrappedBridge)
                
                // Cargamos el código
                rhino.evaluateString(scope, jsCode, "KronosRemote", 1, null)
                println("✅ KRONOS ENGINE: Cerebro remoto cargado")
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ KRONOS ENGINE ERROR: ${e.message}")
            } finally {
                RhinoContext.exit()
            }
        }
    }

    // 2. EJECUTAR FUNCION DE UN PROVIDER
    fun extractLink(providerName: String, html: String): String? {
        if (scope == null) return null
        val rhino = RhinoContext.enter()
        rhino.optimizationLevel = -1
        
        return try {
            // Código puente dinámico
            val runnerCode = "KronosEngine.providers['$providerName'].extract(htmlVar);"
            
            // Inyectamos la variable HTML
            scope?.put("htmlVar", scope, html)
            
            val result = rhino.evaluateString(scope, runnerCode, "Extractor", 1, null)
            org.mozilla.javascript.Context.toString(result)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            RhinoContext.exit()
        }
    }
}
