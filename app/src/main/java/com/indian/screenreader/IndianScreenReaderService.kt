package com.indian.screenreader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.speech.tts.TextToSpeech
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.PyObject

class IndianScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "IndianScreenReader"
    private var tts: TextToSpeech? = null
    private var pythonModule: PyObject? = null
    private var ttsInitialized = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected")

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize Chaquopy
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val py = Python.getInstance()
        try {
            pythonModule = py.getModule("screen_reader")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Python module", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            Log.i(TAG, "TTS Initialized")
            speak("Indian Screen reader started")
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    fun speak(text: String) {
        if (ttsInitialized && text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (pythonModule != null) {
            try {
                // Pass the event to Python. We pass `this` so Python can call `speak()`,
                // and we pass the `event` object.
                pythonModule?.callAttr("on_accessibility_event", this, event)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Python on_accessibility_event", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service Interrupted")
        if (ttsInitialized) {
            tts?.stop()
        }
        if (pythonModule != null) {
            try {
                pythonModule?.callAttr("on_interrupt")
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Python on_interrupt", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}
