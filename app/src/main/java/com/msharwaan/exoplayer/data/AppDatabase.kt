package com.msharwaan.exoplayer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM media_file")
    fun observeFiles(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_file")
    suspend fun allFiles(): List<MediaFile>

    /** One library's files (D52). */
    @Query("SELECT * FROM media_file WHERE libraryId = :libraryId")
    fun observeFilesIn(libraryId: Long): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_file WHERE libraryId = :libraryId")
    suspend fun filesIn(libraryId: Long): List<MediaFile>

    /** A deleted library's files leave the database (never the card). */
    @Query("DELETE FROM media_file WHERE libraryId = :libraryId")
    suspend fun deleteFilesIn(libraryId: Long)

    @Query("SELECT * FROM media_file WHERE id = :id")
    suspend fun file(id: Long): MediaFile?

    @Query("SELECT * FROM media_file WHERE groupKey = :groupKey ORDER BY season, episode, fileName")
    fun observeFilesOf(groupKey: String): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_file WHERE groupKey = :groupKey ORDER BY season, episode, fileName")
    suspend fun filesOf(groupKey: String): List<MediaFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: MediaFile): Long

    @Query("DELETE FROM media_file WHERE id IN (:ids)")
    suspend fun deleteFiles(ids: List<Long>)

    @Query("UPDATE media_file SET durationMs = :durationMs WHERE id = :id")
    suspend fun setDuration(id: Long, durationMs: Long)

    @Query("SELECT * FROM subtitle_file WHERE mediaFileId = :fileId")
    suspend fun subtitlesOf(fileId: Long): List<SubtitleFile>

    @Query("DELETE FROM subtitle_file WHERE mediaFileId = :fileId AND source = 'local'")
    suspend fun clearLocalSubtitles(fileId: Long)

    @Insert
    suspend fun insertSubtitles(subs: List<SubtitleFile>): List<Long>

    @Query("SELECT * FROM title_info")
    fun observeTitles(): Flow<List<TitleInfo>>

    @Query("SELECT * FROM title_info WHERE groupKey = :groupKey")
    suspend fun title(groupKey: String): TitleInfo?

    @Query("SELECT * FROM title_info WHERE groupKey = :groupKey")
    fun observeTitle(groupKey: String): Flow<TitleInfo?>

    @Upsert
    suspend fun upsertTitle(title: TitleInfo)

    @Query("DELETE FROM title_info WHERE groupKey NOT IN (SELECT DISTINCT groupKey FROM media_file)")
    suspend fun deleteOrphanTitles()

    @Query("SELECT * FROM title_info")
    suspend fun titles(): List<TitleInfo>

    @Query("SELECT * FROM season_info WHERE groupKey = :groupKey ORDER BY number")
    suspend fun seasonsOf(groupKey: String): List<SeasonInfo>

    @Query("SELECT * FROM episode_info WHERE groupKey = :groupKey ORDER BY season, number")
    suspend fun episodesOf(groupKey: String): List<EpisodeInfo>

    @Query("DELETE FROM season_info WHERE groupKey = :groupKey")
    suspend fun deleteSeasons(groupKey: String)

    @Query("DELETE FROM episode_info WHERE groupKey = :groupKey")
    suspend fun deleteEpisodes(groupKey: String)

    @Query("SELECT * FROM season_info WHERE groupKey = :groupKey ORDER BY number")
    fun observeSeasons(groupKey: String): Flow<List<SeasonInfo>>

    @Query("SELECT * FROM episode_info WHERE groupKey = :groupKey ORDER BY season, number")
    fun observeEpisodes(groupKey: String): Flow<List<EpisodeInfo>>

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonInfo>)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeInfo>)

    @Query("SELECT * FROM playback_state")
    fun observePlayback(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback_state WHERE mediaFileId = :fileId")
    suspend fun playback(fileId: Long): PlaybackState?

    @Upsert
    suspend fun upsertPlayback(state: PlaybackState)

    @Insert
    suspend fun insertSession(session: WatchSession)

    @Query("SELECT * FROM match_override WHERE groupKey = :groupKey")
    suspend fun override(groupKey: String): MatchOverride?

    @Query("SELECT * FROM match_override")
    suspend fun overrides(): List<MatchOverride>

    /** Moves every file of one title to another (two names of the same show, D50). */
    @Query("UPDATE media_file SET groupKey = :to WHERE groupKey = :from")
    suspend fun regroupFiles(from: String, to: String)

    @Query("DELETE FROM title_info WHERE groupKey = :groupKey")
    suspend fun deleteTitle(groupKey: String)

    // ---- clean-up after files are gone (D54) ----

    @Query("SELECT groupKey FROM title_info")
    suspend fun titleKeys(): List<String>

    @Query("DELETE FROM season_info WHERE groupKey NOT IN (SELECT groupKey FROM title_info)")
    suspend fun deleteOrphanSeasons(): Int

    @Query("DELETE FROM episode_info WHERE groupKey NOT IN (SELECT groupKey FROM title_info)")
    suspend fun deleteOrphanEpisodes(): Int

    @Query("SELECT * FROM subtitle_file WHERE mediaFileId NOT IN (SELECT id FROM media_file)")
    suspend fun orphanSubtitles(): List<SubtitleFile>

    @Query("DELETE FROM subtitle_file WHERE mediaFileId NOT IN (SELECT id FROM media_file)")
    suspend fun deleteOrphanSubtitles(): Int

    @Query("DELETE FROM playback_state WHERE mediaFileId NOT IN (SELECT id FROM media_file)")
    suspend fun deleteOrphanPlayback(): Int

    @Query("DELETE FROM watch_session WHERE mediaFileId NOT IN (SELECT id FROM media_file)")
    suspend fun deleteOrphanSessions(): Int

    @Upsert
    suspend fun upsertOverride(override: MatchOverride)

    @Transaction
    suspend fun replaceSubtitles(fileId: Long, subs: List<SubtitleFile>) {
        clearLocalSubtitles(fileId)
        // A subtitle downloaded next to the video is found by the scan too: keep the one row.
        val downloaded = subtitlesOf(fileId).map { it.uri }.toSet()
        val fresh = subs.filter { it.uri !in downloaded }
        if (fresh.isNotEmpty()) insertSubtitles(fresh)
    }
}

@Database(
    entities = [
        MediaFile::class, SubtitleFile::class, TitleInfo::class, SeasonInfo::class, EpisodeInfo::class,
        PlaybackState::class, WatchSession::class, MatchOverride::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "library.db").addMigrations(MIGRATION_1_2).build()

        /** Libraries (D52): every file so far belongs to library 1. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_file ADD COLUMN libraryId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_file_libraryId ON media_file (libraryId)")
            }
        }
    }
}
