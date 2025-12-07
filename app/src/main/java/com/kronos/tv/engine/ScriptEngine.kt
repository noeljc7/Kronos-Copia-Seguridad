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
 * EL MOTOR (ScriptEngine)
 * Singleton (Objeto único) que maneja el entorno JavaScript.
 * Se encarga de:
 * 1. Inicializar Rhino.
 * 2. Descargar los scripts de GitHub.
 * 3. Ejecutar la extracción de enlaces.
 */
object ScriptEngine {

    private var sharedScope: Scriptable? = null
    private var isInitialized = false

    // Definimos la estructura base JS para evitar errores si los scripts cargan desordenados
    // Básicamente crea un objeto global vacío: var KronosEngine = { providers: {} };
    private const val BASE_JS_ENV = "var KronosEngine = KronosEngine || { providers: {} };"

    /**
     * Inicializa el motor. Debe llamarse al abrir la app (Splash o Main).
     * @param manifestUrl La URL "raw" de tu archivo manifest.json en GitHub.
     */
    suspend fun initialize(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d("KRONOS_ENGINE", "El motor ya estaba inicializado.")
            return@withContext
        }

        try {
            Log.d("KRONOS_ENGINE", "Iniciando Motor Kronos...")

            // 1. Configurar Rhino (El intérprete JS)
            val cx = Context.enter()
            // CRUCIAL: optimizationLevel = -1 fuerza el modo intérprete.
            // Android no permite compilación dinámica de bytecode fácilmente.
            cx.optimizationLevel = -1 
            sharedScope = cx.initStandardObjects()

            // 2. Inyectar el Puente (JsBridge)
            // Esto permite que 'bridge.fetchHtml()' exista dentro del JS
            val bridge = JsBridge()
            val wrappedBridge = Context.javaToJS(bridge, sharedScope)
            ScriptableObject.putProperty(sharedScope, "bridge", wrappedBridge)

            // 3. Crear el entorno base JS
            cx.evaluateString(sharedScope, BASE_JS_ENV, "CoreEnv", 1, null)

            // 4. Descargar el Manifest (La lista de scripts)
            Log.d("KRONOS_ENGINE", "Descargando manifest de: $manifestUrl")
            val manifestContent = URL(manifestUrl).readText()
            val jsonObject = JSONObject(manifestContent)
            val scriptsArray = jsonObject.getJSONArray("scripts")

            // 5. Descargar y cargar cada script individualmente
            for (i in 0 until scriptsArray.length()) {
                val scriptUrl = scriptsArray.getString(i)
                try {
                    Log.d("KRONOS_ENGINE", "Cargando script: $scriptUrl")
                    val scriptContent = URL(scriptUrl).readText()
                    
                    // Nombre del archivo para logs de error (ej: voe.js)
                    val scriptName = scriptUrl.substringAfterLast("/")
                    
                    // Evaluamos el script (lo metemos en memoria)
                    cx.evaluateString(sharedScope, scriptContent, scriptName, 1, null)
                } catch (e: Exception) {
                    Log.e("KRONOS_ENGINE", "Error cargando script $scriptUrl: ${e.message}")
                }
            }

            isInitialized = true
            Log.d("KRONOS_ENGINE", "Motor inicializado correctamente.")

        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error fatal inicializando el motor: ${e.message}")
            e.printStackTrace()
        } finally {
            // Siempre debemos salir del contexto de Rhino
            Context.exit()
        }
    }

    /**
     * Intenta extraer un enlace de video usando un proveedor (ej: "Voe").
     * @param providerName El nombre exacto del proveedor (ej: "Voe", "Streamwish").
     * @param videoUrl La URL de la web donde está el video.
     * @return El enlace directo (.m3u8/.mp4) o null si falla.
     */
    suspend fun extractLink(providerName: String, videoUrl: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || sharedScope == null) {
            Log.e("KRONOS_ENGINE", "Intento de extracción sin inicializar motor.")
            return@withContext null
        }

        var result: String? = null
        val cx = Context.enter()
        cx.optimizationLevel = -1

        try {
            // Buscamos: KronosEngine.providers['NombreProvider']
            val kronosObj = sharedScope?.get("KronosEngine", sharedScope) as? Scriptable
            val providersObj = kronosObj?.get("providers", sharedScope) as? Scriptable
            val providerObj = providersObj?.get(providerName, sharedScope) as? Scriptable

            if (providerObj != null) {
                // Buscamos la función: .extract(url)
                val extractFunc = providerObj.get("extract", sharedScope)
                
                if (extractFunc is Function) {
                    val args = arrayOf<Any>(videoUrl)
                    // EJECUTAMOS LA FUNCIÓN JS
                    val jsResult = extractFunc.call(cx, sharedScope, providerObj, args)
                    
                    // Convertimos el resultado de JS a String de Kotlin
                    if (jsResult != null && jsResult != org.mozilla.javascript.Undefined.instance) {
                        result = Context.toString(jsResult)
                        if (result.isNullOrBlank()) result = null
                    }
                } else {
                    Log.e("KRONOS_ENGINE", "El proveedor $providerName no tiene función 'extract'.")
                }
            } else {
                Log.w("KRONOS_ENGINE", "Proveedor $providerName no encontrado en scripts cargados.")
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error ejecutando script de $providerName: ${e.message}")
            result = null
        } finally {
            Context.exit()
        }

        return@withContext result
    }
}

