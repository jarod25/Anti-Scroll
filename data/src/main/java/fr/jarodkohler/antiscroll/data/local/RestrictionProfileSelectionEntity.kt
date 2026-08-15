package fr.jarodkohler.antiscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restriction_profile_selection")
data class RestrictionProfileSelectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int,
    @ColumnInfo(name = "profile_identifier")
    val profileIdentifier: String
)
