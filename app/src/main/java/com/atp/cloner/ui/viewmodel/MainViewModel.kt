package com.atp.cloner.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atp.cloner.data.db.ProcessingLogEntity
import com.atp.cloner.data.db.ProjectEntity
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.data.model.CloneConfiguration
import com.atp.cloner.data.preferences.ThemePreferencesRepository
import com.atp.cloner.engine.modification.ApkModificationCoordinator
import com.atp.cloner.engine.modification.ApkModificationPlan
import com.atp.cloner.engine.modification.ApkModificationPlanner
import com.atp.cloner.engine.modification.ApkModificationReport
import com.atp.cloner.engine.modification.CloneConfigurationValidator
import com.atp.cloner.engine.modification.ModificationStatus
import com.atp.cloner.repository.ProjectRepository
import com.atp.cloner.ui.theme.AppThemeMode
import com.atp.cloner.engine.build.ApkBuildCoordinator
import com.atp.cloner.engine.build.ApkBuildReport
import com.atp.cloner.engine.build.BuildStatus
import com.atp.cloner.ui.theme.DarkThemeVariant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    HOME,
    IMPORT,
    APK_DETAILS,
    DISCOVERY,
    CLONE_CONFIG,
    MODIFICATION_REVIEW,
    PROCESSING_STATUS,
    MODIFICATION_REPORT,
    BUILDING_STATUS,
    BUILD_REPORT,
    PROJECTS,
    PROJECT_DETAILS,
    COMPATIBILITY_REPORT,
    HISTORY,
    SETTINGS,
    SIGNING_PREP,
    ABOUT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val themePrefs = ThemePreferencesRepository(application)
    private val modificationCoordinator = ApkModificationCoordinator(application)
    private val buildCoordinator = ApkBuildCoordinator(application)

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = themePrefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM)

    val darkVariant: StateFlow<DarkThemeVariant> = themePrefs.darkVariantFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkThemeVariant.DARK_BLUE)

    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectCount: StateFlow<Int> = repository.projectCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val successCount: StateFlow<Int> = repository.successCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _discoveryReport = MutableStateFlow<ApkMetadataDiscoveryReport?>(null)
    val discoveryReport: StateFlow<ApkMetadataDiscoveryReport?> = _discoveryReport.asStateFlow()

    private val _cloneConfig = MutableStateFlow(CloneConfiguration())
    val cloneConfig: StateFlow<CloneConfiguration> = _cloneConfig.asStateFlow()

    private val _modificationPlan = MutableStateFlow<ApkModificationPlan?>(null)
    val modificationPlan: StateFlow<ApkModificationPlan?> = _modificationPlan.asStateFlow()

    private val _modificationReport = MutableStateFlow<ApkModificationReport?>(null)
    val modificationReport: StateFlow<ApkModificationReport?> = _modificationReport.asStateFlow()

    private val _modificationStatus = MutableStateFlow<ModificationStatus?>(null)
    val modificationStatus: StateFlow<ModificationStatus?> = _modificationStatus.asStateFlow()

    private val _buildStatus = MutableStateFlow<BuildStatus?>(null)
    val buildStatus: StateFlow<BuildStatus?> = _buildStatus.asStateFlow()

    private val _buildReport = MutableStateFlow<ApkBuildReport?>(null)
    val buildReport: StateFlow<ApkBuildReport?> = _buildReport.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedDiscoveryTab = MutableStateFlow(0)
    val selectedDiscoveryTab: StateFlow<Int> = _selectedDiscoveryTab.asStateFlow()

    private var modificationJob: Job? = null

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectDiscoveryTab(index: Int) {
        _selectedDiscoveryTab.value = index
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { themePrefs.setThemeMode(mode) }
    }

    fun setDarkVariant(variant: DarkThemeVariant) {
        viewModelScope.launch { themePrefs.setDarkVariant(variant) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun importApk(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            try {
                val project = repository.importApkFromUri(uri, fileName)
                _activeProject.value = project
                val report = repository.getDiscoveryReport(project.id)
                _discoveryReport.value = report

                // Initialize draft clone config
                _cloneConfig.value = CloneConfiguration(
                    changeAppName = false,
                    newAppName = report?.appNameDiscovery?.resolvedDefaultLabel ?: "",
                    changePackageId = false,
                    newPackageId = (report?.packageDiscovery?.manifestPackage ?: "com.example") + ".clone",
                    changeLauncherIcon = false
                )
                _currentScreen.value = Screen.APK_DETAILS
            } catch (e: Exception) {
                _errorMessage.value = "Failed to import APK: ${e.localizedMessage ?: e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadSampleApk() {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            try {
                val project = repository.createAndImportSampleApk()
                _activeProject.value = project
                val report = repository.getDiscoveryReport(project.id)
                _discoveryReport.value = report

                _cloneConfig.value = CloneConfiguration(
                    changeAppName = false,
                    newAppName = (report?.appNameDiscovery?.resolvedDefaultLabel ?: "Sample App") + " Cloned",
                    changePackageId = false,
                    newPackageId = (report?.packageDiscovery?.manifestPackage ?: "com.atp.sampleapp") + ".clone",
                    changeLauncherIcon = false
                )
                _currentScreen.value = Screen.APK_DETAILS
            } catch (e: Exception) {
                _errorMessage.value = "Failed to generate test APK: ${e.localizedMessage ?: e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun selectProject(project: ProjectEntity) {
        _activeProject.value = project
        viewModelScope.launch {
            _isProcessing.value = true
            val report = repository.getDiscoveryReport(project.id)
            _discoveryReport.value = report

            _cloneConfig.value = CloneConfiguration(
                changeAppName = project.enableNameChange,
                newAppName = project.modifiedAppName.ifEmpty { project.originalAppLabel },
                changePackageId = project.enablePackageChange,
                newPackageId = project.modifiedPackageName.ifEmpty { "${project.originalPackageName}.clone" },
                changeLauncherIcon = project.enableIconChange
            )
            _isProcessing.value = false
            _currentScreen.value = Screen.APK_DETAILS
        }
    }

    fun updateCloneConfig(newConfig: CloneConfiguration) {
        _cloneConfig.value = newConfig
    }

    fun prepareModificationReview() {
        val report = _discoveryReport.value ?: return
        val config = _cloneConfig.value
        val plan = ApkModificationPlanner.createPlan(config, report)
        _modificationPlan.value = plan
        _currentScreen.value = Screen.MODIFICATION_REVIEW
    }

    fun startModification() {
        val project = _activeProject.value ?: return
        val report = _discoveryReport.value ?: return
        val config = _cloneConfig.value

        _currentScreen.value = Screen.PROCESSING_STATUS
        _isProcessing.value = true

        modificationJob?.cancel()
        modificationJob = viewModelScope.launch {
            val originalFile = File(project.originalFilePath)
            modificationCoordinator.applyModifications(project.id, originalFile, config, report)
                .collect { status ->
                    _modificationStatus.value = status
                    when (status) {
                        is ModificationStatus.Success -> {
                            _modificationReport.value = status.report
                            _isProcessing.value = false

                            // Update project in DB
                            val updatedProject = project.copy(
                                enableNameChange = config.changeAppName,
                                modifiedAppName = config.newAppName,
                                enablePackageChange = config.changePackageId,
                                modifiedPackageName = config.newPackageId,
                                enableIconChange = config.changeLauncherIcon,
                                lastStatus = "Modified",
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.updateProject(updatedProject)
                            _activeProject.value = updatedProject
                        }
                        is ModificationStatus.Failed -> {
                            _isProcessing.value = false
                            _errorMessage.value = status.error
                        }
                        is ModificationStatus.Progress -> {
                            // in progress
                        }
                    }
                }
        }
    }

    fun cancelModification() {
        modificationJob?.cancel()
        _isProcessing.value = false
        _activeProject.value?.let { modificationCoordinator.rollback(it.id) }
        _currentScreen.value = Screen.CLONE_CONFIG
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = null
                _discoveryReport.value = null
                _currentScreen.value = Screen.HOME
            }
        }
    }

    private var buildJob: Job? = null

    fun startRebuild() {
        val project = _activeProject.value ?: return
        val config = _cloneConfig.value
        val report = _discoveryReport.value

        _currentScreen.value = Screen.BUILDING_STATUS
        _isProcessing.value = true

        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            val targetPkg = if (config.changePackageId) config.newPackageId.trim() else project.originalPackageName
            val targetName = if (config.changeAppName) config.newAppName.trim() else project.originalAppLabel

            buildCoordinator.rebuildAndSignApk(
                projectId = project.id,
                originalApkName = project.originalFileName,
                originalApkChecksum = project.originalSha256,
                targetPackageId = targetPkg,
                targetAppName = targetName,
                hasCustomIcon = config.changeLauncherIcon
            ).collect { status ->
                _buildStatus.value = status
                when (status) {
                    is BuildStatus.Success -> {
                        _buildReport.value = status.report
                        _isProcessing.value = false

                        // Update project entity in DB
                        val updated = project.copy(
                            outputApkPath = status.report.rebuiltApkPath,
                            outputSha256 = status.report.rebuiltApkChecksum,
                            outputFileSize = status.report.rebuiltFileSize,
                            lastStatus = "Rebuilt & Signed",
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.updateProject(updated)
                        _activeProject.value = updated
                    }
                    is BuildStatus.Failed -> {
                        _isProcessing.value = false
                        _errorMessage.value = status.error
                    }
                    is BuildStatus.Progress -> {
                        // in progress
                    }
                }
            }
        }
    }

    fun cancelRebuild() {
        buildJob?.cancel()
        _isProcessing.value = false
        _currentScreen.value = Screen.MODIFICATION_REPORT
    }

    fun installApk(context: Context, apkPath: String) {
        try {
            val file = File(apkPath)
            if (!file.exists()) {
                _errorMessage.value = "Cannot install: APK file not found at $apkPath"
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to open APK installer: ${e.message}"
        }
    }

    fun shareApk(context: Context, apkPath: String) {
        try {
            val file = File(apkPath)
            if (!file.exists()) {
                _errorMessage.value = "Cannot export: APK file not found at $apkPath"
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(shareIntent, "Export Cloned APK").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to export APK: ${e.message}"
        }
    }

    fun duplicateProject(project: ProjectEntity) {
        viewModelScope.launch {
            val duplicate = project.copy(
                id = java.util.UUID.randomUUID().toString(),
                projectName = "${project.projectName} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProject(duplicate) // insert via DAO
        }
    }
}
