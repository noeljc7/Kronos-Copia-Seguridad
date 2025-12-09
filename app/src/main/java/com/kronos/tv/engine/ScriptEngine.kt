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

object ScriptEngine {

    private var webView: WebView? = null
    private var isInitialized = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val bridge = JsBridge() // Instancia única del puente

    private const val BASE_JS_ENV = "var KronosEngine = KronosEngine || { providers: {} };"

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
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        
        // Usamos nuestra instancia controlada del bridge
        wv.addJavascriptInterface(bridge, "bridge")
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                Log.d("KRONOS_JS_CONSOLE", "${cm?.message()} -- line ${cm?.lineNumber()}")
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {}
        wv.evaluateJavascript(BASE_JS_ENV, null)
    }

    suspend fun loadManifest(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext
        try {
            val manifestContent = URL(manifestUrl).readText()
            val jsonObject = JSONObject(manifestContent)
            val scriptsArray = jsonObject.getJSONArray("scripts")
            for (i in 0 until scriptsArray.length()) {
                loadScriptFromUrl(scriptsArray.getString(i))
            }
        } catch (e: Exception) {
            Log.e("KRONOS_ENGINE", "Error cargando manifest: ${e.message}")
        }
    }
    
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
    
    suspend fun loadScript(jsCode: String) = withContext(Dispatchers.Main) {
        webView?.evaluateJavascript(jsCode, null)
    }

    // --- LA SOLUCIÓN CALLBACK (A PRUEBA DE BALAS) ---
    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? = suspendCancellableCoroutine { cont ->
        val argsString = args.joinToString(",") { 
            if (it is String) "'${it.replace("'", "\\'")}'" else it.toString() 
        }

        // Configurar el callback ANTES de ejecutar
        // Esto captura la respuesta cuando el JS llame a bridge.onResult()
        bridge.onResultCallback = { result ->
            // Limpieza básica por seguridad
            bridge.onResultCallback = null // Limpiar para evitar fugas
            if (cont.isActive) {
                cont.resume(result)
            }
        }

        // JS que NO retorna nada, sino que llama al puente al finalizar
        val jsCode = """
            (async function() {
                try {
                    const result = await KronosEngine.providers['$providerName'].$functionName($argsString);
                    // AQUÍ ESTÁ LA MAGIA: Llamada explícita de vuelta a Kotlin
                    bridge.onResult(JSON.stringify(result));
                } catch(e) {
                    bridge.log("JS ERROR: " + e.message);
                    bridge.onResult("[]");
                }
            })()
        """.trimIndent()

        uiHandler.post {
            val wv = webView
            if (wv != null) {
                wv.evaluateJavascript(jsCode, null)
            } else {
                if (cont.isActive) cont.resume("[]")
            }
        }
    }
}
