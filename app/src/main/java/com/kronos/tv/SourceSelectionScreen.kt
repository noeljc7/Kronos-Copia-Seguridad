@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.providers.SourceLink
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SourceSelectionScreen(
    tmdbId: Int,
    title: String,
    originalTitle: String, // <--- DATO VITAL 1 (Para búsqueda en inglés)
    year: Int,             // <--- DATO VITAL 2 (Para filtrar año)
    isMovie: Boolean,
    season: Int,
    episode: Int,
    providerManager: ProviderManager, // <--- INYECCIÓN DEL MANAGER
    onLinkSelected: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    // --- LÓGICA DE ESTADO ---
    var isLoading by remember { mutableStateOf(true) }
    var links by remember { mutableStateOf(emptyList<SourceLink>()) }
    
    // Foco inicial
    val firstLinkFocus = remember { FocusRequester() }

    // --- CARGA DE DATOS (EL CEREBRO) ---
    LaunchedEffect(Unit) {
        // Usamos el providerManager que viene de MainActivity con los datos correctos
        links = providerManager.getLinks(
            tmdbId = tmdbId,
            title = title,
            originalTitle = originalTitle, // Pasamos título original
            isMovie = isMovie,
            year = year,                   // Pasamos año
            season = season,
            episode = episode
        )
        isLoading = false
        
        // Pedir foco al primer elemento si existe
        delay(200)
        try { firstLinkFocus.requestFocus() } catch(e:Exception){}
    }

    // --- DISEÑO VISUAL (TU DISEÑO ORIGINAL) ---
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // COLUMNA IZQUIERDA (INFO)
        Column(
            modifier = Modifier
                .width(350.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.displaySmall, 
                color = Color.White, 
                fontWeight = FontWeight.Bold
            )
            
            if (!isMovie) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Temporada $season - Episodio $episode", 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.Gray
                )
            } else if (year > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Año: $year", 
                    style = MaterialTheme.typography.titleMedium, 
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Botón de Volver (Implementación local para evitar errores de import)
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFFE50914)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Volver Atrás")
            }
        }

        // COLUMNA DERECHA (LISTA DE LINKS)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Escaneando servidores...", color = Color.Gray)
                    Text("Buscando: $originalTitle", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
                }
            } else if (links.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning, 
                        contentDescription = null, 
                        tint = Color(0xFFEF5350), 
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No se encontraron servidores", color = Color.White)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp), 
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(links) { index, link ->
                        val modifier = if (index == 0) Modifier.focusRequester(firstLinkFocus) else Modifier
                        
                        SourceCardPremium(
                            link = link, 
                            onClick = { onLinkSelected(link.url, link.requiresWebView) },
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourceCardPremium(link: SourceLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "scale")
    val bgColor = if (isFocused) Color(0xFF353535) else Color(0xFF1F1F1F) 
    val borderColor = if (isFocused) Color.White else Color.Transparent
    
    // Distinción visual simple
    val badgeText = if (link.isDirect) "VIDEO" else "WEB"
    val badgeColor = if (link.isDirect) Color(0xFF66BB6A) else Color(0xFF42A5F5)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // BANDERAS (Requiere Coil)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(130.dp)) {
                AsyncImage(
                    model = getFlagUrl(link.language), 
                    contentDescription = link.language, 
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(2.dp)), 
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = cleanLanguageText(link.language), 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White, 
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // INFORMACIÓN CENTRAL
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = link.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White, 
                    maxLines = 1
                )
                Text(
                    text = "${link.quality} • ${link.provider}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color.Gray, 
                    fontWeight = FontWeight.Bold
                )
            }
            
            // BADGE DERECHO
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText, 
                    color = badgeColor, 
                    style = MaterialTheme.typography.labelSmall, 
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- UTILIDADES VISUALES ---

fun getFlagUrl(language: String): String {
    val lang = language.lowercase()
    val countryCode = when {
        lang.contains("latino") -> "mx"
        lang.contains("castellano") || lang.contains("esp") -> "es"
        lang.contains("english") || lang.contains("ing") || lang.contains("sub") -> "us"
        lang.contains("port") -> "br"
        lang.contains("fr") -> "fr"
        lang.contains("it") -> "it"
        else -> "un" 
    }
    return "https://flagcdn.com/w80/$countryCode.png"
}

fun cleanLanguageText(language: String): String {
    val cleaned = language.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
    return if (cleaned.isNotEmpty()) {
        cleaned.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } else "Desconocido"
}
