package io.github.galis228.materialereader.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** A ready-to-run model that can be downloaded straight into the shared AI-Models folder. */
data class ModelOffer(val title: String, val note: String, val file: String, val url: String, val sizeMb: Int)

/**
 * In-app model downloads. Files land in Download/AI-Models through the system download manager, so they
 * survive the app being closed and become visible to НаноПеро and «Персона» too.
 */
object ModelDownloads {
    val offers = listOf(
        ModelOffer("Qwen 2.5 · 0.5B", "Самая лёгкая: быстрые объяснения слов и абзацев", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf", 400),
        ModelOffer("Gemma 3 · 1B", "Хорошо понимает русский, подходит для пересказов", "gemma-3-1b-it-Q4_K_M.gguf",
            "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf", 810),
        ModelOffer("Llama 3.2 · 1B", "Сильнее в английском", "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf", 810),
        ModelOffer("Qwen 2.5 · 1.5B", "Умнее, но медленнее: пересказы глав и карта персонажей", "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", 1100),
    )

    private fun dm(ctx: Context) = ctx.getSystemService(DownloadManager::class.java)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("model_downloads", 0)

    fun start(ctx: Context, o: ModelOffer) {
        val req = DownloadManager.Request(Uri.parse(o.url))
            .setTitle("Модель ${o.title}")
            .setDescription("Material eReader · AI-Models")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${SharedModels.FOLDER}/${o.file}")
            .setAllowedOverMetered(true)
        prefs(ctx).edit().putLong(o.file, dm(ctx).enqueue(req)).apply()
    }

    fun cancel(ctx: Context, o: ModelOffer) {
        val id = prefs(ctx).getLong(o.file, -1)
        if (id >= 0) dm(ctx).remove(id)
        prefs(ctx).edit().remove(o.file).apply()
    }

    sealed interface State {
        data object Absent : State
        data class Loading(val fraction: Float) : State
        data object Ready : State
        data class Failed(val reason: Int) : State
    }

    fun state(ctx: Context, o: ModelOffer): State {
        val id = prefs(ctx).getLong(o.file, -1)
        if (id >= 0) dm(ctx).query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                return when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> State.Ready
                    DownloadManager.STATUS_FAILED -> State.Failed(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                    else -> State.Loading(if (total > 0) done.toFloat() / total else 0f)
                }
            }
        }
        return if (java.io.File(SharedModels.dir, o.file).let { it.isFile && it.length() > 1_000_000 }) State.Ready else State.Absent
    }
}
