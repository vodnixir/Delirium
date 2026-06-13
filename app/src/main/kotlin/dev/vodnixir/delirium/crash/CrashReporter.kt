package dev.vodnixir.delirium.crash

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Lightweight crash reporting without extra SDKs: an uncaught-exception handler
 * writes the stack trace to a file synchronously (so it survives the dying
 * process), and on the next launch any saved crashes are uploaded to the
 * `crashReports` collection — where the admin panel can read them.
 */
object CrashReporter {
    private const val DIR = "crash_reports"
    private const val MAX_STACK_CHARS = 8000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        File(dir, "${System.currentTimeMillis()}.txt").writeText(trace)
    }

    /** Uploads and clears any crash files saved from previous runs. */
    suspend fun uploadPending(
        context: Context,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ) {
        if (auth.currentUser == null) return // rules require auth; retry next launch
        val dir = File(context.filesDir, DIR)
        val files = dir.listFiles { f -> f.extension == "txt" } ?: return
        for (file in files) {
            val stack = runCatching { file.readText() }.getOrNull() ?: continue
            runCatching {
                firestore.collection("crashReports").add(
                    mapOf(
                        "createdAt" to file.nameWithoutExtension.toLongOrNull()
                            .let { it ?: System.currentTimeMillis() },
                        "uid" to (auth.currentUser?.uid ?: ""),
                        "appVersion" to appVersion(context),
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "androidSdk" to Build.VERSION.SDK_INT,
                        "stacktrace" to stack.take(MAX_STACK_CHARS),
                    ),
                ).await()
            }.onSuccess { file.delete() }
        }
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("?")
}
