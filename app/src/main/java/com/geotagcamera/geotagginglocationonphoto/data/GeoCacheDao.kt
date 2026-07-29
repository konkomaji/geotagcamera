package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GeoCacheEntity)

    @Query("SELECT * FROM geo_cache WHERE latGridKey = :latKey AND lngGridKey = :lngKey LIMIT 1")
    suspend fun get(latKey: Long, lngKey: Long): GeoCacheEntity?

    /** Nearest cached cell within a small search box, for when the exact cell was never visited before. */
    @Query(
        """
        SELECT * FROM geo_cache
        WHERE latGridKey BETWEEN :latKey - :radius AND :latKey + :radius
          AND lngGridKey BETWEEN :lngKey - :radius AND :lngKey + :radius
        ORDER BY (latGridKey - :latKey) * (latGridKey - :latKey) + (lngGridKey - :lngKey) * (lngGridKey - :lngKey) ASC
        LIMIT 1
        """
    )
    suspend fun getNearby(latKey: Long, lngKey: Long, radius: Long): GeoCacheEntity?
}
