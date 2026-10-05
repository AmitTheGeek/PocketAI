package com.pocketai.offline.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.pocketai.offline.PocketAiApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(
            this,
            MainViewModel.factory(application)
        )[MainViewModel::class.java]

        setContent {
            PocketAiTheme {
                PocketAiRoute(viewModel = viewModel)
            }
        }
    }
}
