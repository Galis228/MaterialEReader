package io.github.galis228.materialereader.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.galis228.materialereader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ApiProvider(val label: String, val defaultUrl: String, val modelHint: String) {
    OPENAI("OpenAI-совместимый", "https://api.openai.com/v1", "например, gpt-4.1-mini или deepseek-chat"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "например, gemini-2.5-flash"),
    CLAUDE("Claude", "https://api.anthropic.com/v1", "например, claude-sonnet-5-5"),
}

data class ApiConfig(val provider: ApiProvider, val baseUrl: String, val model: String, val key: String) {
    val ready get() = model.isNotBlank() && (key.isNotBlank() || isLocalServer)
    /** LM Studio / Ollama on the local network don't need a key. */
    private val isLocalServer get() = provider == ApiProvider.OPENAI && Regex("https?://(localhost|127\\.|10\\.|192\\.168\\.)").containsMatchIn(baseUrl)
}

/** Encrypts the API key with a key that never leaves the Android Keystore. */
object KeyVault {
    private const val ALIAS = "materialereader_api_key"
    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String = runCatching {
        if (stored.isEmpty()) return ""
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, all, 0, 12)) }
        String(c.doFinal(all, 12, all.size - 12))
    }.getOrDefault("")
}

/** Cloud models over HTTPS with streaming (server-sent events). */
object ApiEngine {

    fun stream(cfg: ApiConfig, system: String, user: String, temperature: Float, maxOut: Int): Flow<String> = flow {
        val base = cfg.baseUrl.trim().trimEnd('/')
        val (url, headers, body) = when (cfg.provider) {
            ApiProvider.OPENAI -> Triple(
                "$base/chat/completions",
                mapOf("Authorization" to "Bearer ${cfg.key.trim()}"),
                JSONObject().put("model", cfg.model.trim()).put("stream", true).put("temperature", temperature.toDouble())
                    .put("max_tokens", maxOut)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user))),
            )
            ApiProvider.GEMINI -> Triple(
                "$base/models/${cfg.model.trim()}:streamGenerateContent?alt=sse",
                mapOf("x-goog-api-key" to cfg.key.trim()),
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                    .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()).put("maxOutputTokens", maxOut)),
            )
            ApiProvider.CLAUDE -> Triple(
                "$base/messages",
                mapOf("x-api-key" to cfg.key.trim(), "anthropic-version" to "2023-06-01"),
                JSONObject().put("model", cfg.model.trim()).put("system", system).put("max_tokens", maxOut).put("stream", true)
                    .put("temperature", temperature.toDouble())
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user))),
            )
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000; readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", "MaterialEReader/${BuildConfig.VERSION_NAME}")
            headers.forEach { (k, v) -> if (v.isNotEmpty()) setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                throw AiException(explain(code, err))
            }
            val sb = StringBuilder()
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = runCatching { piece(cfg.provider, JSONObject(data)) }.getOrNull() ?: continue
                    if (piece.isNotEmpty()) { sb.append(piece); emit(sb.toString()) }
                }
            }
            if (sb.isEmpty()) throw AiException("Сервис вернул пустой ответ. Проверь название модели.")
        } finally { conn.disconnect() }
    }.flowOn(Dispatchers.IO)

    /** Models the account can use, straight from the service (needs the key; LM Studio and similar need none). */
    suspend fun listModels(cfg: ApiConfig): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val base = cfg.baseUrl.trim().trimEnd('/')
        val (url, headers) = when (cfg.provider) {
            ApiProvider.OPENAI -> "$base/models" to mapOf("Authorization" to "Bearer ${cfg.key.trim()}")
            ApiProvider.GEMINI -> "$base/models?pageSize=200" to mapOf("x-goog-api-key" to cfg.key.trim())
            ApiProvider.CLAUDE -> "$base/models?limit=100" to mapOf("x-api-key" to cfg.key.trim(), "anthropic-version" to "2023-06-01")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "MaterialEReader/${BuildConfig.VERSION_NAME}")
            headers.forEach { (k, v) -> if (v.isNotBlank() && !v.endsWith(" ")) setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw AiException(explain(code, runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()))
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            when (cfg.provider) {
                ApiProvider.GEMINI -> o.optJSONArray("models")?.let { a ->
                    (0 until a.length()).map { a.getJSONObject(it) }
                        .filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { g -> (0 until g.length()).any { g.optString(it) == "generateContent" } } != false }
                        .map { it.getString("name").removePrefix("models/") }
                }.orEmpty()
                else -> o.optJSONArray("data")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }.orEmpty()
            }.distinct().sorted()
        } finally { conn.disconnect() }
    }

    private fun piece(p: ApiProvider, o: JSONObject): String? = when (p) {
        ApiProvider.OPENAI -> o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
        ApiProvider.GEMINI -> o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?.let { parts -> (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") } }
        ApiProvider.CLAUDE -> if (o.optString("type") == "content_block_delta") o.optJSONObject("delta")?.optString("text") else null
    }?.takeUnless { it == "null" }

    private fun explain(code: Int, body: String): String {
        val msg = runCatching {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message") ?: o.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(200)
        val human = when (code) {
            400 -> "Сервис не принял запрос — проверь название модели"
            401, 403 -> "Ключ не подошёл"
            402 -> "Закончились деньги или кредиты на аккаунте"
            404 -> "Не найдено — проверь адрес и название модели"
            429 -> "Превышен лимит запросов или закончились кредиты"
            in 500..599 -> "Сервис сейчас не отвечает, попробуй позже"
            else -> "Ошибка сервиса ($code)"
        }
        return if (msg != null) "$human. $msg" else "$human."
    }
}
