package com.indian.screenreader.core

import android.view.accessibility.AccessibilityEvent
import com.indian.screenreader.IndianScreenReaderService

class EventHandler(private val service: IndianScreenReaderService) {

    private var lastFocusEventTimeMs = 0L
    private var lastSpokenTimeMs = 0L
    private var lastSpokenText = ""

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

    private fun openIndianMenu() {
        Settings.INDIAN_MENU_OPEN = true
        Settings.INDIAN_MENU_SELECTED_INDEX = 0
        val currentItem = Settings.INDIAN_MENU_ITEMS[0]
        service.speak("Indian Menu opened. $currentItem. Swipe right for next item, double tap to select.")
    }

    private fun closeIndianMenu() {
        Settings.INDIAN_MENU_OPEN = false
        service.speak("Indian Menu closed.")
    }

    private fun navigateIndianMenu(direction: Int) {
        val totalItems = Settings.INDIAN_MENU_ITEMS.size
        Settings.INDIAN_MENU_SELECTED_INDEX = if (direction > 0) {
            (Settings.INDIAN_MENU_SELECTED_INDEX + 1) % totalItems
        } else {
            val next = Settings.INDIAN_MENU_SELECTED_INDEX - 1
            if (next < 0) totalItems - 1 else next
        }

        val itemText = Settings.INDIAN_MENU_ITEMS[Settings.INDIAN_MENU_SELECTED_INDEX]
        service.speak(itemText)
    }

    private fun executeIndianMenuSelection() {
        val idx = Settings.INDIAN_MENU_SELECTED_INDEX
        Settings.INDIAN_MENU_OPEN = false

        when (idx) {
            0 -> service.aiSummarizeScreen()
            1 -> {
                Settings.AUTO_TRANSLATE_ENABLED = !Settings.AUTO_TRANSLATE_ENABLED
                val state = if (Settings.AUTO_TRANSLATE_ENABLED) "Enabled" else "Disabled"
                service.speak("AI Translation $state")
            }
            2 -> {
                service.speak("Capturing screen for AI Vision description...")
                service.captureScreenForAI()
            }
            3 -> service.readDeviceStatus()
            4 -> service.toggleInputHelp()
            5 -> cycleGranularity(1)
            6 -> service.togglePunctuationVerbosity()
            7 -> service.toggleScreenCurtain()
            8 -> service.readFromHere()
            9 -> service.readFromTop()
            10 -> service.startVoiceCommand()
            11 -> service.aiSimplifyScreen()
            12 -> service.speak("Indian Menu closed.")
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
            1 -> "Focus Next"
            2 -> "Focus Previous"
            3 -> "Granularity Up"
            4 -> "Granularity Down"
            9 -> "Open Indian Context Menu"
            10 -> "Read From Top"
            11 -> "Read From Here"
            12 -> "Voice Command Mode"
            17 -> "Click / Activate Element"
            18 -> "Long Click Element"
            else -> "Custom Action $gestureId"
        }
    }

    fun handleGesture(gestureId: Int): Boolean {
        if (Settings.INDIAN_MENU_OPEN) {
            return when (gestureId) {
                1 -> { navigateIndianMenu(1); true }
                2 -> { navigateIndianMenu(-1); true }
                17, 18 -> { executeIndianMenuSelection(); true }
                else -> false
            }
        }

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
            "open_indian_menu" -> { openIndianMenu(); return true }
            "toggle_screen_curtain" -> { service.toggleScreenCurtain(); return true }
            "focus_next" -> return service.performFocusNext()
            "focus_prev" -> return service.performFocusPrevious()
            "granularity_up" -> { cycleGranularity(1); return true }
            "granularity_down" -> { cycleGranularity(-1); return true }
            "click" -> return service.performNodeClick()
            "long_click" -> return service.performNodeLongClick()
            "read_from_top" -> { service.readFromTop(); return true }
            "read_from_here" -> { service.readFromHere(); return true }
            "voice_command" -> { service.startVoiceCommand(); return true }
        }

        // Fallback to standard
        return when (gestureId) {
            1 -> service.performFocusNext()
            2 -> service.performFocusPrevious()
            3 -> { cycleGranularity(1); true }
            4 -> { cycleGranularity(-1); true }
            9 -> { openIndianMenu(); true }
            else -> false
        }
    }
}
