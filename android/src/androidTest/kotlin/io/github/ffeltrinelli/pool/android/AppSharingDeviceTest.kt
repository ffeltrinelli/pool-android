package io.github.ffeltrinelli.pool.android

import android.app.Activity
import android.content.*
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AppSharingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun sharedApkHasOriginalBytesIdentityAndRecipientReadPermission() {
        val source = File(context.applicationInfo.sourceDir)
        val shared = ApkShareFile.prepare(source, false, "0.2.0", false, context.cacheDir)
        assertEquals("Feltri-8-Ball-0.2.0.apk", shared.name)
        assertArrayEquals(digest(source), digest(shared))
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(shared.path, 0)!!
        assertEquals(context.packageName, archive.packageName)
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(installed.versionName, archive.versionName)
        @Suppress("DEPRECATION")
        assertEquals(installed.versionCode, archive.versionCode)
        val received = CountDownLatch(1)
        var result = byteArrayOf()
        var recipientFailure: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                recipientFailure = intent.getStringExtra("failure")
                result = intent.getByteArrayExtra("digest") ?: byteArrayOf()
                received.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ApkRecipientActivity.RESULT), ContextCompat.RECEIVER_EXPORTED)
        try {
            val send = apkShareIntent(context, shared)
            assertEquals("application/vnd.android.package-archive", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            send.component = ComponentName("${context.packageName}.test", ApkRecipientActivity::class.java.name)
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(send)
            assertTrue("Recipient could not read APK", received.await(30, TimeUnit.SECONDS))
            assertNull(recipientFailure)
            assertArrayEquals(digest(source), result)
        } finally { context.unregisterReceiver(receiver) }
    }

    @Test fun splitAndCopyFailuresDoNotLeavePartialFilesAndRetryIsIndependent() {
        val cache = File(context.cacheDir, "apk-file-test").apply { deleteRecursively(); mkdirs() }
        try {
            try {
                ApkShareFile.prepare(File("missing"), true, "0.2.0", false, cache)
                fail("Split APK accepted")
            } catch (_: SplitApkException) { }
            try {
                ApkShareFile.prepare(File(cache, "missing"), false, "0.2.0", false, cache)
                fail("Missing source accepted")
            } catch (_: java.io.IOException) { }
            assertTrue(File(cache, "app-sharing").listFiles()!!.isEmpty())
            val source = File(context.applicationInfo.sourceDir)
            val first = ApkShareFile.prepare(source, false, "0.2.0", true, cache)
            val second = ApkShareFile.prepare(source, false, "0.2.0", true, cache)
            assertTrue(first.name.endsWith("-debug.apk"))
            assertNotEquals(first, second)
            assertArrayEquals(digest(source), digest(first))
        } finally { cache.deleteRecursively() }
    }

    @Test fun preparationIgnoresDuplicatesAndChooserFailureCanBeRetried() {
        val activity = instrumentation.startActivitySync(Intent(context, AndroidLauncher::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Activity
        val worker = Executors.newSingleThreadExecutor()
        val gate = CountDownLatch(1)
        worker.execute { gate.await() }
        val launches = AtomicInteger()
        val failFirst = AtomicBoolean(true)
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val sharing = AndroidAppSharing(activity, worker) {
            launches.incrementAndGet()
            if (failFirst.getAndSet(false)) {
                first.countDown()
                throw ActivityNotFoundException("Test chooser failure")
            }
            second.countDown()
        }
        try {
            instrumentation.runOnMainSync { sharing.share(); sharing.share() }
            gate.countDown()
            assertTrue(first.await(30, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(1, launches.get())
            instrumentation.runOnMainSync { sharing.share() }
            assertTrue(second.await(30, TimeUnit.SECONDS))
            assertEquals(2, launches.get())
        } finally {
            gate.countDown()
            instrumentation.runOnMainSync { sharing.close(); activity.finish() }
        }
    }

    private fun digest(file: File): ByteArray = file.inputStream().use { input ->
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            sha.update(buffer, 0, count)
        }
        sha.digest()
    }
}
