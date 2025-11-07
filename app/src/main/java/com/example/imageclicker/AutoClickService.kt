package com.example.imageclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class AutoClickService : AccessibilityService() {

    companion object {
        private var instance: AutoClickService? = null
        
        fun isEnabled(): Boolean = instance != null
        
        fun performClick(x: Float, y: Float) {
            instance?.clickAtPosition(x, y)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoClickService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d("AutoClickService", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("AutoClickService", "Service destroyed")
    }

    private fun clickAtPosition(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        val gesture = gestureBuilder.build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("AutoClickService", "Click performed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e("AutoClickService", "Click cancelled")
            }
        }, null)
        
        if (!result) {
            Log.e("AutoClickService", "Failed to dispatch gesture")
        }
    }
}

