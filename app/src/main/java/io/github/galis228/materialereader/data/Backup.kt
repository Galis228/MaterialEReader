package io.github.galis228.materialereader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup to one .zip: books, covers, positions, bookmarks, quotes, word cards and reading stats.
 * The file goes wherever the person picks in the system dialog — Google Drive included, which makes it
 * the way to move the library to a new phone.
 */
object Backup {
    private val files = listOf("library.json", "words.json", "stats.json", "shelves.json", "ai_history.json", "wishlist.json")
    private val dirs = listOf("books", "covers")

    suspend fun export(ctx: Context, to: Uri): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.openOutputStream(to)!!.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("material-ereader-backup.txt")); zip.write("Material eReader backup v1\n".toByteArray()); zip.closeEntry()
                for (name in files) File(ctx.filesDir, name).takeIf { it.isFile }?.let { f ->
                    zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                for (d in dirs) File(ctx.filesDir, d).listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("$d/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    if (d == "books") n++
                }
            }
        }
        n
    }

    /** Replaces the library with the backup's contents. Returns the number of books restored. */
    suspend fun restore(ctx: Context, from: Uri): Int {
        val n = withContext(Dispatchers.IO) {
            val stage = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
            var valid = false
            var books = 0
            ctx.contentResolver.openInputStream(from)!!.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name
                        if (name == "material-ereader-backup.txt") { valid = true; continue }
                        // only known names: nothing in the archive can write outside the app's folders
                        val ok = name in files || dirs.any { d -> name.startsWith("$d/") && name.count { it == '/' } == 1 && !name.contains("..") }
                        if (!ok || e.isDirectory) continue
                        val f = File(stage, name); f.parentFile?.mkdirs()
                        f.outputStream().use { zip.copyTo(it) }
                        if (name.startsWith("books/")) books++
                    }
                }
            }
            require(valid && File(stage, "library.json").isFile) { "Это не резервная копия Material eReader" }
            for (d in dirs) {
                val src = File(stage, d)
                if (src.isDirectory) { val dst = File(ctx.filesDir, d); dst.deleteRecursively(); src.renameTo(dst) || src.copyRecursively(dst, true) }
            }
            for (name in files) File(stage, name).takeIf { it.isFile }?.let { it.copyTo(File(ctx.filesDir, name), true) }
            stage.deleteRecursively()
            books
        }
        Library.get(ctx).reload()
        Words.get(ctx).reload()
        Stats.get(ctx).reload()
        Wishlist.get(ctx).reload()
        io.github.galis228.materialereader.ai.AiHistory.get(ctx).reload()
        ContinueWidget.refresh(ctx)
        return n
    }
}
