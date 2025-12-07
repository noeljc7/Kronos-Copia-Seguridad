@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kronos.tv.ui

import android.annotation.SuppressLint
import androidx.media3.common.TrackSelectionOverride
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.view.KeyEvent as NativeKeyEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn as JavaOptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text as MobileText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon // <--- ESTA ERA LA IMPORTACIÓN QUE FALTABA
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kronos.tv.utils.AdBlocker
import com.kronos.tv.utils.HistoryManager
import com.kronos.tv.utils.WatchProgress
import kotlinx.coroutines.delay

// --- CONTROLADOR PRINCIPAL ---
@JavaOptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    videoTitle: String,
    tmdbId: Int = 0, isMovie: Boolean = true, season: Int = 0, episode: Int = 0,
    posterUrl: String = "", backdropUrl: String = "",
    onBackPressed: () -> Unit
) {
    var playerState by remember { mutableIntStateOf(0) }
    var finalPlayableUrl by remember { mutableStateOf(videoUrl) }
    var sniffedCookies by remember { mutableStateOf("") }
    val defaultUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    var sniffedUserAgent by remember { mutableStateOf(defaultUA) }

    when (playerState) {
        0 -> VideoSniffer(
            videoUrl, defaultUA,
            onFound = { u, c, ua -> finalPlayableUrl = u; sniffedCookies = c; sniffedUserAgent = ua; playerState = 1 },
            onTimeout = { finalPlayableUrl = videoUrl; playerState = 1 },
            onBack = onBackPressed
        )
        1 -> KodiExoPlayer(
            finalPlayableUrl, videoTitle, sniffedCookies, sniffedUserAgent,
            tmdbId, isMovie, season, episode, posterUrl, backdropUrl,
            onBack = onBackPressed, 
            onError = { playerState = 2 }
        )
        2 -> InteractiveSniffer(
            url = videoUrl,
            onVideoFound = { u, c, ua -> 
                finalPlayableUrl = u
                sniffedCookies = c
                sniffedUserAgent = ua
                playerState = 1 
            },
            onBack = onBackPressed
        )
    }
}

// --- SNIFFER AUTOMÁTICO ---
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoSniffer(url: String, userAgent: String, onFound: (String, String, String) -> Unit, onTimeout: () -> Unit, onBack: () -> Unit) {
    var statusText by remember { mutableStateOf("Analizando enlace...") }
    // Variable para evitar doble disparo (debounce manual)
    var foundCalled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFE50914), strokeWidth = 4.dp)
            Spacer(modifier = Modifier.height(20.dp))
            MobileText("Escaneando Servidor...", color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            MobileText(statusText, color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(30.dp))
            Box(modifier = Modifier.clickable { onBack() }) {
                Button(onClick = onBack, colors = ButtonDefaults.colors(containerColor = Color.DarkGray)) { MobileText("Cancelar") }
            }
        }
        
        AndroidView(factory = { context -> WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1) // Invisible pero funcional
            settings.apply { 
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = userAgent
                // Importante para evitar caches viejos que causen bucles
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE 
            }
            
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    
                    // 1. FILTRO DE BASURA (AdBlocker básico interno)
                    // Si la URL contiene palabras clave de publicidad, la ignoramos totalmente
                    if (reqUrl.contains("google") || reqUrl.contains("doubleclick") || 
                        reqUrl.contains("facebook") || reqUrl.contains("analytics") || 
                        reqUrl.contains("pixel") || reqUrl.contains("favicon")) {
                        return AdBlocker.createEmptyResponse()
                    }

                    // 2. DETECCIÓN DE VIDEO
                    val isM3u8 = reqUrl.contains(".m3u8")
                    val isMp4 = reqUrl.contains(".mp4")
                    val isJsonMaster = reqUrl.contains("master.json")

                    if (isM3u8 || isMp4 || isJsonMaster) {
                        
                        // 3. FILTRO DE SEGURIDAD PARA FALSOS POSITIVOS
                        // Muchos anuncios son .mp4. Filtramos si parece un banner o preview.
                        if (reqUrl.contains("banner") || reqUrl.contains("preview") || reqUrl.contains("intro")) {
                             return super.shouldInterceptRequest(view, request)
                        }

                        // Vidhide/Voe a veces usan blobs o rutas relativas raras, nos aseguramos que sea http
                        if (!reqUrl.startsWith("http")) return null

                        // EVITAR REBOTE: Si ya encontramos uno, no hacemos nada más
                        if (!foundCalled) {
                            val cookies = CookieManager.getInstance().getCookie(reqUrl) ?: ""
                            foundCalled = true // Bloqueamos futuros disparos
                            
                            view?.post { 
                                statusText = "¡Video Detectado!"
                                onFound(reqUrl, cookies, userAgent) 
                            }
                            return AdBlocker.createEmptyResponse()
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    statusText = "Esperando video..."
                    // Inyección JS agresiva para forzar la carga del video en Voe/Vidhide
                    // Intentamos buscar todos los videos y darles play
                    val js = """
                        var vids = document.getElementsByTagName('video'); 
                        for(var i=0; i<vids.length; i++){ 
                            vids[i].muted = true; 
                            vids[i].play(); 
                        }
                        // Click en posibles botones de "Play" falsos (overlays)
                        var buttons = document.querySelectorAll('.play, .vjs-big-play-button');
                        for(var i=0; i<buttons.length; i++){
                            buttons[i].click();
                        }
                    """
                    view?.evaluateJavascript(js, null)
                }
            }
            loadUrl(url)
        } }, modifier = Modifier.size(1.dp)) // Mantenemos tamaño mínimo para que Android no lo mate
    }
    
    // Timeout de seguridad: Si en 25 segundos no hallamos nada, salimos.
    LaunchedEffect(Unit) { delay(25000); if(!foundCalled) onTimeout() }
}

// --- REPRODUCTOR EXOPLAYER ACTUALIZADO ---
@JavaOptIn(UnstableApi::class)
@Composable
fun KodiExoPlayer(
    videoUrl: String, videoTitle: String, cookies: String, userAgent: String,
    tmdbId: Int, isMovie: Boolean, season: Int, episode: Int, posterUrl: String, backdropUrl: String,
    onBack: () -> Unit, onError: () -> Unit
) {
    val context = LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    val sharedPreferences = remember { context.getSharedPreferences("KronosProgress", Context.MODE_PRIVATE) }

    // Estados UI Globales
    var isControlsVisible by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    
    // Estado para submenús de ajustes: "main", "audio", "subs"
    var activeMenuState by remember { mutableStateOf("main") } 

    var lastInputTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Estados Player
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    
    // Estados de Tracks (Audio/Subtitulos)
    var availableTracks by remember { mutableStateOf<Tracks?>(null) }

    // Navegación / Resume
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewTime by remember { mutableLongStateOf(0L) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumePosition by remember { mutableLongStateOf(0L) }

    // Focus Requesters
    val playPauseFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() } // Foco exclusivo para Resume
    val settingsFocus = remember { FocusRequester() } // Foco para el menú principal
    val subMenuFocus = remember { FocusRequester() }  // Foco para las listas de audio/subs

    fun wakeUpUI() {
        lastInputTime = System.currentTimeMillis()
        if (!isControlsVisible) isControlsVisible = true
    }

    // Lógica inicial de Resume
    LaunchedEffect(Unit) {
        val savedDB = historyManager.getProgress(tmdbId, season, episode)
        val savedPref = sharedPreferences.getLong(videoUrl, 0L)
        val finalSaved = maxOf(savedDB, savedPref)
        if (finalSaved > 5000) {
            resumePosition = finalSaved
            showResumeDialog = true
        }
    }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Referer" to "https://zonaaps.com/", "Cookie" to cookies))

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { if (!showResumeDialog) onError() }
                    override fun onIsPlayingChanged(p: Boolean) { isPlaying = p; wakeUpUI() }
                    override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) duration = this@apply.duration }
                    override fun onTracksChanged(tracks: Tracks) { availableTracks = tracks }
                })
                setMediaItem(MediaItem.Builder().setUri(Uri.parse(videoUrl)).setMimeType(if(videoUrl.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else null).build())
                prepare()
                playWhenReady = false
            }
    }

    // --- FUNCIONES DE TRACKS MEJORADAS ---
    fun selectTrack(group: Tracks.Group, trackIndex: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
            .setTrackTypeDisabled(group.type, false)
            .build()
        wakeUpUI()
    }

    fun disableTrack(type: Int) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, true)
            .build()
        wakeUpUI()
    }

    // --- GESTIÓN DE AUTO-OCULTAR CONTROLES ---
    LaunchedEffect(lastInputTime, isPlaying, isControlsVisible, showSettingsMenu, isSeeking, showResumeDialog) {
        if (isPlaying && isControlsVisible && !showResumeDialog && !isSeeking && !showSettingsMenu) {
            delay(5000)
            if (System.currentTimeMillis() - lastInputTime > 4000) {
                isControlsVisible = false
            }
        }
    }

    // --- GUARDADO DE PROGRESO ---
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (!isSeeking && exoPlayer.isPlaying) {
                currentTime = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (dur > 0) {
                    historyManager.saveProgress(WatchProgress(tmdbId, isMovie, videoTitle, posterUrl, backdropUrl, season, episode, currentTime, dur, System.currentTimeMillis()))
                    sharedPreferences.edit().putLong(videoUrl, currentTime).apply()
                }
            }
        }
    }

    // --- ESTRUCTURA UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    wakeUpUI()
                    // Si presionan Back y no hay menús abiertos
                    if (event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BACK) {
                        if (showSettingsMenu) {
                            if (activeMenuState != "main") activeMenuState = "main" // Volver atrás en submenú
                            else showSettingsMenu = false // Cerrar menú
                            return@onKeyEvent true
                        }
                        if (isControlsVisible) { onBack(); return@onKeyEvent true }
                    }
                }
                false
            }
    ) {
        // CAPA 1: VIDEO
        AndroidView(
            factory = { PlayerView(context).apply {
                player = exoPlayer
                useController = false
                setResizeMode(resizeMode)
                keepScreenOn = true
                setOnClickListener { wakeUpUI() }
            } },
            update = { view ->
                view.setResizeMode(resizeMode)
                view.isFocusable = !isControlsVisible 
            },
            modifier = Modifier.fillMaxSize()
        )

        // CAPA 2: FONDO TRANSPARENTE PARA CLICS
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusable(false) // No queremos que atrape foco del teclado
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { wakeUpUI() }
        )

        // CAPA 3: CONTROLES DE REPRODUCCIÓN
        AnimatedVisibility(
            visible = (isControlsVisible || showResumeDialog) && !showSettingsMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                
                if (!showResumeDialog) {
                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MobileText(text = videoTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MobileText(text = formatTime(currentTime), color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(20.dp))
                            KodiTextButton(text = "Omitir Intro", icon = Icons.Default.FastForward) {
                                exoPlayer.seekTo(exoPlayer.currentPosition + 85000); wakeUpUI()
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            KodiControlButton(icon = Icons.Default.Settings, label = "Ajustes", onClick = { 
                                showSettingsMenu = true
                                activeMenuState = "main"
                                wakeUpUI() 
                            })
                        }
                    }
                }

                // BOTTOM CONTROLS
                if (!showResumeDialog) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(32.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MobileText(formatTime(if(isSeeking) seekPreviewTime else currentTime), color = Color.White, fontSize = 12.sp)
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                KodiSlider(
                                    value = if(isSeeking) seekPreviewTime.toFloat() else currentTime.toFloat(),
                                    duration = duration.toFloat(),
                                    onValueChange = { isSeeking = true; seekPreviewTime = it.toLong(); wakeUpUI() },
                                    onSeekFinished = { exoPlayer.seekTo(seekPreviewTime); currentTime = seekPreviewTime; isSeeking = false; wakeUpUI() }
                                )
                            }
                            MobileText(formatTime(duration), color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            
                            // BOTÓN PREVIOUS ELIMINADO AQUÍ

                            KodiControlButton(icon = Icons.Default.FastRewind, label = "-10s", onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000); wakeUpUI() })
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            KodiControlButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = "Play", isMain = true, focusRequester = playPauseFocus,
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play(); wakeUpUI() }
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            KodiControlButton(icon = Icons.Default.FastForward, label = "+10s", onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000); wakeUpUI() })
                            Spacer(modifier = Modifier.width(16.dp))
                            KodiControlButton(icon = Icons.Default.Stop, label = "Salir", onClick = { onBack() })
                        }
                    }
                    
                    // Foco inicial cuando se abren los controles (si no es resume)
                    LaunchedEffect(isControlsVisible) {
                         if (isControlsVisible) playPauseFocus.requestFocus() 
                    }
                }
            }
        }

        // CAPA 4: MENÚ DE AJUSTES (Overlay)
        if (showSettingsMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                        showSettingsMenu = false 
                    }
            )
            
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Column(
                    modifier = Modifier
                        .padding(30.dp)
                        .width(350.dp)
                        .background(Color(0xFF202020), RoundedCornerShape(10.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                        .onKeyEvent { event ->
                             if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BACK) {
                                 if (activeMenuState != "main") activeMenuState = "main" // Volver atrás
                                 else showSettingsMenu = false
                                 true
                             } else false
                        }
                ) {
                    MobileText(
                        text = when(activeMenuState) { "audio" -> "Seleccionar Audio"; "subs" -> "Seleccionar Subtítulos"; else -> "Ajustes" }, 
                        color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 15.dp)
                    )

                    if (activeMenuState == "main") {
                        // --- MENÚ PRINCIPAL ---
                        val resizeLabel = when(resizeMode) { 
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Normal (Fit)"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Rellenar (Fill)"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Crop)"
                            else -> "Desconocido" 
                        }
                        
                        KodiMenuOption(text = "Pantalla: $resizeLabel", icon = Icons.Default.AspectRatio, focusRequester = settingsFocus, isFirstItem = true) {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }

                        // Verificación básica de si hay pistas disponibles
                        val hasSubs = availableTracks?.groups?.any { it.type == C.TRACK_TYPE_TEXT } == true
                        val hasAudio = availableTracks?.groups?.any { it.type == C.TRACK_TYPE_AUDIO } == true

                        KodiMenuOption(text = "Subtítulos...", icon = Icons.Default.Subtitles) { 
                             if (hasSubs) activeMenuState = "subs" 
                        }

                        KodiMenuOption(text = "Audio...", icon = Icons.Default.Audiotrack) { 
                             if (hasAudio) activeMenuState = "audio"
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        KodiMenuOption(text = "Cerrar Menú", icon = Icons.Default.Close) { showSettingsMenu = false }
                        
                    } else if (activeMenuState == "audio") {
                        // --- MENÚ AUDIO ---
                        val audioGroups = availableTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()
                        
                        LazyColumnWithFocus(items = audioGroups, focusRequester = subMenuFocus) { group ->
                             val isSelected = group.isSelected
                             val format = group.mediaTrackGroup.getFormat(0)
                             val label = "${format.language ?: "Und"} - ${format.label ?: "Track"}"
                             
                             KodiMenuOption(text = label, icon = if(isSelected) Icons.Default.Check else Icons.Default.Audiotrack) {
                                 selectTrack(group, 0)
                             }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        KodiMenuOption(text = "Volver", icon = Icons.Default.ArrowBack) { activeMenuState = "main" }

                    } else if (activeMenuState == "subs") {
                        // --- MENÚ SUBTÍTULOS ---
                        val subGroups = availableTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
                        
                        // Opción Desactivar
                        KodiMenuOption(text = "Desactivado", icon = Icons.Default.Block, focusRequester = subMenuFocus, isFirstItem = true) {
                             disableTrack(C.TRACK_TYPE_TEXT)
                             showSettingsMenu = false // Cerrar al seleccionar es más cómodo
                        }

                        LazyColumnWithFocus(items = subGroups) { group ->
                             val isSelected = group.isSelected
                             val format = group.mediaTrackGroup.getFormat(0)
                             val label = "${format.language ?: "Und"} - ${format.label ?: "Track"}"
                             
                             KodiMenuOption(text = label, icon = if(isSelected) Icons.Default.Check else Icons.Default.Subtitles) {
                                 selectTrack(group, 0)
                                 showSettingsMenu = false
                             }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        KodiMenuOption(text = "Volver", icon = Icons.Default.ArrowBack) { activeMenuState = "main" }
                    }
                }
            }

            // FORZAR FOCO AL ABRIR MENÚ O CAMBIAR SUBMENÚ
            LaunchedEffect(activeMenuState) {
                delay(200)
                try {
                    if (activeMenuState == "main") settingsFocus.requestFocus()
                    else subMenuFocus.requestFocus()
                } catch(e:Exception){}
            }
        }

        // CAPA 5: DIÁLOGO RESUME (MÁXIMA PRIORIDAD VISUAL Y DE FOCO)
        if (showResumeDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha=0.9f))
                    .clickable(enabled=false){}, // Bloquear clicks pasantes
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier
                        .background(Color(0xFF202020), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFE50914), RoundedCornerShape(12.dp))
                        .padding(40.dp)
                ) {
                    MobileText("¿Reanudar reproducción?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    MobileText(formatTime(resumePosition), color = Color(0xFFE50914), fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(vertical = 20.dp))
                    
                    KodiSolidButton(text = "REANUDAR", isPrimary = true, focusRequester = resumeFocus) {
                        exoPlayer.seekTo(resumePosition); exoPlayer.play(); showResumeDialog = false; wakeUpUI()
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    KodiSolidButton(text = "EMPEZAR DE CERO", isPrimary = false) {
                        exoPlayer.seekTo(0); exoPlayer.play(); showResumeDialog = false; wakeUpUI()
                    }
                }
            }
            
            // SOLICITAR FOCO AGRESIVAMENTE PARA RESUME
            LaunchedEffect(Unit) {
                delay(300) // Un poco más de delay para asegurar que esté sobre todo lo demás
                try { resumeFocus.requestFocus() } catch(e: Exception) { println("Error focus resume") }
            }
        }
    }
    
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}

// --- HELPER COMPOSABLE PARA LISTAS EN COMPOSE TV ---
// Necesario porque LazyColumn a veces es difícil de manejar con D-Pad si no se hace bien
@Composable
fun <T> LazyColumnWithFocus(items: List<T>, focusRequester: FocusRequester? = null, content: @Composable (T) -> Unit) {
    Column {
        items.forEachIndexed { index, item ->
            // Solo asignamos el FocusRequester al primer elemento para atrapar el foco inicial
            val modifier = if (index == 0 && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            Box(modifier = modifier) {
                content(item)
            }
        }
    }
}

@Composable
fun KodiSolidButton(text: String, isPrimary: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val modifier = Modifier
        .fillMaxWidth(0.8f)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusable(interactionSource = interactionSource)
        .background(if(isFocused) Color.White else if(isPrimary) Color(0xFFE50914) else Color(0xFF404040), RoundedCornerShape(8.dp))
        .padding(vertical = 12.dp)
        .clickable(interactionSource = interactionSource, indication = null) { onClick() }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && isConfirmKey(event.nativeKeyEvent.keyCode)) {
                onClick(); true
            } else false
        }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        MobileText(text, color = if(isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KodiControlButton(icon: ImageVector, label: String, onClick: () -> Unit, isMain: Boolean = false, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val size = if (isMain) 64.dp else 48.dp
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .background(if (isFocused) Color.White else Color(0xFF303030), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && isConfirmKey(event.nativeKeyEvent.keyCode)) {
                    onClick(); true
                } else false
            }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if(isFocused) Color.Black else Color.White, modifier = Modifier.size(if(isMain) 32.dp else 24.dp))
    }
}

@Composable
fun KodiTextButton(text: String, icon: ImageVector, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .background(if (isFocused) Color.White else Color(0xFF303030), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && isConfirmKey(event.nativeKeyEvent.keyCode)) {
                    onClick(); true
                } else false
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if(isFocused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        MobileText(text, color = if(isFocused) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KodiMenuOption(
    text: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    isFirstItem: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(
                if (isFocused) Color(0xFFE50914) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
            // IMPORTANTE: El orden de los modificadores afecta. 
            // onKeyEvent debe ir ANTES de focusable() para interceptar.
            .onKeyEvent { event ->
                // Lógica para atrapar el foco en el primer elemento (Evita salir hacia arriba)
                if (isFirstItem && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_UP) {
                    return@onKeyEvent true // Consumimos el evento (no hace nada, se queda ahí)
                }
                
                // Lógica para confirmar selección (Enter/Center/ButtonA)
                if (event.type == KeyEventType.KeyUp && isConfirmKey(event.nativeKeyEvent.keyCode)) {
                    onClick()
                    return@onKeyEvent true
                }
                false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                enabled = false // Desactivamos el click nativo de Compose para manejarlo 100% vía onKeyEvent en TV
            ) { }, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White, // Siempre blanco para mejor contraste
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        MobileText(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun KodiSlider(value: Float, duration: Float, onValueChange: (Float) -> Unit, onSeekFinished: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Slider(
        value = value, onValueChange = {}, valueRange = 0f..duration.coerceAtLeast(1f),
        colors = SliderDefaults.colors(thumbColor = if (isFocused) Color(0xFFE50914) else Color.White, activeTrackColor = Color(0xFFE50914), inactiveTrackColor = Color.Gray),
        thumb = { Box(Modifier.size(if(isFocused) 20.dp else 12.dp).background(if (isFocused) Color(0xFFE50914) else Color.White, CircleShape)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val step = duration * 0.05f
                    when (event.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_MINUS -> { 
                            onValueChange((value - step).coerceAtLeast(0f)); true 
                        }
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT, NativeKeyEvent.KEYCODE_PLUS, NativeKeyEvent.KEYCODE_EQUALS -> { 
                            onValueChange((value + step).coerceAtMost(duration)); true 
                        }
                        NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER, NativeKeyEvent.KEYCODE_SPACE -> { 
                            onSeekFinished(); true 
                        }
                        else -> false
                    }
                } else false
            }
    )
}

fun isConfirmKey(keyCode: Int): Boolean {
    return keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || 
           keyCode == NativeKeyEvent.KEYCODE_ENTER || 
           keyCode == NativeKeyEvent.KEYCODE_NUMPAD_ENTER || 
           keyCode == NativeKeyEvent.KEYCODE_SPACE ||
           keyCode == NativeKeyEvent.KEYCODE_BUTTON_A
}

@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = (totalSeconds / 60) % 60
    val s = totalSeconds % 60
    val h = totalSeconds / 3600
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}