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
 * MOTOR KRONOS V4 (Async JS Support)
 */
object ScriptEngine {

    private var webView: WebView? = null
    private var isInitialized = false
    private val uiHandler = Handler(Looper.getMainLooper())

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
        
        wv.addJavascriptInterface(JsBridge(), "bridge")
        
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

    suspend fun evaluateJs(script: String): String = suspendCancellableCoroutine { cont ->
        uiHandler.post {
            val wv = webView
            if (wv != null) {
                wv.evaluateJavascript(script) { result ->
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

    // --- FUNCIÓN CRÍTICA ACTUALIZADA (SOPORTA PROMESAS) ---
    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? = suspendCancellableCoroutine { cont ->
        val argsString = args.joinToString(",") { 
            if (it is String) "'${it.replace("'", "\\'")}'" else it.toString() 
        }

        // Wrapper JS para esperar promesas (async/await)
        val jsCode = """
            (async function() {
                try {
                    const result = await KronosEngine.providers['$providerName'].$functionName($argsString);
                    return JSON.stringify(result);
                } catch(e) {
                    return "[]";
                }
            })()
        """.trimIndent()

        uiHandler.post {
            val wv = webView
            if (wv != null) {
                wv.evaluateJavascript(jsCode) { result ->
                    // Limpieza de comillas escapadas del retorno de evaluateJavascript
                    val cleanResult = if (result != null && result != "null") {
                        if (result.startsWith("\"") && result.endsWith("\"")) {
                            // Quitamos comillas iniciales y finales y desescapamos comillas internas
                            result.substring(1, result.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                        } else result
                    } else "[]"
                    
                    cont.resume(cleanResult)
                }
            } else {
                cont.resume("[]")
            }
        }
    }
}
