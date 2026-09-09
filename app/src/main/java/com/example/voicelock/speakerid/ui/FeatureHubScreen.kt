package com.example.voicelock.speakerid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FeatureHubScreen(
    profileCount: Int,
    onVerify: () -> Unit,
    onIdentify: () -> Unit,
    onDiarization: () -> Unit,
    onManageProfiles: () -> Unit,
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Voice experiments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "$profileCount of 10 local voice profiles enrolled. Audio and templates remain on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FeatureCard("Verify a Voice", "Choose a claimed profile and verify it.", Icons.Outlined.Security, enabled = profileCount > 0, onClick = onVerify)
        FeatureCard("Identify Speaker", "Find which enrolled person is speaking.", Icons.Outlined.PersonSearch, enabled = profileCount > 0, onClick = onIdentify)
        FeatureCard("Manage Profiles", "Add, rename, or delete local voice profiles.", Icons.Outlined.ManageAccounts, enabled = true, onClick = onManageProfiles)
        FeatureCard("Speaker Diarization", "Prototype — view the future live speaker timeline.", Icons.Outlined.GraphicEq, enabled = true, onClick = onDiarization)
        FeatureCard("Target Speaker Extraction", "Coming soon — isolate an enrolled voice from overlapping speech.", Icons.Outlined.Groups, enabled = false, onClick = {})
        StatusLogPanel(logs)
    }
}

@Composable
private fun FeatureCard(title: String, detail: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
