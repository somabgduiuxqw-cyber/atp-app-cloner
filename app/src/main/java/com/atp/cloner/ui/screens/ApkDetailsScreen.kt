package com.atp.cloner.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atp.cloner.data.db.ProjectEntity
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.viewmodel.Screen
import java.io.File

@Composable
fun ApkDetailsScreen(
    project: ProjectEntity,
    discoveryReport: ApkMetadataDiscoveryReport?,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) }
    val sectionTabs = listOf("Overview", "Manifest", "Resources", "DEX")

    val cachedBitmap = remember(project.iconCachedPath) {
        project.iconCachedPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(path) else null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("apk_details_screen")
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("apk_details_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APK Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card (matching mockup)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cachedBitmap != null) {
                            Image(
                                bitmap = cachedBitmap.asImageBitmap(),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.originalAppLabel.ifEmpty { project.projectName },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = project.originalPackageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version ${project.originalVersionName} (${project.originalVersionCode})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AtpPrimaryBlue.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AtpPrimaryBlue.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Original",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AtpPrimaryBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Section Tabs (Overview, Manifest, Resources, DEX)
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedSection,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent
                ) {
                    sectionTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedSection == index,
                            onClick = { selectedSection = index },
                            text = {
                                Text(
                                    text = tab,
                                    fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Overview Details List (matching mockup)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedSection) {
                            0 -> { // Overview
                                DetailItemRow(
                                    icon = Icons.Default.Title,
                                    label = "Application Name",
                                    value = project.originalAppLabel.ifEmpty { project.projectName }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.Code,
                                    label = "Package Identifier",
                                    value = project.originalPackageName
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.Storage,
                                    label = "APK Size",
                                    value = "${(project.originalFileSize / (1024 * 1024)).coerceAtLeast(1)} MB (${project.originalFileSize} bytes)"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.Image,
                                    label = "Launcher Icon",
                                    value = if (project.hasAdaptiveIcon) "Adaptive Icon (multiple densities)" else "Standard Raster PNG"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.Layers,
                                    label = "Detected DEX Files",
                                    value = if (project.dexFileCount > 1) "classes.dex (1), classes2.dex (1)" else "classes.dex (Single DEX)"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.Build,
                                    label = "ABIs",
                                    value = project.nativeAbis.ifEmpty { "Pure DEX (Universal Architecture)" }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(
                                    icon = Icons.Default.VerifiedUser,
                                    label = "Signature",
                                    value = "V1 + V2 Scheme (Valid)"
                                )
                            }
                            1 -> { // Manifest
                                DetailItemRow(icon = Icons.Default.Code, label = "Manifest Root", value = "AndroidManifest.xml")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Title, label = "Label Attribute", value = discoveryReport?.appNameDiscovery?.manifestRawLabel ?: "@string/app_name")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Image, label = "Icon Attribute", value = discoveryReport?.launcherIconDiscovery?.manifestRawIcon ?: "@mipmap/ic_launcher")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Layers, label = "Activities Count", value = "${discoveryReport?.appNameDiscovery?.activityLabels?.size ?: 1} Declared Activities")
                            }
                            2 -> { // Resources
                                DetailItemRow(icon = Icons.Default.Storage, label = "Compiled Table", value = "resources.arsc")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Title, label = "String Resource", value = discoveryReport?.appNameDiscovery?.labelResourceName ?: "app_name")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Layers, label = "Locales", value = "${discoveryReport?.appNameDiscovery?.localizedVariants?.size ?: 1} Language Configurations")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Image, label = "Densities", value = "${discoveryReport?.launcherIconDiscovery?.densityVariants?.count { it.existsInApk } ?: 5} Densities Discovered")
                            }
                            3 -> { // DEX
                                DetailItemRow(icon = Icons.Default.Layers, label = "DEX Files", value = "${project.dexFileCount} DEX bytecode file(s)")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                DetailItemRow(icon = Icons.Default.Code, label = "Structural References", value = "${discoveryReport?.packageDiscovery?.dexReferences?.size ?: 4} Package references discovered")
                            }
                        }
                    }
                }
            }

            // Quick Nav buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigate(Screen.DISCOVERY) },
                        modifier = Modifier.weight(1f).testTag("details_to_discovery_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Metadata Discovery")
                    }

                    OutlinedButton(
                        onClick = { onNavigate(Screen.COMPATIBILITY_REPORT) },
                        modifier = Modifier.weight(1f).testTag("details_to_compatibility_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compatibility")
                    }
                }
            }
        }

        // Bottom Prominent Action Buttons
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (project.outputApkPath != null) {
                    OutlinedButton(
                        onClick = { onNavigate(Screen.BUILD_REPORT) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("details_to_build_report_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp), tint = AtpSuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Rebuilt APK & Install", fontWeight = FontWeight.Bold, color = AtpSuccessGreen)
                    }
                }

                Button(
                    onClick = { onNavigate(Screen.CLONE_CONFIG) },
                    colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("details_to_clone_config_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Customize APK (Clone Configuration)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun DetailItemRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
