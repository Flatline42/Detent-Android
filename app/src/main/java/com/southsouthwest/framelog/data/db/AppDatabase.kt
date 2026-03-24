package com.southsouthwest.framelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.southsouthwest.framelog.data.db.dao.CameraBodyDao
import com.southsouthwest.framelog.data.db.dao.FilmStockDao
import com.southsouthwest.framelog.data.db.dao.FilterDao
import com.southsouthwest.framelog.data.db.dao.FrameDao
import com.southsouthwest.framelog.data.db.dao.KitDao
import com.southsouthwest.framelog.data.db.dao.KitFilterDao
import com.southsouthwest.framelog.data.db.dao.KitLensDao
import com.southsouthwest.framelog.data.db.dao.LensDao
import com.southsouthwest.framelog.data.db.dao.RollDao
import com.southsouthwest.framelog.data.db.dao.RollFilterDao
import com.southsouthwest.framelog.data.db.dao.RollLensDao
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.KitFilter
import com.southsouthwest.framelog.data.db.entity.KitLens
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollFilter
import com.southsouthwest.framelog.data.db.entity.RollLens

/**
 * The single Room database for FRAME//LOG. All photography data lives here.
 * App configuration (active roll ID, onboarding flag, settings) lives in SharedPreferences.
 *
 * Version history:
 *   1 — initial schema
 *
 * Multi-DAO transactions are implemented here as @Transaction methods so they have access
 * to all DAOs. The Repository layer calls these methods rather than coordinating DAOs itself.
 */
@Database(
    entities = [
        CameraBody::class,
        Lens::class,
        Filter::class,
        FilmStock::class,
        Roll::class,
        RollLens::class,
        RollFilter::class,
        Frame::class,
        FrameFilter::class,
        Kit::class,
        KitLens::class,
        KitFilter::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rollDao(): RollDao
    abstract fun frameDao(): FrameDao
    abstract fun cameraBodyDao(): CameraBodyDao
    abstract fun lensDao(): LensDao
    abstract fun filterDao(): FilterDao
    abstract fun filmStockDao(): FilmStockDao
    abstract fun kitDao(): KitDao
    abstract fun rollLensDao(): RollLensDao
    abstract fun rollFilterDao(): RollFilterDao
    abstract fun kitLensDao(): KitLensDao
    abstract fun kitFilterDao(): KitFilterDao

    // ---------------------------------------------------------------------------
    // Multi-DAO transactions
    // These are the three atomic operations described in the architecture docs.
    // ---------------------------------------------------------------------------

    /**
     * Atomically creates a roll with all its associated lenses, filters, and pre-generated
     * frame slots. Returns the auto-generated roll ID.
     *
     * The [roll] must have id = 0 (auto-generate). The rollId fields in [rollLenses],
     * [rollFilters], and [frames] are ignored — the actual generated rollId is substituted.
     *
     * All four writes succeed or none do.
     */
    @Transaction
    open suspend fun createRollWithAssociations(
        roll: Roll,
        rollLenses: List<RollLens>,
        rollFilters: List<RollFilter>,
        frames: List<Frame>,
    ): Long {
        val rollId = rollDao().insertRoll(roll)
        rollLenses.forEach { rollLensDao().insertRollLens(it.copy(rollId = rollId.toInt())) }
        rollFilters.forEach { rollFilterDao().insertRollFilter(it.copy(rollId = rollId.toInt())) }
        frameDao().insertFrames(frames.map { it.copy(rollId = rollId.toInt()) })
        return rollId
    }

    /**
     * Atomically saves a kit (create or update) with a wholesale replacement of its
     * lens and filter associations. Returns the kit ID.
     *
     * For a new kit, [kit].id must be 0. For an update, [kit].id must be the existing kit ID.
     * Wholesale replace is used (delete all, reinsert current set) rather than delta updates —
     * kits are edited deliberately at home, not in the field, so simplicity is preferred.
     *
     * All writes succeed or none do.
     */
    @Transaction
    open suspend fun saveKitWithAssociations(
        kit: Kit,
        kitLenses: List<KitLens>,
        kitFilters: List<KitFilter>,
    ): Long {
        val kitId: Long = if (kit.id == 0) {
            kitDao().insertKit(kit)
        } else {
            kitDao().updateKit(kit)
            kit.id.toLong()
        }

        // Wholesale replace: clear existing associations, then reinsert the current set.
        kitLensDao().deleteAllKitLenses(kitId.toInt())
        kitLenses.forEach { kitLensDao().insertKitLens(it.copy(kitId = kitId.toInt())) }

        kitFilterDao().deleteAllKitFilters(kitId.toInt())
        kitFilters.forEach { kitFilterDao().insertKitFilter(it.copy(kitId = kitId.toInt())) }

        return kitId
    }

    // ---------------------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------------------

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Returns the singleton AppDatabase instance, creating it if necessary.
         * Uses double-checked locking to ensure thread safety without synchronizing
         * every call after initialization.
         */
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "detent.db",
                ).build().also { instance = it }
            }
        }

        /**
         * Closes and clears the singleton instance.
         * Must be called before overwriting the database file during a backup restore.
         * After calling this, the next call to [getInstance] will open a fresh connection.
         */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
