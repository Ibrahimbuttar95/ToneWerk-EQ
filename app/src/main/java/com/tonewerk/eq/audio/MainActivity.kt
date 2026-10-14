package com.tonewerk.eq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tonewerk.eq.audio.FxEngine
import com.tonewerk.eq.ui.AppRoot
import com.tonewerk.eq.ui.TonewerkTheme

class MainActivity : ComponentActivity() {
    private val model: PlayerModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FxEngine.restore(this)
        setContent { TonewerkTheme { AppRoot(model) } }
    }
}
