package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voicelock.speakerid.audio.DiarizedSegment

/** Displays safe, low-rate level and clustering metadata. PCM and embeddings never reach Compose. */
@Composable
fun DiarizationScreen(
    state: DiarizationUiState?,
    onRecord: () -> Unit,
    onTestIncluded: () -> Unit,
    onChooseFile: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWorking = state?.isRecording == true || state?.isProcessing == true
    val capturedSeconds = (state?.capturedMs ?: 0) / 1_000f
    val clusterIds = state?.segments?.map(DiarizedSegment::speaker)?.distinct().orEmpty()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onChooseFile) }
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Speaker diarization", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Record a 20-second sample. You can play conversation audio from another device near this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRecord, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text(if (isWorking) "Working…" else "Record and cluster speakers")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onTestIncluded, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text("Test 3 voices") }
            Button(onClick = { filePicker.launch(arrayOf("audio/flac", "audio/wav", "audio/x-wav")) }, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text("Choose audio") }
        }
        if (state?.isRecording == true) {
            LinearProgressIndicator(progress = { (state.capturedMs / 20_000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("Recording ${"%.1f".format(capturedSeconds)} / 20.0 seconds", style = MaterialTheme.typography.labelMedium)
        }
        if (state?.isProcessing == true) Text("Extracting speech-window embeddings and clustering them on device…", style = MaterialTheme.typography.bodyMedium)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge(state?.isRecording == true)
                Text("Live audio level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                RollingWaveform(state?.waveform.orEmpty(), Modifier.fillMaxWidth().height(128.dp))
                Text("A decimated microphone level is shown while recording; raw audio is not retained by the screen.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diarized timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state?.segments.isNullOrEmpty()) {
                    Text("After a recording, detected speech regions will be grouped as Speaker 1 through Speaker 10.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${state.speechDurationMs / 1_000f}s speech across ${clusterIds.size} temporary speaker clusters", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DiarizedTimeline(state.segments, state.durationMs)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(clusterIds, key = { it }) { id -> LegendItem("Speaker $id", speakerColor(id)) }
                    }
                }
                Text("Speaker numbers are local to this recording. Overlapping voices may be grouped incorrectly in this first experiment.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveBadge(recording: Boolean) {
    val color = if (recording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(if (recording) "LIVE RECORDING" else "READY", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun RollingWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        drawLine(guideColor, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 1.dp.toPx())
        if (levels.size < 2) return@Canvas
        val path = Path()
        levels.forEachIndexed { index, level ->
            val x = size.width * index / levels.lastIndex.toFloat()
            val y = centerY - level.coerceIn(0f, 1f) * centerY * 1.8f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun DiarizedTimeline(segments: List<DiarizedSegment>, durationMs: Long) {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        drawRoundRect(background, cornerRadius = CornerRadius(8.dp.toPx()))
        val safeDuration = durationMs.coerceAtLeast(1)
        segments.forEach { segment ->
            val left = (segment.startMs.toFloat() / safeDuration * size.width).coerceIn(0f, size.width)
            val right = (segment.endMs.toFloat() / safeDuration * size.width).coerceIn(left + 1f, size.width)
            drawRoundRect(
                color = speakerColor(segment.speaker),
                topLeft = Offset(left, 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun speakerColor(id: Int): Color = SPEAKER_COLORS[(id - 1).mod(SPEAKER_COLORS.size)]

private val SPEAKER_COLORS = listOf(
    Color(0xFF386A20), Color(0xFF006C4C), Color(0xFF355CA8), Color(0xFF765B00), Color(0xFF8C4A60),
    Color(0xFF7A5900), Color(0xFF006780), Color(0xFF884B6D), Color(0xFF6C5F00), Color(0xFF5C5F71),
)
