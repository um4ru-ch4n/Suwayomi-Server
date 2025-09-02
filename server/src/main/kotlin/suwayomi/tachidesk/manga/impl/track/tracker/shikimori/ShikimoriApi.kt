package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import java.io.IOException
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriManga
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriSearchData
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriUser
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriUserRate
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriUserRateCreated
import uy.kohesive.injekt.injectLazy

class ShikimoriApi(
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {
    private val json: Json by injectLazy()
    private val logger = KotlinLogging.logger {}

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    companion object {
        private const val BASE_URL = "https://shikimori.one"
        private const val API_URL = "$BASE_URL/api"
        private const val GRAPHQL_URL = "$BASE_URL/api/graphql"
        private const val OAUTH_URL = "$BASE_URL/oauth"

        const val CLIENT_ID = "wmAYL0J5czw50zyVmbZXI2vfOmTqvT4qYt-SQScYdgc"
        const val CLIENT_SECRET = "z_khyAWfpJuLOsEci9C-gpFqugUuDvcmD-4U9E7t5Tw"

        fun authUrl(): Uri = "$OAUTH_URL/authorize".toUri()
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", "https://suwayomi.org/tracker-oauth")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "user_rates") // Add required scope for user_rates
            .build()
    }

    suspend fun getAccessToken(authCode: String): ShikimoriOAuth =
        withIOContext {
            val formBody: RequestBody =
                FormBody
                    .Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", authCode)
                    .add("redirect_uri", "https://suwayomi.org/tracker-oauth")
                    .build()
            
            with(json) {
                client
                    .newCall(POST("$OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    suspend fun refreshAccessToken(refreshToken: String): ShikimoriOAuth =
        withIOContext {
            val formBody: RequestBody =
                FormBody
                    .Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("refresh_token", refreshToken)
                    .build()
    
            with(json) {
                client
                    .newCall(POST("$OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    suspend fun getCurrentUser(): ShikimoriUser =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET("$API_URL/users/whoami"))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    suspend fun search(query: String): List<TrackSearch> =
        withIOContext {
            val graphqlQuery = """
                query Mangas(${'$'}search: String, ${'$'}limit: Int) {
                    mangas(search: ${'$'}search, limit: ${'$'}limit) {
                        id
                        name
                        russian
                        english
                        japanese
                        synonyms
                        kind
                        score
                        status
                        chapters
                        volumes
                        airedOn {
                            date
                        }
                        releasedOn {
                            date
                        }
                        poster {
                            originalUrl
                            previewUrl
                        }
                        url
                        description
                        descriptionHtml
                        descriptionSource
                        franchise
                        isCensored
                        licenseNameRu
                        malId
                        opengraphImageUrl
                        createdAt
                        updatedAt
                        userRate {
                            id
                            volumes
                            chapters
                            createdAt
                            episodes
                            rewatches
                            score
                            status
                            text
                            updatedAt
                        }
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", graphqlQuery)
                putJsonObject("variables") {
                    put("search", query)
                    put("limit", 20)
                }
            }

            with(json) {
                authClient
                    .newCall(
                        POST(
                            GRAPHQL_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { response ->
                        val data = response["data"]?.jsonObject
                        if (data != null) {
                            val mangas = data["mangas"]?.jsonArray
                            if (mangas != null) {
                                mangas.map { mangaElement ->
                                    json.decodeFromJsonElement<ShikimoriManga>(mangaElement).toTrackSearch()
                                }
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }
            }
        }

    suspend fun getMangaDetails(id: String): TrackSearch =
        withIOContext {
            val graphqlQuery = """
                query Mangas(${'$'}id: ID!) {
                    manga(ids: ${'$'}id) {
                        id
                        volumes
                        chapters
                        createdAt
                        description
                        descriptionHtml
                        descriptionSource
                        english
                        franchise
                        isCensored
                        japanese
                        kind
                        licenseNameRu
                        licensors
                        malId
                        name
                        opengraphImageUrl
                        russian
                        score
                        status
                        synonyms
                        updatedAt
                        url
                        userRate {
                            chapters
                            createdAt
                            episodes
                            id
                            rewatches
                            score
                            status
                            text
                            updatedAt
                            volumes
                        }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", graphqlQuery)
                putJsonObject("variables") {
                    put("id", id)
                }
            }

            with(json) {
                authClient
                    .newCall(
                        POST(
                            GRAPHQL_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { response ->
                        // Parse the response to get manga data
                        val data = response["data"]?.jsonObject
                        if (data != null) {
                            val mangaData = data["mangas"]
                            if (mangaData != null) {
                                json.decodeFromJsonElement<ShikimoriManga>(mangaData).toTrackSearch()
                            } else {
                                throw Exception("Manga not found")
                            }
                        } else {
                            throw Exception("Manga not found")
                        }
                    }
            }
        }
    
    suspend fun getMangaDetailsWithUserRate(id: String): ShikimoriManga =
        withIOContext {
            val graphqlQuery = """
                query Mangas {
                    mangas(ids: "${id}") {
                        id
                        name
                        russian
                        english
                        japanese
                        synonyms
                        kind
                        score
                        status
                        chapters
                        volumes
                        airedOn {
                            date
                        }
                        releasedOn {
                            date
                        }
                        poster {
                            originalUrl
                            previewUrl
                        }
                        url
                        description
                        descriptionHtml
                        descriptionSource
                        isCensored
                        licenseNameRu
                        malId
                        opengraphImageUrl
                        createdAt
                        updatedAt
                        userRate {
                            chapters
                            createdAt
                            episodes
                            id
                            rewatches
                            score
                            status
                            text
                            updatedAt
                            volumes
                        }
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", graphqlQuery)
            }

            with(json) {
                authClient
                    .newCall(
                        POST(
                            GRAPHQL_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .use { response ->
                        val responseBody = response.parseAs<JsonObject>()
                        // Parse the response to get manga data
                        val data = responseBody["data"]?.jsonObject
                        if (data != null) {
                            val mangasArray = data["mangas"]?.jsonArray
                            if (mangasArray != null && mangasArray.size > 0) {
                                json.decodeFromJsonElement<ShikimoriManga>(mangasArray[0])
                            } else {
                                throw Exception("Manga not found")
                            }
                        } else {
                            throw Exception("Manga not found")
                        }
                    }
            }
        }

    // ===== REST API v2 METHODS (MUTATIONS) =====

    suspend fun addUserRate(track: Track, userId: Int): ShikimoriUserRateCreated =
        withIOContext {
            // Create JSON manually to ensure user_id is treated as string
            val requestBodyString = """
                {
                    "user_rate": {
                        "status": "${track.toShikimoriStatus()}",
                        "target_id": "${track.remote_id}",
                        "target_type": "Manga",
                        "user_id": "$userId",
                        "chapters": "${track.last_chapter_read.toInt()}"
                    }
                }
            """.trimIndent()

            logger.info { "addUserRate POST: $requestBodyString" }

            with(json) {
                authClient
                    .newCall(POST("$API_URL/v2/user_rates", body = requestBodyString.toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .use { response ->
                        val responseBody = response.parseAs<JsonObject>()
                        logger.info { "addUserRate response: $responseBody" }
                        json.decodeFromJsonElement<ShikimoriUserRateCreated>(responseBody)
                    }
            }
        }

    suspend fun updateUserRate(track: Track) =
        withIOContext {
            val requestBody = buildJsonObject {
                putJsonObject("user_rate") {
                    put("chapters", track.last_chapter_read.toInt().toString())
                    put("score", track.score.toInt().toString())
                    put("status", track.toShikimoriStatus())
                }
            }

            with(json) {
                authClient
                    .newCall(PATCH("$API_URL/v2/user_rates/${track.library_id}", body = requestBody.toString().toRequestBody(jsonMime)))
                    .awaitSuccess()
                    .use { response ->
                        // Response body is consumed and closed automatically
                    }
            }
        }
    
    suspend fun incrementUserRate(track: Track) =
        withIOContext {
            with(json) {
                authClient
                    .newCall(POST("$API_URL/v2/user_rates/${track.library_id}/increment"))
                    .awaitSuccess()
                    .use { response ->
                        // Response body is consumed and closed automatically
                    }
            }
        }

    suspend fun deleteUserRate(track: Track) =
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.library_id}"))
                .awaitSuccess()
                .use { response ->
                    // Response body is consumed and closed automatically
                }
        }

    private fun ShikimoriManga.toTrackSearch(): TrackSearch =
        TrackSearch.create(TrackerManager.SHIKIMORI).apply {
            remote_id = this@toTrackSearch.id.toLong()
            library_id = this@toTrackSearch.userRate?.id?.toLong()
            title = this@toTrackSearch.name
            total_chapters = this@toTrackSearch.chapters
            tracking_url = "$BASE_URL${this@toTrackSearch.url}"
            cover_url = this@toTrackSearch.poster?.originalUrl ?: ""
            summary = this@toTrackSearch.description ?: ""
            publishing_status = this@toTrackSearch.status ?: ""
            publishing_type = this@toTrackSearch.kind ?: ""
            start_date = this@toTrackSearch.airedOn?.date ?: ""
            authors = emptyList() 
            artists = emptyList()
        }

    private fun Track.toShikimoriStatus(): String =
        when (status) {
            1 -> "watching" // Reading
            2 -> "completed"
            3 -> "on_hold"
            4 -> "dropped"
            5 -> "planned" // Plan to read
            6 -> "rewatching" // Rereading
            else -> "planned"
        }
}
