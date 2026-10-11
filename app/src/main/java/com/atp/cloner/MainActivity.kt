package com.atp.cloner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.atp.cloner.ui.screens.AboutScreen
import com.atp.cloner.ui.screens.ApkDetailsScreen
import com.atp.cloner.ui.screens.BuildReportScreen
import com.atp.cloner.ui.screens.BuildStatusScreen
import com.atp.cloner.ui.screens.CloneConfigurationScreen
import com.atp.cloner.ui.screens.CompatibilityReportScreen
import com.atp.cloner.ui.screens.DiscoveryScreen
import com.atp.cloner.ui.screens.HistoryScreen
import com.atp.cloner.ui.screens.HomeScreen
import com.atp.cloner.ui.screens.ImportScreen
import com.atp.cloner.ui.screens.ModificationReportScreen
import com.atp.cloner.ui.screens.ModificationReviewScreen
import com.atp.cloner.ui.screens.ProcessingStatusScreen
import com.atp.cloner.ui.screens.ProjectsScreen
import com.atp.cloner.ui.screens.SettingsScreen
import com.atp.cloner.ui.screens.SigningPrepScreen
import com.atp.cloner.ui.theme.AtpClonerTheme
import com.atp.cloner.ui.viewmodel.MainViewModel
import com.atp.cloner.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkVariant by viewModel.darkVariant.collectAsState()

            AtpClonerTheme(themeMode = themeMode, darkVariant = darkVariant) {
                MainAppContent(viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val darkVariant by viewModel.darkVariant.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val projectCount by viewModel.projectCount.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val discoveryReport by viewModel.discoveryReport.collectAsState()
    val cloneConfig by viewModel.cloneConfig.collectAsState()
    val modificationPlan by viewModel.modificationPlan.collectAsState()
    val modificationReport by viewModel.modificationReport.collectAsState()
    val modificationStatus by viewModel.modificationStatus.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val buildReport by viewModel.buildReport.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedDiscoveryTab by viewModel.selectedDiscoveryTab.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = currentScreen != Screen.HOME) {
        when (currentScreen) {
            Screen.BUILDING_STATUS -> viewModel.cancelRebuild()
            Screen.BUILD_REPORT -> viewModel.navigateTo(Screen.HOME)
            Screen.MODIFICATION_REVIEW -> viewModel.navigateTo(Screen.CLONE_CONFIG)
            Screen.CLONE_CONFIG -> viewModel.navigateTo(Screen.APK_DETAILS)
            Screen.DISCOVERY -> viewModel.navigateTo(Screen.APK_DETAILS)
            Screen.APK_DETAILS -> viewModel.navigateTo(Screen.HOME)
            Screen.COMPATIBILITY_REPORT -> viewModel.navigateTo(Screen.APK_DETAILS)
            Screen.MODIFICATION_REPORT -> viewModel.navigateTo(Screen.HOME)
            Screen.HISTORY, Screen.SIGNING_PREP, Screen.ABOUT -> viewModel.navigateTo(Screen.SETTINGS)
            else -> viewModel.navigateTo(Screen.HOME)
        }
    }

    val showBottomBar = currentScreen == Screen.HOME || currentScreen == Screen.PROJECTS || currentScreen == Screen.SETTINGS

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.HOME,
                        onClick = { viewModel.navigateTo(Screen.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.PROJECTS,
                        onClick = { viewModel.navigateTo(Screen.PROJECTS) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
                        label = { Text("Projects") },
                        modifier = Modifier.testTag("nav_item_projects")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.SETTINGS,
                        onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.HOME -> {
                HomeScreen(
                    projectCount = projectCount,
                    successCount = successCount,
                    failedCount = failedCount,
                    recentProjects = allProjects,
                    onNavigate = { viewModel.navigateTo(it) },
                    onSelectProject = { viewModel.selectProject(it) },
                    onLoadSample = { viewModel.loadSampleApk() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.IMPORT -> {
                ImportScreen(
                    isProcessing = isProcessing,
                    errorMessage = errorMessage,
                    onImportFile = { uri, name -> viewModel.importApk(uri, name) },
                    onLoadSample = { viewModel.loadSampleApk() },
                    onBack = { viewModel.navigateTo(Screen.HOME) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.APK_DETAILS -> {
                if (activeProject != null) {
                    ApkDetailsScreen(
                        project = activeProject!!,
                        discoveryReport = discoveryReport,
                        onNavigate = { viewModel.navigateTo(it) },
                        onBack = { viewModel.navigateTo(Screen.HOME) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    viewModel.navigateTo(Screen.HOME)
                }
            }
            Screen.DISCOVERY -> {
                if (activeProject != null) {
                    DiscoveryScreen(
                        project = activeProject!!,
                        report = discoveryReport,
                        selectedTab = selectedDiscoveryTab,
                        onSelectTab = { viewModel.selectDiscoveryTab(it) },
                        onBack = { viewModel.navigateTo(Screen.APK_DETAILS) },
                        onNavigateToConfig = { viewModel.navigateTo(Screen.CLONE_CONFIG) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    viewModel.navigateTo(Screen.HOME)
                }
            }
            Screen.CLONE_CONFIG -> {
                if (activeProject != null) {
                    CloneConfigurationScreen(
                        project = activeProject!!,
                        discoveryReport = discoveryReport,
                        config = cloneConfig,
                        onUpdateConfig = { viewModel.updateCloneConfig(it) },
                        onContinue = { viewModel.prepareModificationReview() },
                        onBack = { viewModel.navigateTo(Screen.APK_DETAILS) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    viewModel.navigateTo(Screen.HOME)
                }
            }
            Screen.MODIFICATION_REVIEW -> {
                ModificationReviewScreen(
                    plan = modificationPlan,
                    onApplyModifications = { viewModel.startModification() },
                    onCancel = { viewModel.navigateTo(Screen.CLONE_CONFIG) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.PROCESSING_STATUS -> {
                ProcessingStatusScreen(
                    status = modificationStatus,
                    onViewReport = { viewModel.navigateTo(Screen.MODIFICATION_REPORT) },
                    onCancel = { viewModel.cancelModification() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.MODIFICATION_REPORT -> {
                ModificationReportScreen(
                    report = modificationReport,
                    onStartRebuild = { viewModel.startRebuild() },
                    onNavigateToHome = { viewModel.navigateTo(Screen.HOME) },
                    onNavigateToProjects = { viewModel.navigateTo(Screen.PROJECTS) },
                    onBack = { viewModel.navigateTo(Screen.HOME) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.BUILDING_STATUS -> {
                BuildStatusScreen(
                    status = buildStatus,
                    onViewReport = { viewModel.navigateTo(Screen.BUILD_REPORT) },
                    onCancel = { viewModel.cancelRebuild() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.BUILD_REPORT -> {
                BuildReportScreen(
                    report = buildReport,
                    onInstallApk = { apkPath -> viewModel.installApk(context, apkPath) },
                    onShareApk = { apkPath -> viewModel.shareApk(context, apkPath) },
                    onNavigateToHome = { viewModel.navigateTo(Screen.HOME) },
                    onNavigateToProjects = { viewModel.navigateTo(Screen.PROJECTS) },
                    onBack = { viewModel.navigateTo(Screen.HOME) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.PROJECTS -> {
                ProjectsScreen(
                    projects = allProjects,
                    onSelectProject = { viewModel.selectProject(it) },
                    onDeleteProject = { viewModel.deleteProject(it) },
                    onNavigate = { viewModel.navigateTo(it) },
                    onBack = { viewModel.navigateTo(Screen.HOME) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.PROJECT_DETAILS -> {
                if (activeProject != null) {
                    ApkDetailsScreen(
                        project = activeProject!!,
                        discoveryReport = discoveryReport,
                        onNavigate = { viewModel.navigateTo(it) },
                        onBack = { viewModel.navigateTo(Screen.PROJECTS) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    viewModel.navigateTo(Screen.PROJECTS)
                }
            }
            Screen.COMPATIBILITY_REPORT -> {
                CompatibilityReportScreen(
                    discoveryReport = discoveryReport,
                    onBack = { viewModel.navigateTo(Screen.APK_DETAILS) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.HISTORY -> {
                HistoryScreen(
                    projects = allProjects,
                    onBack = { viewModel.navigateTo(Screen.SETTINGS) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.SIGNING_PREP -> {
                SigningPrepScreen(
                    onBack = { viewModel.navigateTo(Screen.SETTINGS) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.ABOUT -> {
                AboutScreen(
                    onBack = { viewModel.navigateTo(Screen.SETTINGS) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    themeMode = themeMode,
                    darkVariant = darkVariant,
                    onSetThemeMode = { viewModel.setThemeMode(it) },
                    onSetDarkVariant = { viewModel.setDarkVariant(it) },
                    onNavigate = { viewModel.navigateTo(it) },
                    onBack = { viewModel.navigateTo(Screen.HOME) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
