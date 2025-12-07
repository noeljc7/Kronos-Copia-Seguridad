package com.kronos.tv.engine

import org.jsoup.Jsoup

class JsBridge {
    // GET (Para ver la página)
    fun fetchHtml(url: String): String {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
                .html()
        } catch (e: Exception) {
            ""
        }
    }

    // POST (Nuevo! Para sacar los enlaces ocultos)
    fun post(url: String, data: String): String {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest") // Importante para que el servidor responda
                .requestBody(data)
                .ignoreContentType(true)
                .post()
            
            doc.body().text() // Devuelve el JSON limpio
        } catch (e: Exception) {
            "{}"
        }
    }

    fun log(message: String) {
        println("KRONOS_JS: $message")
    }
}
