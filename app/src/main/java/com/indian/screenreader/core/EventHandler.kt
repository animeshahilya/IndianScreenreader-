package com.indian.screenreader.core

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import com.indian.screenreader.IndianScreenReaderService

class EventHandler(private val service: IndianScreenReaderService) {

    private var lastFocusEventTimeMs = 0L
    private var lastSpokenTimeMs = 0L
    private var lastSpokenText = ""
    private var lastNotificationTimeMs = 0L
    private var lastNotificationText = ""

    companion object {
        private const val EVENT_THROTTLE_MS = 40L
    }

    private fun isFocusThrottled(): Boolean {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastFocusEventTimeMs < EVENT_THROTTLE_MS) {
            return true
        }
        lastFocusEventTimeMs = nowMs
        return false
    }

    fun processEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        val source = event.source ?: return

        try {
            // TYPE_VIEW_FOCUSED (8), TYPE_VIEW_HOVER_ENTER (128), TYPE_VIEW_ACCESSIBILITY_FOCUSED (32768)
            if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER ||
                eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                
                // If user physically touches the screen (touch exploration hover event), cancel continuous reading
                if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
                    if (Settings.CONTINUOUS_READING_ACTIVE) {
                        Settings.CONTINUOUS_READING_ACTIVE = false
                        service.stopSpeech()
                    }
                }

                if (isFocusThrottled()) return

                val spokenText = NodeParser.formatNodeSpeech(source)

                if (Settings.DEDUPLICATE_SPEECH && spokenText == lastSpokenText) {
                    if (System.currentTimeMillis() - lastSpokenTimeMs < 500) {
                        return
                    }
                }

                if (spokenText.isNotBlank()) {
                    lastSpokenText = spokenText
                    lastSpokenTimeMs = System.currentTimeMillis()
                    
                    if (Settings.AUTO_TRANSLATE_ENABLED) {
                        AiService.translateTextAsync(spokenText, Settings.TRANSLATION_TARGET_LANGUAGE) { translated ->
                            service.speak(translated)
                        }
                    } else {
                        service.speak(spokenText)
                    }
                }
            } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (Settings.ANNOUNCE_WINDOW_CHANGES) {
                    val pkg = event.packageName?.toString() ?: ""
                    
                    if (pkg.isNotEmpty() && pkg != Settings.CURRENT_APP_PACKAGE) {
                        Settings.CURRENT_APP_PACKAGE = pkg
                        
                        // Apply App Profiles dynamically
                        val profile = Settings.APP_PROFILES[pkg]
                        if (profile != null) {
                            (profile["speech_rate"] as? Float)?.let { service.setSpeechRate(it) }
                            (profile["auto_translate"] as? Boolean)?.let { Settings.AUTO_TRANSLATE_ENABLED = it }
                        } else {
                            service.setSpeechRate(Settings.SPEECH_RATE)
                            Settings.AUTO_TRANSLATE_ENABLED = false
                        }
                    }

                    val windowText = NodeParser.getNodeRawText(source)
                    if (windowText.isNotBlank() && windowText != lastSpokenText) {
                        lastSpokenText = windowText
                        service.speak("Window: $windowText")
                    }
                }
            } else if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                if (Settings.ANNOUNCE_NOTIFICATIONS) {
                    val notifText = event.text.joinToString(", ").trim()
                    val now = System.currentTimeMillis()
                    if (notifText.isNotBlank() && (notifText != lastNotificationText || now - lastNotificationTimeMs > 3000)) {
                        lastNotificationTimeMs = now
                        lastNotificationText = notifText
                        service.speak("Notification: $notifText", flush = false)
                    }
                }
            } else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
                val text = event.text.joinToString("")
                if (text.isNotBlank()) {
                    service.speak(NodeParser.formatCharacterSpeech(text))
                }
            } else if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                try {
                    var scrollY = event.scrollY
                    var maxScrollY = event.maxScrollY
                    
                    if (maxScrollY <= 0) {
                        scrollY = event.scrollX
                        maxScrollY = event.maxScrollX
                    }

                    if (maxScrollY > 0) {
                        val percentage = scrollY.toFloat() / maxScrollY.toFloat()
                        service.playDynamicScrollBeep(percentage)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } finally {
            source.recycle()
        }
    }

    private fun cycleGranularity(step: Int) {
        val granularities = Settings.GRANULARITIES
        Settings.CURRENT_GRANULARITY_INDEX = (Settings.CURRENT_GRANULARITY_INDEX + step + granularities.size) % granularities.size
        val currentName = granularities[Settings.CURRENT_GRANULARITY_INDEX].replaceFirstChar { it.uppercase() }
        service.speak("Granularity: $currentName")
    }

    private fun getGestureDescription(gestureId: Int): String {
        return when (gestureId) {
            4 -> "Focus Next"
            3 -> "Focus Previous"
            1 -> "Granularity Up"
            2 -> "Granularity Down"
            14 -> "Open Indian Context Menu"
            13 -> "Home"
            16 -> "Read From Here"
            15 -> "Back"
            17 -> "Click / Activate Element"
            18 -> "Long Click Element"
            19 -> "2-Finger Single Tap"
            20 -> "2-Finger Double Tap"
            21 -> "2-Finger Triple Tap"
            22 -> "3-Finger Swipe Right"
            23 -> "3-Finger Swipe Left"
            24 -> "3-Finger Swipe Up"
            25 -> "3-Finger Swipe Down"
            else -> "Gesture $gestureId"
        }
    }

    fun handleGesture(gestureId: Int): Boolean {
        if (Settings.CONTINUOUS_READING_ACTIVE) {
            Settings.CONTINUOUS_READING_ACTIVE = false
            service.speak("Stopped reading")
            return true
        }

        if (Settings.INPUT_HELP_MODE) {
            val actionDesc = getGestureDescription(gestureId)
            service.speak("Input Help: Gesture $gestureId performs $actionDesc")
            return true
        }

        val actionName = Settings.GESTURE_MAP[gestureId] ?: ""

        when (actionName) {
            "open_indian_menu" -> { service.showVisibleContextMenu(); return true }
            "toggle_screen_curtain" -> { service.toggleScreenCurtain(); return true }
            "focus_next" -> return service.performFocusNext()
            "focus_prev" -> return service.performFocusPrevious()
            "granularity_up" -> { cycleGranularity(1); return true }
            "granularity_down" -> { cycleGranularity(-1); return true }
            "click" -> return service.performNodeClick()
            "long_click" -> return service.performNodeLongClick()
            "read_from_here" -> { service.readFromHere(); return true }
            "read_from_top" -> { service.readFromTop(); return true }
            "ai_summary" -> { service.aiSummarizeScreen(); return true }
            "global_home" -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); return true }
            "global_back" -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); return true }
        }

        // Fallback to standard
        return when (gestureId) {
            4 -> service.performFocusNext()
            3 -> service.performFocusPrevious()
            1 -> { cycleGranularity(1); true }
            2 -> { cycleGranularity(-1); true }
            14 -> { service.showVisibleContextMenu(); true }
            13 -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); true }
            15 -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); true }
            else -> false
        }
    }
}
