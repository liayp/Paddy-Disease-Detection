package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

    // State Hasil Deteksi (LIST LENGKAP)
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var reportLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isUploading by remember { mutableStateOf(false) }

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
    }

    // Handle Back Button
    BackHandler(enabled = showCapturedImageState.value) {
        onReset()
    }

    // FUNGSI PROSES (Menerima Bitmap & Lokasi)
    fun processResult(bmp: Bitmap, location: Pair<Double, Double>?) {
        // 1. Update UI untuk menampilkan gambar beku
        capturedBitmapState.value = bmp
        showCapturedImageState.value = true
        reportLocation = location

        // 2. Jalankan Deteksi
        detector?.let { d ->
            val results = d.detect(bmp)
            // Simpan SEMUA hasil (bukan cuma maxByOrNull)
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
            // Jika gagal dapat lokasi, tetap proses gambar (nanti user diingatkan di Sheet)
            processResult(bmp, null)
        }
    }

    // --- UI UTAMA ---
    if (arePermissionsGranted) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            // Logic PeekHeight: Munculkan sheet jika gambar sudah dicapture
            sheetPeekHeight = if (capturedBitmapState.value != null) 250.dp else 0.dp, // Tambah tinggi agar muat list
            containerColor = MaterialTheme.colorScheme.background,
            sheetContainerColor = Color(0xFFFFF8E1),
            sheetContent = {
                // Panggil ResultSheetContent dengan List Penuh
                ResultSheetContent(
                    results = detectionResults, // Kirim List
                    locationStr = reportLocation?.let { "${it.first}, ${it.second}" },
                    isLoading = isUploading,
                    onSend = {
                        if (reportLocation != null && capturedBitmapState.value != null) {
                            isUploading = true
                            scope.launch(Dispatchers.IO) {
                                val stream = ByteArrayOutputStream()
                                capturedBitmapState.value!!.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                                // PANGGIL FUNGSI UPLOAD YANG BARU (Kirim List)
                                val result = submitReportToSupabase(
                                    photoBytes = stream.toByteArray(),
                                    results = detectionResults, // Kirim List Deteksi
                                    lat = reportLocation!!.first,
                                    lon = reportLocation!!.second
                                )

                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                    result.onSuccess {
                                        Toast.makeText(context, "Laporan Terkirim & Diproses!", Toast.LENGTH_LONG).show()
                                        onReset()
                                    }.onFailure { e ->
                                        Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Lokasi wajib aktif untuk melapor!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column {
                    ScannerTopBar(navController, cameraExecutor)

                    Box(modifier = Modifier.weight(1f)) {
                        // ScannerContent Logic
                        ScannerContent(
                            hasCameraPermission = remember { mutableStateOf(true) },
                            showCapturedImage = showCapturedImageState,
                            capturedBitmap = capturedBitmapState,
                            // Parameter dummy karena tidak dipakai logika ini
                            selectedGalleryBitmap = remember { mutableStateOf(null) },
                            isGalleryImageShown = remember { mutableStateOf(false) },

                            cameraProviderFuture = cameraProviderFuture,
                            lifecycleOwner = lifecycleOwner,
                            imageCapture = imageCapture,
                            context = context,
                            detector = detector,
                            detectionResults = detectionResults, // Tampilkan box di layar
                            onRealtimeDetection = {
                                // Hanya update realtime jika tidak sedang hold gambar
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
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}