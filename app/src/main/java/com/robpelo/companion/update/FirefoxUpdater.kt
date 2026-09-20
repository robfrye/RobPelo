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

data class FirefoxRelease(
    val versionName: String,
    val downloadUrl: String,
)

sealed interface FirefoxUpdateState {
    data object Checking : FirefoxUpdateState
    data class Current(val installedVersion: String) : FirefoxUpdateState
    data class Available(
        val installedVersion: String?,
        val release: FirefoxRelease,
    ) : FirefoxUpdateState
    data class Downloading(val versionName: String) : FirefoxUpdateState
    data class ReadyToInstall(
        val versionName: String,
        val contentUri: Uri,
    ) : FirefoxUpdateState
    data class Failed(val message: String) : FirefoxUpdateState
}

class FirefoxUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun checkIfDue(callback: (FirefoxUpdateState) -> Unit) {
        val lastCheck = preferences.getLong(KEY_LAST_CHECK_MS, 0)
        val elapsed = System.currentTimeMillis() - lastCheck
        if (elapsed in 0 until CHECK_INTERVAL_MS) {
            val cachedVersion = preferences.getString(KEY_AVAILABLE_VERSION, null)
            if (cachedVersion != null) {
                val installedVersion = installedFirefox()?.versionName
                if (
                    installedVersion == null ||
                    FirefoxVersion.isNewer(cachedVersion, installedVersion)
                ) {
                    post(
                        callback,
                        FirefoxUpdateState.Available(
                            installedVersion,
                            releaseFor(cachedVersion),
                        ),
                    )
                } else {
                    preferences.edit().remove(KEY_AVAILABLE_VERSION).apply()
                    post(callback, FirefoxUpdateState.Current(installedVersion))
                }
            }
            return
        }
        check(callback)
    }

    fun check(callback: (FirefoxUpdateState) -> Unit) {
        post(callback, FirefoxUpdateState.Checking)
        executor.execute {
            val state = try {
                val installed = installedFirefox()
                val latestVersion = fetchLatestVersion()
                preferences.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
                if (
                    installed == null ||
                    FirefoxVersion.isNewer(latestVersion, installed.versionName)
                ) {
                    preferences.edit().putString(KEY_AVAILABLE_VERSION, latestVersion).apply()
                    FirefoxUpdateState.Available(
                        installed?.versionName,
                        releaseFor(latestVersion),
                    )
                } else {
                    preferences.edit().remove(KEY_AVAILABLE_VERSION).apply()
                    FirefoxUpdateState.Current(installed.versionName)
                }
            } catch (exception: IOException) {
                FirefoxUpdateState.Failed(exception.message ?: "network error")
            } catch (exception: org.json.JSONException) {
                FirefoxUpdateState.Failed("Mozilla returned invalid version data")
            }
            post(callback, state)
        }
    }

    fun downloadAndVerify(
        release: FirefoxRelease,
        callback: (FirefoxUpdateState) -> Unit,
    ) {
        post(callback, FirefoxUpdateState.Downloading(release.versionName))
        executor.execute {
            val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY)
            val candidate = File(updateDirectory, UPDATE_FILE)
            val state = try {
                if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                    throw IOException("could not create update directory")
                }
                candidate.delete()
                download(release.downloadUrl, candidate)
                verifyCandidate(candidate)
                FirefoxUpdateState.ReadyToInstall(
                    release.versionName,
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.files",
                        candidate,
                    ),
                )
            } catch (exception: IOException) {
                candidate.delete()
                FirefoxUpdateState.Failed(exception.message ?: "download error")
            } catch (exception: SecurityException) {
                candidate.delete()
                FirefoxUpdateState.Failed(exception.message ?: "APK verification error")
            } catch (exception: IllegalArgumentException) {
                candidate.delete()
                FirefoxUpdateState.Failed(exception.message ?: "APK handoff error")
            }
            post(callback, state)
        }
    }

    fun shutdown() {
        closed = true
        executor.shutdownNow()
    }

    private fun fetchLatestVersion(): String {
        val connection = openHttps(MOZILLA_VERSION_FEED)
        try {
            return connection.inputStream.bufferedReader().use { reader ->
                JSONObject(reader.readText()).getString("version").also { version ->
                    if (!VERSION_PATTERN.matches(version)) {
                        throw IOException("unexpected Firefox version: $version")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, destination: File) {
        val connection = openHttps(url)
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_APK_BYTES) {
            connection.disconnect()
            throw IOException("Firefox APK exceeds the download size limit")
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
                            throw IOException("Firefox APK exceeds the download size limit")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.connect()
        if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException("Mozilla request failed with HTTP $code")
        }
        return connection
    }

    private fun verifyCandidate(candidate: File) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = packageManager.getPackageArchiveInfo(candidate.absolutePath, flags)
            ?: throw SecurityException("downloaded file is not a valid APK")
        val installed = try {
            packageManager.getPackageInfo(FIREFOX_PACKAGE, flags)
        } catch (exception: PackageManager.NameNotFoundException) {
            null
        }

        if (archive.packageName != FIREFOX_PACKAGE) {
            throw SecurityException("downloaded APK has an unexpected package")
        }
        if (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE >
            android.os.Build.VERSION.SDK_INT
        ) {
            throw SecurityException("downloaded Firefox does not support this Android version")
        }
        if (installed != null && archive.longVersionCode <= installed.longVersionCode) {
            throw SecurityException("downloaded Firefox is not newer than the installed version")
        }

        val archiveSigningInfo = archive.signingInfo
            ?: throw SecurityException("downloaded Firefox has no signing information")
        val archiveSigners = archiveSigningInfo.apkContentsSigners.map(::sha256).toSet()
        if (archiveSigners != setOf(MOZILLA_RELEASE_CERT_SHA256)) {
            throw SecurityException("downloaded APK is not signed by the expected Mozilla key")
        }

        if (installed != null) {
            val installedSigningInfo = installed.signingInfo
                ?: throw SecurityException("installed Firefox has no signing information")
            val installedSigners =
                installedSigningInfo.apkContentsSigners.map(::sha256).toSet()
            if (archiveSigners != installedSigners) {
                throw SecurityException("downloaded Firefox signing certificate does not match")
            }
        }
    }

    private fun installedFirefox(): InstalledFirefox? = try {
        val packageInfo = packageManager.getPackageInfo(FIREFOX_PACKAGE, 0)
        InstalledFirefox(packageInfo.versionName.orEmpty())
    } catch (exception: PackageManager.NameNotFoundException) {
        null
    }

    private fun releaseFor(versionName: String): FirefoxRelease = FirefoxRelease(
        versionName = versionName,
        downloadUrl = "https://archive.mozilla.org/pub/fenix/releases/$versionName/" +
            "android/fenix-$versionName-android-arm64-v8a/" +
            "fenix-$versionName.multi.android-arm64-v8a.apk",
    )

    private fun sha256(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun post(
        callback: (FirefoxUpdateState) -> Unit,
        state: FirefoxUpdateState,
    ) {
        if (!closed) {
            mainHandler.post {
                if (!closed) {
                    callback(state)
                }
            }
        }
    }

    private data class InstalledFirefox(val versionName: String)

    private companion object {
        const val FIREFOX_PACKAGE = "org.mozilla.firefox"
        const val MOZILLA_VERSION_FEED =
            "https://product-details.mozilla.org/1.0/mobile_versions.json"
        const val PREFERENCES = "firefox_updates"
        const val KEY_LAST_CHECK_MS = "last_check_ms"
        const val KEY_AVAILABLE_VERSION = "available_version"
        const val UPDATE_DIRECTORY = "updates"
        const val UPDATE_FILE = "firefox-update.apk"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val NETWORK_TIMEOUT_MS = 30_000
        const val MAX_APK_BYTES = 250L * 1024 * 1024
        const val MOZILLA_RELEASE_CERT_SHA256 =
            "a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"
        val VERSION_PATTERN = Regex("""\d+(\.\d+)*""")
    }
}
