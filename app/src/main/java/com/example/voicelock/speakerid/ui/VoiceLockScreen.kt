package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.sp

/** Presentation-only screen. The host supplies actions once the audio pipeline is available. */
@Composable
fun VoiceLockScreen(
    modifier: Modifier = Modifier,
    state: VoiceLockUiState,
    onEnroll: () -> Unit,
    onVerify: () -> Unit,
    statusLines: List<String> = emptyList(),
    actionsEnabled: Boolean = true,
    isMinimal: Boolean = false,
) {
    val contentModifier = if (isMinimal) {
        modifier.fillMaxSize().padding(16.dp)
    } else {
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    }

    Column(
        modifier = contentModifier,
        verticalArrangement = if (isMinimal) Arrangement.Center else Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (isMinimal) 20.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isMinimal) 16.dp else 20.dp),
            ) {
                Icon(
                    imageVector = when (state) {
                        is VoiceLockUiState.VerificationAccepted, is VoiceLockUiState.IdentificationAccepted, VoiceLockUiState.EnrollmentComplete -> Icons.Outlined.CheckCircle
                        is VoiceLockUiState.VerificationRejected, is VoiceLockUiState.UnknownSpeaker, is VoiceLockUiState.Error -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.Security
                    },
                    contentDescription = null,
                    modifier = Modifier.size(if (isMinimal) 48.dp else 64.dp),
                    tint = when (state) {
                        is VoiceLockUiState.VerificationAccepted, is VoiceLockUiState.IdentificationAccepted, VoiceLockUiState.EnrollmentComplete -> MaterialTheme.colorScheme.primary
                        is VoiceLockUiState.VerificationRejected, is VoiceLockUiState.UnknownSpeaker, is VoiceLockUiState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = when (state) {
                            is VoiceLockUiState.EnrollmentComplete -> "Enrollment Successful"
                            is VoiceLockUiState.VerificationAccepted -> "Access Granted"
                            is VoiceLockUiState.VerificationRejected -> "Access Denied"
                            is VoiceLockUiState.IdentificationAccepted -> "Speaker Identified"
                            is VoiceLockUiState.UnknownSpeaker -> "Unknown Speaker"
                            is VoiceLockUiState.Processing -> "Voice Recognition"
                            else -> "VoiceLock"
                        },
                        style = if (isMinimal) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(if (state is VoiceLockUiState.Processing) "Secure Processing" else "Private & Secure") },
                        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }

                Text(
                    text = state.message,
                    style = if (isMinimal) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!isMinimal) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                when (state) {
                    is VoiceLockUiState.Processing -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = state.detail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        state.prompt?.let { prompt ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "SAY THIS CLEARLY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "“$prompt”",
                                        style = if (isMinimal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (!isMinimal) {
                            Text(
                                text = "Speak at a normal volume in a quiet environment.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onEnroll,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (actionsEnabled) state.actionLabel else "Recording…")
                        }
                    }
                    VoiceLockUiState.PermissionRequired -> Button(
                        onClick = onEnroll,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Enable Microphone Access")
                    }
                    VoiceLockUiState.NotEnrolled -> {
                        Button(
                            onClick = onEnroll,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Start Voice Enrollment")
                        }
                    }
                    is VoiceLockUiState.Enrolled,
                    VoiceLockUiState.EnrollmentComplete,
                    is VoiceLockUiState.VerificationRejected -> {
                        Button(
                            onClick = onVerify,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Verify Identity")
                        }
                        OutlinedButton(
                            onClick = onEnroll,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Enroll New Voiceprint")
                        }
                    }
                    is VoiceLockUiState.Error -> Button(
                        onClick = onEnroll,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Retry Operation")
                    }
                    is VoiceLockUiState.VerificationAccepted -> {
                        Button(
                            onClick = onVerify,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Verify Again")
                        }
                    }
                    is VoiceLockUiState.IdentificationAccepted,
                    is VoiceLockUiState.UnknownSpeaker -> {
                        Button(onClick = onVerify, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Text("Identify Another Speaker")
                        }
                        OutlinedButton(onClick = onEnroll, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Text("Manage Profiles")
                        }
                    }
                }
            }
        }
        if (!isMinimal) {
            StatusLogPanel(statusLines)
        }
    }
}

@Composable
fun StatusLogPanel(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Security Logs",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Local Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                text = lines.takeLast(5).ifEmpty { listOf("System ready.") }.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
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
        override val message = "Identity confirmed. Voice similarity: ${(similarity * 100).toInt()}%."
    }
    data class VerificationRejected(val similarity: Float?) : VoiceLockUiState {
        override val message = similarity?.let { "Voice similarity was ${(it * 100).toInt()}%, below the required level. Please try again." }
            ?: "We could not confirm your identity. Please try again."
    }
    data class IdentificationAccepted(val displayName: String, val similarity: Float) : VoiceLockUiState {
        override val message = "$displayName is the closest enrolled speaker. Voice similarity: ${(similarity * 100).toInt()}%."
    }
    data class UnknownSpeaker(val bestSimilarity: Float?) : VoiceLockUiState {
        override val message = bestSimilarity?.let {
            "No enrolled speaker reached the required level. Best voice similarity: ${(it * 100).toInt()}%."
        } ?: "No enrolled speaker could be identified."
    }
    data class Error(val detail: String) : VoiceLockUiState {
        override val message = detail
    }
}
