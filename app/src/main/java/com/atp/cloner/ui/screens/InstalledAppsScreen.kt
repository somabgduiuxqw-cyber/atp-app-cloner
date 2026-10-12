package com.atp.cloner.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atp.cloner.data.model.InstalledAppInfo
import com.atp.cloner.ui.theme.AtpErrorRed
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.viewmodel.InstalledAppsUiState
import com.atp.cloner.ui.viewmodel.InstalledAppsViewModel
import java.text.DecimalFormat

@Composable
fun InstalledAppsScreen(
    onSelectApp: (InstalledAppInfo) -> Unit,
    onBack: () -> Unit,
    onManualImportFallback: () -> Unit,
    isProcessing: Boolean = false,
    extractionError: String? = null,
    onClearExtractionError: () -> Unit = {},
    viewModel: InstalledAppsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val includeSystemApps by viewModel.includeSystemApps.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    var pendingSplitApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showPermissionInfo by remember { mutableStateOf(false) }

    // Split APK Notice Dialog
    if (pendingSplitApp != null) {
        val app = pendingSplitApp!!
        AlertDialog(
            onDismissRequest = { pendingSplitApp = null },
            icon = {
                Icon(Icons.Default.Layers, contentDescription = null, tint = AtpPrimaryBlue)
            },
            title = {
                Text("Split APK Application Detected", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "\"${app.appName}\" uses Android App Bundles with ${app.splitCount} split APKs (${app.splitNames.take(3).joinToString(", ")}${if (app.splitNames.size > 3) "..." else ""}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ATP App Cloner will extract the primary base APK containing the application code and manifest. Standalone cloning works reliably for core app features, while device-specific configurations remain in external splits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selected = pendingSplitApp!!
                        pendingSplitApp = null
                        onSelectApp(selected)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                    modifier = Modifier.testTag("confirm_split_apk_extraction_button")
                ) {
                    Text("Proceed with Base APK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingSplitApp = null },
                    modifier = Modifier.testTag("cancel_split_apk_dialog_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("installed_apps_screen")
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("installed_apps_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Installed Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select an installed app to inspect and clone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.loadInstalledApps() },
                    modifier = Modifier.testTag("refresh_installed_apps_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // Search Bar & Filter Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_installed_apps_input"),
                placeholder = { Text("Search by name or package...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AtpPrimaryBlue)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !includeSystemApps,
                    onClick = { viewModel.toggleSystemApps(false) },
                    label = { Text("User Apps") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AtpPrimaryBlue.copy(alpha = 0.15f),
                        selectedLabelColor = AtpPrimaryBlue
                    ),
                    modifier = Modifier.testTag("filter_user_apps")
                )

                FilterChip(
                    selected = includeSystemApps,
                    onClick = { viewModel.toggleSystemApps(true) },
                    label = { Text("All (Incl. System)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AtpPrimaryBlue.copy(alpha = 0.15f),
                        selectedLabelColor = AtpPrimaryBlue
                    ),
                    modifier = Modifier.testTag("filter_all_apps")
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { viewModel.toggleSortOrder() },
                    modifier = Modifier.testTag("sort_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SortByAlpha,
                        contentDescription = "Sort order",
                        tint = if (sortAscending) AtpPrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Extraction Error Banner with troubleshooting guide
        if (extractionError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("extraction_error_banner"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "APK Extraction Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = extractionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Troubleshooting Guidance:\n• Android security restrictions or SELinux policies prevent reading protected APKs directly from /data/app/.\n• System apps or Knox-protected apps cannot be extracted without privileged rights.\n• You can use the manual APK import button below to pick an APK file already stored on your device or transferred via ADB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onManualImportFallback,
                            colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("error_manual_import_button")
                        ) {
                            Text("Import APK File Manually")
                        }

                        OutlinedButton(
                            onClick = onClearExtractionError,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("dismiss_extraction_error_button")
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Processing Loading Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AtpPrimaryBlue, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Extracting and Validating APK...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copying into isolated private workspace and running metadata scanner...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Main content depending on UI state
            when (val state = uiState) {
                is InstalledAppsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = AtpPrimaryBlue,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("loading_installed_apps_indicator")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Discovering installed packages...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is InstalledAppsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AtpErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Error Querying Packages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AtpErrorRed
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadInstalledApps() },
                                colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is InstalledAppsUiState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Applications Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try adjusting your search query or enabling 'All (Incl. System)' filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onManualImportFallback,
                                colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue)
                            ) {
                                Text("Import APK File Manually")
                            }
                        }
                    }
                }
                is InstalledAppsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("installed_apps_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.apps, key = { it.packageName }) { app ->
                            InstalledAppItemCard(
                                app = app,
                                onSelect = {
                                    if (app.isSplitApk) {
                                        pendingSplitApp = app
                                    } else {
                                        onSelectApp(app)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppItemCard(
    app: InstalledAppInfo,
    onSelect: () -> Unit
) {
    val iconBitmap = remember(app.iconBase64) {
        if (!app.iconBase64.isNullOrEmpty()) {
            try {
                val bytes = Base64.decode(app.iconBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installed_app_card_${app.packageName}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AtpPrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.appName,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = AtpPrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSystemApp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (app.isSplitApk) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AtpPrimaryBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Split (${app.splitCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = AtpPrimaryBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "v${app.versionName} • ${formatBytes(app.apkSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Select Action Button
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                modifier = Modifier.testTag("select_app_button_${app.packageName}")
            ) {
                Text("Select", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
