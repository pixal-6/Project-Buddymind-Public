package com.nvker.buddymind

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.nvker.buddymind.ui.theme.BuddymindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddymindTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    // Shared Preferences to store the time limit
    val sharedPrefs = remember { context.getSharedPreferences("BuddymindPrefs", Context.MODE_PRIVATE) }

    // State variables
    var isGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var timeLimit by remember { mutableStateOf(sharedPrefs.getInt("time_limit", 30).toString()) }
    var userRiddle by remember { mutableStateOf("") }
    var userAnswer by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Fixes screen cut-off
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Buddymind", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTION 1: SET DAILY LIMIT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Set Daily Limit", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = timeLimit,
                    onValueChange = { newValue ->
                        // Only allow numbers
                        if (newValue.all { it.isDigit() }) {
                            timeLimit = newValue
                            val minutes = newValue.toIntOrNull() ?: 30
                            sharedPrefs.edit().putInt("time_limit", minutes).apply()
                        }
                    },
                    label = { Text("Minutes per app") },
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Text(
                    text = "App will alert you after $timeLimit minutes",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 2: MONITORING ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Monitoring Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (!isGranted) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("Grant Usage Permission")
                    }
                } else {
                    Text("Monitoring Enabled ✅", color = MaterialTheme.colorScheme.primary)

                    Button(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            val serviceIntent = Intent(context, ScreenTimeService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }) {
                        Text("Start Background Monitor")
                    }
                }
                TextButton(onClick = { isGranted = hasUsageStatsPermission(context) }) {
                    Text("Refresh Permission Status")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTION 3: COMMUNITY RIDDLES ---
        Text("Share a Community Riddle", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = userRiddle,
            onValueChange = { userRiddle = it },
            label = { Text("Enter a Riddle") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            label = { Text("The Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (userRiddle.isNotEmpty() && userAnswer.isNotEmpty()) {
                    val data = hashMapOf(
                        "text" to userRiddle,
                        "answer" to userAnswer,
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("community_riddles").add(data)
                        .addOnSuccessListener {
                            statusMessage = "Shared successfully!"
                            userRiddle = ""
                            userAnswer = ""
                        }
                        .addOnFailureListener { statusMessage = "Error: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Post to Cloud")
        }
        Text(text = statusMessage, modifier = Modifier.padding(bottom = 32.dp))
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}