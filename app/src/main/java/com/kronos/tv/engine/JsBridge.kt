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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JsBridge {

    // Mapa para gestionar las respuestas asíncronas de JS -> Kotlin
    private val callbacks = ConcurrentHashMap<String, (String) -> Unit>()

    // --- EL SECRETO: GESTIÓN DE COOKIES EN MEMORIA ---
    // Esto permite que si la web nos da una "llave" (Cookie) de acceso,
    // la guardemos y la usemos en la siguiente petición. Jsoup no hacía esto.
    private val memoryCookieJar = object : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Guardamos las cookies nuevas que nos envíe el servidor
            val existingCookies = cookieStore[url.host]?.toMutableList() ?: mutableListOf()
            cookies.forEach { newCookie ->
                existingCookies.removeIf { it.name == newCookie.name }
                existingCookies.add(newCookie)
            }
            cookieStore[url.host] = existingCookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Entregamos las cookies que tenemos guardadas para este sitio
            return cookieStore[url.host] ?: emptyList()
        }
    }

    // --- EL CLIENTE "CHROME DE WINDOWS" ---
    private val client = OkHttpClient.Builder()
        .cookieJar(memoryCookieJar) // Activamos memoria de cookies
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // Ignorar verificación SSL para webs piratas (evita errores de certificados)
        .hostnameVerifier { _, _ -> true }
        .build()

    // Este User-Agent es la clave. Le decimos al servidor que somos una PC, no un Android.
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    fun addCallback(id: String, callback: (String) -> Unit) {
        callbacks[id] = callback
    }

    @JavascriptInterface
    fun onResult(requestId: String, result: String) {
        callbacks.remove(requestId)?.invoke(result)
    }

    @JavascriptInterface
    fun fetchHtml(url: String): String {
        return try {
            ScreenLogger.log("HTTP", "🚀 GET: $url")

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                // Headers modernos que Chrome envía por defecto (Camuflaje total)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")

            // Si vamos a SoloLatino, mentimos diciendo que venimos de Google
            if (url.contains("sololatino")) {
                requestBuilder.header("Referer", "https://www.google.com/")
            }

            val request = requestBuilder.build()

            // Ejecutamos la petición
            client.newCall(request).execute().use { response ->
                // Si nos bloquean (403), devolvemos vacío para no crashear, pero lo avisamos
                if (!response.isSuccessful && response.code == 403) {
                    ScreenLogger.log("HTTP_WARN", "⚠️ Bloqueo 403 en $url (Cookies actualizadas, reintentar podría funcionar)")
                    // A veces el cuerpo del 403 contiene info útil o captchas, pero por ahora devolvemos vacío
                    return@use ""
                }
                return@use response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo red: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun post(url: String, data: String): String {
        return try {
            ScreenLogger.log("HTTP", "📤 POST: $url")

            val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
            val body = data.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", getOrigin(url))
                .header("Referer", url)
                .build()

            client.newCall(request).execute().use { response ->
                return@use response.body?.string() ?: "{}"
            }
        } catch (e: Exception) {
            ScreenLogger.log("HTTP_ERROR", "Fallo POST: ${e.message}")
            "{}"
        }
    }

    @JavascriptInterface
    fun log(message: String) {
        ScreenLogger.log("JS_LOG", message)
    }

    private fun getOrigin(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) { "" }
    }
}
