package com.aioperator.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aioperator.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val savedKey by viewModel.apiKey.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityEnabled.collectAsState()
    var apiKeyInput by remember { mutableStateOf(savedKey) }

    LaunchedEffect(savedKey) {
        apiKeyInput = savedKey
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("Anthropic API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.saveApiKey(apiKeyInput) }) {
            Text("Save API Key")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Permissions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAccessibilityActive) "Accessibility Service: Active ✓" else "Accessibility Service: Disabled ✗",
            color = if (isAccessibilityActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }
    }
}