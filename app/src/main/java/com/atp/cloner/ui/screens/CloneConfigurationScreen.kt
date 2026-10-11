package com.atp.cloner.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atp.cloner.data.db.ProjectEntity
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.data.model.CloneConfiguration
import com.atp.cloner.engine.modification.CloneConfigurationValidator
import com.atp.cloner.ui.theme.AtpAccentRed
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.theme.AtpWarningAmber
import java.io.File

@Composable
fun CloneConfigurationScreen(
    project: ProjectEntity,
    discoveryReport: ApkMetadataDiscoveryReport?,
    config: CloneConfiguration,
    onUpdateConfig: (CloneConfiguration) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    onUpdateConfig(
                        config.copy(
                            changeLauncherIcon = true,
                            customIconBytes = bytes,
                            customIconReference = uri.toString()
                        )
                    )
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    val originalAppName = discoveryReport?.appNameDiscovery?.resolvedDefaultLabel ?: project.originalAppLabel
    val originalPackageId = discoveryReport?.packageDiscovery?.manifestPackage ?: project.originalPackageName

    val validation = remember(config, originalAppName, originalPackageId) {
        CloneConfigurationValidator.validate(config, originalAppName, originalPackageId)
    }

    val originalBitmap = remember(project.iconCachedPath) {
        project.iconCachedPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(path) else null
        }
    }

    val customBitmap = remember(config.customIconBytes) {
        config.customIconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("clone_configuration_screen")
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("config_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clone Configuration",
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
            // Card 1: Change App Name (matching mockup)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (config.changeAppName) AtpPrimaryBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AtpPrimaryBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Title, contentDescription = null, tint = AtpPrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Change App Name",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Switch(
                                checked = config.changeAppName,
                                onCheckedChange = { checked ->
                                    val name = if (config.newAppName.isBlank()) originalAppName else config.newAppName
                                    onUpdateConfig(config.copy(changeAppName = checked, newAppName = name))
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AtpPrimaryBlue),
                                modifier = Modifier.testTag("toggle_app_name")
                            )
                        }

                        if (config.changeAppName) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "New App Name",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = config.newAppName,
                                onValueChange = { onUpdateConfig(config.copy(newAppName = it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_new_app_name"),
                                placeholder = { Text("e.g. My Cloned App") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                trailingIcon = {
                                    if (config.newAppName != originalAppName) {
                                        IconButton(onClick = { onUpdateConfig(config.copy(newAppName = originalAppName)) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Reset to original")
                                        }
                                    }
                                }
                            )

                            if (validation.nameError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = validation.nameError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AtpAccentRed
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Change Package ID (matching mockup)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (config.changePackageId) AtpPrimaryBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AtpPrimaryBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = AtpPrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Change Package ID",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Switch(
                                checked = config.changePackageId,
                                onCheckedChange = { checked ->
                                    val pkg = if (config.newPackageId.isBlank()) "$originalPackageId.clone" else config.newPackageId
                                    onUpdateConfig(config.copy(changePackageId = checked, newPackageId = pkg))
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AtpPrimaryBlue),
                                modifier = Modifier.testTag("toggle_package_id")
                            )
                        }

                        if (config.changePackageId) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "New Package ID",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = config.newPackageId,
                                onValueChange = { onUpdateConfig(config.copy(newPackageId = it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_new_package_id"),
                                placeholder = { Text("e.g. com.mygreen.app") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                                trailingIcon = {
                                    if (config.newPackageId != "$originalPackageId.clone") {
                                        IconButton(onClick = { onUpdateConfig(config.copy(newPackageId = "$originalPackageId.clone")) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                                        }
                                    }
                                }
                            )

                            if (validation.packageError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = validation.packageError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AtpAccentRed
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Syntax valid: Android package name format verified.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AtpSuccessGreen
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: Change Launcher Icon (matching mockup)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (config.changeLauncherIcon) AtpPrimaryBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AtpPrimaryBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = AtpPrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Change Launcher Icon",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Switch(
                                checked = config.changeLauncherIcon,
                                onCheckedChange = { checked ->
                                    onUpdateConfig(config.copy(changeLauncherIcon = checked))
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AtpPrimaryBlue),
                                modifier = Modifier.testTag("toggle_launcher_icon")
                            )
                        }

                        if (config.changeLauncherIcon) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Choose Icon",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Thumbnail display
                                val displayBitmap = customBitmap ?: originalBitmap
                                if (displayBitmap != null) {
                                    Image(
                                        bitmap = displayBitmap.asImageBitmap(),
                                        contentDescription = "Selected Icon",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("select_icon_image_button")
                                ) {
                                    Text("Select Icon")
                                }

                                if (customBitmap != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = {
                                        onUpdateConfig(config.copy(customIconBytes = null, customIconReference = null))
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Restore original icon")
                                    }
                                }
                            }

                            if (validation.iconError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = validation.iconError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AtpAccentRed
                                )
                            }
                        }
                    }
                }
            }

            // Summary Info Notice
            item {
                if (validation.isNoOp) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AtpWarningAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No modifications requested yet. Enable at least one toggle above to prepare your clone workspace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Bottom Continue Button (matching mockup)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onContinue,
                    enabled = validation.isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AtpPrimaryBlue,
                        disabledContainerColor = AtpPrimaryBlue.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("config_continue_button")
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
