package com.sinobash.famly.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [PersonEntity::class, RelationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun relationDao(): RelationDao
}

fun buildAppDatabase(context: Context): AppDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder(
        context = app,
        klass = AppDatabase::class.java,
        name = app.getDatabasePath("famly.db").absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}