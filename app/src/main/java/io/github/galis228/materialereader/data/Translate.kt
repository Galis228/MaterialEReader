package io.github.galis228.materialereader.data

import android.content.Context
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** On-device translation (ML Kit): works offline once the language packs are downloaded. */
object Translate {
    val targets = listOf("" to "Авто", TranslateLanguage.RUSSIAN to "Русский", TranslateLanguage.UKRAINIAN to "Українська", TranslateLanguage.ENGLISH to "English")

    fun target(ctx: Context): String = ctx.getSharedPreferences("dict", 0).getString("target", "")!!
    fun setTarget(ctx: Context, lang: String) = ctx.getSharedPreferences("dict", 0).edit().putString("target", lang).apply()

    /** Language of the text: ML Kit's guess, or by alphabet when it isn't sure. */
    suspend fun detect(text: String): String {
        val id = runCatching { LanguageIdentification.getClient().identifyLanguage(text).await() }.getOrDefault("und")
        if (id != "und" && TranslateLanguage.fromLanguageTag(id) != null) return id
        return when {
            text.any { it in "іїєґІЇЄҐ" } -> TranslateLanguage.UKRAINIAN
            text.any { it in 'а'..'я' || it in 'А'..'Я' } -> TranslateLanguage.RUSSIAN
            else -> TranslateLanguage.ENGLISH
        }
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
            return Result(client.translate(text).await(), source, to)
        } finally { client.close() }
    }

    fun name(lang: String) = when (lang) {
        TranslateLanguage.RUSSIAN -> "русский"; TranslateLanguage.UKRAINIAN -> "українська"; TranslateLanguage.ENGLISH -> "английский"
        else -> Locale(lang).getDisplayLanguage(Locale("ru"))
    }
}
