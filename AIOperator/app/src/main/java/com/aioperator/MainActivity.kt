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
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aioperator.ui.MainViewModel
import com.aioperator.ui.screens.ActivityScreen
import com.aioperator.ui.screens.HomeScreen
import com.aioperator.ui.screens.SettingsScreen

// Professional Cyber-Dark Colors
private val DarkBackground = Color(0xFF0B0F19)
private val CardSurface = Color(0xFF161F36)
private val NeonPurple = Color(0xFF8B5CF6)
private val NeonCyan = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)

private val ZoyaDarkTheme = darkColorScheme(
    primary = NeonPurple,
    secondary = NeonCyan,
    background = DarkBackground,
    surface = CardSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = ZoyaDarkTheme) {
                val navController = rememberNavController()
                var selectedItem by remember { mutableIntStateOf(0) }
                val items = listOf("Home", "Activity", "Settings")

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = TextPrimary
                        ) {
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = NeonPurple,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
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
