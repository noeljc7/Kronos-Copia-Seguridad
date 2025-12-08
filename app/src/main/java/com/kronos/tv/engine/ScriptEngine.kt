package com.kronos.tv.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.coroutines.resume

/**
 * MOTOR KRONOS V3 (WebView Headless)
 * Reemplaza Rhino para soportar JS moderno (ES6, Fetch, Cookies)
 */
object ScriptEngine {

    private var webView: WebView? = null
    private var isInitialized = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private const val BASE_JS_ENV = "var KronosEngine = KronosEngine || { providers: {} };"

    // IMPORTANTE: Llamar a esto en MainActivity.onCreate()
    fun initialize(context: Context) {
        if (isInitialized) return

        uiHandler.post {
            try {
                webView = WebView(context)
                setupWebView(webView!!)
                isInitialized = true
                Log.d("KRONOS_ENGINE", "Motor WebView Iniciado 🚀")
            } catch (e: Exception) {
                Log.e("KRONOS_ENGINE", "Error iniciando WebView: ${e.message}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // Vital para cookies y localStorage
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        
        // Inyectamos el puente para logs y http legacy
        wv.addJavascriptInterface(JsBridge(), "bridge")
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                Log.d("KRONOS_JS_CONSOLE", "${cm?.message()} -- line ${cm?.lineNumber()}")
                return true
            }
        }
        
        wv.webViewClient = object : WebViewClient() {}
        
        // Inyectar ambiente base
        wv.evaluateJavascript(BASE_JS_ENV, null)
    }

    // Carga scripts desde una URL (Manifest)
    suspend fun loadManifest(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e("KRONOS_ENGINE", "¡Error! Debes llamar a ScriptEngine.initialize(context) primero.")
            return@withContext
        }

        try {
            Log.d("KRONOS_ENGINE", "Descargando manifest...")
            val manifestContent = URL(manifestUrl).readText()
            val jsonObject = JSONObject(manifestContent)
            val scriptsArray = jsonObject.getJSONArray("scripts")

            for (i in 0 until scriptsArray.length()) {
                val scriptUrl = scriptsArray.getString(i)
                loadScriptFromUrl(scriptUrl)
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error cargando manifest: ${e.message}")
        }
    }
    
    // Carga un script individual
    suspend fun loadScriptFromUrl(url: String) {
        try {
            val scriptContent = URL(url).readText()
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript(scriptContent, null)
                Log.d("KRONOS_ENGINE", "Script inyectado: $url")
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error inyectando script $url: ${e.message}")
        }
    }
    
    // Carga script directo (String) - Útil para el de Cinemitas local
    suspend fun loadScript(jsCode: String) = withContext(Dispatchers.Main) {
        webView?.evaluateJavascript(jsCode, null)
    }

    // Ejecuta cualquier código JS y devuelve el resultado como String
    suspend fun evaluateJs(script: String): String = suspendCancellableCoroutine { cont ->
        uiHandler.post {
            val wv = webView
            if (wv != null) {
                wv.evaluateJavascript(script) { result ->
                    // WebView devuelve el string entre comillas (ej: "null" o "\"valor\"")
                    // Quitamos las comillas extra si es necesario, o devolvemos raw
                    val cleanResult = if (result != null && result != "null") {
                        if (result.startsWith("\"") && result.endsWith("\"")) {
                            result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                        } else result
                    } else "null"
                    
                    cont.resume(cleanResult)
                }
            } else {
                cont.resume("null")
            }
        }
    }

    // --- MÉTODOS LEGACY (Para compatibilidad con tus Providers viejos) ---

    suspend fun extractLink(providerName: String, videoUrl: String): String? {
        val jsCode = "KronosEngine.providers['$providerName'].extract('$videoUrl')"
        val result = evaluateJs(jsCode)
        return if (result != "null" && result.isNotBlank()) result else null
    }

    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? {
        // Convertimos args de Kotlin a String JS (ej: ['arg1', 'arg2'])
        val argsString = args.joinToString(",") { 
            if (it is String) "'$it'" else it.toString() 
        }
        
        val jsCode = "JSON.stringify(KronosEngine.providers['$providerName'].$functionName($argsString))"
        return evaluateJs(jsCode)
    }
}
