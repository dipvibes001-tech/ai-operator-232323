package com.aioperator.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aioperator.service.FloatingBubbleService
import com.aioperator.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedKey by viewModel.apiKey.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityEnabled.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var isBubbleRunning by remember { mutableStateOf(false) }

    LaunchedEffect(savedKey) {
        if (savedKey.isNotBlank()) {
            apiKeyInput = savedKey
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("AI Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Universal AI API Key Input
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Universal AI API Key") },
                placeholder = { Text("Gemini / Claude / OpenAI / Groq Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (apiKeyInput.isNotBlank()) {
                        viewModel.saveApiKey(apiKeyInput.trim())
                        scope.launch {
                            snackbarHostState.showSnackbar("API Key सफलतापूर्वक सेव हो गई!")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("कृपया पहले API Key दर्ज करें")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save API Key")
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Floating 'Hey Zoya' Assistant", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)
                        )
                        context.startActivity(intent)
                    } else {
                        val serviceIntent = Intent(context, FloatingBubbleService::class.java)
                        if (!isBubbleRunning) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            isBubbleRunning = true
                            scope.launch {
                                snackbarHostState.showSnackbar("ज़ोया फ्लोटिंग असिस्टेंट चालू हो गया!")
                            }
                        } else {
                            context.stopService(serviceIntent)
                            isBubbleRunning = false
                            scope.launch {
                                snackbarHostState.showSnackbar("ज़ोया फ्लोटिंग असिस्टेंट बंद कर दिया गया!")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isBubbleRunning) "Stop 'Hey Zoya' Assistant" else "Start 'Hey Zoya' Assistant")
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Permissions", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isAccessibilityActive) "Accessibility Service: Active ✓" else "Accessibility Service: Disabled ✗",
                color = if (isAccessibilityActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }
        }
    }
}
