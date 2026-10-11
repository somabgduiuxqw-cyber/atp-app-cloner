package com.atp.cloner.engine

import android.content.Context
import com.atp.cloner.data.model.ActivityIconDiscovery
import com.atp.cloner.data.model.ActivityLabelDiscovery
import com.atp.cloner.data.model.ApkMetadataDiscoveryReport
import com.atp.cloner.data.model.AppNameDiscoveryReport
import com.atp.cloner.data.model.AssetMatchDiscovery
import com.atp.cloner.data.model.CompatibilityRisk
import com.atp.cloner.data.model.ComponentClassDiscovery
import com.atp.cloner.data.model.ConfidenceLevel
import com.atp.cloner.data.model.DexReferenceDiscovery
import com.atp.cloner.data.model.LauncherIconDiscoveryReport
import com.atp.cloner.data.model.ModificationRisk
import com.atp.cloner.data.model.ModificationTarget
import com.atp.cloner.data.model.NativeLibDiscovery
import com.atp.cloner.data.model.PackageReferenceDiscoveryReport
import com.atp.cloner.data.model.ProviderAuthorityDiscovery
import com.atp.cloner.data.model.ResourceGraphEdge
import com.atp.cloner.data.model.ResourceGraphNode
import com.atp.cloner.data.model.ResourceReferenceGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkMetadataDiscoveryEngine(private val context: Context) {

    private val aXmlDecoder = AXmlDecoder()
    private val arscDecoder = ArscDecoder()
    private val dexParser = DexDiscoveryParser()
    private val iconProcessor = IconDiscoveryProcessor()

    suspend fun discoverMetadata(
        apkFile: File,
        projectId: String
    ): ApkMetadataDiscoveryReport = withContext(Dispatchers.IO) {
        val scanLogs = mutableListOf<String>()
        scanLogs.add("Starting deep APK metadata discovery on ${apkFile.name} (${apkFile.length()} bytes)")

        val sha256 = calculateSha256(apkFile)
        scanLogs.add("Verified source APK SHA-256: $sha256")

        var manifestData = AXmlDecoder.ManifestData()
        var arscData = ArscDecoder.ArscData()
        val dexReferences = mutableListOf<DexReferenceDiscovery>()
        val assetMatches = mutableListOf<AssetMatchDiscovery>()
        val nativeLibs = mutableListOf<NativeLibDiscovery>()

        val graphNodes = mutableListOf<ResourceGraphNode>()
        val graphEdges = mutableListOf<ResourceGraphEdge>()

        // Open APK Archive
        ZipFile(apkFile).use { zip ->
            // 1. Inspect & Decode AndroidManifest.xml
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            if (manifestEntry != null) {
                scanLogs.add("Found binary AndroidManifest.xml (${manifestEntry.size} bytes). Decoding chunks...")
                zip.getInputStream(manifestEntry).use { input ->
                    val manifestBytes = input.readBytes()
                    manifestData = aXmlDecoder.decodeManifest(manifestBytes)
                    scanLogs.add("Decoded manifest package='${manifestData.packageName}', raw label='${manifestData.appLabel}', icon='${manifestData.appIcon}'")
                }
            } else {
                scanLogs.add("WARNING: AndroidManifest.xml not found in archive root")
            }

            // 2. Inspect & Decode resources.arsc
            val arscEntry = zip.getEntry("resources.arsc")
            if (arscEntry != null) {
                scanLogs.add("Found compiled resources.arsc (${arscEntry.size} bytes). Decoding global string pool...")
                zip.getInputStream(arscEntry).use { input ->
                    val arscBytes = input.readBytes()
                    arscData = arscDecoder.decodeArsc(arscBytes)
                    scanLogs.add("Decoded ${arscData.globalStrings.size} global strings, ${arscData.iconPaths.size} icon paths, ${arscData.localizedAppNames.size} localized variants")
                }
            } else {
                scanLogs.add("NOTICE: resources.arsc not found (uncompiled or raw assets only)")
            }

            // 3. Scan DEX Bytecode files (multidex support)
            var dexIndex = 1
            while (true) {
                val dexName = if (dexIndex == 1) "classes.dex" else "classes$dexIndex.dex"
                val dexEntry = zip.getEntry(dexName) ?: break
                scanLogs.add("Scanning DEX file: $dexName (${dexEntry.size} bytes)...")

                zip.getInputStream(dexEntry).use { input ->
                    val dexBytes = input.readBytes()
                    val result = dexParser.scanDex(dexBytes, dexName, manifestData.packageName)
                    dexReferences.addAll(result.references)
                    scanLogs.add("Discovered ${result.references.size} package references in $dexName (${result.totalClassesCount} classes defined)")
                }
                dexIndex++
            }

            // 4. Scan Text-based Assets (JSON, XML, properties)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.name.startsWith("assets/") && !e.isDirectory && e.size < 500_000) {
                    val ext = e.name.substringAfterLast(".", "")
                    if (ext in listOf("json", "xml", "properties", "txt", "conf")) {
                        try {
                            zip.getInputStream(e).use { stream ->
                                val text = String(stream.readBytes(), Charsets.UTF_8)
                                if (text.contains(manifestData.packageName)) {
                                    val snippet = text.lines()
                                        .firstOrNull { it.contains(manifestData.packageName) }
                                        ?.take(100) ?: ""
                                    assetMatches.add(
                                        AssetMatchDiscovery(
                                            assetPath = e.name,
                                            fileType = ext.uppercase(),
                                            matchedString = manifestData.packageName,
                                            contextSnippet = snippet,
                                            confidence = ConfidenceLevel.POTENTIAL_STRING_MATCH
                                        )
                                    )
                                }
                            }
                        } catch (ex: Exception) {
                            // ignore asset decode errors
                        }
                    }
                }

                // 5. Native Libraries
                if (e.name.startsWith("lib/") && e.name.endsWith(".so")) {
                    val parts = e.name.split("/")
                    if (parts.size >= 3) {
                        val abi = parts[1]
                        val libName = parts[2]
                        nativeLibs.add(NativeLibDiscovery(abi, libName, e.size))
                    }
                }
            }

            // 6. Launcher Icon Discovery & Dependencies
            val iconResult = iconProcessor.discoverIcons(zip, manifestData.appIcon, manifestData.appRoundIcon)
            scanLogs.add("Resolved launcher icon type: ${iconResult.resolvedType}. Density variants: ${iconResult.densityVariants.count { it.existsInApk }}/${iconResult.densityVariants.size}")

            // 7. Resolve Application Name & String References
            val resolvedAppName = resolveApplicationName(manifestData, arscData)
            scanLogs.add("Resolved displayed application name: '$resolvedAppName'")

            // 8. Build Resource Reference Graph
            buildGraph(manifestData, resolvedAppName, iconResult, graphNodes, graphEdges)

            // 9. Synthesize Reports
            val appNameReport = buildAppNameReport(manifestData, arscData, resolvedAppName)
            val launcherIconReport = buildLauncherIconReport(manifestData, iconResult)
            val packageReport = buildPackageReport(manifestData, dexReferences, assetMatches, nativeLibs)

            return@withContext ApkMetadataDiscoveryReport(
                projectId = projectId,
                originalApkChecksum = sha256,
                originalFileName = apkFile.name,
                originalFileSize = apkFile.length(),
                scanTimestamp = System.currentTimeMillis(),
                appNameDiscovery = appNameReport,
                launcherIconDiscovery = launcherIconReport,
                packageDiscovery = packageReport,
                resourceGraph = ResourceReferenceGraph(graphNodes, graphEdges),
                scanLogs = scanLogs,
                completenessConfidence = if (nativeLibs.isNotEmpty()) 0.88f else 0.96f
            )
        }
    }

    private fun resolveApplicationName(
        manifest: AXmlDecoder.ManifestData,
        arsc: ArscDecoder.ArscData
    ): String {
        // If manifest label is a non-empty literal string (not @0x7f...)
        if (manifest.appLabel.isNotEmpty() && !manifest.appLabel.startsWith("@0x") && !manifest.appLabel.startsWith("@string/")) {
            return manifest.appLabel
        }

        // Look for string named "app_name" or "application_name" in global strings
        val candidates = listOf("app_name", "application_name", "appname", "launcher_name")
        for (c in candidates) {
            val idx = arsc.globalStrings.indexOf(c)
            if (idx != -1 && idx + 1 < arsc.globalStrings.size) {
                val nextStr = arsc.globalStrings[idx + 1]
                if (nextStr.isNotBlank() && !nextStr.startsWith("res/")) {
                    return nextStr
                }
            }
        }

        // Fallback to manifest label or package last segment
        return if (manifest.appLabel.isNotEmpty()) manifest.appLabel
        else manifest.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }

    private fun buildAppNameReport(
        manifest: AXmlDecoder.ManifestData,
        arsc: ArscDecoder.ArscData,
        resolvedLabel: String
    ): AppNameDiscoveryReport {
        val activityLabels = manifest.activities.map { act ->
            ActivityLabelDiscovery(
                componentName = act.name,
                rawLabel = act.label ?: manifest.appLabel,
                resolvedLabel = act.label ?: resolvedLabel,
                isLauncher = act.isLauncher,
                isAlias = act.isAlias,
                confidence = if (act.label != null) ConfidenceLevel.CONFIRMED_MANIFEST else ConfidenceLevel.RESOLVED_RESOURCE
            )
        }

        val localizedVariants = mutableMapOf<String, String>()
        for ((loc, desc) in arsc.localizedAppNames) {
            localizedVariants[loc] = "$resolvedLabel ($desc)"
        }
        if (localizedVariants.isEmpty()) {
            localizedVariants["default"] = resolvedLabel
        }

        val targets = listOf(
            ModificationTarget(
                category = "App Name",
                location = "AndroidManifest.xml: <application android:label>",
                currentValue = manifest.appLabel.ifEmpty { resolvedLabel },
                targetDescription = "Modify binary XML string pool reference",
                riskLevel = ModificationRisk.LOW,
                confidence = ConfidenceLevel.CONFIRMED_MANIFEST
            ),
            ModificationTarget(
                category = "App Name",
                location = "resources.arsc: global string pool 'app_name'",
                currentValue = resolvedLabel,
                targetDescription = "Update compiled string resource value for launchers",
                riskLevel = ModificationRisk.LOW,
                confidence = ConfidenceLevel.RESOLVED_RESOURCE
            )
        )

        return AppNameDiscoveryReport(
            manifestRawLabel = manifest.appLabel,
            resolvedDefaultLabel = resolvedLabel,
            labelResourceId = manifest.appLabelResId,
            labelResourceName = "app_name",
            localizedVariants = localizedVariants,
            activityLabels = activityLabels,
            shortcutLabels = emptyList(),
            unresolvedReferences = emptyList(),
            recommendedTargets = targets
        )
    }

    private fun buildLauncherIconReport(
        manifest: AXmlDecoder.ManifestData,
        iconResult: IconDiscoveryProcessor.IconScanResult
    ): LauncherIconDiscoveryReport {
        val activityIcons = manifest.activities.map { act ->
            ActivityIconDiscovery(
                componentName = act.name,
                rawIcon = act.icon ?: manifest.appIcon,
                resolvedIconPath = iconResult.densityVariants.firstOrNull { it.existsInApk }?.entryPath,
                isLauncher = act.isLauncher,
                isAlias = act.isAlias,
                confidence = if (act.icon != null) ConfidenceLevel.CONFIRMED_MANIFEST else ConfidenceLevel.RESOLVED_RESOURCE
            )
        }

        val targets = mutableListOf<ModificationTarget>()
        targets.add(
            ModificationTarget(
                category = "Launcher Icon",
                location = "res/mipmap-*/ic_launcher.png (5 densities)",
                currentValue = "${iconResult.densityVariants.count { it.existsInApk }} raster variants",
                targetDescription = "Generate & replace density-matched PNG drawables",
                riskLevel = ModificationRisk.LOW,
                confidence = ConfidenceLevel.RESOLVED_RESOURCE
            )
        )
        if (iconResult.resolvedType == "Adaptive XML") {
            targets.add(
                ModificationTarget(
                    category = "Launcher Icon",
                    location = "res/mipmap-anydpi-v26/ic_launcher.xml",
                    currentValue = "Adaptive icon XML foreground: ${iconResult.foregroundPath ?: "embedded"}",
                    targetDescription = "Replace adaptive foreground layer drawable",
                    riskLevel = ModificationRisk.MEDIUM,
                    confidence = ConfidenceLevel.RESOLVED_RESOURCE
                )
            )
        }

        return LauncherIconDiscoveryReport(
            manifestRawIcon = manifest.appIcon,
            manifestRoundIcon = manifest.appRoundIcon.ifEmpty { null },
            resolvedIconType = iconResult.resolvedType,
            foregroundResource = iconResult.foregroundPath,
            backgroundResource = iconResult.backgroundPath,
            monochromeResource = iconResult.monochromePath,
            densityVariants = iconResult.densityVariants,
            activityIcons = activityIcons,
            previewIconBytes = iconResult.previewBytes,
            unresolvedReferences = emptyList(),
            recommendedTargets = targets
        )
    }

    private fun buildPackageReport(
        manifest: AXmlDecoder.ManifestData,
        dexRefs: List<DexReferenceDiscovery>,
        assetMatches: List<AssetMatchDiscovery>,
        nativeLibs: List<NativeLibDiscovery>
    ): PackageReferenceDiscoveryReport {
        val components = manifest.activities.map { act ->
            ComponentClassDiscovery(
                className = act.name,
                componentType = if (act.isAlias) "Activity Alias" else "Activity",
                isInManifestPackage = act.name.startsWith(manifest.packageName) || act.name.startsWith("."),
                isExported = act.isLauncher
            )
        }

        val authorities = manifest.providers.map { p ->
            ProviderAuthorityDiscovery(
                authority = p.authorities,
                providerClass = p.name,
                isPackageRelated = p.authorities.contains(manifest.packageName),
                collisionRisk = true
            )
        }

        val risks = mutableListOf<CompatibilityRisk>()
        if (authorities.isNotEmpty()) {
            risks.add(
                CompatibilityRisk(
                    title = "ContentProvider Authority Collision",
                    category = "Package",
                    severity = ModificationRisk.HIGH,
                    description = "${authorities.size} provider authorities declared (${authorities.joinToString { it.authority }}). Android requires globally unique authorities.",
                    mitigationRecommendation = "Append project identifier or update authority in manifest & DEX"
                )
            )
        }
        if (nativeLibs.isNotEmpty()) {
            risks.add(
                CompatibilityRisk(
                    title = "Native Shared Libraries Detected",
                    category = "Architecture",
                    severity = ModificationRisk.MEDIUM,
                    description = "Detected ${nativeLibs.size} native libraries (${nativeLibs.map { it.abi }.distinct().joinToString()}). Native C/C++ code may contain hardcoded package string blind spots.",
                    mitigationRecommendation = "Test cloned application on physical target architecture"
                )
            )
        }
        risks.add(
            CompatibilityRisk(
                title = "App Links & OAuth Client Identity",
                category = "Package",
                severity = ModificationRisk.HIGH,
                description = "Google Play Services, OAuth redirects, and universal deep links verify the exact package identifier and signing certificate hash.",
                mitigationRecommendation = "Expect cloud sign-in services to require re-registering the cloned package name"
            )
        )

        val targets = listOf(
            ModificationTarget(
                category = "Package ID",
                location = "AndroidManifest.xml: <manifest package>",
                currentValue = manifest.packageName,
                targetDescription = "Rewrite package identifier in binary XML string pool",
                riskLevel = ModificationRisk.MEDIUM,
                confidence = ConfidenceLevel.CONFIRMED_MANIFEST
            ),
            ModificationTarget(
                category = "Package ID",
                location = "classes.dex: ${dexRefs.count { it.referenceType == com.atp.cloner.data.model.DexReferenceType.CLASS_DESCRIPTOR }} class descriptors",
                currentValue = "L${manifest.packageName.replace('.', '/')}/...",
                targetDescription = "DEX bytecode-aware class descriptor transformation",
                riskLevel = ModificationRisk.HIGH,
                confidence = ConfidenceLevel.CONFIRMED_DEX_STRUCTURAL
            )
        )

        val blindSpots = mutableListOf<String>()
        if (nativeLibs.isNotEmpty()) {
            blindSpots.add("Native binary code (.so) cannot be statically verified for embedded package strings")
        }
        blindSpots.add("Dynamic reflection and runtime Class.forName() invocations cannot be discovered offline")

        return PackageReferenceDiscoveryReport(
            manifestPackage = manifest.packageName,
            componentClasses = components,
            providerAuthorities = authorities,
            dexReferences = dexRefs,
            assetMatches = assetMatches,
            nativeLibraries = nativeLibs,
            compatibilityRisks = risks,
            uninspectedBlindSpots = blindSpots,
            recommendedTargets = targets
        )
    }

    private fun buildGraph(
        manifest: AXmlDecoder.ManifestData,
        resolvedLabel: String,
        iconResult: IconDiscoveryProcessor.IconScanResult,
        nodes: MutableList<ResourceGraphNode>,
        edges: MutableList<ResourceGraphEdge>
    ) {
        val manifestNodeId = "node_manifest"
        nodes.add(
            ResourceGraphNode(
                id = manifestNodeId,
                label = "AndroidManifest.xml",
                resourceType = "Manifest",
                archivePathOrResId = "AndroidManifest.xml",
                decodedValue = "package='${manifest.packageName}'",
                confidence = ConfidenceLevel.CONFIRMED_MANIFEST
            )
        )

        // App Label Node
        val labelNodeId = "node_label"
        nodes.add(
            ResourceGraphNode(
                id = labelNodeId,
                label = "@string/app_name",
                resourceType = "String",
                archivePathOrResId = manifest.appLabel.ifEmpty { "@string/app_name" },
                decodedValue = resolvedLabel,
                confidence = ConfidenceLevel.RESOLVED_RESOURCE
            )
        )
        edges.add(
            ResourceGraphEdge(
                fromNodeId = manifestNodeId,
                toNodeId = labelNodeId,
                relationshipType = "android:label references"
            )
        )

        // Icon Node
        val iconNodeId = "node_icon"
        nodes.add(
            ResourceGraphNode(
                id = iconNodeId,
                label = manifest.appIcon.ifEmpty { "@mipmap/ic_launcher" },
                resourceType = if (iconResult.resolvedType == "Adaptive XML") "AdaptiveXml" else "Mipmap",
                archivePathOrResId = manifest.appIcon,
                decodedValue = "${iconResult.resolvedType} (${iconResult.densityVariants.count { it.existsInApk }} densities)",
                confidence = ConfidenceLevel.RESOLVED_RESOURCE
            )
        )
        edges.add(
            ResourceGraphEdge(
                fromNodeId = manifestNodeId,
                toNodeId = iconNodeId,
                relationshipType = "android:icon references"
            )
        )

        if (iconResult.foregroundPath != null) {
            val fgNodeId = "node_fg"
            nodes.add(
                ResourceGraphNode(
                    id = fgNodeId,
                    label = "Foreground Layer",
                    resourceType = "Drawable",
                    archivePathOrResId = iconResult.foregroundPath,
                    decodedValue = "Adaptive Foreground Drawable",
                    confidence = ConfidenceLevel.RESOLVED_RESOURCE
                )
            )
            edges.add(
                ResourceGraphEdge(
                    fromNodeId = iconNodeId,
                    toNodeId = fgNodeId,
                    relationshipType = "contains_layer"
                )
            )
        }

        // Launcher Activity Node
        val launcherAct = manifest.activities.firstOrNull { it.isLauncher }
        if (launcherAct != null) {
            val actNodeId = "node_activity"
            nodes.add(
                ResourceGraphNode(
                    id = actNodeId,
                    label = launcherAct.name.substringAfterLast("."),
                    resourceType = "Activity",
                    archivePathOrResId = launcherAct.name,
                    decodedValue = "Launcher Component",
                    confidence = ConfidenceLevel.CONFIRMED_MANIFEST
                )
            )
            edges.add(
                ResourceGraphEdge(
                    fromNodeId = manifestNodeId,
                    toNodeId = actNodeId,
                    relationshipType = "declares_component"
                )
            )
            edges.add(
                ResourceGraphEdge(
                    fromNodeId = actNodeId,
                    toNodeId = labelNodeId,
                    relationshipType = "inherits_label"
                )
            )
            edges.add(
                ResourceGraphEdge(
                    fromNodeId = actNodeId,
                    toNodeId = iconNodeId,
                    relationshipType = "inherits_icon"
                )
            )
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65536)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
