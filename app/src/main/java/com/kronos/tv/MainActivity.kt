package com.kronos.tv

// --- IMPORTS ---
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- TUS IMPORTS ---
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.ui.AppNavigation
import com.kronos.tv.ui.LoadingScreen // <--- IMPORTANTE: El archivo nuevo
import com.kronos.tv.ui.CrashActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR EL MOTOR
        ScriptEngine.initialize(this)
        val providerManager = ProviderManager(this)

        // Crash Handler
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("KronosCrash", "CRASH: ${throwable.message}", throwable)
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("error", Log.getStackTraceString(throwable))
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        setContent {
            // --- ESTADOS DE LA INTERFAZ ---
            var showLogs by remember { mutableStateOf(true) }
            var isSystemReady by remember { mutableStateOf(false) } // Controla si ya cargó
            var loadingStatus by remember { mutableStateOf("Conectando con el servidor...") }

            // --- LÓGICA DE CARGA INICIAL (Solo una vez) ---
            LaunchedEffect(Unit) {
                val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_script/manifest.json"
                
                // Paso 1: Descargar
                loadingStatus = "Actualizando proveedores..."
                ProviderManager.loadRemoteProviders(manifestUrl)
                
                // Paso 2: Pequeña pausa estética (opcional) para que no sea un parpadeo
                // y asegurar que el WebView procesó los scripts
                loadingStatus = "Finalizando configuración..."
                delay(1500) 

                // Paso 3: ¡Listo!
                isSystemReady = true
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_MENU, 
                                KeyEvent.KEYCODE_INFO,
                                KeyEvent.KEYCODE_I,
                                KeyEvent.KEYCODE_M -> {
                                    showLogs = !showLogs
                                    return@onKeyEvent true
                                }
                            }
                        }
                        false
                    }, 
                shape = RectangleShape
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    // LÓGICA PRINCIPAL: ¿CARGANDO O APP?
                    if (!isSystemReady) {
                        // A. PANTALLA DE CARGA
                        LoadingScreen(status = loadingStatus)
                    } else {
                        // B. LA APP REAL
                        AppNavigation(providerManager)
                    }

                    // C. LOGS (Siempre disponibles encima de todo)
                    if (showLogs) {
                        DebugOverlay()
                    }
                }
            }
        }
    }
}

// --- LOGS ---
object ScreenLogger {
    val logs = mutableStateListOf<String>()
    fun log(tag: String, msg: String) {
        val entry = "[$tag] $msg"
        logs.add(0, entry)
        if (logs.size > 50) logs.removeLast()
        Log.d(tag, msg)
    }
}

@Composable
fun DebugOverlay() {
    Box(modifier = Modifier.fillMaxSize().zIndex(999f)) {
        LazyColumn(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.6f)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp)
        ) {
            item { Text("KRONOS DEBUG", color = Color.Yellow, fontSize = 14.sp) }
            items(ScreenLogger.logs.size) { index ->
                Text(
                    text = ScreenLogger.logs[index],
                    color = if (ScreenLogger.logs[index].contains("ERROR")) Color.Red else Color.Green,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
