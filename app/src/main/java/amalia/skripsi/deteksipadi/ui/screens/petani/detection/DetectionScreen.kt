package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.general.home.HomeViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun DetectionScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- STATE ---
    val showCapturedImage = remember { mutableStateOf(false) }
    val capturedBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val selectedGalleryBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val isGalleryImageShown = remember { mutableStateOf(false) }
    val hasCameraPermission = remember { mutableStateOf(false) }
    val showScanFab = remember { mutableStateOf(false) }
    val detectionResults = remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    val currentLocation = remember { mutableStateOf<Location?>(null) }
    val isUploading = remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val detector = remember {
        try { YoloDetector(context, "best.tflite") } catch (e: Exception) { Log.e("YOLO", "load error", e); null }
    }
    DisposableEffect(Unit) { onDispose { detector?.close() } }

    // Logic GPS
    LaunchedEffect(Unit) {
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            run {
                @SuppressLint("MissingPermission")
                try {
                    val loc = fusedLocationClient.lastLocation.await() ?: fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    currentLocation.value = loc
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // Logic Upload
    fun performUpload() {
        val bmp = capturedBitmap.value ?: selectedGalleryBitmap.value
        val result = detectionResults.value.firstOrNull()
        val loc = currentLocation.value
        val lat = loc?.latitude ?: 0.0
        val lon = loc?.longitude ?: 0.0

        if (bmp != null) {
            scope.launch(Dispatchers.IO) {
                isUploading.value = true
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                val uploadResult = submitReportToSupabase(
                    photoBytes = stream.toByteArray(),
                    label = result?.label ?: "Unknown",
                    conf = result?.score ?: 0f,
                    lat = lat,
                    lon = lon
                )

                withContext(Dispatchers.Main) {
                    isUploading.value = false
                    if (uploadResult.isSuccess) {
                        Toast.makeText(context, "Laporan Terkirim!", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    } else {
                        Toast.makeText(context, "Gagal: ${uploadResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val bmp = ImageUtils.loadBitmapFromUri(context, it)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        selectedGalleryBitmap.value = bmp
                        isGalleryImageShown.value = true
                        showScanFab.value = true
                    }
                    detector?.let { d ->
                        val results = d.detect(bmp)
                        withContext(Dispatchers.Main) { detectionResults.value = results }
                    }
                }
            }
        }
    }

    SetupCameraPermission(context, hasCameraPermission)

    // --- UI STRUCTURE ---
    // Karena MainScreen memberikan modifier kosong (tanpa padding) untuk route ini,
    // Scaffold di bawah ini akan mengambil alih seluruh layar (Full Screen).
    Scaffold(
        topBar = { ScannerTopBar(navController, cameraExecutor) },
        bottomBar = {
            ScannerBottomBar(onGalleryClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        },
        floatingActionButton = {
            ScannerFab(
                showCapturedImage = showCapturedImage,
                showScanFab = showScanFab,
                imageCapture = imageCapture,
                isGalleryImageShown = isGalleryImageShown,
                cameraExecutor = cameraExecutor,
                capturedBitmap = capturedBitmap,
                selectedGalleryBitmap = selectedGalleryBitmap,
                navController = navController,
                context = context,
                homeViewModel = homeViewModel,
                onUploadTriggered = { performUpload() },
                isUploading = isUploading.value
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        // ScannerContent hanya mengikuti padding dari Scaffold Scanner ini sendiri.
        ScannerContent(
            hasCameraPermission = hasCameraPermission,
            showCapturedImage = showCapturedImage,
            capturedBitmap = capturedBitmap,
            selectedGalleryBitmap = selectedGalleryBitmap,
            isGalleryImageShown = isGalleryImageShown,
            cameraProviderFuture = cameraProviderFuture,
            lifecycleOwner = lifecycleOwner,
            imageCapture = imageCapture,
            context = context,
            detector = detector,
            detectionResults = detectionResults.value,
            onRealtimeDetection = { results -> detectionResults.value = results },
            onClearImage = {
                showCapturedImage.value = false
                capturedBitmap.value = null
                selectedGalleryBitmap.value = null
                isGalleryImageShown.value = false
                detectionResults.value = emptyList()
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}