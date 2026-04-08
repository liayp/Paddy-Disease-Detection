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
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _userProfile = mutableStateOf<UserProfile?>(null)
    val userProfile: State<UserProfile?> = _userProfile

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    fun loadUserProfile(repository: AuthRepository) {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.getUserProfile()
            _userProfile.value = profile
            _isLoading.value = false
        }
    }

    fun updateProfile(
        repository: AuthRepository,
        fullName: String,
        phoneNumber: String,
        alamat: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.updateProfile(fullName, phoneNumber, alamat)
            if (success) {
                loadUserProfile(repository)
                onResult(true, "Profil berhasil diperbarui")
            } else {
                onResult(false, "Gagal memperbarui profil")
            }
            _isLoading.value = false
        }
    }

    fun changePassword(
        repository: AuthRepository,
        newPass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.updatePassword(newPass)
            onResult(success, if (success) "Password berhasil diubah" else "Gagal mengubah password")
            _isLoading.value = false
        }
    }
}