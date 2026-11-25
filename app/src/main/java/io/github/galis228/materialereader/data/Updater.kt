package io.github.galis228.materialereader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.galis228.materialereader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val pageUrl: String)

/** Self-update from GitHub Releases of [BuildConfig.UPDATE_REPO]. */
object Updater {
    private const val PREFS = "updater"
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val repo: String get() = BuildConfig.UPDATE_REPO

    fun autoCheckEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean("auto", true)
    fun setAutoCheck(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("auto", on).apply()
    fun skippedVersion(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString("skip", null)
    fun skip(ctx: Context, version: String) = ctx.getSharedPreferences(PREFS, 0).edit().putString("skip", version).apply()

    /** Background check on app start, at most every 12 hours. Returns a release worth offering, or null. */
    suspend fun autoCheck(ctx: Context): Release? {
        if (!autoCheckEnabled(ctx)) return null
        val p = ctx.getSharedPreferences(PREFS, 0)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last", 0) < CHECK_EVERY_MS) return null
        val rel = latest().getOrNull() ?: return null
        p.edit().putLong("last", now).apply()
        return rel.takeIf { isNewer(it.version, currentVersion) && it.version != skippedVersion(ctx) }
    }

    suspend fun latest(): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MaterialEReader/$currentVersion")
                connectTimeout = 15_000; readTimeout = 15_000
            }
            try {
                when (conn.responseCode) {
                    200 -> {}
                    404 -> error("Релизов пока нет")
                    else -> error("GitHub ответил ${conn.responseCode}")
                }
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                val assets = o.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk", true) } ?: error("В релизе нет APK")
                Release(
                    version = o.getString("tag_name").removePrefix("v"),
                    notes = o.optString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"),
                    size = apk.optLong("size"),
                    pageUrl = o.optString("html_url"),
                )
            } finally { conn.disconnect() }
        }
    }

    /** Semantic comparison: "0.10.0" > "0.9.3". Non-numeric suffixes are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').map { s -> s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "MaterialEReader-${rel.version}.apk")
        var url = URL(rel.apkUrl)
        var conn: HttpURLConnection
        // follow redirects manually (GitHub -> objects.githubusercontent.com)
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MaterialEReader/$currentVersion")
                connectTimeout = 20_000; readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code != 200) error("Не удалось скачать обновление ($code)")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.size
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        out
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
