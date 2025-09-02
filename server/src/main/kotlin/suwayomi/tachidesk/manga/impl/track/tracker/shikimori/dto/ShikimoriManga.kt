package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShikimoriManga(
    val id: String,
    val name: String,
    val russian: String? = null,
    val english: String? = null,
    val japanese: String? = null,
    val synonyms: List<String> = emptyList(),
    val kind: String? = null,
    val score: Double? = null,
    val status: String? = null,
    val chapters: Int,
    val volumes: Int,
    val airedOn: ShikimoriDate? = null,
    val releasedOn: ShikimoriDate? = null,
    val poster: ShikimoriPoster? = null,
    val url: String,
    val description: String? = null,
    val descriptionHtml: String? = null,
    val descriptionSource: String? = null,
    val franchise: String? = null,
    val isCensored: Boolean? = null,
    val licenseNameRu: String? = null,
    val malId: String? = null,
    val opengraphImageUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val userRate: ShikimoriUserRate? = null,
)

@Serializable
data class ShikimoriDate(
    val date: String? = null,
)

@Serializable
data class ShikimoriPoster(
    val originalUrl: String? = null,
    val previewUrl: String? = null,
)
