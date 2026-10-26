package com.sinobash.famly.ui.tree

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sinobash.famly.app
import com.sinobash.famly.data.FamilyRepository
import com.sinobash.famly.data.local.Gender
import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.domain.TreeBuilder
import com.sinobash.famly.domain.TreeLayout
import com.sinobash.famly.domain.export.TreeExportFormat
import com.sinobash.famly.domain.export.TreeExporter
import com.sinobash.famly.ui.person.PersonAvatar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TreeViewModel(repository: FamilyRepository) : ViewModel() {
    val layout: StateFlow<TreeLayout> = combine(
        repository.observePersons(), repository.observeRelations()
    ) { people, rels -> TreeBuilder.build(people, rels) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TreeLayout.EMPTY)

    suspend fun exportTree(context: Context, format: TreeExportFormat, uri: Uri): Result<Unit> {
        return TreeExporter.export(context, layout.value, format, uri)
    }

    companion object {
        val Factory = viewModelFactory { initializer { TreeViewModel(app().repository) } }
    }
}

// Ukuran kartu & jarak (dp). Gemini: aman diubah, selama konsisten.
private val CardW = 120.dp
private val CardH = 72.dp
private val GapX = 24.dp
private val GapY = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    vm: TreeViewModel = viewModel(factory = TreeViewModel.Factory)
) {
    val layout by vm.layout.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf<TreeExportFormat?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val format = pendingFormat ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = vm.exportTree(context, format, uri)
            val message = if (result.isSuccess) {
                "Berhasil mengekspor famly (${format.extension.uppercase()})"
            } else {
                "Gagal mengekspor: ${result.exceptionOrNull()?.localizedMessage ?: "Terjadi kesalahan"}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Famly") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ekspor Famly")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        TreeExportFormat.entries.forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text(fmt.displayName) },
                                onClick = {
                                    menuOpen = false
                                    pendingFormat = fmt
                                    exportLauncher.launch("famly.${fmt.extension}")
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (layout.nodes.isEmpty()) {
                Text("Belum ada data.", modifier = Modifier.align(Alignment.Center))
            } else {
                TreeCanvas(layout, onOpenPerson)
            }
        }
    }
}

@Composable
private fun TreeCanvas(layout: TreeLayout, onOpenPerson: (Long) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val contentW: Dp = (CardW + GapX) * layout.columnCount + GapX
    val contentH: Dp = (CardH + GapY) * layout.generationCount + GapY

    fun xDp(col: Float): Dp = GapX + (CardW + GapX) * col
    fun yDp(gen: Int): Dp = GapY + (CardH + GapY) * gen

    val lineColor = MaterialTheme.colorScheme.outline
    val spouseColor = MaterialTheme.colorScheme.tertiary
    val byId = layout.nodes.associateBy { it.person.id }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 2.5f)
                    pan += panChange
                }
            }
    ) {
        Box(
            Modifier
                .requiredSize(contentW, contentH)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = pan.x; translationY = pan.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            // garis relasi
            Canvas(Modifier.requiredSize(contentW, contentH)) {
                val cw = CardW.toPx(); val ch = CardH.toPx()
                layout.edges.forEach { e ->
                    val a = byId[e.fromId] ?: return@forEach
                    val b = byId[e.toId] ?: return@forEach
                    val ax = xDp(a.column).toPx(); val ay = yDp(a.generation).toPx()
                    val bx = xDp(b.column).toPx(); val byy = yDp(b.generation).toPx()
                    if (e.isSpouse) {
                        val (left, right) = if (ax <= bx) ax to bx else bx to ax
                        drawLine(
                            spouseColor,
                            Offset(left + cw, ay + ch / 2),
                            Offset(right, byy + ch / 2),
                            strokeWidth = 3.dp.toPx()
                        )
                    } else {
                        val start = Offset(ax + cw / 2, ay + ch)
                        val end = Offset(bx + cw / 2, byy)
                        val midY = (start.y + end.y) / 2
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            lineTo(start.x, midY)
                            lineTo(end.x, midY)
                            lineTo(end.x, end.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
            // kartu orang
            layout.nodes.forEach { n ->
                val px = with(density) { xDp(n.column).roundToPx() }
                val py = with(density) { yDp(n.generation).roundToPx() }
                PersonCard(
                    name = n.person.fullName,
                    gender = n.person.gender,
                    modifier = Modifier
                        .offset { IntOffset(px, py) }
                        .size(CardW, CardH)
                        .clickable { onOpenPerson(n.person.id) },
                    person = n.person
                )
            }
        }
    }
}

/** Kartu satu orang di pohon. Gemini: desain ulang composable ini sesuka hati. */
@Composable
private fun PersonCard(
    name: String,
    gender: String,
    person: PersonEntity,
    modifier: Modifier = Modifier
) {
    val bg = when (gender) {
        Gender.MALE -> MaterialTheme.colorScheme.primaryContainer
        Gender.FEMALE -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier.background(bg, RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PersonAvatar(person, 32)
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}