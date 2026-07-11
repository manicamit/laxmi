package com.laxmi.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laxmi.app.agents.EngineState
import com.laxmi.app.data.EventStatus
import com.laxmi.app.ui.theme.LaxmiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* granted or not — notifications are best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: notifications are silently dropped without this grant.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Foreground context — safe to pin the process so the engine stays warm.
        com.laxmi.app.KeepWarmService.start(this)
        val fromAssist = intent?.action == android.content.Intent.ACTION_ASSIST
        setContent {
            LaxmiTheme {
                Surface(Modifier.fillMaxSize()) {
                    LaxmiApp(vm, fromAssist)
                }
            }
        }
    }
}

@Composable
fun LaxmiApp(vm: AppViewModel, fromAssist: Boolean = false) {
    val engineState by vm.engineState.collectAsState()

    // Only the model IMPORT blocks the whole app. Once the file exists, the ledger /
    // inbox / receipts all work while the engine warms in the background — capture and
    // ask show their own "warming up" state (see CaptureScreen / AskScreen).
    when (engineState) {
        EngineState.NO_MODEL -> ModelSetupScreen(vm)
        else -> MainTabs(vm, if (fromAssist) 1 else 0)
    }
}

@Composable
private fun MainTabs(vm: AppViewModel, startTab: Int = 0) {
    var tab by remember { mutableIntStateOf(startTab) }
    val events by vm.events.collectAsState()
    val busy by vm.busy.collectAsState()
    val pendingCount = events.count { it.status == EventStatus.PENDING_REVIEW }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("📒") }, label = { Text("Ledger") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("🎤") }, label = { Text("Bolo") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = {
                        BadgedBox(badge = {
                            if (pendingCount > 0) Badge { Text("$pendingCount") }
                        }) { Text("📥" ) }
                    },
                    label = { Text("Inbox") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("💬") }, label = { Text("Poocho") },
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                0 -> LedgerScreen(vm)
                1 -> CaptureScreen(vm)
                2 -> InboxScreen(vm)
                3 -> AskScreen(vm)
            }
        }
        MissionOverlay(vm)
    }
}

@Composable
private fun CaptureScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsState()
    val engineState by vm.engineState.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bolo, Laxmi likh legi", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Voice note bolo — kaun, kitna, kab. Entry apne aap ban jayegi, " +
                "awaaz ke saboot ke saath.",
            style = MaterialTheme.typography.bodyMedium,
        )
        // Recording never needs the engine — extraction happens after, when ready.
        RecordButton { wav ->
            vm.ingest(audio = wav, sourceTag = "mic")
        }
        when {
            busy -> {
                CircularProgressIndicator()
                Text("Samajh rahi hoon…", style = MaterialTheme.typography.bodyMedium)
            }
            engineState != EngineState.READY -> {
                Text(
                    "(Engine warm ho raha hai — bol sakte ho, samajhne mein thoda time lagega)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ModelSetupScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        vm.modelFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                copying = false
                vm.onModelImported()
            } catch (t: Throwable) {
                copying = false
                error = t.message
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
    ) {
        Text("Laxmi setup", style = MaterialTheme.typography.headlineMedium)
        Text("Ek baar model file import karo (Downloads mein hai) — uske baad sab offline.")
        if (copying) {
            CircularProgressIndicator()
            Text("Copy ho raha hai (~3.7 GB)…")
        } else {
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text("Model file import karo")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Laxmi jaag rahi hai…", Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ErrorScreen(vm: AppViewModel) {
    val error by vm.engineError.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Engine error", style = MaterialTheme.typography.headlineSmall)
        Text(error ?: "unknown", color = MaterialTheme.colorScheme.error)
        Button(onClick = { vm.initEngine() }, Modifier.padding(top = 12.dp)) { Text("Retry") }
    }
}
