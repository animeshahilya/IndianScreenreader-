# Centralized Settings for Indian Screenreader
# All feature flags, maps, and runtime state in one place.

class ScreenReaderSettings:
    def __init__(self):
        # TalkBack Verbosity & Behavior
        self.SPEECH_RATE = 1.0
        self.SPEECH_PITCH = 1.0
        self.VERBOSITY_LEVEL = "medium"  # "high", "medium", "low"
        self.ANNOUNCE_ELEMENT_TYPES = True
        self.ANNOUNCE_ELEMENT_STATE = True
        self.ANNOUNCE_WINDOW_CHANGES = True
        self.DEDUPLICATE_SPEECH = True

        # Audio & Haptics
        self.AUDIO_FEEDBACK_VOLUME = 0.6  # 0.0 to 1.0
        self.HAPTIC_FEEDBACK_ENABLED = True
        self.HAPTIC_VIBRATION_MS = 30

        # Privacy Features
        self.SCREEN_CURTAIN_ENABLED = False  # Screen Curtain (blacks out screen display for privacy)

        # NVDA Features
        self.INPUT_HELP_MODE = False  # Practice mode: speaks gesture name instead of executing
        self.PUNCTUATION_VERBOSITY = "all"  # "all", "some", "none"
        
        # Node Parser Flags
        self.IGNORE_DECORATIVE_IMAGES = True
        self.ANNOUNCE_GRID_POSITION = True
        self.ANNOUNCE_LIST_COUNT = True
        self.ANNOUNCE_VIEW_IDS = False

    def init_from_android(self, prefs):
        """Loads persistent user settings from Android SharedPreferences."""
        self.CAPITALIZATION_ANNOUNCEMENT = "prefix"  # "prefix" ("Cap A"), "pitch", "none"

        # Indian Menu State
        self.INDIAN_MENU_OPEN = False
        self.INDIAN_MENU_SELECTED_INDEX = 0
        self.INDIAN_MENU_ITEMS = [
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
            "13. Close Indian Menu"
        ]

        # Google AI Studio Gemini Integration (defaults)
        self.GEMINI_API_KEY = ""
        self.AUTO_TRANSLATE_ENABLED = False
        self.TRANSLATION_TARGET_LANGUAGE = "Hindi"
        self.SIMPLIFY_TEXT_ENABLED = False

        # Now load from SharedPreferences — these OVERRIDE the defaults above
        try:
            api_key = prefs.getString("GEMINI_API_KEY", "")
            if api_key:
                self.GEMINI_API_KEY = str(api_key)

            self.AUTO_TRANSLATE_ENABLED = bool(prefs.getBoolean("AUTO_TRANSLATE_ENABLED", False))
            self.SCREEN_CURTAIN_ENABLED = bool(prefs.getBoolean("SCREEN_CURTAIN_ENABLED", False))
            self.INPUT_HELP_MODE = bool(prefs.getBoolean("INPUT_HELP_MODE", False))
            self.DEDUPLICATE_SPEECH = bool(prefs.getBoolean("DEDUPLICATE_SPEECH", True))
        except Exception as e:
            print(f"Error loading SharedPreferences in Python: {e}")

        # Granularities
        self.GRANULARITIES = ["default", "control", "heading", "word", "character"]
        self.CURRENT_GRANULARITY_INDEX = 0

        # Customizations
        self.ANNOUNCE_GRID_POSITION = True
        self.ANNOUNCE_LIST_COUNT = True
        self.ANNOUNCE_VIEW_IDS = False
        self.IGNORE_DECORATIVE_IMAGES = True

        # --- FEATURE 1: Custom Pronunciation Dictionary ---
        # Maps exact string -> spoken replacement (case-insensitive key lookup done at read time)
        self.PRONUNCIATION_DICT = {
            "irctc": "Indian Railways Ticket Booking",
            "upi": "Unified Payments Interface",
            "bsnl": "Bharat Sanchar Nigam Limited",
            "isro": "Indian Space Research Organisation",
            "neft": "National Electronic Funds Transfer",
            "rtgs": "Real Time Gross Settlement",
            "imps": "Immediate Payment Service",
            "pan": "Permanent Account Number",
            "aadhaar": "Aadhaar",
            "epf": "Employees Provident Fund",
            "gst": "Goods and Services Tax",
            "emi": "Equated Monthly Instalment",
            "atm": "Automated Teller Machine",
            "otp": "One Time Password",
            "btn": "Button",
            "img": "Image",
            "ok": "OK",
            "kb": "kilobytes",
            "mb": "megabytes",
            "gb": "gigabytes",
        }

        # --- FEATURE 2: Continuous Reading Mode ---
        self.CONTINUOUS_READING_ACTIVE = False  # True = auto-read all nodes sequentially
        self.CONTINUOUS_READING_NODES = []       # Snapshot of nodes to read
        self.CONTINUOUS_READING_INDEX = 0

        # --- FEATURE 3: Sound Theme ---
        # Options: "classic", "modern", "minimal", "chime"
        self.SOUND_THEME = "classic"
        # Maps event -> tone type integer
        self.SOUND_THEME_MAP = {
            "classic": {
                "focus":    80,   # TONE_PROP_BEEP
                "click":    88,   # TONE_PROP_ACK
                "boundary": 81,   # TONE_PROP_BEEP2
                "window":   27,   # TONE_CDMA_SOFT_ERROR_LITE
                "menu_open": 88,  # TONE_PROP_ACK
            },
            "modern": {
                "focus":    27,   # TONE_CDMA_SOFT_ERROR_LITE
                "click":    79,   # TONE_PROP_PROMPT
                "boundary": 26,   # TONE_CDMA_ABBR_INTERCEPT
                "window":   27,
                "menu_open": 79,
            },
            "minimal": {
                "focus":    80,
                "click":    80,
                "boundary": 80,
                "window":   80,
                "menu_open": 80,
            },
            "chime": {
                "focus":    67,   # TONE_CDMA_NETWORK_BUSY
                "click":    88,
                "boundary": 81,
                "window":   27,
                "menu_open": 88,
            },
            "windows": {
                "focus":    22,   
                "click":    24,   
                "boundary": 28,   
                "window":   26,
                "menu_open": 24,
            },
        }

        # --- FEATURE 4: Per-App Accessibility Profiles ---
        self.CURRENT_APP_PACKAGE = ""
        # Maps package-name prefix -> dict of overrides
        self.APP_PROFILES = {
            "com.whatsapp": {
                "speech_rate": 1.3,
                "verbosity_level": "low",
                "auto_translate": False,
            },
            "com.google.android.youtube": {
                "speech_rate": 1.1,
                "verbosity_level": "low",
                "auto_translate": False,
            },
            "com.android.chrome": {
                "speech_rate": 0.9,
                "verbosity_level": "high",
                "auto_translate": True,
            },
            "com.amazon.kindle": {
                "speech_rate": 0.8,
                "verbosity_level": "high",
                "auto_translate": False,
            },
            "com.google.android.apps.maps": {
                "speech_rate": 1.0,
                "verbosity_level": "medium",
                "auto_translate": False,
            },
        }
        self.CURRENT_APP_PACKAGE = ""  # Updated dynamically on window change

        # --- FEATURE 5: Voice Command Mode ---
        self.VOICE_COMMAND_ACTIVE = False
        self.VOICE_COMMANDS = {
            "summarize screen": "ai_summarize",
            "screen summary": "ai_summarize",
            "screen curtain on": "curtain_on",
            "screen curtain off": "curtain_off",
            "toggle screen curtain": "curtain_toggle",
            "read status": "read_status",
            "go back": "global_back",
            "go home": "global_home",
            "open recents": "global_recents",
            "open notifications": "global_notifications",
            "input help on": "input_help_on",
            "input help off": "input_help_off",
            "open menu": "open_indian_menu",
            "close menu": "close_indian_menu",
            "read from top": "read_from_top",
            "read from here": "read_from_here",
            "stop reading": "stop_reading",
            "translate to hindi": "translate_hi",
            "translate to english": "translate_en",
            "translate to tamil": "translate_ta",
            "translate to telugu": "translate_te",
        }

        # Gesture Remapping Dictionary
        # Android AccessibilityService gesture IDs (API 36):
        # GESTURE_SWIPE_RIGHT=1, GESTURE_SWIPE_LEFT=2, GESTURE_SWIPE_UP=3, GESTURE_SWIPE_DOWN=4
        # GESTURE_SWIPE_RIGHT_AND_UP=5, GESTURE_SWIPE_RIGHT_AND_DOWN=6
        # GESTURE_SWIPE_LEFT_AND_UP=7, GESTURE_SWIPE_LEFT_AND_DOWN=8
        # GESTURE_SWIPE_UP_AND_RIGHT=9, GESTURE_SWIPE_UP_AND_LEFT=10
        # GESTURE_SWIPE_DOWN_AND_RIGHT=11, GESTURE_SWIPE_DOWN_AND_LEFT=12
        # GESTURE_DOUBLE_TAP=17, GESTURE_DOUBLE_TAP_AND_HOLD=18
        self.GESTURE_MAP = {
            1:  "focus_next",           # SWIPE_RIGHT
            2:  "focus_prev",           # SWIPE_LEFT
            3:  "granularity_up",       # SWIPE_UP
            4:  "granularity_down",     # SWIPE_DOWN
            9:  "open_indian_menu",     # SWIPE_UP_AND_RIGHT (Indian Context Menu)
            10: "read_from_top",        # SWIPE_UP_AND_LEFT
            11: "read_from_here",       # SWIPE_DOWN_AND_RIGHT
            12: "voice_command",        # SWIPE_DOWN_AND_LEFT -> Voice Command toggle
            17: "click",               # DOUBLE_TAP
            18: "long_click",          # DOUBLE_TAP_AND_HOLD
        }


# Singleton active settings instance
active_settings = ScreenReaderSettings()

def init_from_android(prefs):
    active_settings.init_from_android(prefs)
