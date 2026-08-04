package com.example.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.UsahakiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    val repository = UsahakiRepository(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        checkCurrentAuthSession()
    }

    fun checkCurrentAuthSession() {
        val firebaseUser = repository.auth.currentUser
        if (firebaseUser != null) {
            _uiState.value = AuthUiState.Loading
            viewModelScope.launch {
                val profile = repository.fetchUserProfile(firebaseUser.uid)
                if (profile != null) {
                    if (profile.status != "active") {
                        repository.auth.signOut()
                        _uiState.value = AuthUiState.Error("Akun ini sudah dinonaktifkan oleh pemilik bisnis. Hubungi owner/admin kalau ini keliru.")
                        return@launch
                    }
                    _currentUser.value = profile
                    _uiState.value = AuthUiState.Success(profile)
                } else {
                    _uiState.value = AuthUiState.Error("Profil user tidak ditemukan di Firestore.")
                }
            }
        } else {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun login(inputEmail: String, inputPassword: String) {
        val trimmedEmail = inputEmail.trim()
        if (trimmedEmail.isBlank() || inputPassword.isBlank()) {
            _uiState.value = AuthUiState.Error("Email dan password harus diisi.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val authResult = repository.auth.signInWithEmailAndPassword(trimmedEmail, inputPassword).await()
                val uid = authResult.user?.uid
                if (uid != null) {
                    val profile = repository.fetchUserProfile(uid)
                    if (profile != null) {
                        if (profile.status != "active") {
                            repository.auth.signOut()
                            _uiState.value = AuthUiState.Error("Akun ini sudah dinonaktifkan oleh pemilik bisnis. Hubungi owner/admin kalau ini keliru.")
                            return@launch
                        }
                        _currentUser.value = profile
                        _uiState.value = AuthUiState.Success(profile)
                    } else {
                        // JANGAN buat sesi palsu di sini — akun Firebase Auth-nya
                        // valid (berhasil signIn), tapi dokumen profil di
                        // Firestore koleksi `users/{uid}` tidak ditemukan. Ini
                        // biasanya berarti akun belum ditautkan dengan benar
                        // dari web app (Employee > tautkan akses), atau salah
                        // project Firestore. Tampilkan error yang jelas &
                        // sign-out, jangan lanjutkan dengan businessId kosong
                        // (nanti semua query produk/order jadi kosong tanpa
                        // penjelasan, terlihat seperti bug padahal akunnya yang
                        // belum lengkap).
                        repository.auth.signOut()
                        _uiState.value = AuthUiState.Error(
                            "Akun berhasil login tapi profil staf tidak ditemukan di sistem. Hubungi pemilik bisnis untuk memastikan akun ini sudah ditautkan dengan benar."
                        )
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Gagal autentikasi pengguna.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Login gagal. Periksa email dan password.")
            }
        }
    }

    fun logout() {
        repository.auth.signOut()
        _currentUser.value = null
        _uiState.value = AuthUiState.Idle
    }
}
