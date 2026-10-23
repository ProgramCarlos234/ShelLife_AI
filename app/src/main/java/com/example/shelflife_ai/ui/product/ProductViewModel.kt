package com.example.shelflife_ai.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shelflife_ai.model.ProductModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.min

sealed class ProductUiState {
    object Idle : ProductUiState()
    object Loading : ProductUiState()
    data class Success(val message: String) : ProductUiState()
    data class Error(val message: String) : ProductUiState()
}

class ProductViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _products = MutableStateFlow<List<ProductModel>>(emptyList())
    val products: StateFlow<List<ProductModel>> = _products.asStateFlow()

    private val _uiState = MutableStateFlow<ProductUiState>(ProductUiState.Idle)
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var snapshotListener: ListenerRegistration? = null

    init {
        loadProducts()
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                snapshotListener?.remove()
                _products.value = emptyList()
            } else {
                loadProducts()
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun resetUiState() {
        _uiState.value = ProductUiState.Idle
    }

    fun loadProducts() {
        val currentUser = auth.currentUser
        snapshotListener?.remove()

        if (currentUser == null) {
            _products.value = emptyList()
            return
        }

        snapshotListener = firestore.collection("products")
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ProductModel::class.java)?.copy(id = doc.id)
                    }
                    _products.value = list.sortedBy { it.expirationDate }
                }
            }
    }

    fun addProduct(
        name: String,
        category: String,
        quantity: Double,
        unit: String,
        storageType: String,
        expirationDate: Long,
        imageUrl: String
    ) {
        val currentUser = auth.currentUser ?: run {
            _uiState.value = ProductUiState.Error("Debes iniciar sesión para agregar productos.")
            return
        }

        if (name.isBlank()) {
            _uiState.value = ProductUiState.Error("El nombre del producto es obligatorio.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProductUiState.Loading
            try {
                val docRef = firestore.collection("products").document()
                val newProduct = ProductModel(
                    id = docRef.id,
                    userId = currentUser.uid,
                    name = name.trim(),
                    category = category,
                    quantity = quantity,
                    unit = unit,
                    expirationDate = expirationDate,
                    originalExpirationDate = expirationDate,
                    storageType = storageType,
                    imageUrl = imageUrl,
                    isOpened = false,
                    createdAt = System.currentTimeMillis()
                )

                docRef.set(newProduct).await()
                _uiState.value = ProductUiState.Success("Producto registrado correctamente.")
            } catch (e: Exception) {
                _uiState.value = ProductUiState.Error(e.localizedMessage ?: "Error al registrar producto.")
            }
        }
    }

    fun markProductAsOpened(product: ProductModel, openedDays: Int) {
        if (product.id.isBlank()) return

        viewModelScope.launch {
            _uiState.value = ProductUiState.Loading
            try {
                val now = System.currentTimeMillis()
                val openedExpiration = now + (openedDays.toLong() * 24 * 60 * 60 * 1000L)
                val originalExp = if (product.originalExpirationDate > 0) product.originalExpirationDate else product.expirationDate

                // Regla: Aplicar siempre la fecha más cercana
                val finalExpirationDate = min(originalExp, openedExpiration)

                val updates = mapOf(
                    "isOpened" to true,
                    "openedDate" to now,
                    "openedDaysLimit" to openedDays,
                    "originalExpirationDate" to originalExp,
                    "expirationDate" to finalExpirationDate
                )

                firestore.collection("products").document(product.id).update(updates).await()
                _uiState.value = ProductUiState.Success("Producto marcado como abierto. Vencimiento recalculado.")
            } catch (e: Exception) {
                _uiState.value = ProductUiState.Error(e.localizedMessage ?: "Error al recalcular vencimiento.")
            }
        }
    }

    fun deleteProduct(productId: String) {
        if (productId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = ProductUiState.Loading
            try {
                firestore.collection("products").document(productId).delete().await()
                _uiState.value = ProductUiState.Success("Producto eliminado correctamente.")
            } catch (e: Exception) {
                _uiState.value = ProductUiState.Error(e.localizedMessage ?: "Error al eliminar producto.")
            }
        }
    }

    override fun onCleared() {
        snapshotListener?.remove()
    }
}
