package com.indian.screenreader

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import android.accessibilityservice.AccessibilityGestureEvent
import android.speech.tts.UtteranceProgressListener
import android.os.Build
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.indian.screenreader.core.AiService
import com.indian.screenreader.core.EventHandler
import com.indian.screenreader.core.NodeParser
import com.indian.screenreader.core.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class IndianScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "IndianScreenReader"
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var windowManager: WindowManager? = null
    private var curtainView: View? = null
    private var contextMenuView: View? = null
    @Volatile private var isScreenOn = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private val executorService = Executors.newFixedThreadPool(2)
    private val audioExecutor = Executors.newSingleThreadExecutor()  // dedicated — never blocked by events
    private val eventExecutor = Executors.newSingleThreadExecutor()  // dedicated — sequential FIFO event processing (Bug 5)
    private lateinit var eventHandler: EventHandler

    // readingNodes is accessed from main thread AND TTS callback thread — must be synchronized
    private val readingLock = Any()
    private val readingNodes = mutableListOf<AccessibilityNodeInfo>()
    private var readingIndex = 0
    @Volatile private var screenStateReceiverRegistered = false

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
        Settings.initFromAndroid(sharedPreferences)
        Log.i(TAG, "Native settings dynamically reloaded from SharedPreferences")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected with TalkBack Low-Level Navigation Engine")

        eventHandler = EventHandler(this)

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize Audio/Haptics/Window
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio/vibration/window manager", e)
        }

        // Register Receivers safely
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenStateReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }
        
        try {
            val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
            Settings.initFromAndroid(prefs)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering SharedPreferences listener", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            tts?.language = Locale.getDefault()
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("Continuous_") == true && Settings.CONTINUOUS_READING_ACTIVE) {
                        mainHandler.post {
                            readNextNode()
                        }
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("Continuous_") == true && Settings.CONTINUOUS_READING_ACTIVE) {
                        Settings.CONTINUOUS_READING_ACTIVE = false
                    }
                }
            })
            
            Log.i(TAG, "TTS Initialized successfully")
            speak("Indian Screen reader ready")
            playAudioBeep(ToneGenerator.TONE_PROP_BEEP)
            playHapticFeedback()
        } else {
            Log.e(TAG, "TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String, flush: Boolean = true, isContinuous: Boolean = false) {
        if (!isScreenOn) return
        if (ttsInitialized && text.isNotBlank()) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (isContinuous) "Continuous_${System.currentTimeMillis()}" else "Utterance_${System.currentTimeMillis()}"
            tts?.speak(text, queueMode, null, utteranceId)
        }
    }

    private fun clearReadingNodes() {
        // Must be called while holding readingLock, or exclusively on the main thread
        synchronized(readingLock) {
            readingNodes.forEach { it.recycle() }
            readingNodes.clear()
        }
    }

    fun stopSpeech() {
        if (ttsInitialized) {
            tts?.stop()
        }
        Settings.CONTINUOUS_READING_ACTIVE = false
        // Recycle reading nodes safely under lock
        synchronized(readingLock) {
            readingNodes.forEach { it.recycle() }
            readingNodes.clear()
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun setSpeechRate(rate: Float) {
        if (ttsInitialized) tts?.setSpeechRate(rate)
    }

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
                        speak("Screen curtain enabled")
                    }
                } else {
                    curtainView?.let {
                        windowManager?.removeView(it)
                        curtainView = null
                        speak("Screen curtain disabled")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling Screen Curtain overlay", e)
            }
        }
    }

    fun toggleScreenCurtain() {
        val newState = !Settings.SCREEN_CURTAIN_ENABLED
        Settings.SCREEN_CURTAIN_ENABLED = newState
        val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("SCREEN_CURTAIN_ENABLED", newState).apply()
    }

    fun playAudioBeep(toneType: Int = ToneGenerator.TONE_PROP_BEEP) {
        try {
            toneGenerator?.startTone(toneType, 50)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio tone", e)
        }
    }

    fun playAudioBeepForEvent(eventType: String) {
        val toneType = Settings.SOUND_THEME_MAP[Settings.SOUND_THEME]?.get(eventType) ?: ToneGenerator.TONE_PROP_BEEP
        playAudioBeep(toneType)
    }

    fun playDynamicScrollBeep(percentage: Float) {
        // Use dedicated audioExecutor — never blocks event processing threads
        audioExecutor.execute {
            try {
                val clampedPercent = percentage.coerceIn(0.0f, 1.0f)
                val hz = 1500.0 - (clampedPercent * 1000.0)

                val durationMs = 40
                val sampleRate = 44100
                val numSamples = (durationMs * sampleRate) / 1000
                val sample = ShortArray(numSamples)
                val twoPi = 2.0 * Math.PI
                val fadeSamples = 200

                for (i in 0 until numSamples) {
                    val envelope = when {
                        i < fadeSamples -> i.toDouble() / fadeSamples
                        i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                        else -> 1.0
                    }
                    sample[i] = (Math.sin(twoPi * i / (sampleRate / hz)) * envelope * Short.MAX_VALUE * 0.5).toInt().toShort()
                }

                val bufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                val audioTrack = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(maxOf(sample.size * 2, bufferSize))
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()

                try {
                    audioTrack.write(sample, 0, sample.size)
                    audioTrack.play()
                    // Use AudioTrack completion listener instead of Thread.sleep
                    audioTrack.setNotificationMarkerPosition(sample.size - 1)
                    audioTrack.setPlaybackPositionUpdateListener(object : android.media.AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: android.media.AudioTrack) {
                            track.release()
                        }
                        override fun onPeriodicNotification(track: android.media.AudioTrack) {}
                    })
                } catch (e: Exception) {
                    audioTrack.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing dynamic scroll beep", e)
            }
        }
    }

    fun playHapticFeedback(durationMs: Long = 30L) {
        try {
            if (Settings.HAPTIC_FEEDBACK_ENABLED && vibrator != null && vibrator!!.hasVibrator()) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering vibration", e)
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

    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (!isScreenOn) return false
        // Stop speech output but do NOT clear readingNodes — continuous reading handles its own state
        tts?.stop()
        playHapticFeedback(25L)
        return eventHandler.handleGesture(gestureEvent.gestureId)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onGesture(gestureId: Int): Boolean {
        if (!isScreenOn) return false
        tts?.stop()
        playHapticFeedback(25L)
        return eventHandler.handleGesture(gestureId)
    }

    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (node == null || !node.isVisibleToUser || depth > 30) return

        val granularity = Settings.GRANULARITIES.getOrElse(Settings.CURRENT_GRANULARITY_INDEX) { "default" }
        val matchesGranularity = when (granularity) {
            "heading" -> NodeParser.isHeading(node)
            "control" -> NodeParser.isControl(node)
            "word", "character" -> !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
            else -> {
                val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || !node.hintText.isNullOrBlank() || !node.stateDescription.isNullOrBlank() || !node.error.isNullOrBlank()
                val isInteractive = node.isClickable || node.isCheckable || node.isFocusable || node.isHeading
                hasText || isInteractive
            }
        }

        if (matchesGranularity) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    collectFocusableNodes(child, list, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private var nodeTextOffset = 0
    private var lastFocusedNodeBounds: android.graphics.Rect? = null

    private fun performGranularityMovement(node: AccessibilityNodeInfo, isNext: Boolean, granularity: String): Boolean {
        val granularityInt = if (granularity == "word") {
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
        } else {
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
        }

        val action = if (isNext) {
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
        } else {
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
        }

        val args = android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularityInt)
            putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
        }

        val nativeSuccess = node.performAction(action, args)
        if (nativeSuccess) {
            playAudioBeepForEvent("focus")
            return true
        }

        // Fallback: manual word/character parsing on node text
        val rawText = NodeParser.getNodeRawText(node)
        if (rawText.isBlank()) return false

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (lastFocusedNodeBounds != bounds) {
            lastFocusedNodeBounds = bounds
            nodeTextOffset = if (isNext) 0 else rawText.length
        }

        if (granularity == "character") {
            if (isNext) {
                if (nodeTextOffset < rawText.length) {
                    val charStr = rawText[nodeTextOffset].toString()
                    nodeTextOffset++
                    speak(NodeParser.formatCharacterSpeech(charStr))
                    playAudioBeepForEvent("focus")
                    return true
                }
            } else {
                if (nodeTextOffset > 0) {
                    nodeTextOffset--
                    val charStr = rawText[nodeTextOffset].toString()
                    speak(NodeParser.formatCharacterSpeech(charStr))
                    playAudioBeepForEvent("focus")
                    return true
                }
            }
        } else if (granularity == "word") {
            val words = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                if (isNext) {
                    if (nodeTextOffset < words.size) {
                        val wordStr = words[nodeTextOffset]
                        nodeTextOffset++
                        speak(wordStr)
                        playAudioBeepForEvent("focus")
                        return true
                    }
                } else {
                    if (nodeTextOffset > 0) {
                        nodeTextOffset--
                        val wordStr = words[nodeTextOffset]
                        speak(wordStr)
                        playAudioBeepForEvent("focus")
                        return true
                    }
                }
            }
        }

        return false
    }

    fun performFocusNext(): Boolean {
        val root = rootInActiveWindow ?: return false
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val granularity = Settings.GRANULARITIES.getOrElse(Settings.CURRENT_GRANULARITY_INDEX) { "default" }

        if (granularity == "word" || granularity == "character") {
            if (currentFocused != null) {
                val moved = performGranularityMovement(currentFocused, isNext = true, granularity = granularity)
                if (moved) {
                    currentFocused.recycle()
                    root.recycle()
                    return true
                }
            }
            nodeTextOffset = 0
            lastFocusedNodeBounds = null
        }

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, nodes)

        if (nodes.isEmpty()) {
            root.recycle()
            currentFocused?.recycle()
            return false
        }

        var targetIndex = 0
        if (currentFocused != null) {
            val currentBounds = android.graphics.Rect()
            currentFocused.getBoundsInScreen(currentBounds)
            for (i in nodes.indices) {
                val nodeBounds = android.graphics.Rect()
                nodes[i].getBoundsInScreen(nodeBounds)
                if (nodes[i] == currentFocused || (currentBounds == nodeBounds && !currentBounds.isEmpty)) {
                    targetIndex = i + 1
                    break
                }
            }
            currentFocused.recycle()
        }

        var success = false
        if (targetIndex < nodes.size) {
            for (i in targetIndex until nodes.size) {
                success = nodes[i].performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                if (success) {
                    playAudioBeepForEvent("focus")
                    break
                }
            }
            if (!success) {
                playAudioBeepForEvent("boundary")
                speak("End of screen")
            }
        } else {
            playAudioBeepForEvent("boundary")
            speak("End of screen")
        }

        nodes.forEach { it.recycle() }
        root.recycle()
        return success
    }

    fun performFocusPrevious(): Boolean {
        val root = rootInActiveWindow ?: return false
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val granularity = Settings.GRANULARITIES.getOrElse(Settings.CURRENT_GRANULARITY_INDEX) { "default" }

        if (granularity == "word" || granularity == "character") {
            if (currentFocused != null) {
                val moved = performGranularityMovement(currentFocused, isNext = false, granularity = granularity)
                if (moved) {
                    currentFocused.recycle()
                    root.recycle()
                    return true
                }
            }
            nodeTextOffset = Int.MAX_VALUE
            lastFocusedNodeBounds = null
        }

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, nodes)

        if (nodes.isEmpty()) {
            root.recycle()
            currentFocused?.recycle()
            return false
        }

        var targetIndex = nodes.size - 1
        if (currentFocused != null) {
            val currentBounds = android.graphics.Rect()
            currentFocused.getBoundsInScreen(currentBounds)
            for (i in nodes.indices) {
                val nodeBounds = android.graphics.Rect()
                nodes[i].getBoundsInScreen(nodeBounds)
                if (nodes[i] == currentFocused || (currentBounds == nodeBounds && !currentBounds.isEmpty)) {
                    targetIndex = i - 1
                    break
                }
            }
            currentFocused.recycle()
        }

        var success = false
        if (targetIndex >= 0 && targetIndex < nodes.size) {
            for (i in targetIndex downTo 0) {
                success = nodes[i].performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                if (success) {
                    playAudioBeepForEvent("focus")
                    break
                }
            }
            if (!success) {
                playAudioBeepForEvent("boundary")
                speak("Top of screen")
            }
        } else {
            playAudioBeepForEvent("boundary")
            speak("Top of screen")
        }

        nodes.forEach { it.recycle() }
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
        if (clicked) playAudioBeepForEvent("click")
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isScreenOn) return
        val eventCopy = AccessibilityEvent.obtain(event)
        eventExecutor.execute {
            try {
                eventHandler.processEvent(eventCopy)
            } catch (e: Exception) {
                Log.e(TAG, "Error in native onAccessibilityEvent", e)
            } finally {
                eventCopy.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service Interrupted")
        stopSpeech()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safe unregister — only if we actually registered (bug 21)
        if (screenStateReceiverRegistered) {
            try { unregisterReceiver(screenStateReceiver) } catch (e: Exception) {
                Log.e(TAG, "Error unregistering screen state receiver", e)
            }
        }
        try {
            setScreenCurtainEnabled(false)
            closeVisibleContextMenu() // Tear down context menu overlay to prevent window leak (Bug 4)
            val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        stopSpeech()
        tts?.shutdown()
        toneGenerator?.release()
        eventExecutor.shutdown()
        executorService.shutdown()
        audioExecutor.shutdown()
    }

    fun showVisibleContextMenu() {
        mainHandler.post {
            try {
                if (contextMenuView != null) return@post

                // Use the app's theme so Material components look correct
                val themeContext = android.view.ContextThemeWrapper(this, R.style.Theme_IndianScreenreader)
                val inflater = android.view.LayoutInflater.from(themeContext)
                contextMenuView = inflater.inflate(R.layout.layout_context_menu, null)

                val listView = contextMenuView?.findViewById<android.widget.ListView>(R.id.menuListView)
                val btnCancel = contextMenuView?.findViewById<android.widget.Button>(R.id.btnCancelMenu)

                val adapter = android.widget.ArrayAdapter(
                    themeContext,
                    android.R.layout.simple_list_item_1,
                    Settings.INDIAN_MENU_ITEMS
                )
                listView?.adapter = adapter

                listView?.setOnItemClickListener { _, _, position, _ ->
                    closeVisibleContextMenu {
                        executeIndianMenuSelection(position)
                    }
                }

                btnCancel?.setOnClickListener {
                    closeVisibleContextMenu()
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    // FLAG_NOT_TOUCH_MODAL: touches outside the card dismiss the menu
                    // FLAG_WATCH_OUTSIDE_TOUCH: we receive the outside touch event
                    // NOT using FLAG_NOT_FOCUSABLE alone as it blocks all touch routing
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                )
                
                windowManager?.addView(contextMenuView, params)
                speak("Indian Context Menu opened")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing Context Menu overlay", e)
            }
        }
    }

    private fun closeVisibleContextMenu(onClosed: (() -> Unit)? = null) {
        // Already on main thread from OnItemClickListener — do work directly, no double-post
        if (Looper.myLooper() == Looper.getMainLooper()) {
            contextMenuView?.let {
                try { windowManager?.removeView(it) } catch (e: Exception) { /* already removed */ }
                contextMenuView = null
                speak("Menu closed")
            }
            onClosed?.invoke()
        } else {
            mainHandler.post {
                contextMenuView?.let {
                    try { windowManager?.removeView(it) } catch (e: Exception) { /* already removed */ }
                    contextMenuView = null
                    speak("Menu closed")
                }
                onClosed?.invoke()
            }
        }
    }

    fun executeIndianMenuSelection(idx: Int) {
        when (idx) {
            0 -> aiSummarizeScreen()
            1 -> toggleAutoTranslate()
            2 -> {
                speak("Capturing screen for AI Vision description...")
                captureScreenForAI()
            }
            3 -> readDeviceStatus()
            4 -> toggleInputHelp()
            5 -> {
                val granularities = Settings.GRANULARITIES
                Settings.CURRENT_GRANULARITY_INDEX = (Settings.CURRENT_GRANULARITY_INDEX + 1) % granularities.size
                val currentName = granularities[Settings.CURRENT_GRANULARITY_INDEX].replaceFirstChar { it.uppercase() }
                speak("Granularity: $currentName")
            }
            6 -> togglePunctuationVerbosity()
            7 -> toggleScreenCurtain()
            8 -> readFromHere()
            9 -> readFromTop()
            10 -> startVoiceCommand()
            11 -> aiSimplifyScreen()
            12 -> speak("Menu closed")
        }
    }

    // AI & Utility Actions 
    
    fun readDeviceStatus() {
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val batteryInfo = if (batteryPct >= 0) "Battery $batteryPct percent" else ""
        speak("$timeStr, $batteryInfo".trim(' ', ','))
    }

    fun toggleAutoTranslate() {
        val newState = !Settings.AUTO_TRANSLATE_ENABLED
        Settings.AUTO_TRANSLATE_ENABLED = newState
        val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("AUTO_TRANSLATE_ENABLED", newState).apply()
        val state = if (newState) "Enabled" else "Disabled"
        speak("AI Translation $state")
    }

    fun toggleInputHelp() {
        val newState = !Settings.INPUT_HELP_MODE
        Settings.INPUT_HELP_MODE = newState
        val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("INPUT_HELP_MODE", newState).apply()
        val state = if (newState) "Enabled" else "Disabled"
        speak("Input Help Practice Mode $state")
    }

    fun togglePunctuationVerbosity() {
        val options = listOf("none", "some", "all")
        val idx = options.indexOf(Settings.PUNCTUATION_VERBOSITY)
        val nextIdx = (idx + 1) % options.size
        val newVerbosity = options[nextIdx]
        Settings.PUNCTUATION_VERBOSITY = newVerbosity
        val prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("PUNCTUATION_VERBOSITY", newVerbosity).apply()
        speak("Punctuation verbosity set to $newVerbosity")
    }

    fun startVoiceCommand() {
        speak("Voice commands not fully implemented natively yet. Please use menu.")
    }

    fun readFromTop() {
        val root = rootInActiveWindow ?: return
        readingNodes.forEach { it.recycle() }
        readingNodes.clear()
        collectFocusableNodes(root, readingNodes)
        root.recycle()
        
        if (readingNodes.isNotEmpty()) {
            Settings.CONTINUOUS_READING_ACTIVE = true
            readingIndex = 0
            speak("Starting continuous reading")
            readNextNode()
        }
    }

    fun readFromHere() {
        val root = rootInActiveWindow ?: return
        val currentFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        
        readingNodes.forEach { it.recycle() }
        readingNodes.clear()
        collectFocusableNodes(root, readingNodes)
        
        readingIndex = 0
        if (currentFocused != null) {
            val currentBounds = android.graphics.Rect()
            currentFocused.getBoundsInScreen(currentBounds)
            for (i in readingNodes.indices) {
                val nodeBounds = android.graphics.Rect()
                readingNodes[i].getBoundsInScreen(nodeBounds)
                if (readingNodes[i] == currentFocused || (currentBounds == nodeBounds && !currentBounds.isEmpty)) {
                    readingIndex = i
                    break
                }
            }
            currentFocused.recycle()
        }
        root.recycle()
        
        if (readingNodes.isNotEmpty()) {
            Settings.CONTINUOUS_READING_ACTIVE = true
            speak("Starting continuous reading from here")
            readNextNode()
        }
    }

    private fun readNextNode() {
        if (!Settings.CONTINUOUS_READING_ACTIVE) return
        if (readingIndex < readingNodes.size) {
            val node = readingNodes[readingIndex]
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            val text = NodeParser.formatNodeSpeech(node)
            
            readingIndex++
            
            if (text.isNotBlank()) {
                speak(text, flush = true, isContinuous = true)
            } else {
                // If this node had no text, immediately skip to the next one
                mainHandler.post { readNextNode() }
            }
        } else {
            Settings.CONTINUOUS_READING_ACTIVE = false
            speak("Finished reading screen")
            clearReadingNodes()
        }
    }

    private fun getFullScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, nodes)
        val textList = nodes.mapNotNull { NodeParser.getNodeRawText(it).takeIf { it.isNotBlank() } }
        nodes.forEach { it.recycle() }
        root.recycle()
        return textList.joinToString("\n")
    }

    fun aiSummarizeScreen() {
        val text = getFullScreenText()
        if (text.isEmpty()) {
            speak("No text found to summarize.")
            return
        }
        speak("Summarizing screen...")
        AiService.summarizeScreenAsync(text, { summary -> 
            speak(summary) 
        }, { err -> 
            speak(err) 
        })
    }

    fun aiSimplifyScreen() {
        val text = getFullScreenText()
        if (text.isEmpty()) {
            speak("No text found to simplify.")
            return
        }
        speak("Simplifying screen...")
        AiService.rewriteSimplifiedAsync(text, { simplified -> 
            speak(simplified) 
        }, { err -> 
            speak(err) 
        })
    }

    fun captureScreenForAI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hwBuffer = screenshot.hardwareBuffer
                        executorService.execute {
                            try {
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                                if (bitmap != null) {
                                    try {
                                        val outputStream = java.io.ByteArrayOutputStream()
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                                        val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
                                        
                                        AiService.describeImageB64Async(base64, { desc ->
                                            speak(desc)
                                        }, { err ->
                                            speak(err)
                                        })
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } else {
                                    speak("Failed to process screenshot")
                                }
                            } finally {
                                hwBuffer.close()
                            }
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
            speak("Screen capture requires Android 12 or higher")
        }
    }
}
