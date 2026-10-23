package com.example.shelflife_ai.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shelflife_ai.model.UserModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String? = null) : AuthState()
    data class Error(val message: String) : AuthState()
    data class OtpSent(val verificationId: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun registerWithEmail(
        email: String,
        phone: String,
        displayName: String,
        password: String,
        confirmPassword: String,
        termsAccepted: Boolean
    ) {
        if (!termsAccepted) {
            _authState.value = AuthState.Error("Debes aceptar los términos y condiciones.")
            return
        }
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Por favor completa el correo y la contraseña.")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Las contraseñas no coinciden.")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("La contraseña debe tener al menos 6 caracteres.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    user.sendEmailVerification()
                    val defaultAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=${user.uid}"

                    val newUser = UserModel(
                        uid = user.uid,
                        email = email,
                        phone = phone,
                        displayName = displayName.ifBlank { email.substringBefore("@") },
                        photoUrl = defaultAvatar,
                        createdAt = System.currentTimeMillis()
                    )

                    firestore.collection("users").document(user.uid).set(newUser, SetOptions.merge()).await()
                    _authState.value = AuthState.Success("Registro exitoso. Se envió un correo de verificación.")
                } else {
                    _authState.value = AuthState.Error("Error al registrar el usuario.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Error en el registro.")
            }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Por favor ingresa tu correo y contraseña.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success("Inicio de sesión exitoso.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Error de autenticación.")
            }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user

                if (user != null) {
                    saveUserIfNotExists(
                        uid = user.uid,
                        email = user.email ?: "",
                        phone = user.phoneNumber ?: "",
                        displayName = user.displayName ?: "Usuario Google",
                        photoUrl = user.photoUrl?.toString() ?: "https://api.dicebear.com/7.x/bottts/svg?seed=${user.uid}"
                    )
                    _authState.value = AuthState.Success("Inicio de sesión con Google exitoso.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Error al autenticar con Google.")
            }
        }
    }

    fun sendSmsOtp(phoneNumber: String, activity: Activity) {
        if (phoneNumber.isBlank()) {
            _authState.value = AuthState.Error("Por favor ingresa un número de teléfono válido (+51...).")
            return
        }

        _authState.value = AuthState.Loading

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _authState.value = AuthState.Error(e.localizedMessage ?: "Error al enviar código SMS.")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    resendingToken = token
                    _authState.value = AuthState.OtpSent(verificationId)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifySmsOtp(verificationId: String, otpCode: String) {
        if (otpCode.isBlank()) {
            _authState.value = AuthState.Error("Ingresa el código OTP de 6 dígitos.")
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user

                if (user != null) {
                    saveUserIfNotExists(
                        uid = user.uid,
                        email = user.email ?: "",
                        phone = user.phoneNumber ?: "",
                        displayName = "Usuario SMS",
                        photoUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=${user.uid}"
                    )
                    _authState.value = AuthState.Success("Autenticación por SMS exitosa.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Código OTP incorrecto o expirado.")
            }
        }
    }

    private suspend fun saveUserIfNotExists(
        uid: String,
        email: String,
        phone: String,
        displayName: String,
        photoUrl: String
    ) {
        val userDoc = firestore.collection("users").document(uid).get().await()
        if (!userDoc.exists()) {
            val newUser = UserModel(
                uid = uid,
                email = email,
                phone = phone,
                displayName = displayName,
                photoUrl = photoUrl,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).set(newUser, SetOptions.merge()).await()
        }
    }
}
