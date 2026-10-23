package com.example.shelflife_ai.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shelflife_ai.model.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Success(val message: String) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _userState = MutableStateFlow<UserModel?>(null)
    val userState: StateFlow<UserModel?> = _userState.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(currentUser.uid).get().await()
                val userModel = doc.toObject(UserModel::class.java) ?: UserModel(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    phone = currentUser.phoneNumber ?: "",
                    displayName = currentUser.displayName ?: "",
                    photoUrl = currentUser.photoUrl?.toString() ?: ""
                )
                _userState.value = userModel
            } catch (e: Exception) {
                _userState.value = UserModel(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    phone = currentUser.phoneNumber ?: "",
                    displayName = currentUser.displayName ?: "",
                    photoUrl = currentUser.photoUrl?.toString() ?: ""
                )
            }
        }
    }

    fun resetUiState() {
        _uiState.value = ProfileUiState.Idle
    }

    fun updateProfile(
        context: Context,
        newDisplayName: String,
        newPhone: String,
        selectedImageUri: Uri?,
        newPassword: String?,
        confirmPassword: String?
    ) {
        val currentUser = auth.currentUser ?: run {
            _uiState.value = ProfileUiState.Error("No hay usuario autenticado.")
            return
        }

        val userEmail = currentUser.email ?: _userState.value?.email ?: ""
        var passwordEmailSent = false

        if (!newPassword.isNullOrBlank()) {
            if (newPassword != confirmPassword) {
                _uiState.value = ProfileUiState.Error("Las nuevas contraseñas no coinciden.")
                return
            }
            if (newPassword.length < 6) {
                _uiState.value = ProfileUiState.Error("La contraseña debe tener al menos 6 caracteres.")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                var photoUrl = _userState.value?.photoUrl ?: ""

                // Convertir foto a Base64 optimizada
                if (selectedImageUri != null) {
                    val base64Image = uriToBase64(context, selectedImageUri)
                    if (base64Image != null) {
                        photoUrl = base64Image
                    }
                }

                // Actualizar en FirebaseAuth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newDisplayName)
                    .run {
                        if (photoUrl.isNotEmpty() && !photoUrl.startsWith("data:")) {
                            setPhotoUri(Uri.parse(photoUrl))
                        } else this
                    }
                    .build()
                currentUser.updateProfile(profileUpdates).await()

                // Si cambio contraseña, actualizar e intentar enviar correo
                if (!newPassword.isNullOrBlank()) {
                    try {
                        currentUser.updatePassword(newPassword).await()
                    } catch (e: Exception) {
                        // Continuar si requiere reautenticación
                    }
                    if (userEmail.isNotEmpty()) {
                        try {
                            auth.sendPasswordResetEmail(userEmail).await()
                            passwordEmailSent = true
                        } catch (e: Exception) {
                            // Continuar silenciosamente
                        }
                    }
                }

                // Actualizar Firestore
                val updatedData = mapOf(
                    "uid" to currentUser.uid,
                    "email" to userEmail,
                    "displayName" to newDisplayName,
                    "phone" to newPhone,
                    "photoUrl" to photoUrl
                )
                firestore.collection("users").document(currentUser.uid)
                    .set(updatedData, SetOptions.merge()).await()

                // Actualizar estado local
                _userState.value = _userState.value?.copy(
                    displayName = newDisplayName,
                    phone = newPhone,
                    photoUrl = photoUrl
                ) ?: UserModel(
                    uid = currentUser.uid,
                    email = userEmail,
                    phone = newPhone,
                    displayName = newDisplayName,
                    photoUrl = photoUrl
                )

                val successMsg = if (passwordEmailSent) {
                    "Perfil actualizado. Se envió un correo de cambio de contraseña a $userEmail."
                } else {
                    "Perfil actualizado correctamente."
                }

                _uiState.value = ProfileUiState.Success(successMsg)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al actualizar perfil.")
            }
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close() ?: return null

            val maxDimension = 300
            val ratio = Math.min(
                maxDimension.toFloat() / bitmap.width,
                maxDimension.toFloat() / bitmap.height
            )
            val newWidth = Math.round(bitmap.width * ratio)
            val newHeight = Math.round(bitmap.height * ratio)

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val byteArray = outputStream.toByteArray()

            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
