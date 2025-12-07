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
 * EL MOTOR (ScriptEngine) - Versión Rhino Android
 * Singleton que maneja el entorno JavaScript.
 * Actualizado para evitar crasheos por dependencias de Java faltantes (javax.lang.model).
 */
object ScriptEngine {

    private var sharedScope: Scriptable? = null
    private var isInitialized = false

    // Entorno base para prevenir errores de referencia en JS
    private const val BASE_JS_ENV = "var KronosEngine = KronosEngine || { providers: {} };"

    /**
     * Inicializa el motor, descarga scripts y prepara el entorno.
     */
    suspend fun initialize(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d("KRONOS_ENGINE", "Motor ya inicializado previamente.")
            return@withContext
        }

        try {
            Log.d("KRONOS_ENGINE", "Iniciando Motor Kronos (Versión Rhino-Android)...")

            // 1. Entramos al contexto de Rhino
            val cx = Context.enter()
            cx.optimizationLevel = -1 // Modo intérprete obligatorio en Android
            
            try {
                // 2. Inicializamos objetos estándar (Math, JSON, etc.)
                sharedScope = cx.initStandardObjects()

                // 3. Inyectar el Puente (JsBridge)
                val bridge = JsBridge()
                // Convertimos el objeto Kotlin a JS pasando el scope explícitamente
                val wrappedBridge = Context.javaToJS(bridge, sharedScope)
                ScriptableObject.putProperty(sharedScope, "bridge", wrappedBridge)

                // 4. Crear estructura base
                cx.evaluateString(sharedScope, BASE_JS_ENV, "CoreEnv", 1, null)

                // 5. Descargar Manifest
                Log.d("KRONOS_ENGINE", "Descargando manifest...")
                val manifestContent = URL(manifestUrl).readText()
                val jsonObject = JSONObject(manifestContent)
                val scriptsArray = jsonObject.getJSONArray("scripts")

                // 6. Cargar cada script
                for (i in 0 until scriptsArray.length()) {
                    val scriptUrl = scriptsArray.getString(i)
                    try {
                        Log.d("KRONOS_ENGINE", "Cargando script: $scriptUrl")
                        val scriptContent = URL(scriptUrl).readText()
                        val scriptName = scriptUrl.substringAfterLast("/")
                        
                        // Compilamos/Evaluamos el script en el scope compartido
                        cx.evaluateString(sharedScope, scriptContent, scriptName, 1, null)
                    } catch (e: Exception) {
                        Log.e("KRONOS_ENGINE", "Fallo al cargar script $scriptUrl: ${e.message}")
                    }
                }

                isInitialized = true
                Log.d("KRONOS_ENGINE", "Motor inicializado y listo.")

            } finally {
                // SIEMPRE debemos salir del contexto para liberar memoria
                Context.exit()
            }

        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error FATAL inicializando motor: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Ejecuta la extracción de enlace usando los scripts cargados.
     */
    suspend fun extractLink(providerName: String, videoUrl: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || sharedScope == null) {
            Log.e("KRONOS_ENGINE", "Motor no inicializado. Cancelando extracción.")
            return@withContext null
        }

        var result: String? = null
        
        // Entramos a un nuevo contexto para esta ejecución
        val cx = Context.enter()
        cx.optimizationLevel = -1

        try {
            // Buscamos: KronosEngine.providers['Nombre']
            val kronosObj = sharedScope?.get("KronosEngine", sharedScope) as? Scriptable
            val providersObj = kronosObj?.get("providers", sharedScope) as? Scriptable
            val providerObj = providersObj?.get(providerName, sharedScope) as? Scriptable

            if (providerObj != null) {
                val extractFunc = providerObj.get("extract", sharedScope)
                
                if (extractFunc is Function) {
                    val args = arrayOf<Any>(videoUrl)
                    
                    // Ejecutamos la función JS
                    // IMPORTANTE: Pasamos 'sharedScope' tanto como scope y como 'this' si es necesario
                    val jsResult = extractFunc.call(cx, sharedScope, providerObj, args)
                    
                    if (jsResult != null && jsResult != org.mozilla.javascript.Undefined.instance) {
                        result = Context.toString(jsResult)
                        if (result.isNullOrBlank()) result = null
                    }
                }
            } else {
                Log.w("KRONOS_ENGINE", "Proveedor '$providerName' no encontrado en el cerebro JS.")
            }

        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Excepción ejecutando script ($providerName): ${e.message}")
            result = null
        } finally {
            Context.exit()
        }

        return@withContext result
    }
}

