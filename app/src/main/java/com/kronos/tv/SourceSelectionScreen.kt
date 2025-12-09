@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.providers.SourceLink // Importante: Ahora viene de providers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SourceSelectionScreen(
    tmdbId: Int,
    title: String,
    originalTitle: String, // <--- NUEVO
    year: Int,             // <--- NUEVO
    isMovie: Boolean,
    season: Int,
    episode: Int,
    providerManager: ProviderManager,
    onLinkSelected: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    // --- 1. VARIABLES DE ESTADO (ESTO ES LO QUE FALTABA) ---
    var links by remember { mutableStateOf<List<SourceLink>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isResolving by remember { mutableStateOf(false) } // Para mostrar diálogo si estamos resolviendo un link final
    
    val scope = rememberCoroutineScope()
    val firstLinkFocus = remember { FocusRequester() }

    // --- 2. CARGA DE ENLACES ---
    LaunchedEffect(Unit) {
        // Llamada con los nuevos parámetros
        links = providerManager.getLinks(
            tmdbId = tmdbId, 
            title = title, 
            originalTitle = originalTitle, 
            isMovie = isMovie, 
            year = year, 
            season = season, 
            episode = episode
        )
        isLoading = false
        
        // Intentar enfocar el primer elemento
        delay(200)
        try { firstLinkFocus.requestFocus() } catch(e:Exception){}
    }

    // --- 3. INTERFAZ DE USUARIO ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(30.dp)
    ) {
        // Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(containerColor = Color.Transparent)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if(isMovie) "Fuentes: $title ($year)" else "Fuentes: $title ${season}x${episode}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Contenido Principal
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Buscando servidores...", color = Color.Gray, modifier = Modifier.padding(top=50.dp))
            }
        } else if (links.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron enlaces disponibles.", color = Color.Gray)
            }
        } else {
            // Lista de enlaces
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(links) { link ->
                    LinkItem(
                        link = link,
                        isFocused = false, // Simplificación, el focus lo maneja el botón interno
                        onClick = {
                            if (link.isDirect) {
                                onLinkSelected(link.url, link.requiresWebView)
                            } else {
                                // Si no es directo, aquí iría la lógica de resolución extra
                                // Por ahora asumimos directo gracias al JS v10
                                onLinkSelected(link.url, link.requiresWebView)
                            }
                        },
                        modifier = if (link == links.first()) Modifier.focusRequester(firstLinkFocus) else Modifier
                    )
                }
            }
        }
        
        // Overlay de "Resolviendo" (Opcional, por si el JS tarda en un segundo paso)
        if (isResolving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Conectando con el servidor...", color = Color.White, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

// Componente para cada fila de enlace
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LinkItem(
    link: SourceLink,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFFE50914)
        ),
        shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(15.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                Spacer(modifier = Modifier.width(15.dp))
                Column {
                    Text(text = link.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(text = "${link.language} • ${link.quality} • ${link.provider}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}
