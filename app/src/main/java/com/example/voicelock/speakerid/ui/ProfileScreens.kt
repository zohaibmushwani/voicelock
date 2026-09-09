package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voicelock.speakerid.storage.SpeakerProfile

@Composable
fun ProfileManagementScreen(
    profiles: List<SpeakerProfile>,
    busy: Boolean,
    error: String?,
    onStartEnrollment: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<SpeakerProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<SpeakerProfile?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local voice profiles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${profiles.size} of 10 profiles. Each profile requires three guided recordings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("New profile name") },
            singleLine = true,
            enabled = !busy && profiles.size < 10,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onStartEnrollment(newName) },
            enabled = !busy && profiles.size < 10,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Enroll new profile")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (profiles.isEmpty()) {
            Text("No profiles enrolled yet. Add a name above to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text("Encrypted local voiceprint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editTarget = profile }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Rename ${profile.displayName}") }
                            IconButton(onClick = { deleteTarget = profile }, enabled = !busy) { Icon(Icons.Outlined.Delete, "Delete ${profile.displayName}") }
                        }
                    }
                }
            }
        }
    }
    editTarget?.let { profile ->
        ProfileNameDialog(
            title = "Rename profile",
            initialName = profile.displayName,
            confirmLabel = "Save",
            onConfirm = { onRename(profile.id, it); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }
    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${profile.displayName}?") },
            text = { Text("This removes the encrypted local voice profile. It cannot be recovered.") },
            confirmButton = { Button(onClick = { onDelete(profile.id); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ProfilePickerScreen(profiles: List<SpeakerProfile>, onSelect: (SpeakerProfile) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose a profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Verification compares only against the person you select.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { profile ->
                OutlinedButton(onClick = { onSelect(profile) }, modifier = Modifier.fillMaxWidth()) { Text(profile.displayName) }
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(title: String, initialName: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Profile name") }, singleLine = true) },
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
