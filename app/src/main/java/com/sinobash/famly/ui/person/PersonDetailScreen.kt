package com.sinobash.famly.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sinobash.famly.app
import com.sinobash.famly.data.FamilyRepository
import com.sinobash.famly.data.LinkRole
import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.data.local.RelationEntity
import com.sinobash.famly.domain.FamilyOf
import com.sinobash.famly.domain.TreeBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val person: PersonEntity? = null,
    val family: FamilyOf? = null,
    val candidates: List<PersonEntity> = emptyList()
)

class PersonDetailViewModel(
    private val repository: FamilyRepository,
    handle: SavedStateHandle
) : ViewModel() {
    private val personId: Long = handle["personId"] ?: -1L

    val state: StateFlow<DetailUiState> = combine(
        repository.observePerson(personId),
        repository.observePersons(),
        repository.observeRelations()
    ) { person, all, rels ->
        DetailUiState(
            person = person,
            family = person?.let { TreeBuilder.familyOf(it.id, all, rels) },
            candidates = all.filter { it.id != personId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun link(otherId: Long, role: LinkRole) = viewModelScope.launch { repository.link(personId, otherId, role) }
    fun unlink(r: RelationEntity) = viewModelScope.launch { repository.unlink(r) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        state.value.person?.let { repository.deletePerson(it) }
        onDone()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PersonDetailViewModel(app().repository, createSavedStateHandle()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddRelative: (personId: Long, role: String) -> Unit,
    onOpenPerson: (Long) -> Unit,
    vm: PersonDetailViewModel = viewModel(factory = PersonDetailViewModel.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val person = s.person
    var confirmDelete by remember { mutableStateOf(false) }
    var pickRole by remember { mutableStateOf<LinkRole?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.fullName.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    if (person != null) {
                        IconButton(onClick = { onEdit(person.id) }) { Icon(Icons.Default.Edit, "Ubah") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
                    }
                }
            )
        }
    ) { padding ->
        if (person == null) return@Scaffold
        val f = s.family
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PersonAvatar(person, 80)
                Column {
                    Text(person.fullName, style = MaterialTheme.typography.titleLarge)
                    person.nickname?.let { Text("“$it”") }
                }
            }
            person.birthDate?.let { Text("Lahir: $it" + (person.birthPlace?.let { p -> " di $p" } ?: "")) }
            person.deathDate?.let { Text("Wafat: $it") }
            person.notes?.let { Text(it) }

            Section("Orang tua", f?.parents.orEmpty(), onOpenPerson, onRemove = { p -> f?.parentRelations?.get(p.id)?.let(vm::unlink) })
            OutlinedButton(onClick = { onAddRelative(person.id, LinkRole.PARENT.name) }) { Text("+ Tambah orang tua baru") }
            OutlinedButton(onClick = { pickRole = LinkRole.PARENT }) { Text("Hubungkan orang tua yang sudah ada") }

            Section("Pasangan", f?.spouses?.map { it.first }.orEmpty(), onOpenPerson,
                onRemove = { p -> f?.spouses?.firstOrNull { it.first.id == p.id }?.second?.let(vm::unlink) })
            OutlinedButton(onClick = { onAddRelative(person.id, LinkRole.SPOUSE.name) }) { Text("+ Tambah pasangan baru") }
            OutlinedButton(onClick = { pickRole = LinkRole.SPOUSE }) { Text("Hubungkan pasangan yang sudah ada") }

            Section("Anak", f?.children.orEmpty(), onOpenPerson, onRemove = { p -> f?.childRelations?.get(p.id)?.let(vm::unlink) })
            OutlinedButton(onClick = { onAddRelative(person.id, LinkRole.CHILD.name) }) { Text("+ Tambah anak baru") }
            OutlinedButton(onClick = { pickRole = LinkRole.CHILD }) { Text("Hubungkan anak yang sudah ada") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${person?.fullName}?") },
            text = { Text("Data dan hubungan keluarganya akan dihapus dari perangkat ini.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }

    pickRole?.let { role ->
        AlertDialog(
            onDismissRequest = { pickRole = null },
            title = { Text("Pilih orang") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (s.candidates.isEmpty()) Text("Belum ada orang lain.")
                    s.candidates.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.fullName) },
                            modifier = Modifier.clickable { vm.link(c.id, role); pickRole = null }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickRole = null }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun Section(
    title: String,
    people: List<PersonEntity>,
    onOpen: (Long) -> Unit,
    onRemove: (PersonEntity) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    if (people.isEmpty()) Text("—")
    people.forEach { p ->
        ListItem(
            leadingContent = { PersonAvatar(p, 40) },
            headlineContent = { Text(p.fullName) },
            trailingContent = { TextButton(onClick = { onRemove(p) }) { Text("Lepas") } },
            modifier = Modifier.fillMaxWidth().clickable { onOpen(p.id) }
        )
    }
}