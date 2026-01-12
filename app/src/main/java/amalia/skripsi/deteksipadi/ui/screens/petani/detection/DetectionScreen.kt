package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.ImageUtils.drawDetectionOnBitmap
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // --- State Management ---
    val scaffoldState = rememberBottomSheetScaffoldState()

    // State Gambar
    val showCapturedImageState = remember { mutableStateOf(false) }
    val capturedBitmapState = remember { mutableStateOf<Bitmap?>(null) }

    // State Hasil Deteksi
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var reportLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // --- STATE BARU: Kontrol Dialog Sukses ---
    var showSuccessDialog by remember { mutableStateOf(false) }

    // --- Tools Setup ---
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val detector = remember {
        try { YoloDetector(context, "best.tflite") } catch (e: Exception) { null }
    }
    DisposableEffect(Unit) { onDispose { detector?.close() } }

    // Izin Lokasi & Kamera
    var arePermissionsGranted by remember { mutableStateOf(false) }
    EnsurePermissions(context) { granted -> arePermissionsGranted = granted }

    // FUNGSI RESET
    fun onReset() {
        capturedBitmapState.value = null
        showCapturedImageState.value = false
        detectionResults = emptyList()
        reportLocation = null
        showSuccessDialog = false // Pastikan dialog tertutup saat reset
    }

    // Handle Back Button
    BackHandler(enabled = showCapturedImageState.value) {
        // Jika dialog muncul, tutup dialog dulu
        if (showSuccessDialog) {
            onReset()
        } else {
            onReset()
        }
    }

    // FUNGSI PROSES
    fun processResult(bmp: Bitmap, location: Pair<Double, Double>?) {
        capturedBitmapState.value = bmp
        showCapturedImageState.value = true
        reportLocation = location

        detector?.let { d ->
            val results = d.detect(bmp)
            detectionResults = results
        }
    }

    // Launcher Galeri
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    val bmp = ImageUtils.loadBitmapFromUri(context, it)
                    val exifLoc = ImageUtils.getGeoLocation(context, it)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) { processResult(bmp, exifLoc) }
                    }
                }
            }
        }
    }

    // Capture Kamera
    fun onCameraCapture(bmp: Bitmap) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                processResult(bmp, if (loc != null) Pair(loc.latitude, loc.longitude) else null)
            }.addOnFailureListener { processResult(bmp, null) }
        } else {
            processResult(bmp, null)
        }
    }

    // --- UI UTAMA ---
    if (arePermissionsGranted) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (capturedBitmapState.value != null) 250.dp else 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
            sheetContainerColor = Color(0xFFFFF8E1),
            sheetContent = {
                // Panggil ResultSheetContent
                ResultSheetContent(
                    results = detectionResults,
                    locationStr = reportLocation?.let { "${it.first}, ${it.second}" },
                    isLoading = isUploading,
                    onSend = {
                        if (reportLocation != null && capturedBitmapState.value != null) {
                            isUploading = true
                            scope.launch(Dispatchers.IO) {

                                val markedBitmap = drawDetectionOnBitmap(
                                    originalBitmap = capturedBitmapState.value!!,
                                    results = detectionResults
                                )

                                val stream = ByteArrayOutputStream()
                                markedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                                val result = submitReportToSupabase(
                                    photoBytes = stream.toByteArray(),
                                    results = detectionResults,
                                    lat = reportLocation!!.first,
                                    lon = reportLocation!!.second
                                )

                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                    result.onSuccess { responseString ->
                                        // Gunakan JSONObject untuk mengambil pesan asli dari SQL
                                        try {
                                            val jsonObject = org.json.JSONObject(responseString)
                                            val isSuccess = jsonObject.optBoolean("success")
                                            val serverMessage = jsonObject.optString("message")

                                            if (isSuccess) {
                                                showSuccessDialog = true
                                            } else {
                                                Toast.makeText(context, "Gagal: $serverMessage", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            if (responseString.contains("success\":true")) {
                                                showSuccessDialog = true
                                            } else {
                                                Toast.makeText(context, "Laporan Ditolak Server.", Toast.LENGTH_LONG).show()
                                            }
                                        }
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
                            onRealtimeDetection = {
                                if (!showCapturedImageState.value) detectionResults = it
                            },
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

                // --- DIALOG SUKSES (Pop Up) ---
                if (showSuccessDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            // Jika user klik luar dialog, kita reset saja (balik ke kamera)
                            onReset()
                        },
                        icon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PadiGreen, modifier = Modifier.size(48.dp))
                        },
                        title = {
                            Text(text = "Laporan Berhasil Disimpan!")
                        },
                        text = {
                            Text(
                                text = "Terima kasih. Data deteksi hama telah masuk ke sistem.\n\nKlik tombol di bawah untuk melihat sebaran titik hama di Peta.",
                                textAlign = TextAlign.Center
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // 1. Tutup dialog & Reset State Scanner
                                    onReset()
                                    // 2. Navigasi ke Peta (Gunakan nama route yang sesuai di BottomNavItem)
                                    navController.navigate("peta") {
                                        // Opsional: Pop up sampai home agar back button di peta balik ke home, bukan scanner
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PadiGreen)
                            ) {
                                Text("Lihat di Peta")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    // Hanya reset, kembali scan lagi
                                    onReset()
                                }
                            ) {
                                Text("Scan Lagi", color = Color.Gray)
                            }
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