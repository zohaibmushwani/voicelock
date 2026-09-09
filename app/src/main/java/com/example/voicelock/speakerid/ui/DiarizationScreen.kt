package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * UI-only diarization experiment. It intentionally renders a synthetic signal: live VAD,
 * segmentation, embedding extraction, and clustering are not wired into this screen yet.
 */
@Composable
fun DiarizationScreen(modifier: Modifier = Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            phase += 0.28f
            delay(250)
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Speaker diarization", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Prototype timeline — it illustrates how a future long-recording analysis will show who spoke when.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge()
                Text("Rolling audio level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                RollingWaveform(phase, Modifier.fillMaxWidth().height(156.dp))
                Text(
                    "Synthetic preview signal — microphone audio is not recorded on this screen.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diarized timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("0:00                                      0:12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SpeakerTimeline()
                TimelineLegend()
                Text(
                    "Future pipeline: VAD → speech segments → embeddings → clustering → timeline labels.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.tertiary))
        Spacer(Modifier.width(8.dp))
        Text("LIVE PREVIEW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun SpeakerTimeline() {
    val speakerOne = MaterialTheme.colorScheme.primary
    val speakerTwo = MaterialTheme.colorScheme.tertiary
    val overlap = MaterialTheme.colorScheme.error
    Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        TimelineSegment(1.5f, speakerOne, "Speaker 1")
        TimelineSegment(0.8f, speakerTwo, "Speaker 2")
        TimelineSegment(0.55f, overlap, "Overlap")
        TimelineSegment(1.15f, speakerOne, "Speaker 1")
    }
}

@Composable
private fun RowScope.TimelineSegment(weight: Float, color: Color, label: String) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(46.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

@Composable
private fun TimelineLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem("Speaker 1", MaterialTheme.colorScheme.primary)
        LegendItem("Speaker 2", MaterialTheme.colorScheme.tertiary)
        LegendItem("Overlap", MaterialTheme.colorScheme.error)
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

private const val WAVEFORM_POINTS = 48

@Composable
private fun RollingWaveform(phase: Float, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        drawLine(guideColor, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 1.dp.toPx())
        val path = Path()
        repeat(WAVEFORM_POINTS) { index ->
            val x = size.width * index / (WAVEFORM_POINTS - 1)
            val y = centerY - (syntheticAmplitude(index, phase).toFloat() * centerY * 1.65f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun syntheticAmplitude(index: Int, phase: Float): Double {
    val position = (index + phase) * 0.52
    val envelope = 0.18 + (index % 11) / 18.0
    return abs(sin(position * PI) * sin((position + phase) * PI * 0.31)) * envelope
}
