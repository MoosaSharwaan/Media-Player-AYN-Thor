package com.msharwaan.exoplayer.library

import com.msharwaan.exoplayer.data.AppDatabase
import com.msharwaan.exoplayer.data.MatchOverride
import com.msharwaan.exoplayer.data.TitleInfo
import com.msharwaan.exoplayer.online.Tmdb

/**
 * One card for one show, whatever its files call it (D50). "Enen no Shouboutai - S02E23" and
 * "Fire Force - S03E01" are the same series under its Japanese and English names: once Fetch info
 * (or Fix match) finds both on TMDB as the same show, they become one title.
 *
 * Decided by TMDB's answer, not by folders: a "Naruto" folder holding Shippuden must not merge
 * two different shows. The merged name is remembered as a match override, so a rescan files it
 * under the same title again.
 */
object SameShow {

    /** Each show key that belongs under another, mapped to that other key. */
    fun aliases(titles: List<TitleInfo>, overrides: List<MatchOverride>): Map<String, String> {
        val titleByKey = titles.associateBy { it.groupKey }
        val idOf = mutableMapOf<String, Long>()
        // A fetched title knows its TMDB show; a name merged earlier has only its override left.
        titles.filter { it.kind == "SHOW" && it.tmdbType == Tmdb.TV && it.tmdbId != null }.forEach { idOf[it.groupKey] = it.tmdbId!! }
        overrides.filter { it.tmdbType == Tmdb.TV }.forEach { idOf.putIfAbsent(it.groupKey, it.tmdbId) }
        val result = mutableMapOf<String, String>()
        idOf.entries.filter { it.key.startsWith("s:") }.groupBy({ it.value }, { it.key }).values
            .filter { it.size > 1 }
            .forEach { keys ->
                val main = mainKey(keys, titleByKey)
                keys.filter { it != main }.forEach { result[it] = main }
            }
        return result
    }

    /** The key named like TMDB's title ("s:fire force"), else the first that has a title. */
    private fun mainKey(keys: List<String>, titleByKey: Map<String, TitleInfo>): String =
        keys.firstOrNull { k -> titleByKey[k]?.let { "s:" + LibraryScanner.normalise(it.name) == k } == true }
            ?: keys.filter { titleByKey[it] != null }.minOrNull()
            ?: keys.min()

    /**
     * Moves the files of every other name onto the show's main title and drops the duplicate
     * title. Returns the titles that gained files, whose new seasons Fetch info should look up.
     */
    suspend fun merge(db: AppDatabase): Set<String> {
        val dao = db.library()
        val aliases = aliases(dao.titles(), dao.overrides())
        val grown = mutableSetOf<String>()
        for ((from, to) in aliases) {
            val title = dao.title(from)
            // Remembered after the title itself is gone, for the next scan.
            title?.tmdbId?.let { dao.upsertOverride(MatchOverride(from, it, Tmdb.TV)) }
            if (dao.filesOf(from).isNotEmpty()) {
                dao.regroupFiles(from, to)
                grown += to
            }
            dao.deleteSeasons(from)
            dao.deleteEpisodes(from)
            dao.deleteTitle(from)
        }
        return grown
    }
}
