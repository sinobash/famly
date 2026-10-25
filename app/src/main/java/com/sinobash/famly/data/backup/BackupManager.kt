package com.sinobash.famly.data.backup

import android.content.Context
import android.net.Uri
import com.sinobash.famly.data.FamilyRepository
import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.data.local.RelationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val version: Int = 1,
    val persons: List<PersonEntity>,
    val relations: List<RelationEntity>
)

/**
 * Backup/restore manual ke file JSON lewat Storage Access Framework
 * (pengguna memilih lokasi sendiri; tidak perlu permission storage).
 * CATATAN: foto belum ikut di backup JSON.
 */
class BackupManager(
    private val context: Context,
    private val repository: FamilyRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val (persons, relations) = repository.snapshot()
            val text = json.encodeToString(BackupFile.serializer(), BackupFile(persons = persons, relations = relations))
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            persons.size
        }
    }

    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val file = json.decodeFromString(BackupFile.serializer(), text)
            // validasi sederhana: relasi hanya ke person yang ada
            val ids = file.persons.map { it.id }.toSet()
            val relations = file.relations.filter { it.fromId in ids && it.toId in ids }
            repository.replaceAll(file.persons, relations)
            file.persons.size
        }
    }
}