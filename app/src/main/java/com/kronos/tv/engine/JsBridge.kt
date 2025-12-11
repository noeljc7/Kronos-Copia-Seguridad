package com.kronos.tv.engine

import android.webkit.JavascriptInterface
import com.kronos.tv.ScreenLogger

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JsBridge {

    // --- 1. GESTIÓN DE MEMORIA Y SESIÓN ---
    
    // Mapa para guardar cookies en memoria RAM (Vital para SoloLatino/Cloudflare)
    private val memoryCookieJar = object : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Guardamos las cookies nuevas combinándolas con las viejas
            val existingCookies = cookieStore[url.host]?.toMutableList() ?: mutableListOf()
            cookies.forEach { newCookie ->
                existingCookies.removeIf { it.name == newCookie.name }
                existingCookies.add(newCookie)
            }
            cookieStore[url.host] = existingCookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Entregamos las cookies guardadas para este dominio
            return cookieStore[url.host] ?: emptyList()
        }
    }

    // --- 2. EL MOTOR DE RED (Navegador Simulado) ---
    
    private val client = OkHttpClient.Builder()
        .cookieJar(memoryCookieJar) // Activamos la memoria de cookies
        .connectTimeout(30, TimeUnit.SECONDS) // Tiempo de espera generoso
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // Ignorar errores SSL (Certificados inválidos en webs de streaming)
        .hostnameVerifier { _, _ -> true }
        .build()

    // LA MÁSCARA: Estos headers hacen creer al servidor que somos Chrome en Windows
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    // --- 3. INTERFACES PARA JAVASCRIPT ---

    @JavascriptInterface
    fun log(message: String) {
        ScreenLogger.log("JS_LOG", message)
    }

    /**
     * Función principal para navegar.
     * El JS la usa para bajar el HTML de SoloLatino o ZonaAps.
     */
    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            ScreenLogger.log("HTTP", "🚀 GET: $url")

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"123\", \"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"123\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")

            // Truco: Si vamos a SoloLatino, decimos que venimos de Google para ganar confianza
            if (url.contains("sololatino")) {
                requestBuilder.header("Referer", "https://www.google.com/")
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                // Si nos bloquean (403), devolvemos vacío para no romper la app
                if (!response.isSuccessful && response.code == 403) {
                    ScreenLogger.log("HTTP_WARN", "⚠️ Bloqueo 403 en $url (Intentando continuar...)")
                    return@use ""
                }
                return@use response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo GET: ${e.message}")
            ""
        }
    }

    /**
     * Función para enviar datos (POST).
     * El JS la usa para enviar formularios o IDs a APIs.
     */
    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            ScreenLogger.log("HTTP", "📤 POST: $url")

            // Usamos el tipo de contenido estándar para formularios web
            val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
            val body = data.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest") // Vital para APIs AJAX
                .header("Origin", getOrigin(url))
                .header("Referer", url) // Referer suele ser la misma URL en llamadas API
                .build()

            client.newCall(request).execute().use { response ->
                return@use response.body?.string() ?: "{}"
            }
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo POST: ${e.message}")
            "{}"
        }
    }

    /**
     * LA NUEVA ARMA SECRETA: EXTRACCIÓN HÍBRIDA
     * El JS llama a esta función cuando encuentra un iframe de Embed69 o XuPalace.
     * Kotlin recibe la URL, la procesa con NativeExtractor y devuelve el JSON listo.
     */
    @JavascriptInterface
    fun resolveNative(url: String): String {
        return try {
            ScreenLogger.log("PUENTE", "🔄 JS solicita ayuda nativa para: $url")
            
            // 1. Llamamos al Soldado (NativeExtractor)
            val links = NativeExtractor.extract(url)
            
            ScreenLogger.log("PUENTE", "✅ Kotlin extrajo ${links.size} servidores.")

            // 2. Convertimos la lista de objetos Kotlin a JSON String para JS
            val jsonArray = JSONArray()
            links.forEach { link ->
                val jsonObj = JSONObject()
                jsonObj.put("server", link.name)
                jsonObj.put("url", link.url)
                jsonObj.put("quality", link.quality)
                jsonObj.put("lang", link.language)
                jsonObj.put("requiresWebView", link.requiresWebView)
                jsonArray.put(jsonObj)
            }
            jsonArray.toString()

        } catch (e: Exception) {
            ScreenLogger.log("PUENTE_ERROR", "Fallo en resolveNative: ${e.message}")
            "[]"
        }
    }

    // Callbacks para sistemas legacy (si los usas)
    private val callbacks = ConcurrentHashMap<String, (String) -> Unit>()
    fun addCallback(id: String, callback: (String) -> Unit) { callbacks[id] = callback }
    @JavascriptInterface
    fun onResult(requestId: String, result: String) { callbacks.remove(requestId)?.invoke(result) }

    // Utilidad para obtener el dominio base
    private fun getOrigin(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) { "" }
    }
}
