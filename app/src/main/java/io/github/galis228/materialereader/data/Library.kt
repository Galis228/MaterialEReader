package io.github.galis228.materialereader.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import io.github.galis228.materialereader.parse.BookFormat
import io.github.galis228.materialereader.parse.BookParser
import io.github.galis228.materialereader.parse.ComicArchive
import io.github.galis228.materialereader.parse.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Bookmark(val block: Int, val charPos: Int, val snippet: String, val chapter: String, val createdAt: Long)

/** A highlighted paragraph with an optional note. color = index in [HighlightColors]. */
data class Highlight(val block: Int, val color: Int, val note: String, val createdAt: Long)

data class BookEntry(
    val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    val fileName: String,
    val hasCover: Boolean,
    val totalBlocks: Int,
    val words: Int,
    val position: Int = 0,
    val offset: Int = 0,
    val charPos: Int = 0,
    val progress: Float = 0f,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0L,
    val readingMillis: Long = 0L,
    val sourceUri: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
) {
    val started get() = lastOpenedAt > 0L
    val finished get() = progress >= 0.98f
    val isComic get() = format.isComic
}

class Library private constructor(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(ctx.filesDir, "covers").apply { mkdirs() }
    private val indexFile = File(ctx.filesDir, "library.json")
    private val mutex = Mutex()
    private val scanPrefs = ctx.getSharedPreferences("scanner", 0)

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<BookEntry>> = _books.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var cached: Pair<String, ParsedBook>? = null

    fun coverFile(id: String) = File(coversDir, "$id.png")
    fun bookFile(e: BookEntry) = File(dir, e.fileName)
    fun entry(id: String) = _books.value.firstOrNull { it.id == id }

    // ---------- import ----------

    /** Copies a picked document into app storage, parses it and adds it to the library. */
    suspend fun import(uri: Uri, sourceUri: String? = null): Result<BookEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "book"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")
            importBytes(bytes, name, sourceUri).getOrThrow()
        }
    }

    suspend fun importBytes(bytes: ByteArray, name: String, sourceUri: String? = null): Result<BookEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val format = BookParser.detect(bytes, name) ?: error("Формат не поддерживается")
            val id = UUID.randomUUID().toString()
            val fileName = "$id.${format.ext}"
            val file = File(dir, fileName)
            val entry: BookEntry
            if (format.isComic) {
                file.writeBytes(bytes)
                try {
                    ComicArchive.open(file, format).use { comic ->
                        val title = name.substringBeforeLast('.').replace('_', ' ')
                        val hasCover = saveCover(id, comic.page(0))
                        entry = BookEntry(id, title, "", format, fileName, hasCover, comic.pages.size, 0, sourceUri = sourceUri)
                    }
                } catch (e: Throwable) { file.delete(); throw e }
            } else {
                val book = BookParser.parse(bytes, name, format)
                if (book.blocks.isEmpty()) error("В книге не найден текст")
                _books.value.firstOrNull { it.title == book.title && it.author == book.author && it.totalBlocks == book.blocks.size }
                    ?.let { existing -> return@runCatching existing }
                file.writeBytes(bytes)
                val hasCover = book.cover?.let { saveCover(id, it) } ?: false
                entry = BookEntry(id, book.title, book.author, format, fileName, hasCover, book.blocks.size, book.wordCount, sourceUri = sourceUri)
                cached = id to book
            }
            mutex.withLock {
                _books.update { listOf(entry) + it }
                save()
            }
            entry
        }
    }

    suspend fun open(id: String): ParsedBook = withContext(Dispatchers.IO) {
        cached?.takeIf { it.first == id }?.let { return@withContext it.second }
        val e = entry(id) ?: error("Книга не найдена")
        val book = BookParser.parse(bookFile(e).readBytes(), e.fileName, e.format)
        cached = id to book
        book
    }

    suspend fun openComic(id: String): ComicArchive = withContext(Dispatchers.IO) {
        val e = entry(id) ?: error("Комикс не найден")
        ComicArchive.open(bookFile(e), e.format)
    }

    // ---------- progress & bookmarks ----------

    suspend fun updatePosition(id: String, position: Int, offset: Int, charPos: Int, addMillis: Long) = mutex.withLock {
        _books.update { list ->
            list.map {
                if (it.id != id) it else it.copy(
                    position = position, offset = offset, charPos = charPos,
                    progress = if (it.totalBlocks <= 1) 1f else position.toFloat() / (it.totalBlocks - 1),
                    lastOpenedAt = System.currentTimeMillis(),
                    readingMillis = it.readingMillis + addMillis,
                )
            }
        }
        withContext(Dispatchers.IO) { save() }
    }

    suspend fun addBookmark(id: String, b: Bookmark) = editBook(id) { it.copy(bookmarks = (it.bookmarks + b).sortedBy { m -> m.block * 1_000_000L + m.charPos }) }
    suspend fun removeBookmark(id: String, createdAt: Long) = editBook(id) { it.copy(bookmarks = it.bookmarks.filterNot { m -> m.createdAt == createdAt }) }

    /** Adds, recolors or re-notes the highlight of a paragraph; color < 0 removes it. */
    suspend fun setHighlight(id: String, block: Int, color: Int, note: String) = editBook(id) { e ->
        val rest = e.highlights.filterNot { it.block == block }
        val old = e.highlights.firstOrNull { it.block == block }
        e.copy(highlights = if (color < 0) rest else (rest + Highlight(block, color, note, old?.createdAt ?: System.currentTimeMillis())).sortedBy { it.block })
    }

    private suspend fun editBook(id: String, f: (BookEntry) -> BookEntry) = mutex.withLock {
        _books.update { list -> list.map { if (it.id == id) f(it) else it } }
        withContext(Dispatchers.IO) { save() }
    }

    suspend fun delete(id: String) = mutex.withLock {
        val e = entry(id) ?: return@withLock
        _books.update { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) {
            bookFile(e).delete(); coverFile(id).delete(); save()
            // remember deleted scanned files so the scanner doesn't bring them back
            e.sourceUri?.let { ignored = ignored + it }
        }
        if (cached?.first == id) cached = null
    }

    // ---------- folder scanner ----------

    var folders: List<String>
        get() = scanPrefs.getStringSet("folders", emptySet())!!.sorted()
        private set(v) { scanPrefs.edit().putStringSet("folders", v.toSet()).apply() }

    private var ignored: Set<String>
        get() = scanPrefs.getStringSet("ignored", emptySet())!!
        set(v) { scanPrefs.edit().putStringSet("ignored", v).apply() }

    private val _folderList = MutableStateFlow(folders)
    val folderList: StateFlow<List<String>> = _folderList.asStateFlow()

    fun addFolder(tree: Uri) {
        ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        folders = (folders + tree.toString()).distinct()
        _folderList.value = folders
    }

    fun removeFolder(tree: String) {
        runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folders = folders - tree
        _folderList.value = folders
    }

    fun folderName(tree: String): String =
        Uri.parse(tree).lastPathSegment?.substringAfter(':')?.ifEmpty { "Память телефона" } ?: tree

    /** Finds supported files in the chosen folders and imports new ones. Returns how many were added. */
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext 0
        _scanning.value = true
        try {
            val known = _books.value.mapNotNull { it.sourceUri }.toSet() + ignored
            var added = 0
            val failed = HashSet<String>()
            for (tree in folders) {
                val root = runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) }.getOrNull() ?: continue
                for (f in walk(root, 0)) {
                    val uri = f.uri.toString()
                    if (uri in known) continue
                    if (f.length() > MAX_SIZE) continue
                    import(f.uri, sourceUri = uri).onSuccess { added++ }.onFailure { failed += uri }
                }
            }
            if (failed.isNotEmpty()) ignored = ignored + failed
            added
        } finally { _scanning.value = false }
    }

    private fun walk(dir: DocumentFile, depth: Int): Sequence<DocumentFile> = sequence {
        if (depth > 8) return@sequence
        for (f in runCatching { dir.listFiles() }.getOrDefault(emptyArray())) {
            val name = f.name ?: continue
            if (name.startsWith(".")) continue
            if (f.isDirectory) yieldAll(walk(f, depth + 1))
            else if (BookFormat.fromName(name) != null) yield(f)
        }
    }

    // ---------- storage ----------

    private fun saveCover(id: String, bytes: ByteArray): Boolean = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > 600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        coverFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        true
    }.getOrDefault(false)

    private fun displayName(uri: Uri): String? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun save() {
        val arr = JSONArray()
        for (b in _books.value) arr.put(JSONObject().apply {
            put("id", b.id); put("title", b.title); put("author", b.author); put("format", b.format.name)
            put("file", b.fileName); put("cover", b.hasCover); put("total", b.totalBlocks); put("words", b.words)
            put("pos", b.position); put("off", b.offset); put("char", b.charPos); put("progress", b.progress.toDouble())
            put("added", b.addedAt); put("opened", b.lastOpenedAt); put("millis", b.readingMillis)
            b.sourceUri?.let { put("source", it) }
            put("highlights", JSONArray().apply {
                b.highlights.forEach { h -> put(JSONObject().put("block", h.block).put("color", h.color).put("note", h.note).put("created", h.createdAt)) }
            })
            put("bookmarks", JSONArray().apply {
                b.bookmarks.forEach { m ->
                    put(JSONObject().put("block", m.block).put("char", m.charPos).put("snippet", m.snippet)
                        .put("chapter", m.chapter).put("created", m.createdAt))
                }
            })
        })
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    private fun load(): List<BookEntry> = runCatching {
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val bm = o.optJSONArray("bookmarks")
                val hl = o.optJSONArray("highlights")
                BookEntry(
                    o.getString("id"), o.getString("title"), o.optString("author"), BookFormat.valueOf(o.getString("format")),
                    o.getString("file"), o.optBoolean("cover"), o.getInt("total"), o.optInt("words"),
                    o.optInt("pos"), o.optInt("off"), o.optInt("char"),
                    o.optDouble("progress").toFloat().takeUnless { it.isNaN() } ?: 0f,
                    o.optLong("added"), o.optLong("opened"), o.optLong("millis"),
                    o.optString("source").ifEmpty { null },
                    if (bm == null) emptyList() else (0 until bm.length()).map { j ->
                        val m = bm.getJSONObject(j)
                        Bookmark(m.getInt("block"), m.optInt("char"), m.optString("snippet"), m.optString("chapter"), m.getLong("created"))
                    },
                    if (hl == null) emptyList() else (0 until hl.length()).map { j ->
                        val h = hl.getJSONObject(j)
                        Highlight(h.getInt("block"), h.optInt("color"), h.optString("note"), h.optLong("created"))
                    },
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_SIZE = 300L * 1024 * 1024

        @Volatile private var instance: Library? = null
        fun get(ctx: Context): Library = instance ?: synchronized(this) {
            instance ?: Library(ctx.applicationContext).also { instance = it }
        }
    }
}
