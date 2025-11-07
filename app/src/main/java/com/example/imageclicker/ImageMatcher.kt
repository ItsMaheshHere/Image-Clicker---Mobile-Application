package com.example.imageclicker

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ImageMatcher {

    init {
        if (!OpenCVLoader.initDebug()) {
            Log.e("ImageMatcher", "OpenCV initialization failed!")
        } else {
            Log.d("ImageMatcher", "OpenCV initialized successfully")
        }
    }

    data class MatchResult(
        val found: Boolean,
        val x: Float,
        val y: Float,
        val confidence: Double
    )

    fun findImage(screenBitmap: Bitmap, targetBitmap: Bitmap, threshold: Double = 0.8): MatchResult {
        try {
            val screenMat = Mat()
            val targetMat = Mat()
            Utils.bitmapToMat(screenBitmap, screenMat)
            Utils.bitmapToMat(targetBitmap, targetMat)
            
            val screenGray = Mat()
            val targetGray = Mat()
            Imgproc.cvtColor(screenMat, screenGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(targetMat, targetGray, Imgproc.COLOR_RGBA2GRAY)
            
            val resultCols = screenGray.cols() - targetGray.cols() + 1
            val resultRows = screenGray.rows() - targetGray.rows() + 1
            val result = Mat(resultRows, resultCols, CvType.CV_32FC1)
            
            Imgproc.matchTemplate(screenGray, targetGray, result, Imgproc.TM_CCOEFF_NORMED)
            
            val minMaxLocResult = Core.minMaxLoc(result)
            val maxVal = minMaxLocResult.maxVal
            val maxLoc = minMaxLocResult.maxLoc
            
            val centerX = (maxLoc.x + targetBitmap.width / 2).toFloat()
            val centerY = (maxLoc.y + targetBitmap.height / 2).toFloat()
            
            screenMat.release()
            targetMat.release()
            screenGray.release()
            targetGray.release()
            result.release()
            
            if (maxVal >= threshold) {
                Log.d("ImageMatcher", "Image found at ($centerX, $centerY) with confidence $maxVal")
                return MatchResult(true, centerX, centerY, maxVal)
            } else {
                Log.d("ImageMatcher", "Image not found. Best match: $maxVal")
                return MatchResult(false, 0f, 0f, maxVal)
            }
            
        } catch (e: Exception) {
            Log.e("ImageMatcher", "Error matching image: ${e.message}")
            return MatchResult(false, 0f, 0f, 0.0)
        }
    }
}

