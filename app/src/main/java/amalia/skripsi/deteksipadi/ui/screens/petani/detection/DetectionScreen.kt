package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.ImageUtils.drawDetectionOnBitmap
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // State
    val scaffoldState = rememberBottomSheetScaffoldState()
    val showCapturedImageState = remember { mutableStateOf(false) }
    val capturedBitmapState = remember { mutableStateOf<Bitmap?>(null) }
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var reportLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Tools
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val detector = remember {
        try { YoloDetector(context, "best.tflite") } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) { onDispose { detector?.close() } }

    var arePermissionsGranted by remember { mutableStateOf(false) }
    EnsurePermissions(context) { granted -> arePermissionsGranted = granted }

    fun onReset() {
        capturedBitmapState.value = null
        showCapturedImageState.value = false
        detectionResults = emptyList()
        reportLocation = null
        showSuccessDialog = false
    }

    BackHandler(enabled = showCapturedImageState.value) { onReset() }

    fun processResult(bmp: Bitmap, location: Pair<Double, Double>?) {
        capturedBitmapState.value = bmp
        showCapturedImageState.value = true
        reportLocation = location
        detector?.let { d -> detectionResults = d.detect(bmp) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch(Dispatchers.IO) {
                    val bmp = ImageUtils.loadBitmapFromUri(context, uri)
                    val exifLoc = ImageUtils.getGeoLocation(context, uri)
                    if (bmp != null) withContext(Dispatchers.Main) { processResult(bmp, exifLoc) }
                }
            }
        }
    }

    fun onCameraCapture(bmp: Bitmap) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                processResult(bmp, if (loc != null) Pair(loc.latitude, loc.longitude) else null)
            }.addOnFailureListener { processResult(bmp, null) }
        } else {
            processResult(bmp, null)
        }
    }

    // --- Helper Function: Ambil Kecamatan secara Aman (Support Android 13+) ---
    suspend fun getDistrictName(ctx: Context, lat: Double, lon: Double): String = suspendCancellableCoroutine { cont ->
        val geocoder = Geocoder(ctx, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    val name = if (addresses.isNotEmpty()) addresses[0].locality ?: "Tidak Diketahui" else "Tidak Diketahui"
                    cont.resume(name)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val name = if (!addresses.isNullOrEmpty()) addresses[0].locality ?: "Tidak Diketahui" else "Tidak Diketahui"
                cont.resume(name)
            }
        } catch (e: Exception) {
            cont.resume("Tidak Diketahui")
        }
    }

    if (arePermissionsGranted) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (capturedBitmapState.value != null) 250.dp else 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
            sheetContainerColor = Color(0xFFFFF8E1),
            sheetContent = {
                ResultSheetContent(
                    results = detectionResults,
                    locationStr = reportLocation?.let { "${it.first}, ${it.second}" },
                    isLoading = isUploading,
                    onSend = {
                        if (reportLocation != null && capturedBitmapState.value != null) {
                            isUploading = true

                            scope.launch(Dispatchers.IO) {
                                // 1. Proses Gambar
                                val markedBitmap = drawDetectionOnBitmap(
                                    originalBitmap = capturedBitmapState.value!!,
                                    results = detectionResults
                                )
                                val stream = ByteArrayOutputStream()
                                markedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                                // 2. Ambil Kecamatan (Tunggu sampai selesai)
                                val kecamatan = getDistrictName(
                                    context,
                                    reportLocation!!.first,
                                    reportLocation!!.second
                                )

                                // 3. Kirim ke Supabase
                                val result = submitReportToSupabase(
                                    photoBytes = stream.toByteArray(),
                                    results = detectionResults,
                                    lat = reportLocation!!.first,
                                    lon = reportLocation!!.second,
                                    districtName = kecamatan // FIX: Nama parameter sesuai
                                )

                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                    result.onSuccess {
                                        showSuccessDialog = true
                                    }.onFailure { e ->
                                        Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Lokasi wajib aktif!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column {
                    ScannerTopBar(navController, cameraExecutor)
                    Box(modifier = Modifier.weight(1f)) {
                        ScannerContent(
                            hasCameraPermission = remember { mutableStateOf(true) },
                            showCapturedImage = showCapturedImageState,
                            capturedBitmap = capturedBitmapState,
                            selectedGalleryBitmap = remember { mutableStateOf(null) },
                            isGalleryImageShown = remember { mutableStateOf(false) },
                            cameraProviderFuture = cameraProviderFuture,
                            lifecycleOwner = lifecycleOwner,
                            imageCapture = imageCapture,
                            context = context,
                            detector = detector,
                            detectionResults = detectionResults,
                            onRealtimeDetection = { if (!showCapturedImageState.value) detectionResults = it },
                            onClearImage = { onReset() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!showCapturedImageState.value) {
                        ScannerBottomBar(onGalleryClick = {
                            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            galleryLauncher.launch(intent)
                        })
                    }
                }
                if (!showCapturedImageState.value) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
                        ScannerFab(
                            imageCapture = imageCapture,
                            cameraExecutor = cameraExecutor,
                            onPhotoCaptured = { bmp -> mainHandler.post { onCameraCapture(bmp) } }
                        )
                    }
                }
                if (showSuccessDialog) {
                    AlertDialog(
                        onDismissRequest = { onReset() },
                        icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp)) },
                        title = { Text("Laporan Terkirim!") },
                        text = { Text("Data deteksi dan lokasi telah berhasil disimpan ke sistem.", textAlign = TextAlign.Center) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onReset()
                                    navController.navigate("peta") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Lihat Peta") }
                        },
                        dismissButton = {
                            TextButton(onClick = { onReset() }) { Text("Scan Lagi", color = Color.Gray) }
                        }
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}