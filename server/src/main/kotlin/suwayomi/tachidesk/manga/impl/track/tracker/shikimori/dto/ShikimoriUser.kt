package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShikimoriUser(
    val id: Int,
    val nickname: String,
    val avatar: String? = null,
    @SerialName("last_online_at")
    val lastOnlineAt: String? = null,
    val url: String,
    val name: String? = null,
    val sex: String? = null,
    val banned: Boolean? = null,
)