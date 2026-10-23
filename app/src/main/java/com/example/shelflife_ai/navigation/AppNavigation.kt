package com.example.shelflife_ai.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shelflife_ai.ui.auth.AuthScreen
import com.example.shelflife_ai.ui.auth.AuthViewModel
import com.example.shelflife_ai.ui.home.HomeScreen
import com.example.shelflife_ai.ui.product.AddProductScreen
import com.example.shelflife_ai.ui.product.ProductViewModel
import com.example.shelflife_ai.ui.profile.ProfileViewModel
import com.example.shelflife_ai.ui.profile.SettingsScreen
import com.google.firebase.auth.FirebaseAuth

object Screen {
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADD_PRODUCT = "add_product"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = if (currentUser != null) Screen.HOME else Screen.AUTH

    val productViewModel: ProductViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.AUTH) {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.HOME) {
                        popUpTo(Screen.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.HOME) {
            HomeScreen(
                productViewModel = productViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.SETTINGS)
                },
                onNavigateToAddProduct = {
                    navController.navigate(Screen.ADD_PRODUCT)
                },
                onSignOut = {
                    navController.navigate(Screen.AUTH) {
                        popUpTo(Screen.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SETTINGS) {
            val profileViewModel: ProfileViewModel = viewModel()
            SettingsScreen(
                viewModel = profileViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ADD_PRODUCT) {
            AddProductScreen(
                viewModel = productViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
