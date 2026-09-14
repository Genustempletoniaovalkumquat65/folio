package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local crash reports, no analytics: the last few crashes are written to Folio's private storage and nothing
 * leaves the phone unless you share a report yourself (Settings › Help › Crash Reports).
 */
internal object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 5

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    internal fun report(context: Context, threadName: String, error: Throwable, now: LocalDateTime = LocalDateTime.now()): String {
        val config = context.resources.configuration
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return buildString {
            appendLine("Folio crash report")
            appendLine("Time: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine("Folio: $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("Screen: ${if (config.screenWidthDp >= 600) "unfolded" else "folded"} (${config.screenWidthDp}×${config.screenHeightDp} dp)")
            appendLine("Thread: $threadName")
            appendLine()
            append(error.stackTraceToString())
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val folder = dir(context)
        File(folder, "crash-${System.currentTimeMillis()}.txt").writeText(report(context, thread.name, error))
        folder.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    fun reports(context: Context): List<File> = dir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()

    fun clear(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }

    /** Share sheet with the report text, so you choose where it goes. */
    fun shareIntent(file: File): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Folio crash report")
            .putExtra(Intent.EXTRA_TEXT, file.readText().take(60_000)), "Share crash report")
}
