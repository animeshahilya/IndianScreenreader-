package com.indian.screenreader

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class IndianScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "IndianScreenReader"
    private var tts: TextToSpeech? = null
    private var pythonModule: PyObject? = null
    private var ttsInitialized = false
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var isScreenOn = true

    private val executorService = Executors.newFixedThreadPool(2)

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopSpeech()
                    Log.i(TAG, "Screen OFF - Suspending background screen reader activity")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.i(TAG, "Screen ON - Resuming screen reader")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected")

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize ToneGenerator & Vibrator for feedback
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tone/vibration generator", e)
        }

        // Register Screen ON/OFF Receiver
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }

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
            speak("Indian Screen reader ready")
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
            playHapticFeedback()
        } else {
            Log.e(TAG, "TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (!isScreenOn) return

        if (ttsInitialized && text.isNotBlank()) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, "Utterance_${System.currentTimeMillis()}")
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

    fun setLanguage(languageCode: String) {
        if (!ttsInitialized) return
        try {
            val locale = when (languageCode.lowercase()) {
                "hi" -> Locale("hi", "IN")
                "ta" -> Locale("ta", "IN")
                "te" -> Locale("te", "IN")
                "bn" -> Locale("bn", "IN")
                "mr" -> Locale("mr", "IN")
                "gu" -> Locale("gu", "IN")
                else -> Locale.ENGLISH
            }
            tts?.language = locale
        } catch (e: Exception) {
            Log.e(TAG, "Error setting TTS locale for $languageCode", e)
        }
    }

    fun playAudioBeep(toneType: Int = ToneGenerator.TONE_PROP_BEEP) {
        try {
            toneGenerator?.startTone(toneType, 50)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio tone", e)
        }
    }

    fun playHapticFeedback(durationMs: Long = 30L) {
        try {
            if (vibrator != null && vibrator!!.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering vibration", e)
        }
    }

    fun getDeviceStatusString(): String {
        return try {
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            
            val batteryInfo = if (batteryPct >= 0) "Battery $batteryPct percent" else ""
            "$timeStr, $batteryInfo".trim(' ', ',')
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device status", e)
            "Status unavailable"
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                stopSpeech()
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onGesture(gestureId: Int): Boolean {
        if (!isScreenOn) return false

        stopSpeech() // Cancel any ongoing background speech on new gesture
        playHapticFeedback(25L)

        if (pythonModule != null) {
            try {
                val handled = pythonModule?.callAttr("on_gesture", this, gestureId)?.toBoolean() ?: false
                if (handled) return true
            } catch (e: Exception) {
                Log.e(TAG, "Error in Python on_gesture", e)
            }
        }

        return when (gestureId) {
            GESTURE_SWIPE_RIGHT -> {
                performFocusNext()
                true
            }
            GESTURE_SWIPE_LEFT -> {
                performFocusPrevious()
                true
            }
            GESTURE_DOUBLE_TAP -> {
                performNodeClick()
                true
            }
            else -> super.onGesture(gestureId)
        }
    }

    fun performFocusNext(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val result = if (focused != null) {
            focused.focusSearch(View.FOCUS_FORWARD)?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ?: false
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        if (result) {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
        } else {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP2)
            speak("End of screen")
        }
        return result
    }

    fun performFocusPrevious(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val result = if (focused != null) {
            focused.focusSearch(View.FOCUS_BACKWARD)?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ?: false
        } else {
            false
        }
        if (result) {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
        } else {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP2)
            speak("Start of screen")
        }
        return result
    }

    fun performNodeClick(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return false
        val clicked = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) playAudioBeep(ToneGenerator.TONE_PROP_ACK)
        return clicked
    }

    fun performNodeLongClick(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return false
        return focused.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performGlobalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performGlobalNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun performGlobalQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isScreenOn) return

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
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        stopSpeech()
        tts?.shutdown()
        toneGenerator?.release()
        executorService.shutdown()
    }
}
