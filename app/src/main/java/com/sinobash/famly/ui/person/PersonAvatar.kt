package com.sinobash.famly.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sinobash.famly.data.local.PersonEntity
import java.io.File

/** Foto bulat, atau inisial nama jika belum ada foto. */
@Composable
fun PersonAvatar(person: PersonEntity, sizeDp: Int, modifier: Modifier = Modifier) {
    val base = modifier.size(sizeDp.dp).clip(CircleShape)
    if (person.photoPath != null) {
        AsyncImage(
            model = File(person.photoPath),
            contentDescription = person.fullName,
            contentScale = ContentScale.Crop,
            modifier = base
        )
    } else {
        Box(base.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                person.fullName.trim().take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}