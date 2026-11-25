package eu.kanade.tachiyomi.extension.pt.mangalivre

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    KeiSource(),
    ConfigurableSource {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    private val apiUrl: String get() = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")

    // ============================== Popular =======================================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(options = listOf("" to SORT_POPULAR)),
                OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
            ),
        ),
    )

    // ============================== Latest =======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(options = listOf("" to SORT_UPDATED)),
                OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
            ),
        ),
    )

    // ============================== Search =======================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url =
            "$apiUrl/mangas/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    url.addQueryParameter("sortBy", filter.selected())
                }
                is OrderDirectionFilter -> {
                    url.addQueryParameter("sortOrder", filter.selected())
                }
                else -> {}
            }
        }
        val dto = client.get(url.build(), Fetch.XHR).parseJson<WrapperDto>()
        val mangas = dto.mangas.map { it.toSManga(useAlternativeTitle) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/manga-by-slug/${manga.url}", Fetch.XHR).parseJson<MangaDto>()
        return SMangaUpdate(
            manga = dto.toSManga(useAlternativeTitle),
            chapters = dto.toSChapterList(),
        )
    }

    // ============================== Pages =======================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}"
        val ref = chapter.memo.parseAs<ChapterReferenceDto>()
        val access = fetchReaderAccess(chapterUrl, ref.toJsonRequestBody())
            ?: fetchReaderAccess(
                chapterUrl,
                ReaderAccessRequestDto(
                    ref.mangaId,
                    ref.chapterId,
                    getTurnstileToken(chapterUrl, TURNSTILE_SITE_KEY, action = TURNSTILE_ACTION),
                ).toJsonRequestBody(),
            )
            ?: throw IOException(READER_VERIFICATION_REQUIRED)

        return access.chapter.pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private suspend fun fetchReaderAccess(chapterUrl: String, body: RequestBody): ReaderAccessResponseDto? {
        client.post(
            "$apiUrl/reader/chapter/access",
            fetch = Fetch.XHR.from(chapterUrl),
            body = body,
            ensureSuccess = false,
        ).use { response ->
            if (response.isSuccessful) return response.parseAs()

            val error = response.parseAs<ReaderAccessErrorDto>()
            if (response.code == 403 && error.error == READER_VERIFICATION_REQUIRED) return null
            throw IOException(error.error)
        }
    }

    // ============================== Filters =======================================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOf(
            OrderByFilter(
                "Ordem",
                listOf(
                    "Mais Visualizados" to SORT_POPULAR,
                    "Lançamentos" to SORT_RELEASE,
                    "Última Atualização" to SORT_UPDATED,
                    "Melhor Avaliação" to SORT_RATING,
                    "A-Z" to SORT_TITLE,
                ),
            ),
            Filter.Separator(),
            OrderDirectionFilter(
                "Direção",
                listOf(
                    "↑ Decrescente" to DIRECTION_DESC,
                    "↓ Crescente" to DIRECTION_ASC,
                ),
            ),
        ),
    )

    val useAlternativeTitle: Boolean get() =
        preferences.getBoolean(ALTERNATIVE_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context)
            .apply {
                key = ALTERNATIVE_TITLE_PREF
                title = "Titulo alternativo"
                summary =
                    buildString {
                        append("Use titulos alternativos como principal quando disponivel.")
                        append(" Essa opção não tem efeito sobre obras já adicionadas na sua biblioteca")
                    }
                setDefaultValue(false)
            }.also(screen::addPreference)
    }

    // ============================== Utilities =======================================

    private inline fun <reified T> Response.parseJson(): T {
        val peek = peekBody(MAX_PEEK).string().trimStart()
        if (peek.isEmpty() || peek.startsWith("<")) {
            close()
            throw IOException(NON_JSON_MESSAGE)
        }
        return parseAs<T>()
    }

    companion object {
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAC_2UGhBWthpfExC"
        private const val TURNSTILE_ACTION = "reader-chapter"

        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val MAX_PEEK = 1024L
        private const val NON_JSON_MESSAGE =
            "Resposta não-JSON (Cloudflare ou header desatualizado). Abra a fonte na WebView do app e tente de novo."
        private const val READER_VERIFICATION_REQUIRED = "Reader verification required"

        private const val SORT_POPULAR = "popular"
        private const val SORT_RELEASE = "release"
        private const val SORT_UPDATED = "updated"
        private const val SORT_RATING = "rating"
        private const val SORT_TITLE = "title"
        private const val DIRECTION_DESC = "desc"
        private const val DIRECTION_ASC = "asc"
    }
}
