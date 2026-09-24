package com.aioperator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aioperator.ui.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == "User"
                val bgColor = when (msg.sender) {
                    "User" -> MaterialTheme.colorScheme.primaryContainer
                    "Assistant" -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = bgColor,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = msg.sender, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (isLoading) {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isListening) {
                        viewModel.stopListening()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Voice",
                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isListening) "सुन रहा हूँ..." else "बोलें या टाइप करें...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
