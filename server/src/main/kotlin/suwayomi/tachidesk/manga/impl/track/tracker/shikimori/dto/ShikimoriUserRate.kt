package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShikimoriUserRate(
    val id: Int,
    val score: Int,
    val status: String,
    val text: String? = null,
    val episodes: Int,
    val chapters: Int,
    val volumes: Int,
    val textHtml: String? = null,
    val rewatches: Int,
    val createdAt: String,
    val updatedAt: String,
    // val user: ShikimoriUser? = null,
    // val anime: ShikimoriManga? = null,
    val manga: ShikimoriManga? = null,
)

@Serializable
data class ShikimoriUserRateCreated(
    val id: Int,
    @SerialName("user_id")
    var userID: Int,
    @SerialName("target_id")
    var targetID: Int,
    @SerialName("target_type")
    var targetType: String,
    val score: Int,
    val status: String,
    val text: String? = null,
    val episodes: Int,
    val chapters: Int,
    val volumes: Int,
    val rewatches: Int,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// @Serializable
// data class ShikimoriUser(
//     val id: Int,
//     val nickname: String,
//     val avatar: String? = null,
//     val image: ShikimoriUserImage? = null,
//     @SerialName("last_online_at")
//     val lastOnlineAt: String? = null,
//     val url: String,
//     val name: String? = null,
//     val sex: String? = null,
//     @SerialName("full_years")
//     val fullYears: Int? = null,
//     @SerialName("last_online")
//     val lastOnline: String? = null,
//     val website: String? = null,
//     val location: String? = null,
//     val banned: Boolean? = null,
//     val about: String? = null,
//     @SerialName("about_html")
//     val aboutHtml: String? = null,
//     @SerialName("common_info")
//     val commonInfo: List<String>? = null,
//     @SerialName("show_comments")
//     val showComments: Boolean? = null,
//     @SerialName("in_friends")
//     val inFriends: Boolean? = null,
//     @SerialName("is_ignored")
//     val isIgnored: Boolean? = null,
//     val stats: ShikimoriUserStats? = null,
//     @SerialName("style_id")
//     val styleId: Int? = null,
// )

// @Serializable
// data class ShikimoriUserImage(
//     val x160: String? = null,
//     val x148: String? = null,
//     val x80: String? = null,
//     val x64: String? = null,
//     val x48: String? = null,
//     val x32: String? = null,
//     val x16: String? = null,
// )

// @Serializable
// data class ShikimoriUserStats(
//     val statuses: ShikimoriStatusStats? = null,
//     @SerialName("full_statuses")
//     val fullStatuses: ShikimoriStatusStats? = null,
//     val scores: ShikimoriScoreStats? = null,
//     val types: ShikimoriTypeStats? = null,
//     val ratings: ShikimoriRatingStats? = null,
//     @SerialName("has_anime?")
//     val hasAnime: Boolean? = null,
//     @SerialName("has_manga?")
//     val hasManga: Boolean? = null,
//     val genres: List<String>? = null,
//     val studios: List<String>? = null,
//     val publishers: List<String>? = null,
//     val activity: List<ShikimoriActivity>? = null,
// )

// @Serializable
// data class ShikimoriStatusStats(
//     val anime: List<ShikimoriStatusStat>? = null,
//     val manga: List<ShikimoriStatusStat>? = null,
// )

// @Serializable
// data class ShikimoriStatusStat(
//     val id: Int,
//     @SerialName("grouped_id")
//     val groupedId: String,
//     val name: String,
//     val size: Int,
//     val type: String,
// )

// @Serializable
// data class ShikimoriScoreStats(
//     val anime: List<ShikimoriScoreStat>? = null,
//     val manga: List<ShikimoriScoreStat>? = null,
// )

// @Serializable
// data class ShikimoriScoreStat(
//     val name: String,
//     val value: Int,
// )

// @Serializable
// data class ShikimoriTypeStats(
//     val anime: List<ShikimoriTypeStat>? = null,
//     val manga: List<ShikimoriTypeStat>? = null,
// )

// @Serializable
// data class ShikimoriTypeStat(
//     val name: String,
//     val value: Int,
// )

// @Serializable
// data class ShikimoriRatingStats(
//     val anime: List<ShikimoriRatingStat>? = null,
// )

// @Serializable
// data class ShikimoriRatingStat(
//     val name: String,
//     val value: Int,
// )

// @Serializable
// data class ShikimoriActivity(
//     val name: List<Long>,
//     val value: Int,
// )
