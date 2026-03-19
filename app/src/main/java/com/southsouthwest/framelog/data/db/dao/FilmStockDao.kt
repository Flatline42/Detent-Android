package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.FilmStock
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmStockDao {

    /**
     * Search film stocks by name or make, optionally including discontinued stocks.
     * Discontinued stocks are hidden by default to keep pickers clean, but still exist
     * in the database to preserve historical roll data.
     */
    @Query("SELECT * FROM film_stocks WHERE (name LIKE '%' || :query || '%' OR make LIKE '%' || :query || '%') AND (discontinued = 0 OR :includeDiscontinued = 1) ORDER BY make ASC, name ASC")
    fun searchFilmStocks(query: String, includeDiscontinued: Boolean = false): Flow<List<FilmStock>>

    @Query("SELECT * FROM film_stocks WHERE id = :id")
    fun getFilmStockById(id: Int): Flow<FilmStock>

    @Insert
    suspend fun insertFilmStock(filmStock: FilmStock)

    @Update
    suspend fun updateFilmStock(filmStock: FilmStock)

    @Delete
    suspend fun deleteFilmStock(filmStock: FilmStock)
}
