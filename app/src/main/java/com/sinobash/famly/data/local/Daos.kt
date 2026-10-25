package com.sinobash.famly.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observe(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    @Query("SELECT * FROM persons")
    suspend fun getAll(): List<PersonEntity>

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("DELETE FROM persons")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<PersonEntity>)
}

@Dao
interface RelationDao {
    @Query("SELECT * FROM relations")
    fun observeAll(): Flow<List<RelationEntity>>

    @Query("SELECT * FROM relations")
    suspend fun getAll(): List<RelationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(relation: RelationEntity): Long

    @Delete
    suspend fun delete(relation: RelationEntity)

    @Query("DELETE FROM relations")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RelationEntity>)
}
