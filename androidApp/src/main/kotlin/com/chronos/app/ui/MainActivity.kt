package com.chronos.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.settings.ChronosPreferences
import com.chronos.app.settings.ChronosSettings
import com.chronos.app.theme.ChronosTheme
import com.chronos.app.ui.navigation.ChronosNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var chronosSettings: ChronosSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences by chronosSettings.preferences.collectAsStateWithLifecycle(
                initialValue = ChronosPreferences.Default
            )
            ChronosTheme(preferences) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChronosNavHost()
                }
            }
        }
    }
}
