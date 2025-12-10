package com.kronos.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.lifecycle.lifecycleScope
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.ui.AppNavigation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR EL MOTOR
        ScriptEngine.initialize(this)

        // 2. INSTANCIAR EL GESTOR (Carga local assets/sololatino.js)
        val providerManager = ProviderManager(this)

        // 3. MODO NUBE (ACTIVADO Y CON LOGS VISIBLES)
        lifecycleScope.launch {
            // CAMBIA ESTO SI TU URL ES DIFERENTE
            val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_script/manifest.json"
            
            ScreenLogger.log("KRONOS", "🚀 Iniciando App...")
            ScreenLogger.log("KRONOS", "Intentando conectar a: $manifestUrl")
            
            ProviderManager.loadRemoteProviders(manifestUrl)
        }

        // --- CRASH HANDLER ---
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("KronosCrash", "CRASH: ${throwable.message}", throwable)
            val intent = Intent(this, com.kronos.tv.ui.CrashActivity::class.java).apply {
                putExtra("error", Log.getStackTraceString(throwable))
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. LA APP (Al fondo)
                    AppNavigation(providerManager)

                    // 2. LA PANTALLA DE LOGS (Encima, transparente)
                    DebugOverlay()
                }
            }
        }
    }
}

// --- HERRAMIENTAS DE DEPURACIÓN (VISIBLES EN PANTALLA) ---

object ScreenLogger {
    val logs = mutableStateListOf<String>()

    fun log(tag: String, msg: String) {
        val entry = "[$tag] $msg"
        // Añadir al principio para que lo nuevo salga arriba
        logs.add(0, entry)
        if (logs.size > 30) logs.removeLast()
        Log.d(tag, msg)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DebugOverlay() {
    // Un panel semitransparente en la parte superior izquierda
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(0.6f) // Ocupa el 60% del ancho
            .height(350.dp)     // Ocupa un poco de alto
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(8.dp)
            .zIndex(999f) // Asegurar que esté siempre encima
    ) {
        items(ScreenLogger.logs.size) { index ->
            Text(
                text = ScreenLogger.logs[index],
                color = Color.Green, // Color Hacker para que resalte
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
        }
    }
}
