package eu.kanade.tachiyomi.extension.pt.kaktustoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.time.Instant

@Serializable
class SeriesDto(
    private val slug: String,
    private val name: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("search_names") private val searchNames: List<String> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = name
        description = this@SeriesDto.description?.takeIf(String::isNotBlank)
        thumbnail_url = coverUrl?.let { baseUrl + it }
    }

    fun toSChapterList(siteLang: String) = chapters
        .map { it.toSChapter("/$siteLang/anime/$slug") }
        .sortedByDescending(SChapter::chapter_number)

    fun matches(query: String): Boolean {
        val needle = query.normalized()
        return (searchNames + name).any { it.normalized().contains(needle) }
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: String,
    private val title: String? = null,
    @SerialName("ilk_yayin") private val publishedAt: String? = null,
) {
    fun toSChapter(seriesPath: String) = SChapter.create().apply {
        url = "$seriesPath/$slug"
        name = buildString {
            append("Capítulo $number")
            this@ChapterDto.title?.takeIf(String::isNotBlank)?.let { append(" - $it") }
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPagesDto(
    private val pages: List<PageDto>,
) {
    fun toPageList(baseUrl: String) = pages
        .sortedBy(PageDto::index)
        .mapIndexed { index, page -> Page(index, imageUrl = baseUrl + page.imageUrl) }
}

// Pages without text are never translated: tr_url comes back null and the site shows the original.
@Serializable
class PageDto(
    val index: Int,
    @SerialName("tr_url") private val trUrl: String? = null,
    @SerialName("src_url") private val srcUrl: String,
) {
    val imageUrl get() = trUrl ?: srcUrl
}

private val MARKS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

// Lets "vilao" find "vilão", since the search runs over the catalog in the app.
private fun String.normalized() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(MARKS_REGEX, "").lowercase().trim()
