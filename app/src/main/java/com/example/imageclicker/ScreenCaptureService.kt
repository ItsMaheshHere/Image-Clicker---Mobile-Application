package com.example.imageclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "ScreenCaptureChannel"
        
        private var instance: ScreenCaptureService? = null
        
        fun getScreenshot(): Bitmap? {
            return instance?.captureScreen()
        }
        
        fun isRunning(): Boolean = instance != null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        
        Log.d("ScreenCaptureService", "Screen: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
        
        createNotificationChannel()
        
        val notification = createNotification()
        startForeground(1, notification)
        
        Log.d("ScreenCaptureService", "Service created and foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenCaptureService", "onStartCommand called")
        intent?.let {
            val resultCode = it.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data: Intent? = it.getParcelableExtra(EXTRA_DATA)
            
            Log.d("ScreenCaptureService", "ResultCode: $resultCode, Data: ${data != null}")
            
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                startMediaProjection(resultCode, data)
            } else {
                Log.e("ScreenCaptureService", "Permission denied or invalid data! ResultCode: $resultCode")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            Log.d("ScreenCaptureService", "Starting MediaProjection...")
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Log.e("ScreenCaptureService", "MediaProjection is NULL!")
                return
            }
            Log.d("ScreenCaptureService", "MediaProjection created successfully")
            
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            Log.d("ScreenCaptureService", "ImageReader created")
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            
            if (virtualDisplay == null) {
                Log.e("ScreenCaptureService", "VirtualDisplay is NULL!")
            } else {
                Log.d("ScreenCaptureService", "VirtualDisplay created successfully")
            }
            
            Log.d("ScreenCaptureService", "Media projection setup complete")
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Error in startMediaProjection: ${e.message}", e)
        }
    }

    private fun captureScreen(): Bitmap? {
        if (imageReader == null) {
            Log.e("ScreenCaptureService", "ImageReader is NULL!")
            return null
        }
        
        val image: Image? = imageReader?.acquireLatestImage()
        
        if (image == null) {
            Log.e("ScreenCaptureService", "Failed to acquire image - no image available")
            return null
        }
        
        Log.d("ScreenCaptureService", "Image acquired, creating bitmap...")
        
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            
            Log.d("ScreenCaptureService", "Screenshot captured successfully!")
            return croppedBitmap
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Error capturing screen: ${e.message}", e)
            return null
        } finally {
            image.close()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Image Detection Active")
            .setContentText("Looking for images on screen...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        instance = null
        Log.d("ScreenCaptureService", "Service destroyed")
    }
}

