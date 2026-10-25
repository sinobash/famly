package com.sinobash.famly

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.sinobash.famly.data.FamilyRepository
import com.sinobash.famly.data.backup.BackupManager
import com.sinobash.famly.data.local.buildAppDatabase

class FamlyApp : Application() {
    val database by lazy { buildAppDatabase(this) }
    val repository by lazy { FamilyRepository(this, database) }
    val backupManager by lazy { BackupManager(this, repository) }
}

/** Helper untuk mengambil Application di dalam ViewModel initializer. */
fun CreationExtras.app(): FamlyApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FamlyApp