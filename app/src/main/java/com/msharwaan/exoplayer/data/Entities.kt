package com.msharwaan.exoplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One video file on the SD card. Size and modified time detect changes between scans. */
@Entity(tableName = "media_file", indices = [Index(value = ["uri"], unique = true), Index("groupKey")])
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** Folders from the library root down to the file, joined with '/'. */
    val folderPath: String,
    val fileName: String,
    val size: Long,
    val modified: Long,
    /** MOVIE, EPISODE or OTHER, from the file name (FileNameParser). */
    val kind: String,
    /** Which title this file belongs to: one movie, one show, or one Others entry. */
    val groupKey: String,
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val anime: Boolean,
    val durationMs: Long = 0,
)

/** A subtitle file next to a video, matched by name (loaded automatically, v1). */
@Entity(tableName = "subtitle_file", indices = [Index("mediaFileId")])
data class SubtitleFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaFileId: Long,
    val uri: String,
    val fileName: String,
    val language: String?,
    /** "local" next to the video, or "downloaded" from OpenSubtitles. */
    val source: String = "local",
)

/** A movie, show or Others entry: what a card in Browsing stands for. */
@Entity(tableName = "title_info")
data class TitleInfo(
    @PrimaryKey val groupKey: String,
    /** MOVIE, SHOW or OTHER. */
    val kind: String,
    /** CONTINUE is never stored; tabs are MOVIES, SERIES, ANIME, OTHERS. */
    val tab: String,
    val name: String,
    val year: Int?,
    /** Filled by Fetch info (TMDB); empty until then. */
    val overview: String? = null,
    val genres: String? = null,
    val runtimeMin: Int? = null,
    val rating: Double? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val tmdbId: Long? = null,
    val tmdbType: String? = null,
    /** Local paths of downloaded, display-sized images (decision D7). */
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val fetchedAt: Long? = null,
)

/** A season from TMDB, so season cards and missing episodes can be shown. */
@Entity(tableName = "season_info", primaryKeys = ["groupKey", "number"])
data class SeasonInfo(
    val groupKey: String,
    val number: Int,
    val name: String,
    val overview: String?,
    val year: Int?,
    val episodeCount: Int,
    val posterPath: String?,
)

/** An episode from TMDB. Linked to a file when one exists; otherwise it shows as missing. */
@Entity(tableName = "episode_info", primaryKeys = ["groupKey", "season", "number"])
data class EpisodeInfo(
    val groupKey: String,
    val season: Int,
    val number: Int,
    val title: String,
    val overview: String?,
    val runtimeMin: Int?,
    val stillPath: String?,
)

/** Where you stopped in one file. Saved on pause, stop, episode change and once a minute. */
@Entity(tableName = "playback_state")
data class PlaybackState(
    @PrimaryKey val mediaFileId: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val finishedCount: Int = 0,
    val lastPlayed: Long = 0,
    val audioTrack: String? = null,
    val subtitleTrack: String? = null,
    val subtitleDelayMs: Int = 0,
)

/** One sitting with one file. Recorded so stats can be added later (decision D27). */
@Entity(tableName = "watch_session", indices = [Index("mediaFileId")])
data class WatchSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaFileId: Long,
    val started: Long,
    val ended: Long,
    val secondsWatched: Long,
)

/** Your Fix match corrections. Applied before automatic matching; survive rescans. */
@Entity(tableName = "match_override")
data class MatchOverride(
    @PrimaryKey val groupKey: String,
    val tmdbId: Long,
    val tmdbType: String,
)
