package com.sinobash.famly.ui.person

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.sinobash.famly.data.local.Gender
import com.sinobash.famly.data.local.PersonEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonFormState(
    val id: Long = 0,
    val fullName: String = "",
    val nickname: String = "",
    val gender: String = Gender.UNKNOWN,
    val birthDate: String = "",
    val deathDate: String = "",
    val birthPlace: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val nameError: Boolean = false,
    val saved: Boolean = false
)

class PersonEditViewModel(
    private val repository: FamilyRepository,
    handle: SavedStateHandle
) : ViewModel() {
    private val personId: Long = handle["personId"] ?: -1L
    private val linkTo: Long = handle["linkTo"] ?: -1L
    private val role: String = handle["role"] ?: ""

    private val _state = MutableStateFlow(PersonFormState())
    val state: StateFlow<PersonFormState> = _state.asStateFlow()
    val isEdit get() = personId > 0

    init {
        if (isEdit) viewModelScope.launch {
            repository.getPerson(personId)?.let { p ->
                _state.value = PersonFormState(
                    id = p.id, fullName = p.fullName, nickname = p.nickname.orEmpty(),
                    gender = p.gender, birthDate = p.birthDate.orEmpty(), deathDate = p.deathDate.orEmpty(),
                    birthPlace = p.birthPlace.orEmpty(), notes = p.notes.orEmpty(), photoPath = p.photoPath
                )
            }
        }
    }

    fun update(block: PersonFormState.() -> PersonFormState) = _state.update { it.block().copy(nameError = false) }

    fun pickPhoto(uri: android.net.Uri) = viewModelScope.launch {
        repository.importPhoto(uri)?.let { path -> _state.update { it.copy(photoPath = path) } }
    }

    fun save() {
        val s = _state.value
        if (s.fullName.isBlank()) { _state.update { it.copy(nameError = true) }; return }
        viewModelScope.launch {
            val id = repository.savePerson(
                PersonEntity(
                    id = if (isEdit) personId else 0,
                    fullName = s.fullName.trim(),
                    nickname = s.nickname.trim().ifBlank { null },
                    gender = s.gender,
                    birthDate = s.birthDate.trim().ifBlank { null },
                    deathDate = s.deathDate.trim().ifBlank { null },
                    birthPlace = s.birthPlace.trim().ifBlank { null },
                    notes = s.notes.trim().ifBlank { null },
                    photoPath = s.photoPath
                )
            )
            if (!isEdit && linkTo > 0 && role.isNotBlank()) {
                // role = peran ORANG BARU terhadap linkTo
                repository.link(personId = linkTo, otherId = id, role = LinkRole.valueOf(role))
            }
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PersonEditViewModel(app().repository, createSavedStateHandle()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreen(
    onDone: () -> Unit,
    vm: PersonEditViewModel = viewModel(factory = PersonEditViewModel.Factory)
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onDone() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::pickPhoto)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Ubah data" else "Tambah orang") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (s.photoPath == null) "Pilih foto" else "Ganti foto") }
                if (s.photoPath != null) {
                    OutlinedButton(onClick = { vm.update { copy(photoPath = null) } }) { Text("Hapus foto") }
                }
            }
            OutlinedTextField(
                value = s.fullName, onValueChange = { vm.update { copy(fullName = it) } },
                label = { Text("Nama lengkap *") }, isError = s.nameError,
                supportingText = { if (s.nameError) Text("Nama wajib diisi") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.nickname, onValueChange = { vm.update { copy(nickname = it) } },
                label = { Text("Nama panggilan") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Gender.MALE to "Laki-laki", Gender.FEMALE to "Perempuan", Gender.UNKNOWN to "Tidak diisi")
                    .forEach { (code, label) ->
                        FilterChip(
                            selected = s.gender == code,
                            onClick = { vm.update { copy(gender = code) } },
                            label = { Text(label) }
                        )
                    }
            }
            OutlinedTextField(
                value = s.birthDate, onValueChange = { vm.update { copy(birthDate = it) } },
                label = { Text("Tanggal lahir (mis. 1950-08-17 atau 1950)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.deathDate, onValueChange = { vm.update { copy(deathDate = it) } },
                label = { Text("Tanggal wafat (opsional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.birthPlace, onValueChange = { vm.update { copy(birthPlace = it) } },
                label = { Text("Tempat lahir") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.notes, onValueChange = { vm.update { copy(notes = it) } },
                label = { Text("Catatan") }, minLines = 3, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
        }
    }
}