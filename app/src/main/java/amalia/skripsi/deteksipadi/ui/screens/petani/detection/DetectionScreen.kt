package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import amalia.skripsi.deteksipadi.util.ImageUtils
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

    // Buat MutableState agar kompatibel dengan ScannerContent
    val showCapturedImageState = remember { mutableStateOf(false) }
    val capturedBitmapState = remember { mutableStateOf<Bitmap?>(null) }

    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var finalLabel by remember { mutableStateOf("") }
    var finalScore by remember { mutableFloatStateOf(0f) }
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

    // FUNGSI RESET (Dipanggil saat tombol X ditekan)
    fun onReset() {
        // Kosongkan Gambar & Hasil
        capturedBitmapState.value = null
        showCapturedImageState.value = false
        detectionResults = emptyList()
        reportLocation = null

        // Sheet otomatis sembunyi karena peekHeight bergantung pada capturedBitmapState
    }

    // Handle Back Button HP: Reset dulu, baru exit app
    BackHandler(enabled = showCapturedImageState.value) {
        onReset()
    }

    // FUNGSI PROSES
    fun processResult(bmp: Bitmap, location: Pair<Double, Double>?) {
        // Update State UI
        capturedBitmapState.value = bmp
        showCapturedImageState.value = true
        reportLocation = location

        detector?.let { d ->
            val results = d.detect(bmp)
            detectionResults = results
            val best = results.maxByOrNull { it.score }
            if (best != null) {
                finalLabel = best.label
                finalScore = best.score
            } else {
                finalLabel = "Tidak Terdeteksi"
                finalScore = 0f
            }
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
                    // Panggil fungsi getGeoLocation
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
            // LOGIC KUNCI:
            // Jika ada foto (capturedBitmapState != null) -> Sheet muncul 130dp.
            // Jika tidak -> Sheet 0dp (Sembunyi).
            sheetPeekHeight = if (capturedBitmapState.value != null) 130.dp else 0.dp,
            sheetContainerColor = Color(0xFFFFF8E1), // Warna Krem
            sheetContent = {
                // Isi Bottom Sheet
                ResultSheetContent(
                    label = finalLabel,
                    confidence = finalScore,
                    locationStr = reportLocation?.let { "${it.first}, ${it.second}" },
                    isLoading = isUploading,
                    onSend = {
                        if (reportLocation != null && capturedBitmapState.value != null) {
                            isUploading = true
                            scope.launch(Dispatchers.IO) {
                                val stream = ByteArrayOutputStream()
                                capturedBitmapState.value!!.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                                val result = submitReportToSupabase(
                                    photoBytes = stream.toByteArray(),
                                    label = finalLabel,
                                    conf = finalScore,
                                    lat = reportLocation!!.first,
                                    lon = reportLocation!!.second
                                )

                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                    result.onSuccess {
                                        Toast.makeText(context, "Laporan Terkirim!", Toast.LENGTH_LONG).show()
                                        onReset() // Reset setelah sukses
                                    }.onFailure { e ->
                                        Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            // --- KONTEN BELAKANG (FOTO / KAMERA) ---
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            ) {
                Column {
                    // FIX 1: ScannerTopBar SELALU MUNCUL (Di luar kondisi if)
                    ScannerTopBar(navController, cameraExecutor)

                    Box(modifier = Modifier.weight(1f)) {
                        // ScannerContent akan menampilkan Kamera Live ATAU Foto Beku + Tombol X
                        // sesuai dengan state yang kita kirim
                        ScannerContent(
                            hasCameraPermission = remember { mutableStateOf(true) },
                            showCapturedImage = showCapturedImageState, // Sync State
                            capturedBitmap = capturedBitmapState,       // Sync State
                            selectedGalleryBitmap = remember { mutableStateOf(null) }, // Tidak perlu, pakai capturedBitmapState saja
                            isGalleryImageShown = remember { mutableStateOf(false) }, // Tidak perlu
                            cameraProviderFuture = cameraProviderFuture,
                            lifecycleOwner = lifecycleOwner,
                            imageCapture = imageCapture,
                            context = context,
                            detector = detector,
                            detectionResults = detectionResults,
                            onRealtimeDetection = { detectionResults = it },
                            onClearImage = {
                                // FIX 2: Saat tombol X ditekan di ScannerContent, panggil reset utama
                                onReset()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom Bar Galeri (Hanya muncul saat Mode Scan)
                    if (!showCapturedImageState.value) {
                        ScannerBottomBar(onGalleryClick = {
                            val intent = Intent(
                                Intent.ACTION_PICK,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            )
                            galleryLauncher.launch(intent)
                        })
                    }
                }

                // FAB (Hanya muncul saat Mode Scan)
                if (!showCapturedImageState.value) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                    ) {
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
        // Loading state saat minta izin
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}