package com.example.shelflife_ai.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.shelflife_ai.getCoilImageModel
import com.example.shelflife_ai.model.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    var userModel by remember { mutableStateOf<UserModel?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    title = { Text("Inicio") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir Menú")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                            contentDescription = "Avatar de Usuario",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "¡Bienvenido a ShelfLife_AI!",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = userModel?.displayName?.ifBlank { "Usuario" } ?: "Usuario",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = userModel?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onNavigateToSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Ir al Perfil y Configuración")
                }
            }
        }
    }
}
