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
import java.net.URL
import java.util.UUID
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
                Log.d("KRONOS", "Motor WebView Iniciado (Modo Nube) 🚀")
            } catch (e: Exception) {
                Log.e("KRONOS", "Error crítico iniciando WebView: ${e.message}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        wv.addJavascriptInterface(bridge, "bridge")
        
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                if (cm?.message()?.contains("blocked") == true) return true
                // Log.d("JS_CONSOLE", "${cm?.message()}") // Descomentar si quieres ver todo el spam de JS
                return true
            }
        }
        
        wv.webViewClient = object : WebViewClient() {
             override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.proceed() // Ignorar errores SSL de sitios piratas
            }
        }

        wv.evaluateJavascript(BASE_JS_ENV, null)
    }

    suspend fun loadScriptFromUrl(url: String) {
        try {
            val content = withContext(Dispatchers.IO) { URL(url).readText() }
            withContext(Dispatchers.Main) { 
                webView?.evaluateJavascript(content, null) 
            }
        } catch (e: Exception) {
            Log.e("KRONOS", "Fallo al descargar script: $url")
        }
    }

    // FUNCIÓN CORE: Ahora maneja concurrencia con UUIDs
    suspend fun queryProvider(providerName: String, functionName: String, args: Array<Any>): String? = suspendCancellableCoroutine { cont ->
        
        val requestId = UUID.randomUUID().toString()
        
        val argsString = args.joinToString(",") { 
            if (it is String) "'${it.replace("'", "\\'")}'" else it.toString() 
        }

        // Registramos la oreja para escuchar la respuesta específica de este ID
        bridge.addCallback(requestId) { result ->
            if (cont.isActive) cont.resume(result)
        }

        val jsCode = """
            (async function() {
                try {
                    if (!KronosEngine.providers['$providerName']) {
                        throw new Error("Provider not found");
                    }
                    const result = await KronosEngine.providers['$providerName'].$functionName($argsString);
                    bridge.onResult('$requestId', JSON.stringify(result));
                } catch(e) {
                    bridge.log("JS ERROR ($providerName): " + e.message);
                    bridge.onResult('$requestId', "[]");
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
