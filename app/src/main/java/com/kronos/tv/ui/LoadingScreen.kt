package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingScreen(status: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010)), // Fondo casi negro (Elegante)
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. TÍTULO DE LA APP
            Text(
                text = "KRONOS",
                color = Color(0xFFE50914), // Rojo estilo Netflix (o cambia a tu gusto)
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. CÍRCULO DE CARGA
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color.White,
                strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. ESTADO ACTUAL (Ej: "Descargando scripts...")
            Text(
                text = status,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}
