package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.local.PendingReport
import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import amalia.skripsi.deteksipadi.workers.UploadWorker
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.animateDpAsState
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
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // --- State UI ---
    val scaffoldState = rememberBottomSheetScaffoldState()
    val showCapturedImageState = remember { mutableStateOf(false) }
    val capturedBitmapState = remember { mutableStateOf<Bitmap?>(null) }

    // --- State Data ---
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var reportLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // --- Tools ---
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val detector = remember {
        try { YoloDetector(context, "best.tflite") } catch (_: Exception) { null }
    }

    DisposableEffect(Unit) { onDispose { detector?.close() } }

    var arePermissionsGranted by remember { mutableStateOf(false) }
    EnsurePermissions(context) { granted -> arePermissionsGranted = granted }

    val sheetPeekHeight by animateDpAsState(
        targetValue = if (showCapturedImageState.value) 110.dp else 0.dp,
        label = "sheetPeekAnimation"
    )

    fun onReset() {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
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
        scope.launch { scaffoldState.bottomSheetState.expand() }
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

    // --- FUNGSI CEK INTERNET DARI OLD CODE ---
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    // --- RENDER UI ---
    if (arePermissionsGranted) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetSwipeEnabled = showCapturedImageState.value,
            containerColor = MaterialTheme.colorScheme.background,
            sheetContainerColor = Color(0xFFFFF8E1),
            sheetContent = {
                ResultSheetContent(
                    results = detectionResults,
                    locationStr = reportLocation?.let { "${it.first}, ${it.second}" },
                    isLoading = isUploading,
                    onSend = {
                        val rawBmp = capturedBitmapState.value
                        val loc = reportLocation

                        if (loc != null && rawBmp != null) {
                            val currentUser = supabase.auth.currentUserOrNull()
                            val userId = currentUser?.id ?: ""
                            val hasInternet = isOnline(context)

                            scope.launch(Dispatchers.IO) {
                                val finalBitmap = ImageUtils.drawDetectionOnBitmap(rawBmp, detectionResults)

                                // Konversi Gambar Berkotak ke Bytes
                                val stream = ByteArrayOutputStream()
                                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                                val photoBytes = stream.toByteArray()
                                stream.close()

                                val addressInfo = ImageUtils.getAddressName(context, loc.first, loc.second)

                                // --- JALUR ONLINE ---
                                if (hasInternet) {
                                    withContext(Dispatchers.Main) { isUploading = true }

                                    val result = submitReportToSupabase(
                                        photoBytes = photoBytes,
                                        results = detectionResults,
                                        lat = loc.first,
                                        lon = loc.second,
                                        kecamatan = addressInfo.first,
                                        kelurahan = addressInfo.second,
                                        addressDetail = addressInfo.third,
                                        userId = userId
                                    )

                                    withContext(Dispatchers.Main) {
                                        isUploading = false
                                        if (result.isSuccess) {
                                            showSuccessDialog = true
                                        } else {
                                            Toast.makeText(context, "Gagal upload, simpan offline...", Toast.LENGTH_SHORT).show()
                                            // Kalau gagal, simpan lokal gambar yg sudah berkotak
                                            saveToLocalAndQueue(context, finalBitmap, detectionResults, loc, addressInfo, userId)
                                            onReset()
                                        }
                                    }
                                }
                                // --- JALUR OFFLINE ---
                                else {
                                    // Simpan lokal gambar yg sudah berkotak
                                    saveToLocalAndQueue(context, finalBitmap, detectionResults, loc, addressInfo, userId)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Disimpan offline. Menunggu sinyal...", Toast.LENGTH_LONG).show()
                                        onReset()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        ) { _ ->
            // PERBAIKAN UI (Abaikan innerPadding agar kamera fix di tempat)
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScannerTopBar(navController, cameraExecutor)

                    // Area Preview Kamera
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

                    // Spacer di bawah kamera
                    Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
                        if (!showCapturedImageState.value) {
                            ScannerBottomBar(onGalleryClick = {
                                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                galleryLauncher.launch(intent)
                            })
                        }
                    }
                }

                // Tombol Capture (Floating)
                if (!showCapturedImageState.value) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 85.dp)) {
                        ScannerFab(
                            imageCapture = imageCapture,
                            cameraExecutor = cameraExecutor,
                            onPhotoCaptured = { bmp -> mainHandler.post { onCameraCapture(bmp) } }
                        )
                    }
                }

                // Dialog Sukses
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

suspend fun saveToLocalAndQueue(
    context: Context,
    bmpWithBox: Bitmap, // Bitmap yang sudah ada kotaknya
    results: List<DetectionResult>,
    loc: Pair<Double, Double>,
    addr: Triple<String, String, String>,
    userId: String
) {
    // Simpan File Gambar (Yang sudah ada kotaknya)
    val fileName = "upload_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, fileName)
    val fileStream = FileOutputStream(file)
    bmpWithBox.compress(Bitmap.CompressFormat.JPEG, 70, fileStream)
    fileStream.close()

    // Ambil data utama untuk DB Lokal
    val best = results.maxByOrNull { it.score }
    val label = best?.label ?: "Unknown"
    val score = best?.score ?: 0f

    // Masukkan ke Room
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "padi-database").build()
    val pendingReport = PendingReport(
        imagePath = file.absolutePath,
        label = label,
        confidence = score,
        lat = loc.first,
        lon = loc.second,
        kecamatan = addr.first,
        kelurahan = addr.second,
        addressDetail = addr.third,
        userId = userId
    )
    db.pendingReportDao().insert(pendingReport)

    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val req = OneTimeWorkRequest.Builder(UploadWorker::class.java).setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniqueWork("UploadReports", ExistingWorkPolicy.KEEP, req)
}
