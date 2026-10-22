package amalia.skripsi.deteksipadi.ui.screens.general.profile

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.util.NotificationHelper
import amalia.skripsi.deteksipadi.util.VisitReminderScheduler
import amalia.skripsi.deteksipadi.util.LocationAccessPreference
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }
    val prefs = remember { context.getSharedPreferences("visit_reminder_settings", Context.MODE_PRIVATE) }

    var notificationsEnabled by remember { mutableStateOf(NotificationHelper.areNotificationsEnabled(context)) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showSystemNotificationSettingsPrompt by remember { mutableStateOf(false) }
    var remindDayBefore by remember { mutableStateOf(prefs.getBoolean("day_before", true)) }
    var remindHourBefore by remember { mutableStateOf(prefs.getBoolean("hour_before", true)) }
    var reportNotifications by remember { mutableStateOf(NotificationHelper.isCategoryEnabled(context, "reports")) }
    var scheduleNotifications by remember { mutableStateOf(NotificationHelper.isCategoryEnabled(context, "schedule")) }
    var geofenceNotifications by remember { mutableStateOf(NotificationHelper.isCategoryEnabled(context, "geofence")) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var fineLocationGranted by remember { mutableStateOf(LocationAccessPreference.hasFinePermission(context)) }
    var locationUsageEnabled by remember { mutableStateOf(LocationAccessPreference.isEnabled(context) && hasLocationPermission(context)) }
    var deviceLocationEnabled by remember { mutableStateOf(isDeviceLocationEnabled(context)) }
    var showLocationSettingsPrompt by remember { mutableStateOf(false) }
    var systemNotificationsEnabled by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var locationDenied by remember { mutableStateOf(false) }

    fun refreshSystemState() {
        locationGranted = hasLocationPermission(context)
        fineLocationGranted = LocationAccessPreference.hasFinePermission(context)
        locationUsageEnabled = LocationAccessPreference.isEnabled(context) && locationGranted
        deviceLocationEnabled = isDeviceLocationEnabled(context)
        systemNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        notificationsEnabled = NotificationHelper.areNotificationsEnabled(context) && systemNotificationsEnabled
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (NotificationHelper.hasExplicitNotificationPreference(context)) {
            // Pilihan lokal yang pernah dibuat pengguna tetap berlaku jika sinkronisasi jaringan gagal.
            notificationsEnabled = NotificationHelper.areNotificationsEnabled(context) && systemNotificationsEnabled
        } else {
            val accountValue = runCatching { authRepo.getPushNotificationsEnabled() }.getOrNull()
            if (accountValue != null) {
                notificationsEnabled = accountValue && systemNotificationsEnabled
                NotificationHelper.setNotificationsEnabled(context, notificationsEnabled)
            } else {
                // Profil tidak dapat dibaca: gunakan default lokal tanpa menandainya sebagai pilihan eksplisit.
                notificationsEnabled = NotificationHelper.areNotificationsEnabled(context) && systemNotificationsEnabled
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission(context)
        fineLocationGranted = LocationAccessPreference.hasFinePermission(context)
        locationDenied = !locationGranted
        locationUsageEnabled = locationGranted
        LocationAccessPreference.setEnabled(context, locationGranted)
        statusMessage = if (locationGranted && fineLocationGranted) {
            "Izin lokasi presisi diberikan. Fitur lokasi dapat digunakan sesuai kebutuhan."
        } else if (locationGranted) {
            "Izin lokasi perkiraan diberikan. Peringatan radius, check-in, dan pemeriksaan radius 20 meter memerlukan izin lokasi presisi."
        } else {
            "Izin lokasi belum diberikan. Fitur lain tetap dapat digunakan."
        }
        if (locationGranted) {
            val serviceIntent = Intent(context, amalia.skripsi.deteksipadi.services.HazardDetectionService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationsEnabled = true
            NotificationHelper.setNotificationsEnabled(context, true)
            isSaving = true
            scope.launch {
                val result = authRepo.setPushNotificationsEnabled(true)
                isSaving = false
                if (result.isFailure) statusMessage = "Notifikasi aktif di perangkat, tetapi pengaturan akun belum tersinkron. Coba lagi saat internet tersedia."
                else statusMessage = "Notifikasi diaktifkan."
            }
        } else {
            notificationsEnabled = false
            NotificationHelper.setNotificationsEnabled(context, false)
            statusMessage = "Izin notifikasi belum diberikan oleh Android. Kamu bisa mengaktifkannya dari pengaturan notifikasi aplikasi."
            showSystemNotificationSettingsPrompt = true
            scope.launch { authRepo.setPushNotificationsEnabled(false) }
        }
    }

    fun changeNotifications(value: Boolean) {
        if (value) {
            if (!systemNotificationsEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showSystemNotificationSettingsPrompt = true
                }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        notificationsEnabled = value
        NotificationHelper.setNotificationsEnabled(context, value)
        isSaving = true
        scope.launch {
            val result = authRepo.setPushNotificationsEnabled(value)
            isSaving = false
            statusMessage = if (result.isFailure) {
                "Pilihan disimpan di perangkat, tetapi belum tersinkron ke akun. Coba lagi saat internet tersedia."
            } else if (value) "Notifikasi diaktifkan." else "Notifikasi aplikasi dinonaktifkan."
            if (!value && systemNotificationsEnabled) showSystemNotificationSettingsPrompt = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionTitle("NOTIFIKASI")
            SettingsSwitchRow(
                title = "Notifikasi aplikasi",
                subtitle = if (!systemNotificationsEnabled) "Izin notifikasi diblokir oleh Android" else if (notificationsEnabled) "Pemberitahuan dari Deteksi Padi diaktifkan" else "Pemberitahuan dari Deteksi Padi dinonaktifkan",
                checked = notificationsEnabled,
                enabled = !isSaving,
                onCheckedChange = ::changeNotifications
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SettingsSwitchRow("Aktivitas laporan", if (notificationsEnabled) "Pembaruan terkait laporan dan tindak lanjut" else "Tidak aktif karena notifikasi aplikasi dimatikan", reportNotifications, enabled = notificationsEnabled) {
                reportNotifications = it
                NotificationHelper.setCategoryEnabled(context, "reports", it)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SettingsSwitchRow("Jadwal kunjungan", if (notificationsEnabled) "Pemberitahuan dan pengingat jadwal POPT" else "Tidak aktif karena notifikasi aplikasi dimatikan", scheduleNotifications, enabled = notificationsEnabled) {
                scheduleNotifications = it
                NotificationHelper.setCategoryEnabled(context, "schedule", it)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SettingsSwitchRow("Peringatan lokasi", if (notificationsEnabled) "Peringatan saat fitur radius/lokasi memerlukannya" else "Tidak aktif karena notifikasi aplikasi dimatikan", geofenceNotifications, enabled = notificationsEnabled) {
                geofenceNotifications = it
                NotificationHelper.setCategoryEnabled(context, "geofence", it)
            }

            SettingsSectionTitle("PENGINGAT KUNJUNGAN")
            SettingsSwitchRow("Sehari sebelum jadwal", if (notificationsEnabled) "Pengingat sekitar 1 hari sebelum kunjungan" else "Tidak aktif karena notifikasi aplikasi dimatikan", remindDayBefore, enabled = notificationsEnabled) {
                remindDayBefore = it
                prefs.edit().putBoolean("day_before", it).apply()
                VisitReminderScheduler.rescheduleStored(context)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SettingsSwitchRow("Satu jam sebelum jadwal", if (notificationsEnabled) "Pengingat sekitar 1 jam sebelum kunjungan" else "Tidak aktif karena notifikasi aplikasi dimatikan", remindHourBefore, enabled = notificationsEnabled) {
                remindHourBefore = it
                prefs.edit().putBoolean("hour_before", it).apply()
                VisitReminderScheduler.rescheduleStored(context)
            }

            SettingsSectionTitle("IZIN PERANGKAT")
            SettingsSwitchRow(
                title = "Gunakan lokasi",
                subtitle = when {
                    !locationGranted -> "Aktifkan untuk meminta izin lokasi Android"
                    !locationUsageEnabled -> "Lokasi tidak digunakan oleh fitur aplikasi"
                    !fineLocationGranted -> "Izin perkiraan tersedia; fitur radius dan check-in memerlukan lokasi presisi"
                    !deviceLocationEnabled -> "Izin tersedia, tetapi lokasi perangkat sedang mati"
                    else -> "Lokasi digunakan saat fitur membutuhkannya"
                },
                checked = locationUsageEnabled,
                enabled = true,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        locationUsageEnabled = false
                        LocationAccessPreference.setEnabled(context, false)
                        val serviceIntent = Intent(context, amalia.skripsi.deteksipadi.services.HazardDetectionService::class.java).apply {
                            action = amalia.skripsi.deteksipadi.services.HazardDetectionService.ACTION_DISABLE_LOCATION
                        }
                        if (locationGranted && amalia.skripsi.deteksipadi.services.HazardDetectionService.isRunning) {
                            runCatching { context.startService(serviceIntent) }
                        }
                        statusMessage = "Penggunaan lokasi di aplikasi dimatikan. Izin Android tetap tersimpan dan tidak digunakan oleh fitur aplikasi."
                    } else if (hasLocationPermission(context)) {
                        LocationAccessPreference.setEnabled(context, true)
                        locationUsageEnabled = true
                        deviceLocationEnabled = isDeviceLocationEnabled(context)
                        statusMessage = when {
                            !fineLocationGranted -> "Penggunaan lokasi diaktifkan dengan izin perkiraan. Aktifkan lokasi presisi untuk peringatan radius dan check-in."
                            deviceLocationEnabled -> "Penggunaan lokasi diaktifkan."
                            else -> "Izin sudah tersedia, tetapi lokasi perangkat masih mati. Aktifkan Lokasi dari panel cepat perangkat."
                        }
                        val serviceIntent = Intent(context, amalia.skripsi.deteksipadi.services.HazardDetectionService::class.java).apply {
                            action = amalia.skripsi.deteksipadi.services.HazardDetectionService.ACTION_ENABLE_LOCATION
                        }
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
                        }
                    } else {
                        val activity = context as? Activity
                        val permanentlyDenied = locationDenied && activity != null &&
                                !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                                !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (permanentlyDenied) {
                            showLocationSettingsPrompt = true
                        } else {
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            SettingsActionRow(
                icon = { Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Izin notifikasi Android",
                subtitle = if (systemNotificationsEnabled) "Diizinkan oleh perangkat" else "Diblokir oleh perangkat",
                actionLabel = "Kelola",
                onClick = { openNotificationSettings(context) }
            )

            statusMessage?.let {
                Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLocationSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsPrompt = false },
            title = { Text("Izin lokasi diperlukan") },
            text = { Text("Android tidak lagi menampilkan dialog izin untuk pilihan ini. Kamu dapat mengizinkan lokasi melalui setelan izin aplikasi, atau membatalkan dan tetap menggunakan fitur lain.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationSettingsPrompt = false
                    openAppSettings(context)
                }) { Text("Buka setelan") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettingsPrompt = false }) { Text("Nanti") }
            }
        )
    }

    if (showSystemNotificationSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSystemNotificationSettingsPrompt = false },
            title = { Text("Pengaturan notifikasi Android") },
            text = {
                Text(
                    if (!systemNotificationsEnabled) "Android sedang memblokir notifikasi untuk aplikasi ini. Buka setelan notifikasi aplikasi lalu izinkan notifikasi agar pemberitahuan bisa muncul."
                    else if (notificationsEnabled) "Android memblokir notifikasi untuk aplikasi ini. Buka setelan notifikasi aplikasi lalu izinkan notifikasi agar pemberitahuan bisa muncul."
                    else "Notifikasi dari aplikasi sudah dimatikan. Jika ingin mencabut izin notifikasi Android sepenuhnya, buka setelan notifikasi aplikasi dan matikan izinnya di sana."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSystemNotificationSettingsPrompt = false
                    openNotificationSettings(context)
                }) { Text("Buka setelan") }
            },
            dismissButton = {
                TextButton(onClick = { showSystemNotificationSettingsPrompt = false }) { Text("Nanti") }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = Modifier.graphicsLayer { scaleX = 0.88f; scaleY = 0.88f })
    }
}

@Composable
private fun SettingsActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isDeviceLocationEnabled(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    })
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
    }
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}
