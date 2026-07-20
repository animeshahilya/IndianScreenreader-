package com.indian.screenreader

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Low-Level TalkBack Adapted Accessibility Engine for Indian Screenreader.
 * Direct Android 16 (minSdk = 36) Framework Integration with Depth-First Search
 * Node Traversal for 100% reliable swipe navigation across all Android applications.
 */
class IndianScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "IndianScreenReader"
    private var tts: TextToSpeech? = null
    private var pythonModule: PyObject? = null
    private var ttsInitialized = false
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var windowManager: WindowManager? = null
    private var curtainView: View? = null
    private var isScreenOn = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private val executorService = Executors.newFixedThreadPool(2)

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopSpeech()
                    Log.i(TAG, "Screen OFF - Suspending background activity")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.i(TAG, "Screen ON - Resuming screen reader")
                }
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "SCREEN_CURTAIN_ENABLED") {
            val enabled = sharedPreferences.getBoolean(key, false)
            setScreenCurtainEnabled(enabled)
        }
        
        executorService.execute {
            try {
                val py = Python.getInstance()
                val settingsModule = py.getModule("settings")
                settingsModule.callAttr("init_from_android", sharedPreferences)
                Log.i(TAG, "Python settings dynamically reloaded from SharedPreferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading settings in python", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected with TalkBack Low-Level Navigation Engine")

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize ToneGenerator, Vibrator & WindowManager
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio/vibration/window manager", e)
        }

        // Register Screen ON/OFF Receiver safely for Android 13+
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }

        // Initialize Chaquopy Python Engine on background thread
        executorService.execute {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }

            val py = Python.getInstance()
            try {
                // Initialize settings with SharedPreferences
                val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
                val settingsModule = py.getModule("settings")
                settingsModule.callAttr("init_from_android", prefs)

                pythonModule = py.getModule("screen_reader")
                Log.i(TAG, "Loaded Python screen_reader module successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Python module", e)
            }
        }
        
        // Register SharedPreferences listener
        try {
            val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering SharedPreferences listener", e)
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

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
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

    /**
     * Screen Curtain Privacy Feature: Blacks out the screen display
     * while touch exploration, TTS, and haptics remain fully active.
     */
    fun setScreenCurtainEnabled(enabled: Boolean) {
        mainHandler.post {
            try {
                if (enabled) {
                    if (curtainView == null && windowManager != null) {
                        curtainView = View(this).apply {
                            setBackgroundColor(Color.BLACK)
                        }
                        val params = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT
                        )
                        windowManager?.addView(curtainView, params)
                        Log.i(TAG, "Screen Curtain Activated")
                    }
                } else {
                    curtainView?.let {
                        windowManager?.removeView(it)
                        curtainView = null
                        Log.i(TAG, "Screen Curtain Deactivated")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling Screen Curtain overlay", e)
            }
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
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
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

        stopSpeech()
        playHapticFeedback(25L)

        if (pythonModule != null) {
            try {
                val handled = pythonModule?.callAttr("on_gesture", this, gestureId)?.toBoolean() ?: false
                if (handled) return true
            } catch (e: Exception) {
                Log.e(TAG, "Error in Python on_gesture", e)
            }
        }

        // Fallback if Python module fails
        return when (gestureId) {
            GESTURE_SWIPE_RIGHT -> performFocusNext()
            GESTURE_SWIPE_LEFT -> performFocusPrevious()
            GESTURE_DOUBLE_TAP -> performNodeClick()
            else -> super.onGesture(gestureId)
        }
    }

    // --- TALKBACK LOW-LEVEL ACCESSIBILITY TREE NAVIGATION ENGINE ---

    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return

        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val isInteractive = node.isClickable || node.isCheckable || node.isFocusable || node.isHeading

        if (hasText || isInteractive) {
            // We obtain a copy to keep in the list so we can recycle original node's children safely.
            val nodeCopy = AccessibilityNodeInfo.obtain(node)
            list.add(nodeCopy)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectFocusableNodes(child, list)
                child.recycle()
            }
        }
    }

    fun performFocusNext(): Boolean {
        val root = rootInActiveWindow ?: return false
        
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, nodes)

        if (nodes.isEmpty()) {
            root.recycle()
            currentFocused?.recycle()
            return false
        }

        var targetIndex = 0
        if (currentFocused != null) {
            for (i in nodes.indices) {
                if (nodes[i] == currentFocused) {
                    targetIndex = i + 1
                    break
                }
            }
            currentFocused.recycle()
        }

        var success = false
        if (targetIndex < nodes.size) {
            val targetNode = nodes[targetIndex]
            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            if (success) {
                playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
            }
        } else {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP2)
            speak("End of screen")
        }

        // Recycle all collected nodes
        for (node in nodes) {
            node.recycle()
        }
        root.recycle()

        return success
    }

    fun performFocusPrevious(): Boolean {
        val root = rootInActiveWindow ?: return false
        
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, nodes)

        if (nodes.isEmpty()) {
            root.recycle()
            currentFocused?.recycle()
            return false
        }

        var targetIndex = -1
        if (currentFocused != null) {
            for (i in nodes.indices) {
                if (nodes[i] == currentFocused) {
                    targetIndex = i - 1
                    break
                }
            }
            currentFocused.recycle()
        }

        var success = false
        if (targetIndex >= 0 && targetIndex < nodes.size) {
            val targetNode = nodes[targetIndex]
            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            if (success) {
                playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
            }
        } else {
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP2)
            speak("Start of screen")
        }

        // Recycle all collected nodes
        for (node in nodes) {
            node.recycle()
        }
        root.recycle()

        return success
    }

    fun clearFocus(): Boolean {
        val root = rootInActiveWindow ?: return false
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        var success = false
        if (currentFocused != null) {
            success = currentFocused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            currentFocused.recycle()
        }
        root.recycle()
        return success
    }

    fun performNodeClick(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        root.recycle()
        if (focused == null) return false
        val clicked = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        focused.recycle()
        if (clicked) playAudioBeep(ToneGenerator.TONE_PROP_ACK)
        return clicked
    }

    fun performNodeLongClick(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        root.recycle()
        if (focused == null) return false
        val longClicked = focused.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        focused.recycle()
        return longClicked
    }

    fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performGlobalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performGlobalNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun performGlobalQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isScreenOn) return

        if (pythonModule != null) {
            // Process on background thread to prevent UI freezing if Python is slow
            val eventCopy = AccessibilityEvent.obtain(event)
            executorService.execute {
                try {
                    pythonModule?.callAttr("on_accessibility_event", this, eventCopy)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Python on_accessibility_event", e)
                } finally {
                    try {
                        eventCopy.recycle()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service Interrupted")
        stopSpeech()
        if (pythonModule != null) {
            executorService.execute {
                try {
                    pythonModule?.callAttr("on_interrupt")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Python on_interrupt", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
            setScreenCurtainEnabled(false)
            val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        stopSpeech()
        tts?.shutdown()
        toneGenerator?.release()
        executorService.shutdown()
    }

    fun captureScreenForAI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hwBuffer = screenshot.hardwareBuffer
                        val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                        if (bitmap != null) {
                            val outputStream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
                            
                            executorService.execute {
                                try {
                                    pythonModule?.callAttr("on_screenshot_captured", this@IndianScreenReaderService, base64)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error passing screenshot to python", e)
                                }
                            }
                        } else {
                            speak("Failed to process screenshot")
                        }
                    } catch (e: Exception) {
                        speak("Error capturing screenshot")
                    }
                }
                override fun onFailure(errorCode: Int) {
                    speak("Failed to capture screenshot. Error code $errorCode")
                }
            })
        } else {
            speak("Screen capture requires Android 11 or higher")
        }
    }
}
