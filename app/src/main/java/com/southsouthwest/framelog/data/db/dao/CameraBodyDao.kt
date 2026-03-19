package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.CameraBody
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraBodyDao {

    /**
     * Search camera bodies by name or make. Empty query returns all bodies.
     * Used by the gear library list and roll setup body picker.
     */
    @Query("SELECT * FROM camera_bodies WHERE name LIKE '%' || :query || '%' OR make LIKE '%' || :query || '%' ORDER BY make ASC, name ASC")
    fun searchCameraBodies(query: String): Flow<List<CameraBody>>

    @Query("SELECT * FROM camera_bodies WHERE id = :id")
    fun getCameraBodyById(id: Int): Flow<CameraBody>

    @Insert
    suspend fun insertCameraBody(cameraBody: CameraBody)

    @Update
    suspend fun updateCameraBody(cameraBody: CameraBody)

    @Delete
    suspend fun deleteCameraBody(cameraBody: CameraBody)

    /**
     * Returns all distinct mount type strings from the Lens table.
     * Lens is the source of truth for mount type vocabulary — camera bodies must pick
     * from existing lens mount values.
     */
    @Query("SELECT DISTINCT mountType FROM lenses ORDER BY mountType ASC")
    fun getDistinctMountTypes(): Flow<List<String>>
}
