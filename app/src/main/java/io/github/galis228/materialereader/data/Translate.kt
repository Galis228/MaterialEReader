package io.github.galis228.materialereader.data

import android.content.Context
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** On-device translation (ML Kit): works offline once the language packs are downloaded. */
object Translate {
    val targets = listOf("" to "Авто", TranslateLanguage.RUSSIAN to "Русский", TranslateLanguage.UKRAINIAN to "Українська", TranslateLanguage.ENGLISH to "English",
        TranslateLanguage.SPANISH to "Español", TranslateLanguage.ITALIAN to "Italiano", TranslateLanguage.GERMAN to "Deutsch", TranslateLanguage.FRENCH to "Français")

    /** Languages offered as "from" when the guess is wrong. */
    val sources = listOf(TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH, TranslateLanguage.ITALIAN, TranslateLanguage.GERMAN,
        TranslateLanguage.FRENCH, TranslateLanguage.RUSSIAN, TranslateLanguage.UKRAINIAN)

    fun target(ctx: Context): String = ctx.getSharedPreferences("dict", 0).getString("target", "")!!
    fun setTarget(ctx: Context, lang: String) = ctx.getSharedPreferences("dict", 0).edit().putString("target", lang).apply()

    /**
     * Language of the text, found automatically. Single foreign words inside a Russian book («madre»,
     * «signore») are too short for a confident guess, so the alphabet and accents decide first, then
     * ML Kit's list of likely languages (with a low threshold), then English.
     */
    suspend fun detect(text: String): String {
        val t = text.trim()
        guessByLetters(t)?.let { return it }
        val client = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.05f).build())
        val cands = runCatching { client.identifyPossibleLanguages(t).await() }.getOrDefault(emptyList())
            .filter { it.languageTag != "und" && TranslateLanguage.fromLanguageTag(it.languageTag) != null }
            .sortedByDescending { it.confidence }
        client.close()
        val best = cands.firstOrNull()
        // a long text is trusted as is; a short one only among the languages people actually read here
        if (best != null && (best.confidence >= 0.5f || t.length > 40)) return best.languageTag
        cands.firstOrNull { it.languageTag in sources }?.let { return it.languageTag }
        return best?.languageTag ?: TranslateLanguage.ENGLISH
    }

    /** Cyrillic by its letters; Latin-script languages by letters only they use. */
    fun guessByLetters(t: String): String? = when {
        t.any { it in "іїєґІЇЄҐ" } -> TranslateLanguage.UKRAINIAN
        t.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' } -> TranslateLanguage.RUSSIAN
        t.any { it in "ñÑ¿¡" } -> TranslateLanguage.SPANISH
        t.any { it in "ßäöüÄÖÜ" } -> TranslateLanguage.GERMAN
        t.any { it in "çœÇŒêâîôûëïÊÂÎÔÛËÏ" } -> TranslateLanguage.FRENCH
        // ì and ò are Italian only; so is a word ending in an accented vowel (città, più) — except French là, où, déjà
        t.any { it in "ìòÌÒ" } || Regex("\\p{L}+[àèù](?!\\p{L})").findAll(t.lowercase()).any { it.value !in setOf("là", "déjà", "où", "voilà", "café", "été") } ->
            TranslateLanguage.ITALIAN
        else -> null
    }

    private fun chooseTarget(ctx: Context, source: String): String {
        val chosen = target(ctx).ifEmpty {
            when (Locale.getDefault().language) { "uk" -> TranslateLanguage.UKRAINIAN; "ru" -> TranslateLanguage.RUSSIAN; else -> TranslateLanguage.ENGLISH }
        }
        if (chosen != source) return chosen
        // same language as the book: translate into English (or from English into the phone's language)
        return if (source == TranslateLanguage.ENGLISH) (if (Locale.getDefault().language == "uk") TranslateLanguage.UKRAINIAN else TranslateLanguage.RUSSIAN)
        else TranslateLanguage.ENGLISH
    }

    /** True if translating from [source] to the target still needs a language pack download. */
    suspend fun needsDownload(ctx: Context, source: String): Boolean = runCatching {
        val have = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }
        val tgt = chooseTarget(ctx, source)
        listOf(source, tgt).any { it != TranslateLanguage.ENGLISH && it !in have }
    }.getOrDefault(true)

    data class Result(val text: String, val from: String, val to: String)

    suspend fun translate(ctx: Context, text: String, source: String): Result {
        val to = chooseTarget(ctx, source)
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(to).build())
        try {
            client.downloadModelIfNeeded().await()
            val out = client.translate(text).await()
            Stats.get(ctx).bump("translate"); Stats.get(ctx).addLanguage(source)
            return Result(out, source, to)
        } finally { client.close() }
    }

    fun name(lang: String) = when (lang) {
        TranslateLanguage.RUSSIAN -> "русский"; TranslateLanguage.UKRAINIAN -> "українська"; TranslateLanguage.ENGLISH -> "английский"
        else -> Locale(lang).getDisplayLanguage(Locale("ru"))
    }
}
