package com.robpelo.companion.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

data class TvBroRelease(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
)

sealed interface TvBroUpdateState {
    data object Checking : TvBroUpdateState
    data class Current(val installedVersion: String) : TvBroUpdateState
    data class Available(
        val installedVersion: String?,
        val release: TvBroRelease,
    ) : TvBroUpdateState
    data class Downloading(val versionName: String) : TvBroUpdateState
    data class ReadyToInstall(
        val versionName: String,
        val contentUri: Uri,
    ) : TvBroUpdateState
    data class Failed(val message: String) : TvBroUpdateState
}

class TvBroUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun checkIfDue(callback: (TvBroUpdateState) -> Unit) {
        val lastCheck = preferences.getLong(KEY_LAST_CHECK_MS, 0)
        val elapsed = System.currentTimeMillis() - lastCheck
        if (elapsed in 0 until CHECK_INTERVAL_MS) {
            val cachedVersion = preferences.getString(KEY_AVAILABLE_VERSION, null)
            val cachedUrl = preferences.getString(KEY_AVAILABLE_URL, null)
            val cachedDigest = preferences.getString(KEY_AVAILABLE_DIGEST, null)
            if (cachedVersion != null && cachedUrl != null && cachedDigest != null) {
                val installedVersion = installedVersion()
                if (
                    installedVersion == null ||
                    AppVersion.isNewer(cachedVersion, installedVersion)
                ) {
                    post(
                        callback,
                        TvBroUpdateState.Available(
                            installedVersion,
                            TvBroRelease(cachedVersion, cachedUrl, cachedDigest),
                        ),
                    )
                } else {
                    clearAvailableRelease()
                    post(callback, TvBroUpdateState.Current(installedVersion))
                }
            }
            return
        }
        check(callback)
    }

    fun check(callback: (TvBroUpdateState) -> Unit) {
        post(callback, TvBroUpdateState.Checking)
        executor.execute {
            val state = try {
                val installedVersion = installedVersion()
                val release = fetchLatestRelease()
                preferences.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
                if (
                    installedVersion == null ||
                    AppVersion.isNewer(release.versionName, installedVersion)
                ) {
                    cacheAvailableRelease(release)
                    TvBroUpdateState.Available(installedVersion, release)
                } else {
                    clearAvailableRelease()
                    TvBroUpdateState.Current(installedVersion)
                }
            } catch (exception: IOException) {
                TvBroUpdateState.Failed(exception.message ?: "network error")
            } catch (exception: org.json.JSONException) {
                TvBroUpdateState.Failed("GitHub returned invalid release data")
            }
            post(callback, state)
        }
    }

    fun downloadAndVerify(
        release: TvBroRelease,
        callback: (TvBroUpdateState) -> Unit,
    ) {
        post(callback, TvBroUpdateState.Downloading(release.versionName))
        executor.execute {
            val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY)
            val candidate = File(updateDirectory, UPDATE_FILE)
            val state = try {
                if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                    throw IOException("could not create update directory")
                }
                candidate.delete()
                download(release.downloadUrl, candidate)
                if (sha256(candidate) != release.sha256) {
                    throw SecurityException("downloaded TV Bro digest does not match GitHub")
                }
                verifyCandidate(candidate)
                TvBroUpdateState.ReadyToInstall(
                    release.versionName,
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.files",
                        candidate,
                    ),
                )
            } catch (exception: IOException) {
                candidate.delete()
                TvBroUpdateState.Failed(exception.message ?: "download error")
            } catch (exception: SecurityException) {
                candidate.delete()
                TvBroUpdateState.Failed(exception.message ?: "APK verification error")
            } catch (exception: IllegalArgumentException) {
                candidate.delete()
                TvBroUpdateState.Failed(exception.message ?: "APK handoff error")
            }
            post(callback, state)
        }
    }

    fun shutdown() {
        closed = true
        executor.shutdownNow()
    }

    private fun fetchLatestRelease(): TvBroRelease {
        val connection = openHttps(LATEST_RELEASE_API, followRedirects = false)
        try {
            val release = connection.inputStream.bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            val versionName = release.getString("tag_name").removePrefix("v")
            if (!VERSION_PATTERN.matches(versionName)) {
                throw IOException("unexpected TV Bro version: $versionName")
            }
            val assets = release.getJSONArray("assets")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.getString("name")
                if (name == "tvbro-$versionName-generic-geckoIncluded-arm64-v8a.apk") {
                    val downloadUrl = asset.getString("browser_download_url")
                    val digest = asset.optString("digest").removePrefix("sha256:")
                    if (
                        !downloadUrl.startsWith(OFFICIAL_DOWNLOAD_PREFIX) ||
                        !SHA256_PATTERN.matches(digest)
                    ) {
                        throw IOException("TV Bro release metadata failed validation")
                    }
                    return TvBroRelease(versionName, downloadUrl, digest)
                }
            }
            throw IOException("official arm64 Gecko TV Bro APK was not found")
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, destination: File) {
        val connection = openHttps(url, followRedirects = true)
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_APK_BYTES) {
            connection.disconnect()
            throw IOException("TV Bro APK exceeds the download size limit")
        }
        var downloaded = 0L
        try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) {
                            break
                        }
                        downloaded += count
                        if (downloaded > MAX_APK_BYTES) {
                            throw IOException("TV Bro APK exceeds the download size limit")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String, followRedirects: Boolean): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = followRedirects
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "RobPelo-TVBro-Updater")
        connection.connect()
        if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException("TV Bro request failed with HTTP $code")
        }
        return connection
    }

    private fun verifyCandidate(candidate: File) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = packageManager.getPackageArchiveInfo(candidate.absolutePath, flags)
            ?: throw SecurityException("downloaded file is not a valid APK")
        val installed = try {
            packageManager.getPackageInfo(TV_BRO_PACKAGE, flags)
        } catch (exception: PackageManager.NameNotFoundException) {
            null
        }
        if (archive.packageName != TV_BRO_PACKAGE) {
            throw SecurityException("downloaded APK has an unexpected package")
        }
        if (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE >
            android.os.Build.VERSION.SDK_INT
        ) {
            throw SecurityException("downloaded TV Bro does not support this Android version")
        }
        if (installed != null && archive.longVersionCode <= installed.longVersionCode) {
            throw SecurityException("downloaded TV Bro is not newer than the installed version")
        }
        val archiveSigningInfo = archive.signingInfo
            ?: throw SecurityException("downloaded TV Bro has no signing information")
        val archiveSigners = archiveSigningInfo.apkContentsSigners.map(::signatureSha256).toSet()
        if (archiveSigners != setOf(TV_BRO_RELEASE_CERT_SHA256)) {
            throw SecurityException("downloaded APK is not signed by the expected TV Bro key")
        }
        if (installed != null) {
            val installedSigningInfo = installed.signingInfo
                ?: throw SecurityException("installed TV Bro has no signing information")
            val installedSigners =
                installedSigningInfo.apkContentsSigners.map(::signatureSha256).toSet()
            if (installedSigners != archiveSigners) {
                throw SecurityException("downloaded TV Bro certificate does not match installed app")
            }
        }
    }

    private fun installedVersion(): String? = try {
        packageManager.getPackageInfo(TV_BRO_PACKAGE, 0).versionName
    } catch (exception: PackageManager.NameNotFoundException) {
        null
    }

    private fun cacheAvailableRelease(release: TvBroRelease) {
        preferences.edit()
            .putString(KEY_AVAILABLE_VERSION, release.versionName)
            .putString(KEY_AVAILABLE_URL, release.downloadUrl)
            .putString(KEY_AVAILABLE_DIGEST, release.sha256)
            .apply()
    }

    private fun clearAvailableRelease() {
        preferences.edit()
            .remove(KEY_AVAILABLE_VERSION)
            .remove(KEY_AVAILABLE_URL)
            .remove(KEY_AVAILABLE_DIGEST)
            .apply()
    }

    private fun signatureSha256(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun post(callback: (TvBroUpdateState) -> Unit, state: TvBroUpdateState) {
        if (!closed) {
            mainHandler.post {
                if (!closed) {
                    callback(state)
                }
            }
        }
    }

    private companion object {
        const val TV_BRO_PACKAGE = "com.phlox.tvwebbrowser"
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/truefedex/tv-bro/releases/latest"
        const val OFFICIAL_DOWNLOAD_PREFIX =
            "https://github.com/truefedex/tv-bro/releases/download/"
        const val TV_BRO_RELEASE_CERT_SHA256 =
            "1e5124be7e7fcb7a6462b47a42a8567863c6fccc6fe7708cd278be4f43047c75"
        const val PREFERENCES = "tvbro_updates"
        const val KEY_LAST_CHECK_MS = "last_check_ms"
        const val KEY_AVAILABLE_VERSION = "available_version"
        const val KEY_AVAILABLE_URL = "available_url"
        const val KEY_AVAILABLE_DIGEST = "available_digest"
        const val UPDATE_DIRECTORY = "updates"
        const val UPDATE_FILE = "tvbro-update.apk"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val NETWORK_TIMEOUT_MS = 30_000
        const val MAX_APK_BYTES = 250L * 1024 * 1024
        val VERSION_PATTERN = Regex("""\d+(\.\d+)*""")
        val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
    }
}
