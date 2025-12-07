package com.kronos.tv.utils

import java.util.regex.Pattern

object JsUnpacker {
    fun unpack(packedJS: String): String? {
        try {
            val pattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\).*?split\\('\\|'\\)\\)\\)", Pattern.DOTALL)
            val matcher = pattern.matcher(packedJS)
            
            if (matcher.find()) {
                val block = matcher.group(0) ?: return null
                // Extraer los argumentos: p, a, c, k
                val argsPattern = Pattern.compile("}\\('(.+?)',(\\d+),(\\d+),'([^']*)'\\.split")
                val argsMatcher = argsPattern.matcher(block)
                
                if (argsMatcher.find()) {
                    var p = argsMatcher.group(1) ?: ""
                    val a = argsMatcher.group(2)?.toInt() ?: 0
                    val c = argsMatcher.group(3)?.toInt() ?: 0
                    val k = (argsMatcher.group(4) ?: "").split("|").toTypedArray()
                    
                    return decode(p, a, c, k)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun decode(p: String, a: Int, c: Int, k: Array<String>): String {
        var currentC = c
        var result = p
        
        while (currentC > 0) {
            currentC--
            val key = if (k[currentC].isNotEmpty()) k[currentC] else baseN(currentC, a)
            val token = "\\b" + baseN(currentC, a) + "\\b"
            result = result.replace(Regex(token), key)
        }
        return result
    }

    private fun baseN(num: Int, base: Int): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (num == 0) return "0"
        var n = num
        var ret = ""
        while (n > 0) {
            ret = chars[n % base] + ret
            n /= base
        }
        return ret
    }
}