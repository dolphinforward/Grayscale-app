package com.dolphinforward.grayscale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.dolphinforward.grayscale.ui.theme.AutoGrayscaleTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> /* UI refreshes onResume */ }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AutoGrayscaleTheme {
                Scaffold { padding ->
                    MainScreen(Modifier.padding(padding))
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var minutes by remember { mutableIntStateOf(prefs.delayMinutes) }
    var hasPermission by remember { mutableStateOf(PermissionManager.hasWriteSecureSettings(context)) }
    var shizukuRunning by remember { mutableStateOf(PermissionManager.shizukuRunning()) }
    var shizukuReady by remember { mutableStateOf(PermissionManager.shizukuReady()) }
    var ignoringBattery by remember {
        mutableStateOf(PermissionManager.isIgnoringBatteryOptimizations(context))
    }

    // Refresh permission/Shizuku state whenever we return to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = PermissionManager.hasWriteSecureSettings(context)
                shizukuRunning = PermissionManager.shizukuRunning()
                shizukuReady = PermissionManager.shizukuReady()
                ignoringBattery = PermissionManager.isIgnoringBatteryOptimizations(context)
                enabled = prefs.enabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringRes(R.string.app_name, context),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringRes(R.string.tagline, context),
            style = MaterialTheme.typography.bodyMedium
        )

        // --- Master on/off ---
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringRes(R.string.auto_grayscale, context),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (enabled) stringRes(R.string.status_on, context)
                        else stringRes(R.string.status_off, context),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { wantOn ->
                        if (wantOn) {
                            if (!PermissionManager.hasWriteSecureSettings(context)) {
                                PermissionManager.tryGrant(context)
                            }
                            hasPermission = PermissionManager.hasWriteSecureSettings(context)
                            if (hasPermission) {
                                prefs.enabled = true
                                enabled = true
                                GrayscaleService.start(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    stringRes(R.string.need_permission, context),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            prefs.enabled = false
                            enabled = false
                            GrayscaleService.stop(context)
                        }
                    }
                )
            }
        }

        // --- Minute selector ---
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringRes(R.string.reapply_after, context),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    minutesLabel(minutes, context),
                    style = MaterialTheme.typography.headlineSmall
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceAtLeast(Prefs.MIN_DELAY_MIN) },
                    onValueChangeFinished = { prefs.delayMinutes = minutes },
                    valueRange = Prefs.MIN_DELAY_MIN.toFloat()..60f,
                    steps = 58
                )
                Text(
                    stringRes(R.string.reapply_hint, context),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // --- Permission status ---
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringRes(R.string.permission_title, context),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasPermission) stringRes(R.string.perm_granted, context)
                    else stringRes(R.string.perm_missing, context),
                    color = if (hasPermission) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!hasPermission) {
                    Spacer(Modifier.height(12.dp))

                    // Shizuku path
                    when {
                        shizukuReady -> Button(
                            onClick = {
                                PermissionManager.tryGrant(context)
                                hasPermission = PermissionManager.hasWriteSecureSettings(context)
                                Toast.makeText(
                                    context,
                                    if (hasPermission) stringRes(R.string.granted, context)
                                    else stringRes(R.string.shizuku_failed, context),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringRes(R.string.grant_via_shizuku, context)) }

                        shizukuRunning -> Button(
                            onClick = { ShizukuGranter.requestPermission() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringRes(R.string.allow_shizuku, context)) }
                    }

                    // Root path
                    OutlinedButton(
                        onClick = {
                            val ok = PermissionManager.rootAvailable() &&
                                RootGranter.grantWriteSecureSettings(context.packageName)
                            hasPermission = PermissionManager.hasWriteSecureSettings(context)
                            Toast.makeText(
                                context,
                                if (ok && hasPermission) stringRes(R.string.granted, context)
                                else stringRes(R.string.root_failed, context),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringRes(R.string.grant_via_root, context)) }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringRes(R.string.adb_instructions, context),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = PermissionManager.adbCommand(context),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- Background reliability (battery optimization) ---
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringRes(R.string.battery_title, context),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ignoringBattery) stringRes(R.string.battery_ok, context)
                    else stringRes(R.string.battery_explain, context),
                    color = if (ignoringBattery) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!ignoringBattery) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringRes(R.string.battery_button, context)) }
                }
            }
        }
    }
}

@Suppress("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fall back to the general battery-optimization settings list.
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun stringRes(id: Int, context: android.content.Context): String =
    context.getString(id)

private fun minutesLabel(minutes: Int, context: android.content.Context): String =
    context.resources.getQuantityString(R.plurals.minutes, minutes, minutes)
