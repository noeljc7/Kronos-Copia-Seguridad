package com.kronos.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// Usamos componentes de TV Material 3
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api

// --- COLORES GLOBALES ---
val NetflixRed = Color(0xFFE50914)
val DarkBackground = Color(0xFF000000)
val GrayText = Color(0xFFBCBCBC)

// --- BOTÓN ESTÁNDAR DE ALTO CONTRASTE ---
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetflixButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false, // <--- AQUÍ ESTÁ EL PARÁMETRO QUE FALTABA
    focusRequester: FocusRequester? = null,
    onReady: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    // Aplicamos el FocusRequester si existe
    val finalModifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier

    // Lógica de color: Rojo si es primario, Gris si no.
    // PERO: Si tiene foco, SIEMPRE es blanco.
    val baseContainer = if (isPrimary) NetflixRed else Color(0xFF333333)

    Button(
        onClick = onClick,
        modifier = finalModifier
            .onFocusChanged { isFocused = it.isFocused }
            .onGloballyPositioned { 
                // Notificamos cuando el botón ya existe en pantalla (para el fix del Home)
                if (focusRequester != null) {
                    onReady?.invoke() 
                }
            },
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        colors = ButtonDefaults.colors(
            containerColor = baseContainer, 
            contentColor = Color.White,
            focusedContainerColor = Color.White, // Blanco al enfocar
            focusedContentColor = Color.Black    // Letras negras al enfocar
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text, 
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// --- ETIQUETA "HD" ---
@Composable
fun BorderText(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, GrayText, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text, 
            color = GrayText, 
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}