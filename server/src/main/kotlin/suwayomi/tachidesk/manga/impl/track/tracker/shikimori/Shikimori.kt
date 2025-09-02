package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import android.annotation.StringRes
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriUser
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.ShikimoriUserRate
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Shikimori(
    id: Int,
) : Tracker(id, "Shikimori"),
    DeletableTracker {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { ShikimoriInterceptor(this) }

    private val api by lazy { ShikimoriApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = false

    private val logger = KotlinLogging.logger {}

    override fun getLogo(): String = "/static/tracker/shikimori.png"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)

    @StringRes
    override fun getStatus(status: Int): String? =
        when (status) {
            READING -> "Reading"
            PLAN_TO_READ -> "Plan to read"
            COMPLETED -> "Completed"
            ON_HOLD -> "On hold"
            DROPPED -> "Dropped"
            REREADING -> "Rereading"
            else -> null
        }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = REREADING

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    private suspend fun add(track: Track): Track {
        val userId = getUserId() ?: throw IOException("User ID not found. Please re-authenticate.")
        val userRate = api.addUserRate(track, userId)
        track.library_id = userRate.id.toLong()
        return track
    }

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED && didReadChapter) {
            if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                track.status = COMPLETED
                track.finished_reading_date = System.currentTimeMillis()
            } else if (track.status != REREADING) {
                track.status = READING
                if (track.last_chapter_read == 1.0) {
                    track.started_reading_date = System.currentTimeMillis()
                }
            }
        }

        return api.updateUserRate(track).let { track }
    }

    override suspend fun delete(track: Track) {
        api.deleteUserRate(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteManga = api.getMangaDetailsWithUserRate(track.remote_id.toString())
        val remoteTrack = remoteManga.userRate
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack.toTrack())
            track.remote_id = remoteManga.id.toLong()
            track.library_id = remoteTrack.id.toLong()

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteManga = api.getMangaDetailsWithUserRate(track.remote_id.toString())
        val remoteTrack = remoteManga.userRate
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack.toTrack())
            track.title = remoteTrack.manga?.name ?: track.title
            track.total_chapters = remoteTrack.manga?.chapters ?: track.total_chapters
            track
        } else {
            add(track)
        }
    }

    override fun authUrl(): String = ShikimoriApi.authUrl().toString()

    override suspend fun authCallback(url: String) {
        val code = url.extractToken("code") ?: throw IOException("cannot find token")
        login(code)
    }

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(authCode: String) {
        try {
            val oauth = api.getAccessToken(authCode)
            interceptor.setAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.id.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            logger.error(e) { "oauth err" }
            logout()
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.setTrackToken(this, null)
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: ShikimoriOAuth?) {
        trackPreferences.setTrackToken(this, json.encodeToString(oAuth))
    }

    fun loadOAuth(): ShikimoriOAuth? =
    try {
        json.decodeFromString<ShikimoriOAuth>(trackPreferences.getTrackToken(this)!!)
    } catch (e: Exception) {
        logger.error(e) { "loadOAuth err" }
        null
    }

    fun getUserId(): Int? = getUsername()?.toIntOrNull()

    suspend fun refreshAccessToken(refreshToken: String): ShikimoriOAuth = api.refreshAccessToken(refreshToken)

    private fun ShikimoriUserRate.toTrack(): Track =
        Track.create(this@Shikimori.id).apply {
            remote_id = this@toTrack.manga?.id?.toLong() ?: 0L
            library_id = this@toTrack.id.toLong()
            title = this@toTrack.manga?.name ?: ""
            last_chapter_read = this@toTrack.chapters.toDouble()
            total_chapters = this@toTrack.manga?.chapters ?: 0
            score = this@toTrack.score.toDouble()
            status = this@toTrack.toShikimoriStatus()
            tracking_url = this@toTrack.manga?.url ?: ""
            started_reading_date = 0L
            finished_reading_date = 0L
            private = false
        }

    private fun ShikimoriUserRate.toShikimoriStatus(): Int =
        when (status) {
            "watching" -> READING
            "completed" -> COMPLETED
            "on_hold" -> ON_HOLD
            "dropped" -> DROPPED
            "planned" -> PLAN_TO_READ
            "rewatching" -> REREADING
            else -> PLAN_TO_READ
        }
}
