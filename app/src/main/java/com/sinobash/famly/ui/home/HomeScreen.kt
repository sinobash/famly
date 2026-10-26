package com.sinobash.famly.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sinobash.famly.app
import com.sinobash.famly.data.FamilyRepository
import com.sinobash.famly.data.backup.BackupManager
import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.ui.person.PersonAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val query: String = "", val people: List<PersonEntity> = emptyList())

class HomeViewModel(
    repository: FamilyRepository,
    val backup: BackupManager
) : ViewModel() {
    private val query = MutableStateFlow("")
    val state: StateFlow<HomeUiState> =
        combine(repository.observePersons(), query) { people, q ->
            HomeUiState(
                query = q,
                people = if (q.isBlank()) people
                else people.filter { it.fullName.contains(q, true) || it.nickname.orEmpty().contains(q, true) }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(q: String) { query.value = q }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(app().repository, app().backupManager) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onOpenTree: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = vm.backup.export(uri)
            snackbar.showSnackbar(r.fold({ "Backup berhasil ($it orang)" }, { "Backup gagal" }))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = vm.backup.import(uri)
            snackbar.showSnackbar(r.fold({ "Restore berhasil ($it orang)" }, { "File backup tidak valid" }))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Famly") },
                actions = {
                    IconButton(onClick = onOpenTree) { Text("🌳") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Backup ke file") }, onClick = {
                            menuOpen = false; exportLauncher.launch("famly-backup.json")
                        })
                        DropdownMenuItem(text = { Text("Restore dari file") }, onClick = {
                            menuOpen = false; importLauncher.launch(arrayOf("application/json", "*/*"))
                        })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) { Icon(Icons.Default.Add, "Tambah orang") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Cari nama") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (state.people.isEmpty()) {
                Text(
                    "Belum ada anggota keluarga. Tekan + untuk menambah.",
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(state.people, key = { it.id }) { p ->
                    ListItem(
                        leadingContent = { PersonAvatar(p, 48) },
                        headlineContent = { Text(p.fullName) },
                        supportingContent = { p.birthDate?.let { Text("Lahir: $it") } },
                        modifier = Modifier.clickable { onOpenPerson(p.id) }
                    )
                }
            }
        }
    }
}