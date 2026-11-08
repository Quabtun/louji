package com.quabtun.louji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quabtun.louji.model.BuildingRank
import com.quabtun.louji.model.BuildingStats
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {
    @Query("SELECT * FROM building_profiles ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BuildingProfileEntity>>

    @Query("SELECT * FROM building_profiles ORDER BY createdAt ASC")
    suspend fun allOnce(): List<BuildingProfileEntity>

    @Query("SELECT * FROM building_profiles WHERE location = :location LIMIT 1")
    suspend fun findByLocation(location: String): BuildingProfileEntity?

    @Query("SELECT * FROM building_profiles WHERE id = :id")
    suspend fun findById(id: Long): BuildingProfileEntity?

    @Query("UPDATE building_profiles SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("SELECT * FROM building_profiles WHERE location = :location LIMIT 1")
    fun observeByLocation(location: String): Flow<BuildingProfileEntity?>

    /** Exact match first, then prefix/contains, so typing "阳光花" can surface the profile. */
    @Query(
        """
        SELECT * FROM building_profiles
        WHERE location = :query
           OR location LIKE :query || '%'
           OR location LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN location = :query THEN 0
                 WHEN location LIKE :query || '%' THEN 1
                 ELSE 2 END,
            pinned DESC, updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findBestMatch(query: String): BuildingProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(profile: BuildingProfileEntity): Long

    @Update
    suspend fun update(profile: BuildingProfileEntity)

    @Query("DELETE FROM building_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM building_profiles")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BuildingProfileEntity>)

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN noStairs = 'NO' THEN 1 ELSE 0 END), 0) AS stairs,
            COALESCE(SUM(CASE WHEN isWarning = 1 THEN 1 ELSE 0 END), 0) AS warnings,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown
        FROM records
        WHERE location = :location AND deletedAt = 0
        """,
    )
    fun observeStats(location: String): Flow<BuildingStats>

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN noStairs = 'NO' THEN 1 ELSE 0 END), 0) AS stairs,
            COALESCE(SUM(CASE WHEN isWarning = 1 THEN 1 ELSE 0 END), 0) AS warnings,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown
        FROM records
        WHERE location = :location AND deletedAt = 0
        """,
    )
    suspend fun statsOnce(location: String): BuildingStats

    @Query(
        """
        SELECT location,
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN noStairs = 'NO' THEN 1 ELSE 0 END), 0) AS stairs
        FROM records
        WHERE deletedAt = 0 AND location != ''
        GROUP BY location
        """,
    )
    fun observeRankings(): Flow<List<BuildingRank>>
}
