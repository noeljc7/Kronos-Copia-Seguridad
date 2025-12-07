@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.kronos.tv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.KeyEvent as NativeKeyEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable // <--- FALTABA ESTE IMPORT
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape // <--- FALTABA ESTE IMPORT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Text
import androidx.compose.material3.Icon // Usamos Material3 estándar para evitar error experimental TV
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.* // Importante para KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kronos.tv.utils.AdBlocker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveSniffer(
    url: String,
    onVideoFound: (String, String, String) -> Unit, // (Url, Cookies, UserAgent)
    onBack: () -> Unit
) {
    // Estado del cursor virtual
    var cursorX by remember { mutableFloatStateOf(600f) }
    var cursorY by remember { mutableFloatStateOf(400f) }
    val cursorSpeed = 25f 
    
    // Referencia al WebView para inyectar clicks
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Foco para atrapar el control remoto
    val focusRequester = remember { FocusRequester() }
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable() 
            .onKeyEvent { event: KeyEvent -> // <--- Especificamos el tipo explícitamente
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_UP -> { cursorY = (cursorY - cursorSpeed).coerceAtLeast(0f); true }
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> { cursorY = (cursorY + cursorSpeed).coerceAtMost(1080f); true } 
                        NativeKeyEvent.KEYCODE_DPAD_LEFT -> { cursorX = (cursorX - cursorSpeed).coerceAtLeast(0f); true }
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> { cursorX = (cursorX + cursorSpeed).coerceAtMost(1920f); true }
                        NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER -> {
                            webViewRef?.let { simulateClick(it, cursorX, cursorY) }
                            true
                        }
                        NativeKeyEvent.KEYCODE_BACK -> { onBack(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. EL WEBVIEW (Navegador)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = userAgent
                    settings.mediaPlaybackRequiresUserGesture = false
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            
                            // A) Bloqueo de Anuncios
                            if (AdBlocker.isAd(reqUrl)) return AdBlocker.createEmptyResponse()

                            // B) DETECCIÓN DE VIDEO
                            if (reqUrl.contains(".m3u8") || reqUrl.contains(".mp4") || reqUrl.contains("master.json")) {
                                if (!reqUrl.contains("favicon")) {
                                    val cookies = CookieManager.getInstance().getCookie(reqUrl) ?: ""
                                    view?.post { 
                                        onVideoFound(reqUrl, cookies, userAgent) 
                                    }
                                    return AdBlocker.createEmptyResponse()
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    loadUrl(url)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. EL CURSOR
        Icon(
            imageVector = Icons.Default.Mouse,
            contentDescription = "Cursor",
            tint = Color(0xFFE50914), 
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { cursorX.toDp() }, 
                    y = with(LocalDensity.current) { cursorY.toDp() }
                )
                .size(32.dp)
                .border(1.dp, Color.White, CircleShape)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape) 
                .padding(4.dp)
        )

        // 3. INSTRUCCIONES / BOTÓN SALIR
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .background(Color.Black.copy(alpha=0.7f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("Modo Navegador: Pulsa OK sobre el Play para extraer el video", color = Color.White, fontSize = 12.sp)
        }
    }
}

// Función auxiliar de clicks
private fun simulateClick(view: WebView, x: Float, y: Float) {
    val downTime = SystemClock.uptimeMillis()
    val eventTime = SystemClock.uptimeMillis() + 100
    val metaState = 0
    
    view.post {
        val motionEventDown = MotionEvent.obtain(
            downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, metaState
        )
        val motionEventUp = MotionEvent.obtain(
            downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, metaState
        )
        view.dispatchTouchEvent(motionEventDown)
        view.dispatchTouchEvent(motionEventUp)
        
        motionEventDown.recycle()
        motionEventUp.recycle()
    }
}