package fr.jarodkohler.antiscroll.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SharedSessionStateDao {
    @Query("SELECT * FROM shared_session_state WHERE singleton_id = 1 LIMIT 1")
    suspend fun get(): SharedSessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SharedSessionStateEntity)

    @Query("DELETE FROM shared_session_state")
    suspend fun clear()
}
