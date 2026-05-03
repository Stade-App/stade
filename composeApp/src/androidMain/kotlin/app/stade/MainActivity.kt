package app.stade

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.stade.service.StadeService
import app.stade.ui.StadeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StadeApplication).container
        startForegroundService(Intent(this, StadeService::class.java))
        setContent { StadeApp(container) }
    }
}
