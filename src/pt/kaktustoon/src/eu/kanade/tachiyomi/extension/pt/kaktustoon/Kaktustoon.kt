package eu.kanade.tachiyomi.extension.pt.kaktustoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Kaktustoon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val apiUrl get() = "$baseUrl/api/public"

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog().toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog().sortedByDescending(SeriesDto::updatedAt).toMangasPage()

    // The site has no search endpoint and its whole catalog comes in a single response.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchCatalog().filter { it.matches(query) }.toMangasPage()

    private suspend fun fetchCatalog(): List<SeriesDto> = client.get("$apiUrl/series?lang=$SITE_LANG", Fetch.XHR).parseAs()

    private fun List<SeriesDto>.toMangasPage() = MangasPage(map { it.toSManga(baseUrl) }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.getOrNull(0) != SITE_LANG || segments.getOrNull(1) != "anime") return null
        val slug = segments.getOrNull(2)?.takeIf(String::isNotBlank) ?: return null

        return fetchSeries(slug).toSManga(baseUrl).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$SITE_LANG/anime/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchSeries(manga.url)
        return SMangaUpdate(manga = series.toSManga(baseUrl), chapters = series.toSChapterList(SITE_LANG))
    }

    private suspend fun fetchSeries(slug: String): SeriesDto = client.get("$apiUrl/series/$slug", Fetch.XHR).parseAs()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterSlug) = chapter.url.split('/').takeLast(2)

        return client.get("$apiUrl/series/$slug/chapters/$chapterSlug", Fetch.XHR.from(getChapterUrl(chapter)))
            .parseAs<ChapterPagesDto>()
            .toPageList(baseUrl)
    }

    companion object {
        private const val SITE_LANG = "pt"
    }
}
