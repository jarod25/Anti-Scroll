package fr.jarodkohler.antiscroll.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RestrictionProfileSelectionDao {
    @Query("SELECT * FROM restriction_profile_selection WHERE singleton_id = 1 LIMIT 1")
    suspend fun get(): RestrictionProfileSelectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: RestrictionProfileSelectionEntity)
}
