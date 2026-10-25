package com.sinobash.famly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sinobash.famly.ui.navigation.AppNav
import com.sinobash.famly.ui.theme.FamlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamlyTheme {
                AppNav()
            }
        }
    }
}