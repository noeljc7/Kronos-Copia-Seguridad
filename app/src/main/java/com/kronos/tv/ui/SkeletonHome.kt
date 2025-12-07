package com.kronos.tv.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// --- PINCEL DE CARGA (SHIMMER) ---
// Crea el efecto de "luz pasando"
@Composable
fun rememberShimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color(0xFF2B2B2B),
        Color(0xFF4D4D4D),
        Color(0xFF2B2B2B),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

// --- PANTALLA ESQUELETO (UI FALSA) ---
@Composable
fun SkeletonHomeScreen() {
    val brush = rememberShimmerBrush()
    
    // Usamos la misma estructura que el Home real: Column con Scroll
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Skeleton (Caja grande del póster principal)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp)
                .background(brush)
        )
        
        // Simulamos 3 filas de contenido
        repeat(3) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Título falso de categoría
            Box(
                modifier = Modifier
                    .padding(start = 50.dp)
                    .size(width = 200.dp, height = 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Fila horizontal de pósters falsos
            Row(
                modifier = Modifier.padding(horizontal = 50.dp)
            ) {
                repeat(6) { // 6 pósters falsos por fila
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        }
    }
}