# Centralized Settings for Indian Screenreader

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
            "9. Close Indian Menu"
        ]

        # Google AI Studio Gemini Integration
        self.GEMINI_API_KEY = ""
        self.AUTO_TRANSLATE_ENABLED = False
        self.TRANSLATION_TARGET_LANGUAGE = "Hindi"
        self.SIMPLIFY_TEXT_ENABLED = False

        # Granularities
        self.GRANULARITIES = ["default", "control", "heading", "word", "character"]
        self.CURRENT_GRANULARITY_INDEX = 0

        # Customizations
        self.ANNOUNCE_GRID_POSITION = True
        self.ANNOUNCE_LIST_COUNT = True
        self.ANNOUNCE_VIEW_IDS = False
        self.IGNORE_DECORATIVE_IMAGES = True

        # Gesture Remapping Dictionary
        # Map gesture ID -> action string name
        self.GESTURE_MAP = {
            1: "focus_next",           # SWIPE_RIGHT
            2: "focus_prev",           # SWIPE_LEFT
            3: "granularity_up",       # SWIPE_UP
            4: "granularity_down",     # SWIPE_DOWN
            5: "open_indian_menu",     # SWIPE_UP_THEN_RIGHT (Indian Context Menu)
            17: "click",               # DOUBLE_TAP
            18: "long_click",          # DOUBLE_TAP_AND_HOLD
        }


# Singleton active settings instance
active_settings = ScreenReaderSettings()
