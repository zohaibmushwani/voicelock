package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Presentation-only screen. The host supplies actions once the audio pipeline is available. */
@Composable
fun VoiceLockScreen(
    state: VoiceLockUiState,
    onEnroll: () -> Unit,
    onVerify: () -> Unit,
    statusLines: List<String> = emptyList(),
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(PaddingValues(horizontal = 28.dp, vertical = 36.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    imageVector = when (state) {
                        is VoiceLockUiState.VerificationAccepted, VoiceLockUiState.EnrollmentComplete -> Icons.Outlined.CheckCircle
                        is VoiceLockUiState.VerificationRejected, is VoiceLockUiState.Error -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("VoiceLock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (state is VoiceLockUiState.Processing) "On-device processing" else "Private by design") },
                    colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (state) {
                    is VoiceLockUiState.Processing -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(state.detail, style = MaterialTheme.typography.labelLarge)
                        state.prompt?.let { prompt ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("Say this sentence", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("“$prompt”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        Text("Use a normal speaking voice in a quiet place.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onEnroll, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(if (actionsEnabled) state.actionLabel else "Recording…") }
                    }
                    VoiceLockUiState.PermissionRequired -> Button(onClick = onEnroll, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Text(" Allow microphone")
                    }
                    VoiceLockUiState.NotEnrolled -> {
                        Button(onClick = onEnroll, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                            Text(" Enroll voice")
                        }
                    }
                    is VoiceLockUiState.Enrolled,
                    VoiceLockUiState.EnrollmentComplete,
                    is VoiceLockUiState.VerificationRejected -> {
                        Button(onClick = onVerify, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Security, contentDescription = null)
                            Text(" Verify voice")
                        }
                        OutlinedButton(onClick = onEnroll, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                            Text("Enroll again")
                        }
                    }
                    is VoiceLockUiState.Error -> Button(onClick = onEnroll, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                    is VoiceLockUiState.VerificationAccepted -> {
                        Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) { Text("Verify again") }
                    }
                }
            }
        }
        StatusLogPanel(statusLines)
    }
}

@Composable
private fun StatusLogPanel(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("App status · local only", style = MaterialTheme.typography.labelLarge)
            Text(lines.takeLast(5).ifEmpty { listOf("Ready") }.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

sealed interface VoiceLockUiState {
    val message: String

    data object NotEnrolled : VoiceLockUiState {
        override val message = "Set up your local voice unlock. Your voiceprint stays on this device."
    }
    data object PermissionRequired : VoiceLockUiState {
        override val message = "VoiceLock needs microphone access to create and verify your local voiceprint."
    }
    data class Processing(
        val detail: String = "Listening securely…",
        val actionLabel: String = "Record this sample",
        val progress: Float = 0.25f,
        val prompt: String? = null,
    ) : VoiceLockUiState {
        override val message = "Keep speaking naturally. Processing happens only on this device."
    }
    data object Enrolled : VoiceLockUiState {
        override val message = "Voiceprint enrolled. Speak naturally to verify your identity."
    }
    data object EnrollmentComplete : VoiceLockUiState {
        override val message = "Enrollment is complete. You can now verify your voice whenever you are ready."
    }
    data class VerificationAccepted(val similarity: Float) : VoiceLockUiState {
        override val message = "Identity confirmed — ${(similarity * 100).toInt()}% voice match."
    }
    data class VerificationRejected(val similarity: Float?) : VoiceLockUiState {
        override val message = similarity?.let { "Voice match was ${(it * 100).toInt()}%. Please try again." }
            ?: "We could not confirm your identity. Please try again."
    }
    data class Error(val detail: String) : VoiceLockUiState {
        override val message = detail
    }
}
