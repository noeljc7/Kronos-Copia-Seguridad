package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BOTÓN TODOTERRENO (TV + Touch)
 * - En TV: Se ilumina al tener foco y activa con Enter.
 * - En Celular: Se presiona y activa con el dedo.
 */
@Composable
fun NetflixButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Escala al presionar o enfocar
    val scale by animateFloatAsState(
        targetValue = if (isFocused || isPressed) 1.05f else 1f, 
        label = "scale"
    )

    val bgColor = if (isPrimary) Color(0xFFE50914) else Color.White
    val contentColor = if (isPrimary) Color.White else Color.Black

    Box(
        modifier = modifier
            .focusRequester(focusRequester) // Para la TV
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable( // 🔥 ESTA LÍNEA HACE QUE FUNCIONE EL DEDO 🔥
                interactionSource = interactionSource,
                indication = null, 
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BorderText(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LoadingScreen(status: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFE50914))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status, color = Color.Gray)
        }
    }
}

@Composable
fun ErrorScreen(msg: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(50.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Error del Sistema", color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = msg, color = Color.Gray, fontSize = 12.sp)
        }
    }
}
