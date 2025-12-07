package com.kronos.tv.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.net.URL

/**
 * EL MOTOR KRONOS (ScriptEngine)
 * Versión 2.0: Soporte para Búsqueda Remota (JsContentProvider)
 */
object ScriptEngine {

    private var sharedScope: Scriptable? = null
    private var isInitialized = false

    private const val BASE_JS_ENV = "var KronosEngine = KronosEngine || { providers: {} };"

    suspend fun initialize(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            Log.d("KRONOS_ENGINE", "Iniciando Motor Kronos...")
            val cx = Context.enter()
            cx.optimizationLevel = -1 
            
            try {
                sharedScope = cx.initStandardObjects()
                
                val bridge = JsBridge()
                val wrappedBridge = Context.javaToJS(bridge, sharedScope)
                ScriptableObject.putProperty(sharedScope, "bridge", wrappedBridge)

                cx.evaluateString(sharedScope, BASE_JS_ENV, "CoreEnv", 1, null)

                Log.d("KRONOS_ENGINE", "Descargando manifest...")
                val manifestContent = URL(manifestUrl).readText()
                val jsonObject = JSONObject(manifestContent)
                val scriptsArray = jsonObject.getJSONArray("scripts")

                for (i in 0 until scriptsArray.length()) {
                    val scriptUrl = scriptsArray.getString(i)
                    try {
                        val scriptContent = URL(scriptUrl).readText()
                        val scriptName = scriptUrl.substringAfterLast("/")
                        cx.evaluateString(sharedScope, scriptContent, scriptName, 1, null)
                        Log.d("KRONOS_ENGINE", "Script cargado: $scriptName")
                    } catch (e: Exception) {
                        Log.e("KRONOS_ENGINE", "Fallo script $scriptUrl: ${e.message}")
                    }
                }
                isInitialized = true
            } finally {
                Context.exit()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun extractLink(providerName: String, videoUrl: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || sharedScope == null) return@withContext null
        var result: String? = null
        val cx = Context.enter()
        cx.optimizationLevel = -1
        try {
            val kronosObj = sharedScope?.get("KronosEngine", sharedScope) as? Scriptable
            val providersObj = kronosObj?.get("providers", sharedScope) as? Scriptable
            val providerObj = providersObj?.get(providerName, sharedScope) as? Scriptable

            if (providerObj != null) {
                val extractFunc = providerObj.get("extract", sharedScope)
                if (extractFunc is Function) {
                    val jsResult = extractFunc.call(cx, sharedScope, providerObj, arrayOf(videoUrl))
                    if (jsResult != null && jsResult != org.mozilla.javascript.Undefined.instance) {
                        result = Context.toString(jsResult)
                        if (result.isNullOrBlank()) result = null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error extract ($providerName): ${e.message}")
        } finally {
            Context.exit()
        }
        return@withContext result
    }

    /**
     * NUEVA FUNCIÓN: Consulta a un proveedor de búsqueda remoto.
     * Devuelve un JSON String con la lista de enlaces encontrados.
     */
    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || sharedScope == null) return@withContext "[]"

        var resultJson: String? = "[]"
        val cx = Context.enter()
        cx.optimizationLevel = -1

        try {
            val kronosObj = sharedScope?.get("KronosEngine", sharedScope) as? Scriptable
            val providersObj = kronosObj?.get("providers", sharedScope) as? Scriptable
            val providerObj = providersObj?.get(providerName, sharedScope) as? Scriptable

            if (providerObj != null) {
                val func = providerObj.get(functionName, sharedScope)
                if (func is Function) {
                    val jsResult = func.call(cx, sharedScope, providerObj, args)
                    
                    // Usamos JSON.stringify de JS para asegurar formato correcto
                    val jsonStringer = cx.initStandardObjects().get("JSON", sharedScope) as Scriptable
                    val stringify = jsonStringer.get("stringify", sharedScope) as Function
                    val stringified = stringify.call(cx, sharedScope, jsonStringer, arrayOf(jsResult))
                    
                    resultJson = Context.toString(stringified)
                }
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error queryProvider ($providerName): ${e.message}")
        } finally {
            Context.exit()
        }
        return@withContext resultJson
    }
}

