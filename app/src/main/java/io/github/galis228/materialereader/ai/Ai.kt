package io.github.galis228.materialereader.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.nanopero.app.LlamaNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AiException(msg: String) : Exception(msg)

enum class EngineChoice(val label: String) { AUTO("Авто"), NANO("Gemini Nano"), FILE("Файл-модель") }

/** Shared models folder with НаноПеро and «Персона»: Download/AI-Models. */
object SharedModels {
    const val FOLDER = "AI-Models"
    val dir: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
    const val path = "Загрузки/AI-Models"

    /** Reading files downloaded by other apps needs "All files access" on Android 11+. */
    fun hasAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    fun readable(): File? = if (hasAccess()) dir.takeIf { it.isDirectory } else null

    fun requestAccess(ctx: Context) {
        val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun isModel(f: File) = f.isFile && (f.name.endsWith(".litertlm", true) || f.name.endsWith(".gguf", true))
}

class AiSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("ai", 0)
    var engine: EngineChoice
        get() = runCatching { EngineChoice.valueOf(p.getString("engine", "AUTO")!!) }.getOrDefault(EngineChoice.AUTO)
        set(v) { p.edit().putString("engine", v.name).apply() }
    var model: String
        get() = p.getString("model", "")!!
        set(v) { p.edit().putString("model", v).apply() }
    var ctxTokens: Int
        get() = p.getInt("ctx", 4096)
        set(v) { p.edit().putInt("ctx", v).apply() }
    var useGpu: Boolean
        get() = p.getBoolean("gpu", false)
        set(v) { p.edit().putBoolean("gpu", v).apply() }
}

private val stopMarkers = listOf(
    "<|im_end|>", "<|im_start|>", "<|endoftext|>", "<end_of_turn>", "<start_of_turn>", "<eos>", "<|eot_id|>",
    "<|start_header_id|>", "\nuser\n", "\nUser:", "\nПользователь:",
)

private fun stopIndex(sb: CharSequence): Int {
    var best = -1
    for (m in stopMarkers) { val i = sb.indexOf(m); if (i >= 0 && (best < 0 || i < best)) best = i }
    return best
}

private fun looping(sb: CharSequence): Boolean {
    if (sb.length < 360) return false
    val tail = sb.subSequence(sb.length - 60, sb.length).toString()
    return sb.subSequence(0, sb.length - 60).toString().contains(tail)
}

/** Strips a leading <think>…</think> block some models emit. */
fun visibleAnswer(s: String): String {
    val end = s.lastIndexOf("</think>")
    if (end >= 0) return s.substring(end + 8).trimStart()
    return if (s.trimStart().startsWith("<think>")) "" else s
}

/** Gemini Nano via ML Kit GenAI Prompt API (AICore). */
class NanoEngine {
    private var model: GenerativeModel? = null
    private fun client() = model ?: Generation.getClient().also { model = it }
    private var cached: Boolean? = null
    var detail = ""; private set

    suspend fun available(): Boolean {
        cached?.let { if (it) return true }
        val ok = try {
            val st = client().checkStatus()
            detail = "статус AICore: $st"
            st == FeatureStatus.AVAILABLE
        } catch (t: Throwable) {
            runCatching { model?.close() }; model = null
            detail = (t.javaClass.simpleName + ": " + (t.message ?: "")).take(200)
            false
        }
        cached = ok
        return ok
    }

    fun generate(system: String, user: String, temperature: Float): Flow<String> = flow {
        val req = generateContentRequest(TextPart("$system\n\n$user")) {
            this.temperature = temperature
            topK = 16
            maxOutputTokens = 256
        }
        val sb = StringBuilder()
        client().generateContentStream(req).collect { chunk ->
            sb.append(chunk.candidates.firstOrNull()?.text.orEmpty())
            emit(sb.toString())
        }
    }
}

/** Model files: .gguf via llama.cpp (libnanollama), .litertlm via LiteRT-LM. One generation at a time. */
class LocalEngine(private val ctx: Context) {
    @Volatile private var lt: Engine? = null
    @Volatile private var gg = 0L
    @Volatile private var loadedKey: String? = null
    @Volatile private var activeConv: Conversation? = null
    private val loadLock = Mutex()
    private val genLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var unloadJob: Job? = null

    private val appDir: File get() = File(ctx.filesDir, "models").apply { mkdirs() }
    private fun dirs(): List<File> = listOfNotNull(SharedModels.readable(), appDir, ctx.getExternalFilesDir("models"))

    fun models(): List<File> = dirs().flatMap { it.listFiles()?.toList() ?: emptyList() }
        .filter { SharedModels.isModel(it) }.distinctBy { it.name }.sortedBy { it.name.lowercase() }

    fun find(name: String): File? = dirs().map { File(it, name) }.firstOrNull { it.exists() }

    private fun unloadLocked() {
        runCatching { lt?.close() }; lt = null
        if (gg != 0L) { runCatching { LlamaNative.free(gg) }; gg = 0L }
        loadedKey = null
    }

    fun cancel() {
        if (gg != 0L) runCatching { LlamaNative.stop() }
        activeConv?.let { runCatching { it.cancelProcess() } }
    }

    suspend fun release() { unloadJob?.cancel(); cancel(); genLock.withLock { loadLock.withLock { unloadLocked() } } }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch { delay(120_000); if (!genLock.isLocked) release() }
    }

    private fun file(s: AiSettings): File {
        if (s.model.isBlank()) throw AiException("Не выбрана модель. Выбери её в «Настройки → ИИ».")
        return find(s.model) ?: throw AiException("Файл ${s.model} не найден. Проверь доступ к папке ${SharedModels.path}.")
    }

    fun generate(system: String, user: String, temperature: Float, maxOut: Int, s: AiSettings): Flow<String> =
        if (s.model.endsWith(".gguf", true)) genGguf(system, user, temperature, maxOut, s) else genLiteRt(system, user, temperature, maxOut, s)

    private suspend fun ensureGguf(s: AiSettings): Long = loadLock.withLock {
        val f = file(s)
        val key = "gg|${f.path}|${s.ctxTokens}"
        if (gg != 0L && loadedKey == key) return gg
        withContext(Dispatchers.IO) {
            unloadLocked()
            if (!LlamaNative.available) throw AiException("Движок GGUF не загрузился на этом телефоне.")
            val h = LlamaNative.load(f.absolutePath, s.ctxTokens, 0)
            if (h == 0L) throw AiException("Не удалось загрузить ${f.name}: файл повреждён или не хватает памяти.")
            gg = h; loadedKey = key; h
        }
    }

    private fun genGguf(system: String, user: String, temperature: Float, maxOut: Int, s: AiSettings): Flow<String> = channelFlow {
        unloadJob?.cancel()
        genLock.withLock {
            val h = ensureGguf(s)
            val sb = StringBuilder()
            val stopped = AtomicBoolean(false)
            var lastEmit = 0L
            val rc = withContext(Dispatchers.IO) {
                LlamaNative.generate(h, arrayOf("system", "user"), arrayOf(system, user), temperature.coerceIn(0.05f, 1.5f), maxOut, 1.1f, false) { piece ->
                    if (stopped.get()) return@generate false
                    sb.append(piece)
                    val cut = stopIndex(sb)
                    if (cut >= 0 || looping(sb) || sb.length > 6000) {
                        if (cut >= 0) sb.setLength(cut)
                        stopped.set(true); return@generate false
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - lastEmit > 80) { lastEmit = now; trySend(sb.toString()) }
                    !isClosedForSend
                }
            }
            when (rc) {
                1 -> throw AiException("Модель использует неизвестный формат диалога.")
                2 -> throw AiException("Текст слишком длинный для модели. Увеличь контекст в настройках ИИ.")
                3 -> if (sb.isEmpty()) throw AiException("Модель не смогла ответить — возможно, не хватило памяти.")
            }
            send(sb.toString())
        }
        scheduleUnload()
    }.onCompletion { if (it != null) runCatching { LlamaNative.stop() } }

    private suspend fun ensureLt(s: AiSettings): Engine = loadLock.withLock {
        val f = file(s)
        val key = "lt|${f.path}|${s.ctxTokens}|${s.useGpu}"
        lt?.takeIf { loadedKey == key }?.let { return it }
        withContext(Dispatchers.IO) {
            unloadLocked()
            val backend: Backend = if (s.useGpu) Backend.GPU() else Backend.CPU()
            val e = Engine(EngineConfig(f.absolutePath, backend, null, null, s.ctxTokens, null, ctx.cacheDir.absolutePath))
            try { e.initialize() } catch (t: Throwable) {
                runCatching { e.close() }
                if (s.useGpu) throw AiException("Модель не запустилась на GPU (${t.message}). Выключи ускорение на GPU.")
                throw AiException("Не удалось загрузить модель: ${t.message}")
            }
            e.also { lt = it; loadedKey = key }
        }
    }

    private fun genLiteRt(system: String, user: String, temperature: Float, maxOut: Int, s: AiSettings): Flow<String> = channelFlow {
        unloadJob?.cancel()
        genLock.withLock {
            val engine = ensureLt(s)
            val conv = engine.createConversation(
                ConversationConfig(Contents.of(system), emptyList(), emptyList(),
                    SamplerConfig(40, 0.95, temperature.toDouble().coerceIn(0.05, 1.5), 0)),
            )
            activeConv = conv
            val stopped = AtomicBoolean(false)
            val sb = StringBuilder()
            var lastEmit = 0L
            try {
                conv.sendMessageAsync(
                    Contents.of(user), emptyMap(), RepetitionPenaltyConfig(1.12f, null, null, 256), null, null,
                    maxOut, ThinkingConfig(false, 0), null,
                ).collect { m ->
                    if (stopped.get()) return@collect
                    m.contents.contents.forEach { c -> if (c is Content.Text) sb.append(c.text) }
                    val cut = stopIndex(sb)
                    if (cut >= 0 || looping(sb) || sb.length > 6000) {
                        stopped.set(true); if (cut >= 0) sb.setLength(cut)
                        runCatching { conv.cancelProcess() }
                        return@collect
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - lastEmit > 80) { lastEmit = now; send(sb.toString()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!stopped.get() && sb.isEmpty()) throw AiException(e.message ?: "Ошибка модели")
            } finally {
                activeConv = null
                withContext(NonCancellable) { runCatching { conv.cancelProcess() }; runCatching { conv.close() } }
            }
            send(sb.toString())
        }
        scheduleUnload()
    }
}

/** Picks Gemini Nano or a model file according to settings. */
class Ai private constructor(private val ctx: Context) {
    val settings = AiSettings(ctx)
    val nano = NanoEngine()
    val local = LocalEngine(ctx)

    private suspend fun useNano(): Boolean = when (settings.engine) {
        EngineChoice.NANO -> if (nano.available()) true
            else throw AiException("Gemini Nano недоступна на этом телефоне (${nano.detail}). Выбери файл-модель в «Настройки → ИИ».")
        EngineChoice.FILE -> false
        EngineChoice.AUTO -> settings.model.isBlank() && nano.available()
    }

    /** How much book text fits into one request. */
    suspend fun budgetChars(): Int = if (useNano()) 2800 else ((settings.ctxTokens - 700) * 1.6).toInt().coerceIn(1200, 12000)

    suspend fun label(): String = runCatching { if (useNano()) "Gemini Nano" else settings.model.substringBeforeLast('.') }
        .getOrDefault("модель не выбрана")

    suspend fun stream(system: String, user: String, temperature: Float = 0.3f, maxOut: Int = 700): Flow<String> {
        if (useNano()) return nano.generate(system, user, temperature).flowOn(Dispatchers.Default)
        if (settings.model.isBlank()) throw AiException(
            "ИИ пока не настроен: Gemini Nano недоступна, а модель не выбрана. Открой «Настройки → ИИ» и выбери модель из папки ${SharedModels.path}.")
        return local.generate(system, user, temperature, maxOut, settings)
    }

    suspend fun complete(system: String, user: String, maxOut: Int = 400): String {
        var last = ""
        stream(system, user, 0.2f, maxOut).collect { last = it }
        return visibleAnswer(last).trim()
    }

    fun cancel() = local.cancel()

    companion object {
        @Volatile private var instance: Ai? = null
        fun get(ctx: Context): Ai = instance ?: synchronized(this) { instance ?: Ai(ctx.applicationContext).also { instance = it } }
    }
}
