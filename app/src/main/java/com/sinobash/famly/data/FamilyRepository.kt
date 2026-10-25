package com.sinobash.famly.data

import android.content.Context
import android.net.Uri
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.sinobash.famly.data.local.AppDatabase
import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.data.local.RelationEntity
import com.sinobash.famly.data.local.RelationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FamilyRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val personDao = db.personDao()
    private val relationDao = db.relationDao()

    fun observePersons(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun observePerson(id: Long): Flow<PersonEntity?> = personDao.observe(id)
    fun observeRelations(): Flow<List<RelationEntity>> = relationDao.observeAll()

    suspend fun getPerson(id: Long): PersonEntity? = personDao.get(id)

    /** Mengembalikan id orang yang disimpan. */
    suspend fun savePerson(person: PersonEntity): Long =
        if (person.id == 0L) personDao.insert(person)
        else { personDao.update(person); person.id }

    suspend fun deletePerson(person: PersonEntity) {
        personDao.delete(person) // relasi ikut terhapus (CASCADE)
        person.photoPath?.let { File(it).delete() }
    }

    /**
     * Hubungkan [otherId] ke [personId] sebagai [role].
     * role: "PARENT" = other adalah orang tua person, "CHILD" = other adalah anak person,
     * "SPOUSE" = other adalah pasangan person.
     */
    suspend fun link(personId: Long, otherId: Long, role: LinkRole) {
        if (personId == otherId) return
        when (role) {
            LinkRole.PARENT -> relationDao.insert(RelationEntity(fromId = otherId, toId = personId, type = RelationType.PARENT))
            LinkRole.CHILD -> relationDao.insert(RelationEntity(fromId = personId, toId = otherId, type = RelationType.PARENT))
            LinkRole.SPOUSE -> {
                // simpan satu arah dengan urutan id kecil -> besar agar tidak duplikat
                val (a, b) = if (personId < otherId) personId to otherId else otherId to personId
                relationDao.insert(RelationEntity(fromId = a, toId = b, type = RelationType.SPOUSE))
            }
        }
    }

    suspend fun unlink(relation: RelationEntity) = relationDao.delete(relation)

    /** Salin foto pilihan pengguna ke penyimpanan internal; return path file. */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    // --- dipakai BackupManager ---
    suspend fun snapshot(): Pair<List<PersonEntity>, List<RelationEntity>> =
        personDao.getAll() to relationDao.getAll()

    suspend fun replaceAll(persons: List<PersonEntity>, relations: List<RelationEntity>) {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                relationDao.clear()
                personDao.clear()
                personDao.insertAll(persons)
                relationDao.insertAll(relations)
            }
        }
    }
}

enum class LinkRole { PARENT, CHILD, SPOUSE }