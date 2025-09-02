package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShikimoriOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    // Assumes expired a minute earlier
    private val adjustedExpiresIn: Long = (expiresIn - 60)

    fun isExpired() = createdAt + adjustedExpiresIn < System.currentTimeMillis() / 1000
}
