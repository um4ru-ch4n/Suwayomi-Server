package eu.kanade.tachiyomi.util.chapter

/**
 * -R> = regex conversion.
 */
object ChapterRecognition {
    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    /**
     * All cases with Ch.xx
     * Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 4
     */
    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")

    /**
     * Example: Bleach 567: Down With Snowwhite -R> 567
     */
    private val number = Regex(NUMBER_PATTERN)

    /**
     * Regex used to remove unwanted tags
     * Example Prison School 12 v.1 vol004 version1243 volume64 -R> Prison School 12
     */
    private val unwanted = Regex("""\b(?:v|ver|vol|version|volume|season|s)[^a-z]?[0-9]+""")

    /**
     * Regex used to remove unwanted whitespace
     * Example One Piece 12 special -R> One Piece 12special
     */
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")

    /**
     * Regex patterns for volume and chapter recognition
     * Supports both Russian (Том/Глава) and English (Volume/Chapter) formats
     */
    private val volumeChapterPatterns = listOf(
        // Russian formats
        Regex("""том\s*(\d+).*?глава\s*(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""vol\.?\s*(\d+).*?ch\.?\s*(\d+)""", RegexOption.IGNORE_CASE),
        // English formats  
        Regex("""volume\s*(\d+).*?chapter\s*(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""vol\.?\s*(\d+).*?chapter\s*(\d+)""", RegexOption.IGNORE_CASE),
        // Mixed formats
        Regex("""volume\s*(\d+).*?глава\s*(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""том\s*(\d+).*?chapter\s*(\d+)""", RegexOption.IGNORE_CASE)
    )

    /**
     * Data class to hold volume and chapter information
     */
    data class VolumeChapterInfo(
        val volumeNumber: Int,
        val chapterNumber: Int
    )

    /**
     * Parse volume and chapter numbers from chapter name
     * @param chapterName The chapter name to parse
     * @return VolumeChapterInfo if volume/chapter pattern is found, null otherwise
     */
    fun parseVolumeAndChapter(chapterName: String): VolumeChapterInfo? {
        val cleanName = chapterName.lowercase().trim()
        
        for (pattern in volumeChapterPatterns) {
            val match = pattern.find(cleanName)
            if (match != null) {
                val volumeNum = match.groupValues[1].toIntOrNull()
                val chapterNum = match.groupValues[2].toIntOrNull()
                if (volumeNum != null && chapterNum != null) {
                    return VolumeChapterInfo(volumeNum, chapterNum)
                }
            }
        }
        return null
    }

    /**
     * Calculate cumulative chapter numbers for volume-based chapters
     * Based on the algorithm from convert_volumes_to_chapters.sh
     * @param chapters List of chapter names to analyze
     * @return Map of volume number to starting chapter number
     */
    fun calculateVolumeStartChapters(chapters: List<String>): Map<Int, Int> {
        val volumeLastChapter = mutableMapOf<Int, Int>()
        
        // First pass: analyze all chapters to determine volume structure
        for (chapterName in chapters) {
            val volumeChapter = parseVolumeAndChapter(chapterName)
            if (volumeChapter != null) {
                val currentLast = volumeLastChapter[volumeChapter.volumeNumber] ?: 0
                if (volumeChapter.chapterNumber > currentLast) {
                    volumeLastChapter[volumeChapter.volumeNumber] = volumeChapter.chapterNumber
                }
            }
        }
        
        // Second pass: calculate starting chapter numbers for each volume
        val volumeStartChapter = mutableMapOf<Int, Int>()
        var currentChapter = 1
        
        // Sort volumes by number
        val sortedVolumes = volumeLastChapter.keys.sorted()
        
        for (volumeNum in sortedVolumes) {
            volumeStartChapter[volumeNum] = currentChapter
            currentChapter += volumeLastChapter[volumeNum] ?: 0
        }
        
        return volumeStartChapter
    }

    /**
     * Parse chapter number with volume support
     * @param mangaTitle The manga title
     * @param chapterName The chapter name
     * @param chapterNumber Existing chapter number (if any)
     * @param volumeStartChapters Map of volume numbers to their starting chapter numbers
     * @return Parsed chapter number
     */
    fun parseChapterNumberWithVolumes(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
        volumeStartChapters: Map<Int, Int> = emptyMap()
    ): Double {
        // First try to parse as volume/chapter format
        val volumeChapter = parseVolumeAndChapter(chapterName)
        if (volumeChapter != null && volumeStartChapters.isNotEmpty()) {
            val volumeStart = volumeStartChapters[volumeChapter.volumeNumber] ?: 1
            val cumulativeChapterNumber = volumeStart + volumeChapter.chapterNumber - 1
            return cumulativeChapterNumber.toDouble()
        }
        
        // Fall back to original parsing logic
        return parseChapterNumber(mangaTitle, chapterName, chapterNumber)
    }

    fun parseChapterNumber(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
    ): Double {
        // If chapter number is known return.
        if (chapterNumber != null && (chapterNumber == -2.0 || chapterNumber > -1.0)) {
            return chapterNumber
        }

        // Get chapter title with lower case
        val cleanChapterName =
            chapterName
                .lowercase()
                // Remove manga title from chapter title.
                .replace(mangaTitle.lowercase(), "")
                .trim()
                // Remove comma's or hyphens.
                .replace(',', '.')
                .replace('-', '.')
                // Remove unwanted white spaces.
                .replace(unwantedWhiteSpace, "")

        val numberMatch = number.findAll(cleanChapterName)

        when {
            numberMatch.none() -> {
                return chapterNumber ?: -1.0
            }
            numberMatch.count() > 1 -> {
                // Remove unwanted tags.
                unwanted.replace(cleanChapterName, "").let { name ->
                    // Check base case ch.xx
                    basic.find(name)?.let { return getChapterNumberFromMatch(it) }

                    // need to find again first number might already removed
                    number.find(name)?.let { return getChapterNumberFromMatch(it) }
                }
            }
        }

        // return the first number encountered
        return getChapterNumberFromMatch(numberMatch.first())
    }

    /**
     * Check if chapter number is found and return it
     * @param match result of regex
     * @return chapter number if found else null
     */
    private fun getChapterNumberFromMatch(match: MatchResult): Double =
        match.let {
            val initial = it.groups[1]?.value?.toDouble()!!
            val subChapterDecimal = it.groups[2]?.value
            val subChapterAlpha = it.groups[3]?.value
            val addition = checkForDecimal(subChapterDecimal, subChapterAlpha)
            initial.plus(addition)
        }

    /**
     * Check for decimal in received strings
     * @param decimal decimal value of regex
     * @param alpha alpha value of regex
     * @return decimal/alpha float value
     */
    private fun checkForDecimal(
        decimal: String?,
        alpha: String?,
    ): Double {
        if (!decimal.isNullOrEmpty()) {
            return decimal.toDouble()
        }

        if (!alpha.isNullOrEmpty()) {
            if (alpha.contains("extra")) {
                return 0.99
            }

            if (alpha.contains("omake")) {
                return 0.98
            }

            if (alpha.contains("special")) {
                return 0.97
            }

            val trimmedAlpha = alpha.trimStart('.')
            if (trimmedAlpha.length == 1) {
                return parseAlphaPostFix(trimmedAlpha[0])
            }
        }

        return 0.0
    }

    /**
     * x.a -> x.1, x.b -> x.2, etc
     */
    private fun parseAlphaPostFix(alpha: Char): Double {
        val number = alpha.code - ('a'.code - 1)
        if (number >= 10) return 0.0
        return number / 10.0
    }
}
