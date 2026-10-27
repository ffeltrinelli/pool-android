package io.github.ffeltrinelli.pool.android

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.ffeltrinelli.pool.AppSharing
import java.io.File
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The Activity owns this worker. Shared files outlive the chooser and expire after seven days. */
internal class AndroidAppSharing(
    private val activity: Activity,
    private val worker: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "pool-apk-share") },
    private val launchChooser: (Intent) -> Unit = activity::startActivity
) : AppSharing, AutoCloseable {
    override val available = true
    private val preparing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    override fun share() {
        activity.runOnUiThread {
            if (closed.get() || !preparing.compareAndSet(false, true)) return@runOnUiThread
            worker.execute {
                try {
                    @Suppress("DEPRECATION")
                    val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "unknown"
                    val info = activity.applicationInfo
                    val file = ApkShareFile.prepare(
                        File(info.sourceDir), info.splitSourceDirs?.isNotEmpty() == true,
                        version, info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, activity.cacheDir
                    )
                    activity.runOnUiThread { openChooser(file) }
                } catch (failure: Exception) {
                    activity.runOnUiThread {
                        preparing.set(false)
                        if (!closed.get()) {
                            Log.e("PoolApkShare", "Could not prepare APK", failure)
                            message(if (failure is SplitApkException) failure.message!! else "Could not prepare app. Please try again.")
                        }
                    }
                }
            }
        }
    }

    private fun openChooser(file: File) {
        try {
            if (closed.get() || activity.isFinishing || activity.isDestroyed) return
            launchChooser(Intent.createChooser(apkShareIntent(activity, file), "Share app"))
        } catch (failure: RuntimeException) {
            Log.e("PoolApkShare", "Could not open sharing", failure)
            message("Could not open sharing. Please try again.")
        } finally {
            preparing.set(false)
        }
    }

    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    override fun close() {
        closed.set(true)
        worker.shutdownNow()
    }
}

internal fun apkShareIntent(context: android.content.Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.apk", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal class SplitApkException : Exception("This installation uses split APKs. Share a standalone APK instead.")

internal object ApkShareFile {
    private const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun prepare(source: File, split: Boolean, version: String, debug: Boolean, cache: File): File {
        if (split) throw SplitApkException()
        val directory = File(cache, "app-sharing").apply { mkdirs() }
        val expiry = System.currentTimeMillis() - RETENTION_MILLIS
        directory.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.deleteRecursively() }
        // A unique directory prevents a later share from replacing bytes still being transferred.
        val request = File(directory, UUID.randomUUID().toString()).apply { check(mkdir()) }
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(request, "Feltri-8-Ball-$safeVersion${if (debug) "-debug" else ""}.apk")
        try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            return destination
        } catch (failure: Exception) {
            request.deleteRecursively()
            throw failure
        }
    }
}
