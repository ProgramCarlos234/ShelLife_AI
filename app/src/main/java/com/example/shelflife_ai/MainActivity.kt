package com.example.shelflife_ai

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.shelflife_ai.navigation.AppNavigation
import com.example.shelflife_ai.ui.theme.ShelfLife_AITheme

// ==========================================
// FUNCIÓN AUXILIAR PARA OBTENER MODELO DE IMAGEN EN COIL
// ==========================================
fun getCoilImageModel(selectedUri: Uri?, photoUrl: String?, defaultUid: String): Any {
    if (selectedUri != null) {
        return selectedUri
    }
    if (!photoUrl.isNullOrEmpty()) {
        if (photoUrl.startsWith("data:image") && photoUrl.contains("base64,")) {
            return try {
                val base64Data = photoUrl.substringAfter("base64,")
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (e: Exception) {
                "https://api.dicebear.com/7.x/bottts/svg?seed=$defaultUid"
            }
        } else if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
            return photoUrl
        }
    }
    return "https://api.dicebear.com/7.x/bottts/svg?seed=$defaultUid"
}

// ==========================================
// ACTIVIDAD PRINCIPAL (PUNTO DE ENTRADA)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShelfLife_AITheme {
                AppNavigation()
            }
        }
    }
}
