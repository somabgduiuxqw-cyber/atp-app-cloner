package com.atp.cloner.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atp.cloner.R
import com.atp.cloner.data.db.ProjectEntity
import com.atp.cloner.ui.theme.AtpAccentRed
import com.atp.cloner.ui.theme.AtpPrimaryBlue
import com.atp.cloner.ui.theme.AtpStrongBlue
import com.atp.cloner.ui.theme.AtpSuccessGreen
import com.atp.cloner.ui.viewmodel.Screen
import java.io.File

@Composable
fun HomeScreen(
    projectCount: Int,
    successCount: Int,
    failedCount: Int,
    recentProjects: List<ProjectEntity>,
    onNavigate: (Screen) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar Logo & Branding
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.atp_flexed_arm_logo_1791698056071),
                        contentDescription = "ATP App Cloner Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ATP App Cloner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "ATP Network • Clone • Customize • Rebuild",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AtpPrimaryBlue.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AtpPrimaryBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Pro",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AtpPrimaryBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Hero Card (Matching Mockup: "Clone Your Apps Your Way")
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AtpStrongBlue, AtpPrimaryBlue)
                            )
                        )
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clone Your Apps\nYour Way",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Modify name, package ID and icon of existing APK files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onNavigate(Screen.IMPORT) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = AtpStrongBlue
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("hero_import_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import APK", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onNavigate(Screen.INSTALLED_APPS) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("hero_installed_apps_button")
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Installed Apps", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Visual phone / cloning artwork
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Testing Action Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AtpSuccessGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AtpSuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Instant Test Sample",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Generate on-device compiled APK in 2s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLoadSample,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("home_quick_sample_button")
                    ) {
                        Text("Test Sample")
                    }
                }
            }
        }

        // Recent Projects Section Header with "View all >"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Projects",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { onNavigate(Screen.PROJECTS) },
                    modifier = Modifier.testTag("view_all_projects_button")
                ) {
                    Text(
                        text = "View all >",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (recentProjects.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Projects Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import an APK or click 'Test Sample' to begin customizing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentProjects.take(6)) { project ->
                ProjectItemCard(
                    project = project,
                    onClick = { onSelectProject(project) }
                )
            }
        }
    }
}

@Composable
fun ProjectItemCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cachedBitmap = remember(project.iconCachedPath) {
        project.iconCachedPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(path) else null
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cachedBitmap != null) {
                Image(
                    bitmap = cachedBitmap.asImageBitmap(),
                    contentDescription = project.projectName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.projectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${project.originalPackageName} • ${(project.originalFileSize / (1024 * 1024)).coerceAtLeast(1)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Status: ${project.lastStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
