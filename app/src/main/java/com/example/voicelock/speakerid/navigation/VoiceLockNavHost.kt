package com.example.voicelock.speakerid.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.voicelock.speakerid.ui.FeatureHubScreen
import com.example.voicelock.speakerid.ui.DiarizationScreen
import com.example.voicelock.speakerid.ui.ProfileManagementScreen
import com.example.voicelock.speakerid.ui.ProfilePickerScreen
import com.example.voicelock.speakerid.ui.VoiceFlowEvent
import com.example.voicelock.speakerid.ui.VoiceLockScreen
import com.example.voicelock.speakerid.ui.VoiceLockUiState
import com.example.voicelock.speakerid.ui.VoiceLockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLockNavHost(
    hasMicrophonePermission: Boolean,
    requestMicrophonePermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VoiceLockViewModel = viewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val start = if (hasMicrophonePermission) AppDestination.Hub else AppDestination.Permission
    val backStack = rememberNavBackStack(start)
    val navigate: (AppDestination) -> Unit = remember(backStack) { { destination -> backStack.add(destination) } }

    LaunchedEffect(hasMicrophonePermission) {
        if (hasMicrophonePermission && backStack.lastOrNull() == AppDestination.Permission) {
            backStack.removeTop()
            backStack.add(AppDestination.Hub)
        }
    }
    LaunchedEffect(viewModel, backStack) {
        viewModel.events.collect { event ->
            when (event) {
                is VoiceFlowEvent.EnrollmentProgress -> if (backStack.lastOrNull() is AppDestination.Enroll) {
                    backStack.removeTop(); backStack.add(AppDestination.Enroll(event.completed))
                }
                is VoiceFlowEvent.EnrollmentComplete -> if (backStack.lastOrNull() is AppDestination.Enroll) {
                    backStack.removeTop(); backStack.add(AppDestination.Result(ResultType.ENROLLMENT, profileId = event.profileId))
                }
                is VoiceFlowEvent.Verification -> if (backStack.lastOrNull() is AppDestination.Verify) {
                    backStack.removeTop(); backStack.add(AppDestination.Result(ResultType.VERIFICATION, event.profileId, event.accepted, event.percent))
                }
                is VoiceFlowEvent.Identification -> if (backStack.lastOrNull() == AppDestination.Identify) {
                    backStack.removeTop(); backStack.add(AppDestination.Result(ResultType.IDENTIFICATION, event.profileId, similarityPercent = event.percent, unknown = event.unknown))
                }
                is VoiceFlowEvent.Failed -> Unit
            }
        }
    }

    val canPop = backStack.size > 1 && !ui.busy
    val onBack = { if (canPop) backStack.removeTop() }
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VoiceLock", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (canPop) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = onBack,
            entryProvider = entryProvider {
                entry<AppDestination.Permission> {
                    VoiceLockScreen(state = VoiceLockUiState.PermissionRequired, onEnroll = requestMicrophonePermission, onVerify = requestMicrophonePermission, statusLines = ui.logs)
                }
                entry<AppDestination.Hub> {
                    FeatureHubScreen(ui.profiles.size, onVerify = { navigate(AppDestination.ProfilePicker) }, onIdentify = { navigate(AppDestination.Identify) }, onDiarization = { navigate(AppDestination.Diarization) }, onManageProfiles = { navigate(AppDestination.Profiles) }, logs = ui.logs)
                }
                entry<AppDestination.Profiles> {
                    ProfileManagementScreen(
                        profiles = ui.profiles,
                        busy = ui.busy,
                        error = ui.error,
                        onStartEnrollment = { name -> if (viewModel.beginEnrollment(name)) navigate(AppDestination.Enroll()) },
                        onRename = viewModel::renameProfile,
                        onDelete = viewModel::deleteProfile,
                    )
                }
                entry<AppDestination.ProfilePicker> {
                    ProfilePickerScreen(ui.profiles, onSelect = { navigate(AppDestination.Verify(it.id)) })
                }
                entry<AppDestination.Enroll> { destination ->
                    val sentence = ENROLLMENT_SENTENCES[destination.completedUtterances.coerceIn(0, ENROLLMENT_SENTENCES.lastIndex)]
                    VoiceLockScreen(
                        state = ui.error?.let(VoiceLockUiState::Error) ?: VoiceLockUiState.Processing(
                            detail = if (ui.busy) "Recording now — read the complete sentence below." else "Tap record, then read the sentence below clearly.",
                            actionLabel = "Record sample ${destination.completedUtterances + 1}",
                            progress = (destination.completedUtterances + 1) / 3f,
                            prompt = sentence,
                        ),
                        onEnroll = { viewModel.clearError(); viewModel.recordEnrollmentSample(destination.completedUtterances) },
                        onVerify = {}, statusLines = ui.logs, actionsEnabled = !ui.busy, isMinimal = true,
                    )
                }
                entry<AppDestination.Verify> { destination ->
                    val profile = ui.profiles.firstOrNull { it.id == destination.profileId }
                    VoiceLockScreen(
                        state = ui.error?.let(VoiceLockUiState::Error) ?: VoiceLockUiState.Processing(
                            detail = if (ui.busy) "Recording now — speak naturally until it finishes." else "Verifying ${profile?.displayName ?: "selected profile"}. Speak for the full 4-second recording.",
                            actionLabel = "Verify voice", progress = 1f, prompt = "My voice is my secure key, and it stays on this device.",
                        ),
                        onEnroll = { viewModel.clearError(); viewModel.verify(destination.profileId) },
                        onVerify = {}, statusLines = ui.logs, actionsEnabled = !ui.busy, isMinimal = true,
                    )
                }
                entry<AppDestination.Identify> {
                    VoiceLockScreen(
                        state = ui.error?.let(VoiceLockUiState::Error) ?: VoiceLockUiState.Processing(
                            detail = if (ui.busy) "Recording now — identifying the closest enrolled speaker." else "Tap identify, then speak naturally for the full 4-second recording.",
                            actionLabel = "Identify speaker", progress = 1f, prompt = "My voice is my secure key, and it stays on this device.",
                        ),
                        onEnroll = { viewModel.clearError(); viewModel.identify() },
                        onVerify = {}, statusLines = ui.logs, actionsEnabled = !ui.busy, isMinimal = true,
                    )
                }
                entry<AppDestination.Diarization> {
                    DiarizationScreen()
                }
                entry<AppDestination.Result> { result ->
                    val profileName = result.profileId?.let { id -> ui.profiles.firstOrNull { it.id == id }?.displayName }
                    val state = when (result.type) {
                        ResultType.ENROLLMENT -> VoiceLockUiState.EnrollmentComplete
                        ResultType.VERIFICATION -> if (result.accepted == true && result.similarityPercent != null) VoiceLockUiState.VerificationAccepted(result.similarityPercent / 100f) else VoiceLockUiState.VerificationRejected(result.similarityPercent?.div(100f))
                        ResultType.IDENTIFICATION -> if (!result.unknown && profileName != null && result.similarityPercent != null) VoiceLockUiState.IdentificationAccepted(profileName, result.similarityPercent / 100f) else VoiceLockUiState.UnknownSpeaker(result.similarityPercent?.div(100f))
                    }
                    VoiceLockScreen(
                        state = state,
                        onEnroll = { navigate(AppDestination.Profiles) },
                        onVerify = { if (result.type == ResultType.IDENTIFICATION) navigate(AppDestination.Identify) else navigate(AppDestination.ProfilePicker) },
                        statusLines = ui.logs,
                    )
                }
            },
        )
    }
}

private fun <T> MutableList<T>.removeTop() = removeAt(lastIndex)

private val ENROLLMENT_SENTENCES = listOf(
    "My voice is my secure key, and it stays on this device.",
    "VoiceLock verifies me safely and privately.",
    "I am enrolling my voice for secure access.",
)
