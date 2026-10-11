# ATP App Cloner

**Developer:** ATP Network  
**Application ID:** `com.atp.cloner`  
**Platform:** Android (Jetpack Compose + Material 3)  
**Architecture:** 100% Offline APK Analysis, Customization, Rebuilding & Signing Suite  

---

## Overview

ATP App Cloner is a comprehensive, on-device Android application designed to inspect, customize, clone, rebuild, align, sign, and verify already-compiled Android APK binaries directly on device without requiring the original developer's source code, Java/Kotlin files, or Gradle workspace.

---

## Phase 4 — APK Rebuilding, Alignment, Signing & Final Verification

Phase 4 completes the end-to-end cloning pipeline by transforming the prepared Phase 3 modification workspace into a valid, rebuild, aligned, signed, and installable Android APK.

### Rebuild Pipeline Architecture

1. **`ApkWorkspaceValidator`**
   - Inspects the isolated workspace directory (`projects/<projectId>/workspace`).
   - Confirms `working_modified.apk` is present and valid.
   - Verifies all critical components: `AndroidManifest.xml`, `resources.arsc`, `classes*.dex`, and native `.so` libraries.
   - Prevents execution on missing or malformed inputs with descriptive feedback.

2. **`ApkManifestCompiler` & `ApkResourceRebuilder`**
   - Validates compiled binary AXML chunk headers (`RES_XML_TYPE = 0x00080003`), string pool offsets, and namespace mappings.
   - Ensures `resources.arsc` chunk structures (`RES_TABLE_TYPE = 0x0002`), package counts, and global string tables maintain 4-byte alignment and data consistency.

3. **`ApkDexValidator`**
   - Performs deep inspection of all Dalvik Executable files (`classes.dex`, `classes2.dex`, etc.).
   - Validates magic byte sequences (`dex\n035\0`, `dex\n037\0`, `dex\n038\0`, `dex\n039\0`).
   - Recalculates and verifies Adler32 checksums (offset 12..EOF) and SHA-1 signatures (offset 32..EOF).
   - Validates class definitions, method ID counts, and string ID tables.

4. **`ApkAlignmentService` (Zipalign Engine)**
   - Re-aligns archive entries to Android OS runtime specifications.
   - Uncompressed entries (`resources.arsc` and native `.so` shared libraries) are aligned to 4-byte (and 4KB/16KB for `.so`) boundaries using standard ZIP extra field padding.
   - Deflated entries are packed efficiently without unneeded padding.

5. **`ApkSigningService` (APK Signature Scheme v1)**
   - Generates and manages a secure, persistent RSA-2048 key pair in a local KeyStore.
   - Computes SHA-256 digests for every file entry in the archive (excluding `META-INF/`).
   - Synthesizes standard `META-INF/MANIFEST.MF` with individual `SHA-256-Digest` entries.
   - Builds `META-INF/CERT.SF` with `SHA-256-Digest-Manifest`.
   - Digitally signs `CERT.SF` using `SHA256withRSA` and encapsulates the result into a PKCS#7 signed structure in `META-INF/CERT.RSA`.

6. **`ApkSignatureVerifier`**
   - Verifies the signed output APK before user presentation.
   - Validates that `MANIFEST.MF`, `CERT.SF`, and `CERT.RSA` exist.
   - Recalculates cryptographic SHA-256 digests of all archive entries and ensures zero mismatches against `MANIFEST.MF`.
   - Confirms Scheme v1 signature integrity.

7. **`ApkFinalValidator` & `ApkBuildReport`**
   - Confirms the final APK is a valid, readable ZIP archive.
   - Decodes the final `AndroidManifest.xml` to verify the target package ID and application label match configuration.
   - Generates complete build metrics: Output file size, output SHA-256, original vs clone comparison, signing certificate SHA-256 fingerprint, alignment count, DEX integrity status, and stage-by-stage timings.

8. **Export & Installation Actions**
   - **Direct Install:** Triggers Android Package Installer via `FileProvider` (`content://com.atp.cloner.fileprovider`) with `Intent.ACTION_VIEW` and `application/vnd.android.package-archive`.
   - **Export / Share:** Shares the generated `.apk` file to device storage or external apps via `Intent.ACTION_SEND`.

---

## Phase 3 — APK Modification Engine & Branding

Phase 3 implements the actual modification logic and application branding:

- **Application Name Modification:** Rewrites application labels in both binary `AndroidManifest.xml` string pool and `resources.arsc` global table.
- **Package Identifier Transformation:** Rewrites manifest package declaration, component class paths, and ContentProvider authorities (`android:authorities`).
- **Launcher Icon Replacement:** Replaces raster mipmaps/drawables across all standard densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) with user-selected or auto-generated icons.
- **Rollback & Isolation:** Modifications are staged in `projects/<projectId>/workspace` without altering the immutable original APK. Failed operations cleanly rollback.
- **ATP Branding:** Arm-style logo icon 💪, high-tech dark palettes (Dark Blue, Dark Charcoal, Dark Green, Dark Teal), and Material 3 responsive layouts.

---

## Phase 2.5 — Deep Metadata Discovery Engine

- **Application Name Discovery:** Maps manifest labels, resource table keys, and multi-language translations (`values-es`, `values-hi`, etc.).
- **Launcher Icon Discovery:** Resolves adaptive XML layers, density variants, and provides in-app icon previews.
- **Package Reference Discovery:** Scans DEX string pools, component declarations, ContentProvider authorities, asset configs, and native `.so` ABIs.
- **Compatibility Analysis:** Flags risks for Google Play Services, ContentProvider authority collisions, and OAuth dependencies.

---

## Offline & Privacy Guarantee

- **100% Offline:** Zero network requests, analytics, or remote build dependencies.
- **Isolated Sandboxing:** All operations occur within application-private directories (`context.filesDir`).
- **Original Preserved:** The original imported APK is never altered or replaced.
- **No DRM Circumvention:** Does not circumvent system DRM or application copy protection.
