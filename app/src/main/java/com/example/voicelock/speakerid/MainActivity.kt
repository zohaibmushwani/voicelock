package com.example.voicelock.speakerid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.voicelock.speakerid.navigation.VoiceLockNavHost
import com.example.voicelock.speakerid.ui.theme.VoiceLockTheme

class MainActivity : ComponentActivity() {
    private var hasMicrophonePermission by mutableStateOf(false)
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicrophonePermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicrophonePermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        enableEdgeToEdge()
        setContent {
            VoiceLockTheme {
                VoiceLockNavHost(hasMicrophonePermission, requestMicrophonePermission = { microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO) })
            }
        }
    }
}


