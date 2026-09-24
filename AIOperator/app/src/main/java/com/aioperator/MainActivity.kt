package com.aioperator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aioperator.ui.MainViewModel
import com.aioperator.ui.screens.ActivityScreen
import com.aioperator.ui.screens.HomeScreen
import com.aioperator.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                var selectedItem by remember { mutableIntStateOf(0) }
                val items = listOf("Home", "Activity", "Settings")

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = {
                                        when (index) {
                                            0 -> Icon(Icons.Default.Home, contentDescription = item)
                                            1 -> Icon(Icons.Default.History, contentDescription = item)
                                            else -> Icon(Icons.Default.Settings, contentDescription = item)
                                        }
                                    },
                                    label = { Text(item) },
                                    selected = selectedItem == index,
                                    onClick = {
                                        selectedItem = index
                                        navController.navigate(item) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "Home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("Home") { HomeScreen(viewModel) }
                        composable("Activity") { ActivityScreen(viewModel) }
                        composable("Settings") { SettingsScreen(viewModel) }
                    }
                }
            }
        }
    }
}