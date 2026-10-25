package com.sinobash.famly.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

object Gender {
    const val MALE = "M"
    const val FEMALE = "F"
    const val UNKNOWN = "U"
}

object RelationType {
    /** fromId adalah ORANG TUA dari toId */
    const val PARENT = "PARENT"
    /** fromId adalah PASANGAN dari toId (simetris) */
    const val SPOUSE = "SPOUSE"
}

@Serializable
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val nickname: String? = null,
    val gender: String = Gender.UNKNOWN,
    /** Format bebas/ISO, mis. 1950-08-17 atau "1950" (tanggal sering tidak lengkap di silsilah) */
    val birthDate: String? = null,
    val deathDate: String? = null,
    val birthPlace: String? = null,
    val notes: String? = null,
    /** Path file foto di penyimpanan internal aplikasi */
    val photoPath: String? = null
)

@Serializable
@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(PersonEntity::class, ["id"], ["fromId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PersonEntity::class, ["id"], ["toId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("fromId"),
        Index("toId"),
        Index(value = ["fromId", "toId", "type"], unique = true)
    ]
)
data class RelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val type: String
)
