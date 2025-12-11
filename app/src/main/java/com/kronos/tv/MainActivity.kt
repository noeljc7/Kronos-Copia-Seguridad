package com.kronos.tv

// --- IMPORTS DE ANDROID ---
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent // Necesario para detectar botones del mando

// --- IMPORTS DE ACTIVITY ---
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// --- IMPORTS DE COMPOSE UI & LAYOUT ---
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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

// --- IMPORTS DE TV MATERIAL ---
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

// --- IMPORTS DE CORUTINAS ---
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// --- IMPORTS DE TU PROYECTO (KRONOS) ---
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.ui.AppNavigation
import com.kronos.tv.ui.CrashActivity // Asegúrate de que esta Activity existe en tu proyecto

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR MOTOR (WebView Oculto)
        ScriptEngine.initialize(this)

        // 2. INICIALIZAR GESTOR (Modo Nube)
        val providerManager = ProviderManager(this)

        // 3. CARGAR SCRIPTS DE LA NUBE
        lifecycleScope.launch {
            // URL de tu repositorio (Control Remoto)
            val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_script/manifest.json"
            
            // Llamada al método estático (companion object)
            ProviderManager.loadRemoteProviders(manifestUrl)
        }

        // --- MANEJO DE ERRORES (CRASH HANDLER) ---
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

        // --- INTERFAZ GRÁFICA (COMPOSE) ---
        setContent {
            // Estado para mostrar/ocultar los logs (Empieza visible = true)
            var showLogs by remember { mutableStateOf(true) }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    // DETECTOR DE TECLAS DEL MANDO
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
                    
                    // A. LA APP (Capa inferior)
                    AppNavigation(providerManager)

                    // B. LOGS DE DEPURACIÓN (Capa superior, condicional)
                    if (showLogs) {
                        DebugOverlay()
                    }
                }
            }
        }
    }
}

// --- HERRAMIENTAS DE LOGS ---

object ScreenLogger {
    val logs = mutableStateListOf<String>()

    fun log(tag: String, msg: String) {
        val entry = "[$tag] $msg"
        logs.add(0, entry)
        // Guardamos los últimos 50 logs para no saturar memoria
        if (logs.size > 50) logs.removeLast()
        Log.d(tag, msg)
    }
}

@Composable
fun DebugOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f) // Asegura que esté siempre encima
    ) {
        LazyColumn(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopStart) // Arriba izquierda
                .fillMaxWidth(0.5f) // Mitad del ancho
                .fillMaxHeight(0.6f) // 60% del alto
                .background(Color.Black.copy(alpha = 0.85f)) // Fondo oscuro semi-transparente
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "KRONOS DEBUG (Menu/Info to hide)", 
                    color = Color.Yellow, 
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            items(ScreenLogger.logs.size) { index ->
                val logText = ScreenLogger.logs[index]
                Text(
                    text = logText,
                    // Si es error se pinta rojo, si no verde hacker
                    color = if (logText.contains("ERROR") || logText.contains("Fallo")) Color.Red else Color.Green,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
