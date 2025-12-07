package com.kronos.tv.utils

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    private val AD_HOSTS = setOf(
        // Redes de anuncios globales
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com",
        "mc.yandex.ru", "yandex.ru", "bebi.com", "onclickads.net",
        "adsupply.com", "juicyads.com", "exoclick.com", "adnxs.com",
        "trafficjunky.net", "livejasmin.com", "ad-maven.com",
        "tsyndicate.com", "whos.amung.us", "histats.com", "acscdn.com",
        
        // Específicos de tus logs (Vidhide/Streamwish/ZonaAps)
        "mosevura.com", "harborcrestdesignstudio.com", "trafficbass.com",
        "disables-devtool.com", "static.cloudflareinsights.com",
        "jads.co", "dramiyos.com", "vectorrab.com", "meadowlarkaninearts.com"
    )

    fun isAd(url: String?): Boolean {
        if (url == null) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        
        // Bloqueo por dominio exacto o parcial
        return AD_HOSTS.any { adHost -> host.contains(adHost) } || 
               // Bloqueo por patrones sospechosos en la URL
               url.contains("/ad/") || 
               url.contains("/ads/") || 
               url.contains("popunder") || 
               url.contains("tracker")
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}