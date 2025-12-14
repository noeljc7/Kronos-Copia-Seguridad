package com.kronos.tv.utils

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kronos.tv.ui.AppLogger
import java.io.File

object PythonBoot {

    fun init(context: Context) {
        if (!Python.isStarted()) {
            val androidPlatform = AndroidPlatform(context)
            Python.start(androidPlatform)
        }

        // 1. Obtenemos la ruta donde guardaremos los plugins descargados
        // Ruta típica: /data/user/0/com.kronos.tv/files/plugins
        val pluginsDir = File(context.filesDir, "plugins")
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        // 2. Le decimos a Python que agregue esa ruta a su sys.path
        val py = Python.getInstance()
        val sys = py.getModule("sys")
        val path = sys["path"]
        
        // Solo agregamos si no existe ya
        val pluginsPathStr = pluginsDir.absolutePath
        val alreadyInPath = path?.asList()?.any { it.toString() == pluginsPathStr } ?: false

        if (!alreadyInPath) {
            path?.callAttr("append", pluginsPathStr)
            AppLogger.log("BOOT", "📂 Ruta de plugins agregada a Python: $pluginsPathStr")
        } else {
            AppLogger.log("BOOT", "✅ Ruta de plugins ya configurada.")
        }
    }
}
