package com.example.shelflife_ai.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.shelflife_ai.data.UserPreferences
import com.example.shelflife_ai.getCoilImageModel
import com.example.shelflife_ai.model.ProductModel
import com.example.shelflife_ai.model.UserModel
import com.example.shelflife_ai.ui.product.ProductViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

// ==========================================
// TEMA DE COLORES PASTEL SEGÚN VENCIMIENTO
// ==========================================
data class ProductColorTheme(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color
)

fun getProductStatusColor(expirationDate: Long): ProductColorTheme {
    val nowMillis = System.currentTimeMillis()
    val daysLeft = ((expirationDate - nowMillis) / (1000 * 60 * 60 * 24)).toInt()

    return when {
        daysLeft < 15 -> ProductColorTheme(
            backgroundColor = Color(0xFFFFEBEE), // Rojo pastel (< 15 días)
            borderColor = Color(0xFFE57373),
            textColor = Color(0xFFC62828)
        )
        daysLeft in 15..30 -> ProductColorTheme(
            backgroundColor = Color(0xFFFFF8E1), // Amarillo / Naranja pastel (15 - 30 días)
            borderColor = Color(0xFFFFB74D),
            textColor = Color(0xFFEF6C00)
        )
        else -> ProductColorTheme(
            backgroundColor = Color(0xFFE8F5E9), // Verde pastel (> 30 días)
            borderColor = Color(0xFF81C784),
            textColor = Color(0xFF2E7D32)
        )
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Frutas", "Verduras" -> Icons.Default.Eco
        "Carnes y Embutidos" -> Icons.Default.Restaurant
        "Lácteos" -> Icons.Default.Icecream
        "Bebidas" -> Icons.Default.LocalBar
        "Abarrotes / Despensa" -> Icons.Default.ShoppingBag
        else -> Icons.Default.Fastfood
    }
}

fun getDefaultDaysAndTipForCategory(category: String): Pair<Int, String> {
    return when (category) {
        "Frutas" -> Pair(3, "Fruta cortada o pelada dura aprox. 3 días en refrigeración.")
        "Verduras" -> Pair(4, "Verdura cortada o cocida dura aprox. 4 días en refrigeración.")
        "Carnes y Embutidos" -> Pair(4, "Carnes molidas 1-2 días; sobras 3-4 días; fiambres 3-5 días.")
        "Lácteos" -> Pair(3, "Leche y yogur abiertos 3 días; queso duro y mantequilla hasta 3 semanas.")
        "Bebidas" -> Pair(7, "Jugos y refrescos 7-10 días; gaseosas abiertas 2-3 días.")
        "Abarrotes / Despensa" -> Pair(30, "Salsas de frasco y conservas 3-4 días; mermeladas y aderezos 1-3 meses.")
        else -> Pair(7, "Ingresa la duración sugerida según el producto.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    productViewModel: ProductViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddProduct: () -> Unit,
    onSignOut: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    var userModel by remember { mutableStateOf<UserModel?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val userPreferences = remember { UserPreferences(context) }
    val isTutorialSeenState by userPreferences.isTutorialSeen.collectAsState(initial = false)
    var showTutorialManually by remember { mutableStateOf(false) }

    val shouldShowTutorial = showTutorialManually || !isTutorialSeenState
    var tutorialStep by remember { mutableStateOf(1) }

    val products by productViewModel.products.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var productToOpen by remember { mutableStateOf<ProductModel?>(null) }
    var openDaysInput by remember { mutableStateOf("3") }
    var currentTip by remember { mutableStateOf("") }

    var productToDelete by remember { mutableStateOf<ProductModel?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            try {
                val doc = firestore.collection("users").document(currentUser.uid).get().await()
                val user = doc.toObject(UserModel::class.java)
                userModel = user ?: UserModel(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    displayName = currentUser.displayName ?: "Usuario",
                    photoUrl = currentUser.photoUrl?.toString() ?: ""
                )
            } catch (e: Exception) {
                userModel = UserModel(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    displayName = currentUser.displayName ?: "Usuario",
                    photoUrl = currentUser.photoUrl?.toString() ?: ""
                )
            }
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val nowMillis = System.currentTimeMillis()
    val fiveDaysInMillis = 5 * 24 * 60 * 60 * 1000L
    val expiringProducts = remember(products) {
        products.filter { product ->
            val diff = product.expirationDate - nowMillis
            diff <= fiveDaysInMillis
        }.sortedBy { it.expirationDate }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        val avatarModel = remember(userModel?.photoUrl) {
                            getCoilImageModel(
                                selectedUri = null,
                                photoUrl = userModel?.photoUrl,
                                defaultUid = currentUser?.uid ?: "default"
                            )
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarModel)
                                .crossfade(true)
                                .decoderFactory(SvgDecoder.Factory())
                                .build(),
                            contentDescription = "Foto de Perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = userModel?.displayName?.ifBlank { "Usuario" } ?: "Usuario",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = userModel?.email?.ifBlank { currentUser?.email ?: "Sin correo" } ?: "Sin correo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Configuración") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Guía de Uso / Tutorial") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        tutorialStep = 1
                        showTutorialManually = true
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Tutorial") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        auth.signOut()
                        onSignOut()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("ShelfLife_AI") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir Menú")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToAddProduct,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.then(
                        if (shouldShowTutorial && tutorialStep == 3) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                        } else Modifier
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar Producto")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar alimento o categoría...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (shouldShowTutorial && tutorialStep == 1) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    } else Modifier
                                ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    if (expiringProducts.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (shouldShowTutorial && tutorialStep == 2) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                                        } else Modifier
                                    ),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Próximos a Vencer (${expiringProducts.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    expiringProducts.take(3).forEach { product ->
                                        val daysLeft = ((product.expirationDate - nowMillis) / (1000 * 60 * 60 * 24)).toInt()
                                        val badgeColor = if (daysLeft <= 1) Color(0xFFD32F2F) else Color(0xFFF57C00)
                                        val badgeText = if (daysLeft < 0) "¡Vencido!" else if (daysLeft == 0) "¡Vence Hoy!" else "Vence en $daysLeft días"

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    getCategoryIcon(product.category),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(product.name, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            Surface(
                                                color = badgeColor,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    item {
                        Text(
                            "Mis Productos (${filteredProducts.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (filteredProducts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Kitchen,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No hay productos registrados.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                dateFormatter = dateFormatter,
                                onOpenClick = {
                                    val (defaultDays, tip) = getDefaultDaysAndTipForCategory(product.category)
                                    openDaysInput = defaultDays.toString()
                                    currentTip = tip
                                    productToOpen = product
                                },
                                onDeleteClick = {
                                    productToDelete = product
                                }
                            )
                        }
                    }
                }

                // Modal Dialog para "Marcar como Abierto"
                productToOpen?.let { selectedProduct ->
                    val daysInt = openDaysInput.toIntOrNull() ?: 3
                    val calculatedOpenedExpiration = nowMillis + (daysInt.toLong() * 24 * 60 * 60 * 1000L)
                    val originalExp = if (selectedProduct.originalExpirationDate > 0) selectedProduct.originalExpirationDate else selectedProduct.expirationDate
                    val finalExp = min(originalExp, calculatedOpenedExpiration)

                    AlertDialog(
                        onDismissRequest = { productToOpen = null },
                        title = { Text("Marcar '${selectedProduct.name}' como Abierto") },
                        text = {
                            Column {
                                Text(
                                    text = "Sugerencia por categoría (${selectedProduct.category}):",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentTip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = openDaysInput,
                                    onValueChange = { openDaysInput = it },
                                    label = { Text("Días de duración tras abrir") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Original exp: ${dateFormatter.format(Date(originalExp))}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Nuevo vencimiento estimado: ${dateFormatter.format(Date(finalExp))}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    productViewModel.markProductAsOpened(selectedProduct, daysInt)
                                    productToOpen = null
                                }
                            ) {
                                Text("Confirmar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { productToOpen = null }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                // Modal Dialog para Confirmar ELIMINAR PRODUCTO
                productToDelete?.let { selectedProduct ->
                    AlertDialog(
                        onDismissRequest = { productToDelete = null },
                        title = { Text("¿Eliminar producto?") },
                        text = {
                            Text("¿Estás seguro de que deseas eliminar '${selectedProduct.name}'? Esta acción no se puede deshacer.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    productViewModel.deleteProduct(selectedProduct.id)
                                    productToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Eliminar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { productToDelete = null }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                if (shouldShowTutorial) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(16.dp)
                    ) {
                        when (tutorialStep) {
                            1 -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(top = 100.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("1. Barra de Búsqueda", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Filtra en tiempo real tus productos almacenados por nombre o categoría.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(onClick = { tutorialStep = 2 }) {
                                                Text("Siguiente")
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.Center)
                                        .padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("2. Alertas de Vencimiento", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Revisa los productos con vencimiento cercano (3 a 5 días) resaltados con colores de alerta.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            OutlinedButton(onClick = { tutorialStep = 1 }) {
                                                Text("Anterior")
                                            }
                                            Button(onClick = { tutorialStep = 3 }) {
                                                Text("Siguiente")
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 90.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("3. Registrar Productos", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Presiona el botón '+' para agregar nuevos alimentos manualmente o escaneando con la Cámara IA.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            OutlinedButton(onClick = { tutorialStep = 2 }) {
                                                Text("Anterior")
                                            }
                                            Button(
                                                onClick = {
                                                    showTutorialManually = false
                                                    scope.launch {
                                                        userPreferences.setTutorialSeen(true)
                                                    }
                                                }
                                            ) {
                                                Text("¡Entendido!")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE COMPONENTE: TARJETA DE PRODUCTO
// ==========================================
@Composable
fun ProductCard(
    product: ProductModel,
    dateFormatter: SimpleDateFormat,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val nowMillis = System.currentTimeMillis()
    val isOpened = product.isOpened

    val colorTheme = remember(product.expirationDate) {
        getProductStatusColor(product.expirationDate)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorTheme.backgroundColor),
        border = BorderStroke(1.5.dp, colorTheme.borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isOpened) {
                val daysLeft = ((product.expirationDate - nowMillis) / (1000 * 60 * 60 * 24)).toInt()
                val openedMsg = if (daysLeft < 0) "¡Excedió plazo de apertura!" else if (daysLeft == 0) "Consumir HOY mismo" else "Consumir en los próximos $daysLeft días"

                Surface(
                    color = colorTheme.borderColor,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRODUCTO ABIERTO • $openedMsg",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Cuadro de Imagen / Ícono de Categoría a la izquierda
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (product.imageUrl.isNotEmpty()) {
                        val avatarModel = getCoilImageModel(
                            selectedUri = null,
                            photoUrl = product.imageUrl,
                            defaultUid = product.id
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarModel).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = getCategoryIcon(product.category),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = colorTheme.textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorTheme.textColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        "${product.quantity} ${product.unit} - ${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Vence: ${dateFormatter.format(Date(product.expirationDate))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorTheme.textColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar Producto",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            product.storageType,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenClick,
                    enabled = !isOpened,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorTheme.borderColor,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.25f),
                        disabledContentColor = Color.Gray
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = if (isOpened) Icons.Default.Check else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOpened) "Envase Abierto" else "Abrir Envase",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
