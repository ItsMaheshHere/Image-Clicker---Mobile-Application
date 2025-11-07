package com.example.imageclicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.imageclicker.ui.theme.ImageClickerTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private var targetImageBitmap: Bitmap? = null
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ImageClickerTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        var isRunning by remember { mutableStateOf(false) }
        var hasImage by remember { mutableStateOf(false) }
        
        val imagePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                targetImageBitmap = loadImageFromUri(it)
                hasImage = targetImageBitmap != null
            }
        }
        
        val screenCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startScreenCaptureService(result.resultCode, result.data!!)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    startImageSearch(0.7)
                }
                isRunning = true
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasImage) "Change Image" else "Select Image",
                    modifier = Modifier
                        .background(Color.LightGray)
                        .clickable(enabled = !isRunning) { 
                            imagePickerLauncher.launch("image/*") 
                        }
                        .padding(16.dp)
                )
                
                Text(
                    text = if (isRunning) "STOP" else "START",
                    modifier = Modifier
                        .background(Color.LightGray)
                        .clickable {
                            if (isRunning) {
                                stopImageSearch()
                                isRunning = false
                            } else {
                                if (!hasImage) {
                                    return@clickable
                                }
                                if (!AutoClickService.isEnabled()) {
                                    openAccessibilitySettings()
                                    return@clickable
                                }
                                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            }
                        }
                        .padding(16.dp)
                )
            }
        }
    }

    private fun loadImageFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading image: ${e.message}")
            null
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, data)
        }
        startForegroundService(intent)
    }

    private fun startImageSearch(confidence: Double) {
        android.util.Log.d("MainActivity", "Starting image search with confidence: $confidence")
        val imageMatcher = ImageMatcher()
        var searchCount = 0
        
        searchJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                searchCount++
                android.util.Log.d("MainActivity", "Search iteration: $searchCount")
                
                val screenshot = ScreenCaptureService.getScreenshot()
                val target = targetImageBitmap
                
                android.util.Log.d("MainActivity", "Screenshot: ${screenshot != null}, Target: ${target != null}")
                
                if (screenshot != null && target != null) {
                    android.util.Log.d("MainActivity", "Finding image...")
                    val result = imageMatcher.findImage(screenshot, target, confidence)
                    
                    if (result.found) {
                        withContext(Dispatchers.Main) {
                            AutoClickService.performClick(result.x, result.y)
                        }
                    }
                }
                
                delay(1000)
            }
        }
    }

    private fun stopImageSearch() {
        searchJob?.cancel()
        stopService(Intent(this, ScreenCaptureService::class.java))
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopImageSearch()
    }
}
