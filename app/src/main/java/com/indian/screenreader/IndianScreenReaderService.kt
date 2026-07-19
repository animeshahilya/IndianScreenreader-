package com.indian.screenreader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.speech.tts.TextToSpeech
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.PyObject
import java.util.Locale

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
            Log.i(TAG, "Loaded python screen_reader module successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Python module", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            tts?.language = Locale.getDefault()
            Log.i(TAG, "TTS Initialized successfully")
            speak("Indian Screen reader started")
        } else {
            Log.e(TAG, "TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (ttsInitialized && text.isNotBlank()) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, "UtteranceId_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeech() {
        if (ttsInitialized) {
            tts?.stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        if (ttsInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        if (ttsInitialized) {
            tts?.setPitch(pitch)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (pythonModule != null) {
            try {
                pythonModule?.callAttr("on_accessibility_event", this, event)
            } catch (e: Exception) {
                Log.e(TAG, "Error in Python on_accessibility_event", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service Interrupted")
        stopSpeech()
        if (pythonModule != null) {
            try {
                pythonModule?.callAttr("on_interrupt")
            } catch (e: Exception) {
                Log.e(TAG, "Error in Python on_interrupt", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeech()
        tts?.shutdown()
    }
}
