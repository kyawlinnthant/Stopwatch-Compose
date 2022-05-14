package mdy.klt.stopwatchwithforegroundservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import mdy.klt.stopwatchwithforegroundservice.ui.theme.StopWatchWithForegroundServiceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopWatchWithForegroundServiceTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    StopwatchScreen()
                }
            }
        }
    }
}

@Composable
private fun StopwatchScreen() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLifecycle = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    val context = LocalContext.current
    val hours = remember { mutableStateOf(0) }
    val minutes = remember { mutableStateOf(0) }
    val seconds = remember { mutableStateOf(0) }
    val isRunning = remember { mutableStateOf(false) }
    val isPause = remember { mutableStateOf(false) }

    DisposableEffect(key1 = lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            latestLifecycle.value = event
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    if (latestLifecycle.value == Lifecycle.Event.ON_START) {
        moveToBackground(context = context)
    }
    if (latestLifecycle.value == Lifecycle.Event.ON_STOP) {
        moveToForeground(context = context)
    }

    if (latestLifecycle.value == Lifecycle.Event.ON_RESUME) {
        moveToBackground(context = context)
        getStopwatchStatus(context = context)
        isPause.value = false
        BroadcastListener(
            systemAction = StopwatchService.STOPWATCH_STATUS,
            onSystemEvent = {
                val running = it?.getBooleanExtra(StopwatchService.IS_STOPWATCH_RUNNING, false)!!
                val timeElapsed = it.getIntExtra(StopwatchService.TIME_ELAPSED, 0)
                isRunning.value = running
                hours.value = (timeElapsed / 60) / 60
                minutes.value = timeElapsed / 60
                seconds.value = timeElapsed % 60
            }
        )
        BroadcastListener(
            systemAction = StopwatchService.STOPWATCH_TICK,
            onSystemEvent = {
                val timeElapsed = it?.getIntExtra(StopwatchService.TIME_ELAPSED, 0)!!
                hours.value = (timeElapsed / 60) / 60
                minutes.value = timeElapsed / 60
                seconds.value = timeElapsed % 60
            }
        )
    }
    if (latestLifecycle.value == Lifecycle.Event.ON_PAUSE) {
        isPause.value = true
        moveToForeground(context = context)
    }


    StopwatchContent(
        hours = hours.value,
        minutes = minutes.value,
        seconds = seconds.value,
        isRunningUi = isRunning.value,
        onPlayClicked = {
            if (isRunning.value) {
                isRunning.value = false
                pauseStopwatch(context = context)
            } else {
                isRunning.value = true
                startStopwatch(context = context)
            }
        },
        onResetClicked = {
            resetStopwatch(context = context)
        }
    )

}

@Composable
private fun BroadcastListener(

    systemAction: String,
    onSystemEvent: (intent: Intent?) -> Unit,
) {
    // Grab the current context in this part of the UI tree
    val context = LocalContext.current

    // Safely use the latest onSystemEvent lambda passed to the function
    val currentOnSystemEvent by rememberUpdatedState(onSystemEvent)

    // If either context or systemAction changes, unregister and register again
    DisposableEffect(context,systemAction) {

        val intentFilter = IntentFilter(systemAction)
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentOnSystemEvent(intent)
            }
        }

        context.registerReceiver(broadcast, intentFilter)

        // When the effect leaves the Composition, remove the callback
        onDispose {
                context.unregisterReceiver(broadcast)
        }
    }
}


@Composable
private fun StopwatchContent(
    modifier: Modifier = Modifier,
    hours: Int,
    minutes: Int,
    seconds: Int,
    isRunningUi: Boolean,
    onPlayClicked: () -> Unit,
    onResetClicked: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${"%02d".format(hours)}:${"%02d".format(minutes)}:${"%02d".format(seconds)}",
            style = MaterialTheme.typography.h3
        )
        if (!isRunningUi) {
            Spacer(modifier = modifier.height(64.dp))
            TextButton(
                onClick = onResetClicked
            ) {
                Text(text = "Reset")
            }
        }
        Spacer(modifier = modifier.height(64.dp))
        Button(onClick = onPlayClicked) {
            if (isRunningUi) {
                Icon(imageVector = Icons.Default.ThumbUp, contentDescription = "running")
            } else {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "pause")
            }
        }
    }
}


private fun getStopwatchStatus(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(StopwatchService.STOPWATCH_ACTION, StopwatchService.GET_STATUS)
    context.startService(stopwatchService)
}

private fun moveToForeground(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(
        StopwatchService.STOPWATCH_ACTION,
        StopwatchService.MOVE_TO_FOREGROUND
    )
    context.startService(stopwatchService)
}

private fun moveToBackground(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(
        StopwatchService.STOPWATCH_ACTION,
        StopwatchService.MOVE_TO_BACKGROUND
    )
    context.startService(stopwatchService)
}

private fun startStopwatch(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(StopwatchService.STOPWATCH_ACTION, StopwatchService.START)
    context.startService(stopwatchService)
}

private fun pauseStopwatch(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(StopwatchService.STOPWATCH_ACTION, StopwatchService.PAUSE)
    context.startService(stopwatchService)
}

private fun resetStopwatch(context: Context) {
    val stopwatchService = Intent(context, StopwatchService::class.java)
    stopwatchService.putExtra(StopwatchService.STOPWATCH_ACTION, StopwatchService.RESET)
    context.startService(stopwatchService)
}



