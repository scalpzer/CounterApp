package com.example.counterapp

// Android & Compose imports
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// DataStore for persistent storage (saves app data)
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

// Theme import
import com.example.counterapp.ui.theme.CounterAppTheme

// Coroutines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// -----------------------------
// DATASTORE SETUP
// -----------------------------

// Extension property that lets you access a DataStore from your activity
val ComponentActivity.dataStore by preferencesDataStore(name = "settings")

// -----------------------------
// MAIN ACTIVITY (entry point)
// -----------------------------

class MainActivity : ComponentActivity() {

    // Key used to save/load counter value in DataStore
    private val COUNT_KEY = intPreferencesKey("count")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Makes layout extend behind system bars

        setContent {
            // Set the app’s visual theme (Material 3)
            CounterAppTheme {

                // Compose tools
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                // --- STATE VARIABLES (remembered across recompositions) ---
                var count by remember { mutableIntStateOf(0) }              // current set count
                var totalSets by remember { mutableIntStateOf(10) }         // total number of sets
                var timerText by remember { mutableStateOf("01:00") }       // displayed timer text
                var timerDurationMillis by remember { mutableLongStateOf(60_000L) } // timer length in ms
                var showDialog by remember { mutableStateOf(false) }        // controls "Next Set" popup visibility
                var isTimerRunning by remember { mutableStateOf(false) }    // tracks if timer is active

                // Will hold reference to the current running CountDownTimer (so we can cancel it)
                var countDownTimer by remember { mutableStateOf<CountDownTimer?>(null) }

                // --- Load saved count value from DataStore when app starts ---
                LaunchedEffect(Unit) {
                    val prefs = dataStore.data.first()
                    count = prefs[COUNT_KEY] ?: 0
                }

                // --- Save count to DataStore whenever it changes ---
                LaunchedEffect(count) {
                    scope.launch {
                        dataStore.edit { settings ->
                            settings[COUNT_KEY] = count
                        }
                    }
                }

                // --- MAIN SCREEN ---
                CounterScreen(
                    count = count,
                    totalSets = totalSets,
                    timerText = timerText,
                    isTimerRunning = isTimerRunning,

                    // When "+" button pressed
                    onIncrement = {
                        count++ // increase count
                        countDownTimer?.cancel() // stop any previous timer
                        isTimerRunning = true

                        // Start a new countdown timer
                        countDownTimer = object : CountDownTimer(timerDurationMillis, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                val totalSec = millisUntilFinished / 1000
                                val min = totalSec / 60
                                val sec = totalSec % 60
                                // Update displayed timer text
                                timerText = String.format("%02d:%02d", min, sec)
                            }

                            override fun onFinish() {
                                timerText = "00:00"
                                isTimerRunning = false
                                showDialog = true // Show “Next Set” dialog

                                // Auto dismiss after 3 seconds
                                scope.launch {
                                    kotlinx.coroutines.delay(3000)
                                    showDialog = false
                                }
                            }
                        }.start()
                    },

                    // When "-" button pressed
                    onDecrement = {
                        if (count > 0) count--
                        countDownTimer?.cancel()
                        isTimerRunning = false
                        val min = (timerDurationMillis / 1000) / 60
                        val sec = (timerDurationMillis / 1000) % 60
                        timerText = String.format("%02d:%02d", min, sec)
                    },

                    // When "Reset" button pressed
                    onReset = {
                        count = 0
                        countDownTimer?.cancel()
                        isTimerRunning = false
                        val min = (timerDurationMillis / 1000) / 60
                        val sec = (timerDurationMillis / 1000) % 60
                        timerText = String.format("%02d:%02d", min, sec)
                    },

                    // Called when user sets a new timer duration
                    onSetTimer = { newDuration ->
                        timerDurationMillis = newDuration
                        countDownTimer?.cancel()
                        isTimerRunning = false
                        val min = (newDuration / 1000) / 60
                        val sec = (newDuration / 1000) % 60
                        timerText = String.format("%02d:%02d", min, sec)
                        Toast.makeText(context, "Timer: $min min $sec s", Toast.LENGTH_SHORT).show()
                    },

                    // Called when user sets total number of sets
                    onSetSets = { newTotalSets ->
                        totalSets = newTotalSets
                        Toast.makeText(context, "Total sets: $newTotalSets", Toast.LENGTH_SHORT).show()
                    },

                    // Dialog state control
                    showDialog = showDialog,
                    onDismissDialog = { showDialog = false }
                )
            }
        }
    }
}

// -----------------------------
// CUSTOM NUMBER PICKER
// -----------------------------

/*
 * A scrollable wheel-style number picker that loops infinitely.
 * Used for selecting minutes and seconds for the timer.
 */
@Composable
fun InfiniteScrollableNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    displayMultiplier: Int = 1
) {
    val rangeSize = range.count()
    val middleIndex = 50000 // middle position so it feels infinite
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = middleIndex + value - 1)
    val scope = rememberCoroutineScope()

    // When user stops scrolling, snap to nearest number and update state
    LaunchedEffect(listState.firstVisibleItemIndex, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (firstVisibleItem != null) {
                val offset = firstVisibleItem.offset
                if (offset != 0) {
                    scope.launch {
                        if (offset < -25) {
                            listState.animateScrollToItem(firstVisibleItem.index + 1)
                        } else {
                            listState.animateScrollToItem(firstVisibleItem.index)
                        }
                    }
                }
            }

            // Compute the selected value and call callback
            val actualValue = (listState.firstVisibleItemIndex + 1 - middleIndex).mod(rangeSize)
            onValueChange(actualValue)
        }
    }

    // Visual layout
    Box(
        modifier = modifier
            .height(150.dp)
            .width(80.dp)
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(100000) { index ->
                val itemValue = (index - middleIndex).mod(rangeSize)
                val displayValue = itemValue * displayMultiplier
                val isSelected = index == listState.firstVisibleItemIndex + 1

                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", displayValue),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = if (isSelected && !listState.isScrollInProgress) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }

        // Horizontal divider lines marking the selected value
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = (-25).dp),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = 25.dp),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// -----------------------------
// COUNTER SCREEN COMPOSABLE
// -----------------------------

/*
 * The main UI layout for the app.
 * Displays timer, count progress, buttons, and settings dialogs.
 */
@Composable
fun CounterScreen(
    count: Int,
    totalSets: Int,
    timerText: String,
    isTimerRunning: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onReset: () -> Unit,
    onSetTimer: (Long) -> Unit,
    onSetSets: (Int) -> Unit,
    showDialog: Boolean,
    onDismissDialog: () -> Unit
) {
    var showTimerPicker by remember { mutableStateOf(false) }
    var showSetsPicker by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(1) }
    var seconds by remember { mutableStateOf(0) }
    var setsInput by remember { mutableStateOf("") }

    // Animated progress bar value
    val progress by animateFloatAsState(targetValue = (count.coerceAtMost(totalSets)) / totalSets.toFloat())

    // Scaffold provides Material layout structure (top bar, content, bottom bar, etc.)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TIMER DISPLAY ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
                Text(text = "Rest", style = MaterialTheme.typography.headlineMedium)
                Text(text = timerText, fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
            }

            // --- COUNT SECTION ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = "$count / $totalSets", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // Progress bar (fills based on completed sets)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.8f).height(8.dp),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons for + and -
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onDecrement, enabled = count > 0, modifier = Modifier.size(64.dp)) {
                        Text("-", fontSize = 32.sp)
                    }
                    Button(onClick = onIncrement, modifier = Modifier.size(64.dp)) {
                        Text("+", fontSize = 32.sp)
                    }
                }
            }

            // --- SETTINGS + RESET ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                Button(onClick = onReset) { Text("Reset") }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showTimerPicker = true }) { Text("Set Timer") }
                    Button(onClick = { showSetsPicker = true }) { Text("Set Sets") }
                }
            }
        }

        // --- TIMER PICKER DIALOG ---
        if (showTimerPicker) {
            AlertDialog(
                onDismissRequest = { showTimerPicker = false },
                title = { Text("Set Timer Duration") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minutes", style = MaterialTheme.typography.labelMedium)
                                InfiniteScrollableNumberPicker(value = minutes, range = 0..59, onValueChange = { minutes = it })
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(":", style = MaterialTheme.typography.headlineLarge)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Seconds", style = MaterialTheme.typography.labelMedium)
                                InfiniteScrollableNumberPicker(
                                    value = seconds / 15,
                                    range = 0..3,
                                    onValueChange = { seconds = it * 15 },
                                    displayMultiplier = 15
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val total = (minutes * 60 + seconds) * 1000L
                        onSetTimer(total)
                        showTimerPicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimerPicker = false }) { Text("Cancel") }
                }
            )
        }

        // --- SETS PICKER DIALOG ---
        if (showSetsPicker) {
            AlertDialog(
                onDismissRequest = { showSetsPicker = false },
                title = { Text("Set Total Sets") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = setsInput,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || (newValue.toIntOrNull() != null && newValue.toInt() in 1..99)) {
                                    setsInput = newValue
                                }
                            },
                            label = { Text("Number of sets") },
                            placeholder = { Text("Enter number") },
                            singleLine = true,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val sets = setsInput.toIntOrNull() ?: 10
                        onSetSets(sets)
                        setsInput = ""
                        showSetsPicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        setsInput = ""
                        showSetsPicker = false
                    }) { Text("Cancel") }
                }
            )
        }

        // --- NEXT SET ALERT DIALOG ---
        if (showDialog) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("NEXT SET") },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) { Text("OK") }
                }
            )
        }
    }
}

// -----------------------------
// PREVIEW FOR ANDROID STUDIO
// -----------------------------

@Preview(showBackground = true)
@Composable
fun PreviewCounter() {
    CounterAppTheme {
        CounterScreen(
            count = 3,
            totalSets = 10,
            timerText = "00:45",
            isTimerRunning = true,
            onIncrement = {},
            onDecrement = {},
            onReset = {},
            onSetTimer = {},
            onSetSets = {},
            showDialog = false,
            onDismissDialog = {}
        )
    }
}
