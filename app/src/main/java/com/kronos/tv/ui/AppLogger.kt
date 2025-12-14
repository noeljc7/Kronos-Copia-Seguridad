package com.kronos.tv.ui

import android.util.Log

// Objeto Singleton accesible desde Python
object AppLogger {
    
    // @JvmStatic es VITAL para que Python lo vea como método estático
    @JvmStatic
    fun log(tag: String, msg: String) {
        // Redirigimos al Logcat de Android
        Log.d("KRONOS_PYTHON", "[$tag] $msg")
        
        // Opcional: Si quieres verlo en tu pantalla de debug en la UI
        // ScreenLogger.log(tag, msg) 
    }
}
