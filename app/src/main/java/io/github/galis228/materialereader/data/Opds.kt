package io.github.galis228.materialereader.data

import android.content.Context
import io.github.galis228.materialereader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Catalog(val name: String, val url: String, val searchTemplate: String? = null)

data class Acquisition(val url: String, val type: String) {
    val label: String get() = when {
        "epub" in type -> "EPUB"
        "fb2" in type || "fictionbook" in type -> "FB2"
        "cbz" in type || "comicbook+zip" in type -> "CBZ"
        "cbr" in type || "comicbook-rar" in type -> "CBR"
        "text/plain" in type -> "TXT"
        else -> type.substringAfter('/').take(10).uppercase()
    }
    /** Lower is better. Formats we can't open are filtered out. */
    val rank: Int get() = when (label) { "EPUB" -> 0; "FB2" -> 1; "CBZ" -> 2; "CBR" -> 3; "TXT" -> 4; else -> 99 }
}

data class OpdsEntry(
    val id: String, val title: String, val author: String, val summary: String,
    val coverUrl: String?, val navUrl: String?, val acquisitions: List<Acquisition>,
    /** Heading on an author's page («Проза», «Стихотворения 1830»…); poetry sections are folded in the list. */
    val section: String? = null,
)

/** Sections of an author page that list other people's writing about them. */
fun isAboutSection(s: String?): Boolean {
    val h = s?.lowercase() ?: return false
    return listOf("критик", "библиограф", "бібліограф", "см. также", "див. також", "ссылк", "посилан", "литератур", "літератур",
        "о нём", "о ней", "про нього", "про неї", "примечан", "примітк", "джерел", "источник", "see also", "about", "bibliograph",
        "critic", "works about", "external", "sources", "памят", "пам'ят").any { it in h }
}

/** Section headings that hold short poems rather than books. */
fun isPoetrySection(s: String?): Boolean {
    val h = s?.lowercase() ?: return false
    return listOf("стих", "вірш", "поэзи", "поезі", "лирик", "лірик", "poem", "poetry", "verse", "sonnet", "сонет", "песн", "пісн", "басн", "байк")
        .any { it in h }
}

data class OpdsFeed(val url: String, val title: String, val entries: List<OpdsEntry>, val next: String?, val searchTemplate: String?)

object Opds {
    val USER_AGENT = "MaterialEReader/${BuildConfig.VERSION_NAME} (https://github.com/Galis228/materialereader; Android e-book reader)"

    private val defaults = listOf(
        Catalog("Викитека — русская классика", "wikisource:ru", "wikisource:ru?q={searchTerms}"),
        Catalog("Вікіджерела — українська класика", "wikisource:uk", "wikisource:uk?q={searchTerms}"),
        Catalog("Project Gutenberg", "https://www.gutenberg.org/ebooks.opds/",
            "https://www.gutenberg.org/ebooks/search.opds/?query={searchTerms}"),
        Catalog("Wikisource (English)", "https://ws-export.wmcloud.org/opds/en/Ready_for_export.xml", "wikisource:en?q={searchTerms}"),
    )

    /** Catalogs added in a later version show up for people who already have a saved list. */
    private const val DEFAULTS_VERSION = 3

    fun catalogs(ctx: Context): List<Catalog> {
        val p = ctx.getSharedPreferences("opds", 0)
        val raw = p.getString("catalogs", null) ?: return defaults
        val saved = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Catalog(o.getString("name"), o.getString("url"), o.optString("search").ifEmpty { null }) } }
        }.getOrDefault(defaults)
        if (p.getInt("defaults_version", 1) >= DEFAULTS_VERSION) return saved
        val merged = defaults.filter { d -> saved.none { it.url == d.url } } + saved
        saveCatalogs(ctx, merged)
        return merged
    }

    fun saveCatalogs(ctx: Context, list: List<Catalog>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url).put("search", it.searchTemplate ?: "")) }
        ctx.getSharedPreferences("opds", 0).edit().putString("catalogs", a.toString()).putInt("defaults_version", DEFAULTS_VERSION).apply()
    }

    /** Classics that are free to read everywhere, as quick searches — matched to the catalog's language. */
    private val russianClassics = listOf("Пушкин", "Лермонтов", "Гоголь", "Тургенев", "Достоевский", "Толстой", "Чехов", "Куприн", "Крылов", "Гончаров")
    private val ukrainianClassics = listOf(
        "Шевченко", "Франко", "Леся Українка", "Коцюбинський", "Нечуй-Левицький", "Марко Вовчок", "Панас Мирний",
        "Котляревський", "Квітка-Основ'яненко", "Стефаник", "Кобилянська", "Грінченко", "Олесь", "Васильченко",
    )
    private val englishClassics = listOf(
        "Shakespeare", "Jane Austen", "Charles Dickens", "Mark Twain", "Arthur Conan Doyle", "Oscar Wilde", "Robert Louis Stevenson",
        "Charlotte Brontë", "Emily Brontë", "Jack London", "Edgar Allan Poe", "Herman Melville", "H. G. Wells", "Lewis Carroll",
        "Rudyard Kipling", "Jules Verne", "Mary Shelley", "Bram Stoker",
    )

    fun classicsFor(c: Catalog?): List<String> = when {
        c == null -> emptyList()
        c.url.startsWith("wikisource:uk") -> ukrainianClassics
        c.url.startsWith("wikisource:ru") -> russianClassics
        c.url.contains("gutenberg") || c.url.contains("/opds/en/") || c.url.startsWith("wikisource:en") -> englishClassics
        else -> emptyList()
    }

    /**
     * Wikisource has no OPDS search, so "wikisource:<lang>?q=…" searches its works through the MediaWiki API
     * and offers each as an EPUB made by the official WS Export tool.
     */
    private fun wsDownload(lang: String, t: String) =
        listOf(Acquisition("https://ws-export.wmcloud.org/?format=epub&lang=$lang&page=${URLEncoder.encode(t.replace(' ', '_'), "UTF-8")}", "application/epub+zip"))

    private fun wikisource(url: String): OpdsFeed {
        val lang = url.removePrefix("wikisource:").substringBefore('?').ifEmpty { "ru" }
        val title = when (lang) { "ru" -> "Викитека"; "uk" -> "Вікіджерела"; else -> "Wikisource" }
        val template = "wikisource:$lang?q={searchTerms}"
        val author = url.substringAfter("?author=", "").let { java.net.URLDecoder.decode(it, "UTF-8") }
        if (author.isNotBlank()) return runCatching { wikisourceAuthor(lang, author, template) }.getOrNull()
            ?: wikisource("wikisource:$lang?q=" + URLEncoder.encode(author, "UTF-8"))
        val q = url.substringAfter("?q=", "").let { java.net.URLDecoder.decode(it, "UTF-8") }
        if (q.isBlank()) return OpdsFeed(url, title, emptyList(), null, template)
        val api = "https://$lang.wikisource.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=40&format=json&srsearch=" +
            URLEncoder.encode(q, "UTF-8")
        val hits = JSONObject(String(get(api), Charsets.UTF_8)).optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
        val entries = (0 until hits.length()).map { hits.getJSONObject(it) }
            .map { it.getString("title") }
            .filter { '/' !in it } // whole works, not their chapters
            .distinct()
            .map { t -> OpdsEntry("ws:$lang:$t", t, "", "", null, null, wsDownload(lang, t)) }
        return OpdsFeed(url, "$title: «$q»", entries, null, template)
    }

    /**
     * An author's works as listed on their Wikisource page, grouped by the page's own sections
     * (prose and drama first, poems folded at the end).
     */
    private fun wikisourceAuthor(lang: String, name: String, template: String): OpdsFeed? {
        val title = when (lang) { "ru" -> "Викитека"; "uk" -> "Вікіджерела"; else -> "Wikisource" }
        // author pages live in namespace 102 ("Автор:", "Author:")
        val find = "https://$lang.wikisource.org/w/api.php?action=query&list=search&srnamespace=102&srlimit=1&format=json&srsearch=" +
            URLEncoder.encode(name, "UTF-8")
        val page = JSONObject(String(get(find), Charsets.UTF_8)).optJSONObject("query")?.optJSONArray("search")
            ?.optJSONObject(0)?.optString("title")?.takeIf { it.isNotEmpty() } ?: return null
        val parse = "https://$lang.wikisource.org/w/api.php?action=parse&prop=text&format=json&formatversion=2&disableeditsection=1&page=" +
            URLEncoder.encode(page, "UTF-8")
        val html = JSONObject(String(get(parse), Charsets.UTF_8)).optJSONObject("parse")?.optString("text") ?: return null
        val authorName = page.substringAfter(':')
        val doc = Jsoup.parse(html)
        val prose = ArrayList<OpdsEntry>(); val poems = ArrayList<OpdsEntry>()
        val seen = HashSet<String>()
        var section: String? = null
        for (el in doc.select("h2, h3, h4, li a[href^=/wiki/]")) {
            if (el.tagName().matches(Regex("h[234]"))) { section = el.text().trim().ifEmpty { section }; continue }
            if (el.hasClass("new")) continue // red link: not transcribed yet
            if (isAboutSection(section)) continue // criticism, bibliography, "see also" — not the author's works
            val t = el.attr("title").ifEmpty { el.text() }.trim()
            val ns = t.substringBefore(':', "")
            if (t.isEmpty() || (ns.isNotEmpty() && ' ' !in ns && ns.length < 20)) continue // other namespaces: Автор:, Категория:, Файл:…
            if ('/' in t || !seen.add(t)) continue
            val e = OpdsEntry("ws:$lang:$t", t, authorName, "", null, null, wsDownload(lang, t), section)
            if (isPoetrySection(section)) poems += e else prose += e
        }
        if (prose.isEmpty() && poems.isEmpty()) return null
        return OpdsFeed("wikisource:$lang?author=$name", "$title: $authorName", prose + poems, null, template)
    }

    suspend fun load(url: String): OpdsFeed = withContext(Dispatchers.IO) {
        if (url.startsWith("wikisource:")) return@withContext wikisource(url)
        val f = parse(String(get(url), Charsets.UTF_8), url)
        if (f.searchTemplate != null || f.osdUrl == null) return@withContext f.feed
        val template = runCatching {
            val d = Jsoup.parse(String(get(f.osdUrl), Charsets.UTF_8), f.osdUrl, Parser.xmlParser())
            d.getElementsByTag("Url").firstOrNull { it.attr("type").contains("atom") }?.attr("template")
                ?.let { URL(URL(f.osdUrl), it).toString().replace("%7BsearchTerms%7D", "{searchTerms}") }
        }.getOrNull()
        f.feed.copy(searchTemplate = template)
    }

    class Parsed(val feed: OpdsFeed, val osdUrl: String?) { val searchTemplate get() = feed.searchTemplate }

    fun parse(xml: String, url: String): Parsed {
        val doc = Jsoup.parse(xml, url, Parser.xmlParser())
        val feed = doc.getElementsByTag("feed").firstOrNull() ?: error("Это не OPDS-каталог")
        fun abs(href: String) = resolve(url, href)
        fun links(e: Element) = e.children().filter { it.tagName() == "link" }

        val entries = feed.children().filter { it.tagName() == "entry" }.map { e ->
            val ls = links(e)
            val acq = ls.filter { it.attr("rel").contains("acquisition") && it.attr("href").isNotEmpty() }
                .map { Acquisition(abs(it.attr("href")), it.attr("type").lowercase()) }
                .filter { it.rank < 99 }
                .sortedBy { it.rank }
            val nav = ls.firstOrNull { l ->
                val t = l.attr("type")
                t.contains("atom+xml") && !l.attr("rel").contains("acquisition") && l.attr("rel") !in setOf("self", "alternate") ||
                    (t.contains("atom+xml") && l.attr("rel").isEmpty())
            } ?: ls.firstOrNull { it.attr("type").contains("atom+xml") && it.attr("rel") != "self" }
            val cover = ls.firstOrNull { it.attr("rel").contains("thumbnail") }
                ?: ls.firstOrNull { it.attr("rel").contains("image") }
            OpdsEntry(
                id = e.getElementsByTag("id").firstOrNull()?.text() ?: e.hashCode().toString(),
                title = e.children().firstOrNull { it.tagName() == "title" }?.text().orEmpty(),
                author = e.getElementsByTag("author").joinToString(", ") { a -> a.getElementsByTag("name").text() },
                summary = (e.getElementsByTag("summary").firstOrNull() ?: e.getElementsByTag("content").firstOrNull())
                    ?.text()?.let { Jsoup.parse(it).text() }?.take(300).orEmpty(),
                coverUrl = cover?.attr("href")?.takeIf { it.isNotEmpty() }?.let(::abs),
                navUrl = if (acq.isEmpty()) nav?.attr("href")?.takeIf { it.isNotEmpty() }?.let(::abs) else null,
                acquisitions = acq,
            )
        }.filter { it.navUrl != null || it.acquisitions.isNotEmpty() }

        val feedLinks = links(feed)
        val next = feedLinks.firstOrNull { it.attr("rel") == "next" }?.attr("href")?.let(::abs)
        val search = feedLinks.firstOrNull { it.attr("rel") == "search" && it.attr("href").contains("{searchTerms}") }
            ?.attr("href")?.let(::abs)?.replace("%7BsearchTerms%7D", "{searchTerms}")
        val osd = feedLinks.firstOrNull { it.attr("rel") == "search" && it.attr("type").contains("opensearch") }?.attr("href")?.let(::abs)
        return Parsed(OpdsFeed(url, feed.children().firstOrNull { it.tagName() == "title" }?.text().orEmpty(), entries, next, search), osd)
    }

    /** RFC 3986 resolution, including query-only references like "?start=26". */
    fun resolve(base: String, href: String): String =
        if (href.startsWith("?")) base.substringBefore('?').substringBefore('#') + href else URL(URL(base), href).toString()

    fun searchUrl(template: String, query: String): String =
        template.replace("{searchTerms}", URLEncoder.encode(query, "UTF-8"))
            .replace(Regex("\\{[a-zA-Z:]+\\?\\}"), "") // Android's regex engine needs the closing brace escaped

    /** Downloads a book; returns bytes and a file name with the right extension. */
    suspend fun download(a: Acquisition, title: String): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val bytes = get(a.url, maxBytes = 300L * 1024 * 1024)
        val ext = when (a.label) { "EPUB" -> "epub"; "FB2" -> if (a.type.contains("zip")) "fb2.zip" else "fb2"; "CBZ" -> "cbz"; "CBR" -> "cbr"; else -> "txt" }
        bytes to "${title.take(80).replace(Regex("[\\\\/:*?\"<>|]"), "_")}.$ext"
    }

    fun get(url: String, maxBytes: Long = 20L * 1024 * 1024): ByteArray {
        var u = URL(url)
        repeat(6) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                // Wikimedia and others reject requests without a descriptive agent with a contact link
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/atom+xml, application/xml, */*")
                connectTimeout = 15_000; readTimeout = 90_000
            }
            try {
                val code = c.responseCode
                if (code in 300..399) { u = URL(u, c.getHeaderField("Location")); return@repeat }
                if (code == 429) error("Сервис просит подождать: слишком много запросов. Попробуй через минуту.")
                if (code == 503) error("Каталог сейчас на обслуживании (503). Попробуй позже или другой каталог.")
                if (code != 200) error("Сервер ответил $code")
                c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n; if (total > maxBytes) error("Файл слишком большой")
                        out.write(buf, 0, n)
                    }
                    return out.toByteArray()
                }
            } finally { c.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
