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
    private val bridge = JsBridge() 

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
        // IMPORTANTE: Habilitar acceso total para evitar bloqueos CORS en iframes piratas
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
        
        wv.addJavascriptInterface(bridge, "bridge")
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                if (cm?.message()?.contains("blocked by CORS") == true) return true // Ignorar spam
                Log.d("KRONOS_JS_CONSOLE", "${cm?.message()}")
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.proceed() // Ignorar errores SSL en sitios piratas
            }
        }
        wv.evaluateJavascript(BASE_JS_ENV, null)
    }

    suspend fun loadManifest(manifestUrl: String) = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext
        try {
            val content = URL(manifestUrl).readText()
            val json = JSONObject(content)
            val scripts = json.getJSONArray("scripts")
            for (i in 0 until scripts.length()) {
                loadScriptFromUrl(scripts.getString(i))
            }
        } catch (e: Exception) {}
    }
    
    suspend fun loadScriptFromUrl(url: String) {
        try {
            val content = URL(url).readText()
            withContext(Dispatchers.Main) { webView?.evaluateJavascript(content, null) }
        } catch (e: Exception) {}
    }
    
    suspend fun loadScript(jsCode: String) = withContext(Dispatchers.Main) {
        webView?.evaluateJavascript(jsCode, null)
    }

    // --- FUNCIÓN CALLBACK FINAL ---
    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? = suspendCancellableCoroutine { cont ->
        val argsString = args.joinToString(",") { 
            if (it is String) "'${it.replace("'", "\\'")}'" else it.toString() 
        }

        bridge.onResultCallback = { result ->
            bridge.onResultCallback = null
            if (cont.isActive) cont.resume(result)
        }

        val jsCode = """
            (async function() {
                try {
                    const result = await KronosEngine.providers['$providerName'].$functionName($argsString);
                    bridge.onResult(JSON.stringify(result));
                } catch(e) {
                    bridge.log("JS ERROR: " + e.message);
                    bridge.onResult("[]");
                }
            })()
        """.trimIndent()

        uiHandler.post {
            val wv = webView
            if (wv != null) wv.evaluateJavascript(jsCode, null)
            else if (cont.isActive) cont.resume("[]")
        }
    }
}
