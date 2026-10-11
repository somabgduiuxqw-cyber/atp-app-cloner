package com.atp.cloner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atp.cloner.ui.components.AtpSectionHeader
import com.atp.cloner.ui.theme.AppThemeMode
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.theme.DarkThemeVariant
import com.atp.cloner.ui.viewmodel.Screen

@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    darkVariant: DarkThemeVariant,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetDarkVariant: (DarkThemeVariant) -> Unit,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cleanedCacheMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings & Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Appearance & Themes Section
        item {
            AtpSectionHeader(
                title = "Appearance & Theme Mode",
                subtitle = "Choose light, dark, or system default display",
                icon = Icons.Default.Palette
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "THEME MODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtpPrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetThemeMode(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = AtpPrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SELECTABLE DARK PALETTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AtpPrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DarkThemeVariant.entries.forEach { variant ->
                        val color = Color(android.graphics.Color.parseColor(variant.hexCode))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetDarkVariant(variant) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = darkVariant == variant,
                                onClick = { onSetDarkVariant(variant) },
                                colors = RadioButtonDefaults.colors(selectedColor = AtpPrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "${variant.displayName} (${variant.hexCode})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Sub-pages Quick Nav
        item {
            AtpSectionHeader(
                title = "Management & Information",
                subtitle = "History, signing foundation, and about",
                icon = Icons.Default.Info
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Default.History,
                        title = "Processing History",
                        subtitle = "View chronological operation records",
                        onClick = { onNavigate(Screen.HISTORY) }
                    )
                    HorizontalDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Key,
                        title = "Signing Preparation",
                        subtitle = "Inspect local RSA-2048 signing keys",
                        onClick = { onNavigate(Screen.SIGNING_PREP) }
                    )
                    HorizontalDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Info,
                        title = "About ATP App Cloner",
                        subtitle = "ATP Network specifications & privacy",
                        onClick = { onNavigate(Screen.ABOUT) }
                    )
                }
            }
        }

        // Cache Cleaner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = AtpPrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clean Temporary Workspaces",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Deletes temporary working buffers. Preserves your project records and original APKs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.cacheDir.deleteRecursively()
                            context.cacheDir.mkdirs()
                            cleanedCacheMessage = "Workspace cache cleared successfully."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("clean_cache_button")
                    ) {
                        Text("Clean Cache")
                    }
                    if (cleanedCacheMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cleanedCacheMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = AtpSuccessGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AtpPrimaryBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AtpPrimaryBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
