package com.example.voicelock.speakerid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.voicelock.speakerid.ui.VoiceLockScreen
import com.example.voicelock.speakerid.ui.VoiceLockUiState
import com.example.voicelock.speakerid.ui.VoiceLockViewModel
import com.example.voicelock.speakerid.ui.VoiceFlowEvent

@Composable
fun VoiceLockNavHost(
    hasMicrophonePermission: Boolean,
    requestMicrophonePermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VoiceLockViewModel = viewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val start = if (hasMicrophonePermission) AppDestination.Home else AppDestination.Permission
    val backStack = rememberNavBackStack(start)
    val navigate: (AppDestination) -> Unit = remember(backStack) { { destination ->
        backStack.add(destination)
    } }
    LaunchedEffect(hasMicrophonePermission) {
        if (hasMicrophonePermission && backStack.lastOrNull() == AppDestination.Permission) {
            backStack.removeTop()
            backStack.add(AppDestination.Home)
        }
    }
    LaunchedEffect(viewModel, backStack) {
        viewModel.events.collect { event ->
            when (event) {
                is VoiceFlowEvent.EnrollmentProgress -> {
                    if (backStack.lastOrNull() is AppDestination.Enroll) backStack.removeTop()
                    backStack.add(AppDestination.Enroll(event.completed))
                }
                VoiceFlowEvent.EnrollmentComplete -> {
                    if (backStack.lastOrNull() is AppDestination.Enroll) backStack.removeTop()
                    backStack.add(AppDestination.Result(ResultType.ENROLLMENT))
                }
                is VoiceFlowEvent.Verification -> {
                    if (backStack.lastOrNull() == AppDestination.Verify) backStack.removeTop()
                    backStack.add(AppDestination.Result(ResultType.VERIFICATION, event.accepted, event.percent))
                }
                is VoiceFlowEvent.Failed -> Unit
            }
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (!ui.busy && backStack.size > 1) backStack.removeTop() },
        entryProvider = entryProvider {
            entry<AppDestination.Permission> {
                VoiceLockScreen(VoiceLockUiState.PermissionRequired, onEnroll = requestMicrophonePermission, onVerify = requestMicrophonePermission, statusLines = ui.logs)
            }
            entry<AppDestination.Home> {
                VoiceLockScreen(if (ui.enrolled) VoiceLockUiState.Enrolled else VoiceLockUiState.NotEnrolled, onEnroll = { navigate(AppDestination.Enroll()) }, onVerify = { navigate(AppDestination.Verify) }, statusLines = ui.logs)
            }
            entry<AppDestination.Enroll> { destination ->
                val sentence = ENROLLMENT_SENTENCES[destination.completedUtterances.coerceIn(0, ENROLLMENT_SENTENCES.lastIndex)]
                VoiceLockScreen(
                    ui.error?.let(VoiceLockUiState::Error) ?: VoiceLockUiState.Processing(
                        detail = if (ui.busy) "Recording now — read the complete sentence below." else "Tap record first, then read the sentence below clearly.",
                        actionLabel = "Record sample ${destination.completedUtterances + 1}",
                        progress = (destination.completedUtterances + 1) / 3f,
                        prompt = sentence,
                    ),
                    onEnroll = { viewModel.clearError(); viewModel.recordEnrollmentSample(destination.completedUtterances) },
                    onVerify = {},
                    statusLines = ui.logs,
                    actionsEnabled = !ui.busy,
                )
            }
            entry<AppDestination.Verify> {
                VoiceLockScreen(
                    ui.error?.let(VoiceLockUiState::Error) ?: VoiceLockUiState.Processing(
                        detail = if (ui.busy) "Recording now — speak naturally until it finishes." else "Tap verify first, then speak for the full 4-second recording.",
                        actionLabel = "Verify my voice",
                        progress = 1f,
                        prompt = "My voice is my secure key, and it stays on this device.",
                    ),
                    onEnroll = { viewModel.clearError(); viewModel.verify() }, onVerify = {}, statusLines = ui.logs,
                    actionsEnabled = !ui.busy,
                )
            }
            entry<AppDestination.Result> { result ->
                val state = when (result.type) {
                    ResultType.ENROLLMENT -> VoiceLockUiState.EnrollmentComplete
                    ResultType.VERIFICATION -> if (result.accepted == true && result.similarityPercent != null) {
                        VoiceLockUiState.VerificationAccepted(result.similarityPercent / 100f)
                    } else VoiceLockUiState.VerificationRejected(result.similarityPercent?.div(100f))
                }
                VoiceLockScreen(state, onEnroll = { navigate(AppDestination.Enroll()) }, onVerify = { navigate(AppDestination.Verify) }, statusLines = ui.logs)
            }
        },
    )
}

/**
 * Navigation 3's [NavBackStack] is list-like, but its newer `removeLast` member is not
 * present in every compatible runtime artifact. Use the stable MutableList operation so
 * an app built against a newer compiler cannot fail on an older Navigation 3 runtime.
 */
private fun <T> MutableList<T>.removeTop() {
    removeAt(lastIndex)
}

private val ENROLLMENT_SENTENCES = listOf(
    "My voice is my secure key, and it stays on this device.",
    "VoiceLock verifies me safely and privately.",
    "I am enrolling my voice for secure access.",
)
