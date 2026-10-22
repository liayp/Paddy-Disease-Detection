package amalia.skripsi.deteksipadi.util

import amalia.skripsi.deteksipadi.workers.UploadWorker
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.work.*

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile private var isRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                Log.d("NetworkMonitor", "Akses internet tersedia; antrean sinkronisasi dijadwalkan")
                triggerUploadNow()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) triggerUploadNow()
        }
    }

    @Synchronized
    fun startMonitoring() {
        if (isRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, networkCallback); isRegistered = true }
            .onFailure { Log.w("NetworkMonitor", "Pemantauan koneksi tidak dapat dimulai", it) }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        isRegistered = false
    }

    private fun triggerUploadNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "UploadReports",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )
    }
}