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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atp.cloner.engine.modification.ModificationStatus
import com.atp.cloner.ui.theme.AtpAccentRed
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen

@Composable
fun ProcessingStatusScreen(
    status: ModificationStatus?,
    onViewReport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressStatus = status as? ModificationStatus.Progress
    val isSuccess = status is ModificationStatus.Success
    val isFailed = status is ModificationStatus.Failed

    val currentPercent = progressStatus?.progressPercent ?: if (isSuccess) 100 else 0
    val currentStage = progressStatus?.stageName ?: if (isSuccess) "Modification Workspace Prepared" else if (isFailed) "Modification Failed" else "Initializing..."
    val currentDetails = progressStatus?.details ?: if (isSuccess) "Working APK workspace ready for Phase 4 signing & rebuilding." else (status as? ModificationStatus.Failed)?.error ?: "Preparing modification pipeline..."

    val allStages = listOf(
        "Validating Configuration",
        "Preparing Isolated Workspace",
        "Transforming Binary AndroidManifest",
        "Modifying Compiled Resource Table",
        "Generating Launcher Icon Assets",
        "Repackaging Modified Workspace",
        "Validating Modified Structures"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("processing_status_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Big Status Indicator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSuccess) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(AtpSuccessGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AtpSuccessGreen, modifier = Modifier.size(36.dp))
                    }
                } else if (isFailed) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(AtpAccentRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = AtpAccentRed, modifier = Modifier.size(36.dp))
                    }
                } else {
                    CircularProgressIndicator(
                        progress = { currentPercent / 100f },
                        modifier = Modifier.size(68.dp).testTag("modification_progress_spinner"),
                        color = AtpPrimaryBlue,
                        strokeWidth = 6.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = currentStage,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { currentPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isSuccess) AtpSuccessGreen else if (isFailed) AtpAccentRed else AtpPrimaryBlue
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$currentPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Real Stage Tracker
        Text(
            text = "Pipeline Stages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allStages.size) { index ->
                val stageName = allStages[index]
                val currentStep = progressStatus?.stepNumber ?: if (isSuccess) 8 else 0
                val isCompleted = index + 1 < currentStep || isSuccess
                val isCurrent = index + 1 == currentStep && !isSuccess

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCompleted) AtpSuccessGreen
                                else if (isCurrent) AtpPrimaryBlue
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) Color.White else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCompleted || isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Actions at the bottom
        if (isSuccess) {
            Button(
                onClick = onViewReport,
                colors = ButtonDefaults.buttonColors(containerColor = AtpPrimaryBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("view_modification_report_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Modification Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_modification_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel & Rollback")
            }
        }
    }
}
