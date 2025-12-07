package com.kronos.tv.ui

import androidx.compose.runtime.mutableStateListOf

// Un "Singleton" (objeto único) que guarda los mensajes de error
object AppLogger {
    // Lista observable para que la pantalla se actualice sola cuando llegue un mensaje
    val logs = mutableStateListOf<String>()

    fun log(tag: String, msg: String) {
        val entry = "[$tag] $msg"
        println(entry) // Imprime en consola de sistema por si acaso
        logs.add(0, entry) // Agrega al principio (los más nuevos arriba)
        if (logs.size > 50) logs.removeLast() // Mantiene solo los últimos 50 mensajes
    }
    
    fun clear() {
        logs.clear()
    }
}