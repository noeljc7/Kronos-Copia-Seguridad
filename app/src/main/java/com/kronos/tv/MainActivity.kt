@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.kronos.tv

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
import kotlinx.coroutines.delay

import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.ui.AppNavigation
import com.kronos.tv.ui.LoadingScreen
import com.kronos.tv.ui.CrashActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR COMPONENTES
        // ScriptEngine (JS) lo dejamos por si acaso, aunque usaremos Python
        ScriptEngine.initialize(this)
        
        // ProviderManager ahora iniciará Python internamente
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
            var showLogs by remember { mutableStateOf(true) }
            var isSystemReady by remember { mutableStateOf(false) }
            var loadingStatus by remember { mutableStateOf("Iniciando sistema...") }

            // --- LÓGICA DE INICIO ---
            LaunchedEffect(Unit) {
                // Ya no descargamos nada de la nube al inicio.
                // Python está listo en cuanto se instala la app.
                loadingStatus = "Preparando motores..."
                delay(1000) // Pequeña pausa estética
                
                ScreenLogger.log("KRONOS", "🚀 Sistema listo (Modo Python Nativo)")
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
                    
                    if (!isSystemReady) {
                        LoadingScreen(status = loadingStatus)
                    } else {
                        AppNavigation(providerManager)
                    }

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
            item { Text("KRONOS PYTHON DEBUG", color = Color.Cyan, fontSize = 14.sp) }
            items(ScreenLogger.logs.size) { index ->
                Text(
                    text = ScreenLogger.logs[index],
                    color = if (ScreenLogger.logs[index].contains("ERROR") || ScreenLogger.logs[index].contains("ERR")) Color.Red else Color.Green,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
