package com.indian.screenreader.core

import android.content.SharedPreferences
import android.media.ToneGenerator

object Settings {
    // TalkBack Verbosity & Behavior
    var SPEECH_RATE = 1.0f
    var SPEECH_PITCH = 1.0f
    var VERBOSITY_LEVEL = "medium" // "high", "medium", "low"
    var ANNOUNCE_ELEMENT_TYPES = true
    var ANNOUNCE_ELEMENT_STATE = true
    var ANNOUNCE_WINDOW_CHANGES = true
    var DEDUPLICATE_SPEECH = true

    // Audio & Haptics
    var AUDIO_FEEDBACK_VOLUME = 0.6f
    var HAPTIC_FEEDBACK_ENABLED = true
    var HAPTIC_VIBRATION_MS = 30L

    // Privacy Features
    var SCREEN_CURTAIN_ENABLED = false

    // NVDA Features
    var INPUT_HELP_MODE = false
    var PUNCTUATION_VERBOSITY = "all" // "all", "some", "none"
    var CAPITALIZATION_ANNOUNCEMENT = "prefix" // "prefix", "pitch", "none"

    // Node Parser Flags
    var IGNORE_DECORATIVE_IMAGES = true
    var ANNOUNCE_GRID_POSITION = true
    var ANNOUNCE_LIST_COUNT = true
    var ANNOUNCE_VIEW_IDS = false
    var ANNOUNCE_NOTIFICATIONS = true
    var TTS_LOCALE = "default"

    val INDIAN_LANGUAGES = listOf(
        "default" to "Default System Voice",
        "hi_IN" to "Hindi (हिंदी)",
        "bn_IN" to "Bengali (বাংলা)",
        "ta_IN" to "Tamil (தமிழ்)",
        "te_IN" to "Telugu (తెలుగు)",
        "mr_IN" to "Marathi (मराठी)",
        "kn_IN" to "Kannada (ಕನ್ನಡ)",
        "ml_IN" to "Malayalam (മലയാളം)",
        "gu_IN" to "Gujarati (ગુજરાતી)",
        "pa_IN" to "Punjabi (ਪੰਜਾਬੀ)"
    )

    // Indian Menu State (visual overlay — no state machine needed)
    val INDIAN_MENU_ITEMS = listOf(
        "1. AI Screen Summary",
        "2. AI Language Translation",
        "3. AI Image Description",
        "4. Read Device Status (Time & Battery)",
        "5. Toggle Input Help Practice Mode",
        "6. Cycle Navigation Granularity",
        "7. Toggle Punctuation Verbosity",
        "8. Toggle Screen Curtain (Privacy)",
        "9. Read From Here (Continuous Reading)",
        "10. Read From Top",
        "11. Voice Command Mode",
        "12. AI Simplify Screen",
        "13. AI OCR (Extract Text in Image)",
        "14. Find Text on Screen",
        "15. Emergency SOS Alert",
        "16. Close Indian Menu"
    )

    // Gemini
    var GEMINI_API_KEY = ""
    var AUTO_TRANSLATE_ENABLED = false
    var TRANSLATION_TARGET_LANGUAGE = "Hindi"
    var SIMPLIFY_TEXT_ENABLED = false

    // Granularities
    val GRANULARITIES = listOf("default", "control", "heading", "word", "character")
    var CURRENT_GRANULARITY_INDEX = 0

    // Feature 1: Pronunciation Dictionary (ConcurrentHashMap for thread-safe custom additions)
    val PRONUNCIATION_DICT: java.util.concurrent.ConcurrentHashMap<String, String> = java.util.concurrent.ConcurrentHashMap(mapOf(
        "irctc" to "Indian Railways Ticket Booking",
        "upi" to "Unified Payments Interface",
        "bsnl" to "Bharat Sanchar Nigam Limited",
        "isro" to "Indian Space Research Organisation",
        "neft" to "National Electronic Funds Transfer",
        "rtgs" to "Real Time Gross Settlement",
        "imps" to "Immediate Payment Service",
        "pan" to "Permanent Account Number",
        "aadhaar" to "Aadhaar",
        "epf" to "Employees Provident Fund",
        "gst" to "Goods and Services Tax",
        "emi" to "Equated Monthly Instalment",
        "atm" to "Automated Teller Machine",
        "otp" to "One Time Password",
        "btn" to "Button",
        "img" to "Image",
        "ok" to "OK",
        "kb" to "kilobytes",
        "mb" to "megabytes",
        "gb" to "gigabytes"
    ))

    // Feature 2: Continuous Reading
    @Volatile var CONTINUOUS_READING_ACTIVE = false

    // Feature 3: Sound Theme
    var SOUND_THEME = "classic"
    val SOUND_THEME_MAP = mapOf(
        "classic" to mapOf(
            "focus" to ToneGenerator.TONE_PROP_BEEP,
            "click" to ToneGenerator.TONE_PROP_ACK,
            "boundary" to ToneGenerator.TONE_PROP_BEEP2,
            "window" to ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,
            "menu_open" to ToneGenerator.TONE_PROP_ACK
        ),
        "modern" to mapOf(
            "focus" to ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,
            "click" to ToneGenerator.TONE_PROP_PROMPT,
            "boundary" to ToneGenerator.TONE_CDMA_ABBR_INTERCEPT,
            "window" to ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,
            "menu_open" to ToneGenerator.TONE_PROP_PROMPT
        ),
        "minimal" to mapOf(
            "focus" to ToneGenerator.TONE_PROP_BEEP,
            "click" to ToneGenerator.TONE_PROP_BEEP,
            "boundary" to ToneGenerator.TONE_PROP_BEEP,
            "window" to ToneGenerator.TONE_PROP_BEEP,
            "menu_open" to ToneGenerator.TONE_PROP_BEEP
        ),
        "chime" to mapOf(
            "focus" to ToneGenerator.TONE_CDMA_NETWORK_BUSY,
            "click" to ToneGenerator.TONE_PROP_ACK,
            "boundary" to ToneGenerator.TONE_PROP_BEEP2,
            "window" to ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE,
            "menu_open" to ToneGenerator.TONE_PROP_ACK
        ),
        "windows" to mapOf(
            "focus" to 22,
            "click" to 24,
            "boundary" to 28,
            "window" to 26,
            "menu_open" to 24
        )
    )

    // Feature 4: Per-App Profiles
    var CURRENT_APP_PACKAGE = ""
    val APP_PROFILES = mapOf(
        "com.whatsapp" to mapOf(
            "speech_rate" to 1.3f,
            "verbosity_level" to "low",
            "auto_translate" to false
        ),
        "com.google.android.youtube" to mapOf(
            "speech_rate" to 1.1f,
            "verbosity_level" to "low",
            "auto_translate" to false
        ),
        "com.android.chrome" to mapOf(
            "speech_rate" to 0.9f,
            "verbosity_level" to "high",
            "auto_translate" to true
        ),
        "com.amazon.kindle" to mapOf(
            "speech_rate" to 0.8f,
            "verbosity_level" to "high",
            "auto_translate" to false
        ),
        "com.google.android.apps.maps" to mapOf(
            "speech_rate" to 1.0f,
            "verbosity_level" to "medium",
            "auto_translate" to false
        )
    )

    // Gesture Remapping Dictionary — ConcurrentHashMap for thread-safety across UI and event threads
    var GESTURE_MAP: java.util.concurrent.ConcurrentHashMap<Int, String> = java.util.concurrent.ConcurrentHashMap(mapOf(
        4 to "focus_next",           // SWIPE_RIGHT
        3 to "focus_prev",           // SWIPE_LEFT
        1 to "granularity_up",       // SWIPE_UP
        2 to "granularity_down",     // SWIPE_DOWN
        14 to "open_indian_menu",    // SWIPE_UP_AND_RIGHT (Indian Context Menu)
        16 to "read_from_here",      // SWIPE_DOWN_AND_RIGHT
        13 to "global_home",         // SWIPE_UP_AND_LEFT (Home)
        15 to "global_back",         // SWIPE_DOWN_AND_LEFT (Back)
        17 to "click",               // DOUBLE_TAP
        18 to "long_click"           // DOUBLE_TAP_AND_HOLD
    ))

    var EMERGENCY_CONTACT_NUMBER = ""

    val GESTURE_NAMES = mapOf(
        4 to "Swipe Right",
        3 to "Swipe Left",
        1 to "Swipe Up",
        2 to "Swipe Down",
        14 to "Swipe Up & Right",
        16 to "Swipe Down & Right",
        13 to "Swipe Up & Left",
        15 to "Swipe Down & Left",
        17 to "Double Tap",
        18 to "Double Tap & Hold"
    )

    val GESTURE_ACTIONS = listOf(
        "focus_next" to "Next Focus Item",
        "focus_prev" to "Previous Focus Item",
        "granularity_up" to "Granularity Up",
        "granularity_down" to "Granularity Down",
        "open_indian_menu" to "Open Indian Context Menu",
        "read_from_here" to "Read From Here",
        "global_home" to "Home System Action",
        "global_back" to "Back System Action",
        "click" to "Click Item",
        "long_click" to "Long Click Item"
    )

    fun saveGestureMap(prefs: SharedPreferences) {
        val editor = prefs.edit()
        GESTURE_MAP.forEach { (gestureId, action) ->
            editor.putString("GESTURE_ACTION_$gestureId", action)
        }
        editor.apply()
    }

    fun loadGestureMap(prefs: SharedPreferences) {
        GESTURE_MAP.keys.toList().forEach { gestureId ->
            val savedAction = prefs.getString("GESTURE_ACTION_$gestureId", null)
            if (savedAction != null) {
                GESTURE_MAP[gestureId] = savedAction
            }
        }
    }

    fun savePronunciationDict(prefs: SharedPreferences) {
        val editor = prefs.edit()
        val jsonObj = org.json.JSONObject()
        PRONUNCIATION_DICT.forEach { (k, v) -> jsonObj.put(k, v) }
        editor.putString("CUSTOM_PRONUNCIATION_JSON", jsonObj.toString())
        editor.apply()
    }

    fun loadPronunciationDict(prefs: SharedPreferences) {
        val jsonStr = prefs.getString("CUSTOM_PRONUNCIATION_JSON", null) ?: return
        try {
            val jsonObj = org.json.JSONObject(jsonStr)
            jsonObj.keys().forEach { key ->
                PRONUNCIATION_DICT[key.lowercase()] = jsonObj.getString(key)
            }
        } catch (e: Exception) {
            // Keep default dictionary entries
        }
    }

    fun initFromAndroid(prefs: SharedPreferences) {
        GEMINI_API_KEY = prefs.getString("GEMINI_API_KEY", "") ?: ""
        AUTO_TRANSLATE_ENABLED = prefs.getBoolean("AUTO_TRANSLATE_ENABLED", false)
        SCREEN_CURTAIN_ENABLED = prefs.getBoolean("SCREEN_CURTAIN_ENABLED", false)
        INPUT_HELP_MODE = prefs.getBoolean("INPUT_HELP_MODE", false)
        DEDUPLICATE_SPEECH = prefs.getBoolean("DEDUPLICATE_SPEECH", true)
        SPEECH_RATE = prefs.getFloat("SPEECH_RATE", 1.0f)
        SPEECH_PITCH = prefs.getFloat("SPEECH_PITCH", 1.0f)
        HAPTIC_FEEDBACK_ENABLED = prefs.getBoolean("HAPTIC_FEEDBACK_ENABLED", true)
        PUNCTUATION_VERBOSITY = prefs.getString("PUNCTUATION_VERBOSITY", "all") ?: "all"
        TRANSLATION_TARGET_LANGUAGE = prefs.getString("TRANSLATION_TARGET_LANGUAGE", "Hindi") ?: "Hindi"
        TTS_LOCALE = prefs.getString("TTS_LOCALE", "default") ?: "default"
        EMERGENCY_CONTACT_NUMBER = prefs.getString("EMERGENCY_CONTACT_NUMBER", "") ?: ""
        loadGestureMap(prefs)
        loadPronunciationDict(prefs)
    }
}
