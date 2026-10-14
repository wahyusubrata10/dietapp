package com.hawdietpro.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lapis 1 auto-update: cek GitHub Releases (repo publik, tanpa token), unduh APK,
 * verifikasi tanda tangan sama dengan aplikasi terpasang, lalu buka installer sistem.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val prefs: AppPreferences,
) {
    private val dir = File(context.filesDir, "update").apply { mkdirs() }
    private val releaseFile = File(dir, "latest.json")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    init {
        restore()
    }

    /** Memulihkan status dari disk (mis. APK sudah terunduh sebelum aplikasi ditutup). */
    private fun restore() {
        val release = runCatching { json.decodeFromString(ReleaseInfo.serializer(), releaseFile.readText()) }.getOrNull() ?: return
        if (release.versionCode <= currentVersionCode) {
            cleanup()
            return
        }
        val apk = apkFile(release)
        _state.value = if (apk.exists() && verifyApk(apk, release) == null) {
            UpdateState.ReadyToInstall(release, apk.path)
        } else {
            UpdateState.Available(release)
        }
    }

    /** @return rilis baru bila ada. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val previous = _state.value
        if (previous !is UpdateState.Downloading && previous !is UpdateState.ReadyToInstall) _state.value = UpdateState.Checking
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HawDietPro/$currentVersionName")
                .build()
            val body = http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null // belum ada rilis
                    !resp.isSuccessful -> throw IOException("GitHub HTTP ${resp.code}")
                    else -> resp.body?.string()
                }
            }
            prefs.setLastUpdateCheck(System.currentTimeMillis())
            val release = body?.let { json.decodeFromString(GithubRelease.serializer(), it) }?.toInfo()
            if (release == null || release.versionCode <= currentVersionCode) {
                cleanup()
                _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                return@withContext null
            }
            releaseFile.writeText(json.encodeToString(ReleaseInfo.serializer(), release))
            val apk = apkFile(release)
            _state.value = when {
                previous is UpdateState.Downloading && previous.release.versionCode == release.versionCode -> previous
                apk.exists() && verifyApk(apk, release) == null -> UpdateState.ReadyToInstall(release, apk.path)
                else -> UpdateState.Available(release)
            }
            release
        } catch (e: Exception) {
            Log.w(TAG, "Cek update gagal", e)
            _state.value = when (previous) {
                is UpdateState.Available, is UpdateState.ReadyToInstall, is UpdateState.Downloading -> previous
                else -> UpdateState.Error("Gagal cek update: ${if (e is IOException) "periksa koneksi internet" else e.message}")
            }
            null
        }
    }

    fun latestKnownRelease(): ReleaseInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        is UpdateState.Error -> s.release
        else -> runCatching { json.decodeFromString(ReleaseInfo.serializer(), releaseFile.readText()) }.getOrNull()
            ?.takeIf { it.versionCode > currentVersionCode }
    }

    /** Mengunduh APK (dipanggil dari UpdateDownloadWorker). */
    suspend fun download(release: ReleaseInfo, onProgress: suspend (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = apkFile(release)
        if (target.exists() && verifyApk(target, release) == null) {
            _state.value = UpdateState.ReadyToInstall(release, target.path)
            return@withContext target
        }
        dir.listFiles { f -> f.extension == "apk" || f.extension == "part" }?.forEach { it.delete() }
        val part = File(dir, target.name + ".part")
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val req = Request.Builder().url(release.apkUrl).header("User-Agent", "HawDietPro/$currentVersionName").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Respons kosong")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReported = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    val p = pct / 100f
                                    _state.value = UpdateState.Downloading(release, p)
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Gagal menyimpan APK")
            verifyApk(target, release)?.let { problem ->
                target.delete()
                throw SecurityException(problem)
            }
            _state.value = UpdateState.ReadyToInstall(release, target.path)
            target
        } catch (e: Exception) {
            part.delete()
            _state.value = UpdateState.Error(
                when (e) {
                    is SecurityException -> e.message ?: "APK tidak valid"
                    is IOException -> "Unduhan terputus. Akan dicoba lagi otomatis."
                    else -> "Unduhan gagal: ${e.message}"
                },
                release,
            )
            throw e
        }
    }

    /** Null = APK aman dipasang; selain itu berisi alasan penolakan. */
    fun verifyApk(file: File, release: ReleaseInfo): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "File APK rusak"
        if (archive.packageName != context.packageName) return "APK untuk aplikasi lain"
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode.toInt() else @Suppress("DEPRECATION") archive.versionCode
        if (archiveCode < release.versionCode || archiveCode <= currentVersionCode) return "Versi APK tidak lebih baru"
        val installed = signatureDigests(pm.getPackageInfo(context.packageName, flags))
        val incoming = signatureDigests(archive)
        if (installed.isEmpty() || incoming.isEmpty() || installed != incoming) {
            return "Tanda tangan APK berbeda dengan aplikasi terpasang. Update dibatalkan demi keamanan."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signatureDigests(info: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Membuka installer sistem; pengguna cukup menekan "Install". Data aplikasi tetap aman. */
    suspend fun install(apkPath: String) = withContext(Dispatchers.IO) {
        val file = File(apkPath)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    fun skip(release: ReleaseInfo) {
        _state.value = UpdateState.UpToDate(System.currentTimeMillis())
        releaseFile.delete()
        apkFile(release).delete()
    }

    fun reportInstallError(message: String) {
        val release = latestKnownRelease()
        _state.value = UpdateState.Error(message, release)
    }

    private fun cleanup() {
        releaseFile.delete()
        dir.listFiles { f -> f.extension == "apk" || f.extension == "part" }?.forEach { it.delete() }
    }

    private fun apkFile(release: ReleaseInfo) = File(dir, "HawDietPro-${release.versionCode}.apk")

    private fun GithubRelease.toInfo(): ReleaseInfo? {
        if (draft || prerelease) return null
        val code = TAG_REGEX.find(tagName)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val apk = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return ReleaseInfo(
            versionCode = code,
            versionName = name?.removePrefix("Haw Diet Pro")?.trim()?.ifBlank { null } ?: tagName,
            changelog = body.orEmpty().trim(),
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
            publishedAt = publishedAt,
            pageUrl = htmlUrl,
        )
    }

    private companion object {
        const val TAG = "UpdateRepository"
        val TAG_REGEX = Regex("""build-(\d+)""")
    }
}
