package suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShikimoriSearchResult(
    val data: ShikimoriSearchData,
)

@Serializable
data class ShikimoriSearchData(
    val mangas: List<ShikimoriManga>,
)

@Serializable
data class ShikimoriUserRatesResult(
    val data: ShikimoriUserRatesData,
)

@Serializable
data class ShikimoriUserRatesData(
    val userRates: List<ShikimoriUserRate>,
)
