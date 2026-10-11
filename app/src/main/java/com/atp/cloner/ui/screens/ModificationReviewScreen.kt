package com.atp.cloner.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atp.cloner.engine.modification.ApkModificationPlan
import com.atp.cloner.ui.theme.AtpAccentRed
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.theme.AtpWarningAmber

@Composable
fun ModificationReviewScreen(
    plan: ApkModificationPlan?,
    onApplyModifications: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (plan == null) return

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("modification_review_screen")
    ) {
        // Top Bar
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
                IconButton(onClick = onCancel, modifier = Modifier.testTag("review_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Modification Review",
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
            // Before & After Comparison Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "BEFORE & AFTER COMPARISON",
                            style = MaterialTheme.typography.labelSmall,
                            color = AtpPrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // App Name Row
                        ComparisonRow(
                            label = "Application Name",
                            originalValue = plan.originalAppName,
                            requestedValue = if (plan.changeAppName) plan.targetAppName else "(Unchanged)",
                            isModified = plan.changeAppName
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Package ID Row
                        ComparisonRow(
                            label = "Package Identifier",
                            originalValue = plan.originalPackageId,
                            requestedValue = if (plan.changePackageId) plan.targetPackageId else "(Unchanged)",
                            isModified = plan.changePackageId,
                            isMonospace = true
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Launcher Icon Row
                        ComparisonRow(
                            label = "Launcher Icon",
                            originalValue = "Original APK Icon",
                            requestedValue = if (plan.changeLauncherIcon) "Custom Icon (5 densities generated)" else "(Unchanged)",
                            isModified = plan.changeLauncherIcon
                        )
                    }
                }
            }

            // Planned Execution Stages Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "PLANNED MODIFICATION OPERATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = AtpPrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val allPlanned = plan.plannedManifestChanges + plan.plannedResourceChanges + plan.plannedIconReplacements
                        if (allPlanned.isEmpty()) {
                            Text("No modifications scheduled.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            allPlanned.forEach { action ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AtpSuccessGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = action, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Safety & Offline Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AtpPrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Workspace Isolation Guarantee",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All modifications are staged inside an isolated working workspace. The original imported APK file is never altered or replaced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom Actions
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onApplyModifications,
                    colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("apply_modifications_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ComparisonRow(
    label: String,
    originalValue: String,
    requestedValue: String,
    isModified: Boolean,
    isMonospace: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isModified) AtpPrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (isModified) "MODIFIED" else "UNCHANGED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isModified) AtpPrimaryBlue else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Original: $originalValue",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Target: $requestedValue",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isModified) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = if (isModified) AtpPrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
