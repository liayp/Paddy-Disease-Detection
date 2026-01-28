package amalia.skripsi.deteksipadi.ui.screens.general.profile

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.UserProfile
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    // Kita inject AuthRepository manual atau via Hilt Module (asumsi AuthRepository bisa di provide)
    // Jika belum setup Module AuthRepository, kita init manual di screen saja untuk simpelnya
    // Tapi best practice pakai DI. Untuk sekarang kita pakai cara Hybrid agar tidak error.
) : ViewModel() {

    private val _userProfile = mutableStateOf<UserProfile?>(null)
    val userProfile: State<UserProfile?> = _userProfile

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // Panggil fungsi ini dari Screen
    fun loadUserProfile(repository: AuthRepository) {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.getUserProfile()
            _userProfile.value = profile
            _isLoading.value = false
        }
    }
}