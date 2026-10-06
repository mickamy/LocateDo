package com.locatedo.locatedo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.locatedo.locatedo.ui.LocateDoApp
import com.locatedo.locatedo.ui.theme.LocateDoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocateDoTheme {
                LocateDoApp()
            }
        }
    }
}
