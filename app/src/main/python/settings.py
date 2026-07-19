# Configurable options for Indian Screenreader

class Settings:
    # --- Verbosity & Output Customizations ---
    # Verbosity: 'high' (all details), 'medium' (standard), 'low' (essential text only)
    VERBOSITY_LEVEL = "medium"

    # Element Types & Roles (e.g., "Button", "Checkbox", "Editing field")
    ANNOUNCE_ELEMENT_TYPES = True

    # Element State (e.g., "Checked", "Not checked", "Selected", "Disabled", "Password")
    ANNOUNCE_ELEMENT_STATE = True

    # Window Changes (e.g., announcing screen/dialog transitions)
    READ_WINDOW_CHANGES = True

    # Window title prefix
    WINDOW_TITLE_PREFIX = "Window"

    # Prevent repeating the exact same speech twice in quick succession
    FILTER_DUPLICATE_SPEECH = True

    # --- Advanced Customization Options ---
    # Grid/Table Position (e.g., "Row 2, Column 3")
    ANNOUNCE_GRID_POSITION = True

    # Total List Item Count (e.g., "List with 12 items")
    ANNOUNCE_LIST_COUNT = True

    # Developer/Power User Mode: Speak view IDs (e.g. "btn_submit")
    ANNOUNCE_VIEW_IDS = False

    # Ignore unlabelled decorative image views to speed up reading
    IGNORE_DECORATIVE_IMAGES = False

    # --- Audio & Haptic Customizations ---
    AUDIO_FEEDBACK_ENABLED = True
    AUDIO_BEEP_VOLUME = 60  # 0 to 100
    HAPTIC_FEEDBACK_ENABLED = True
    HAPTIC_DURATION_MS = 25  # vibration duration in ms

    # --- Granular Navigation Modes ---
    GRANULARITIES = ["default", "control", "heading", "word", "character"]
    CURRENT_GRANULARITY_INDEX = 0

    # Continuous Reading State ("Read from top")
    CONTINUOUS_READING_ACTIVE = False

    # Event Throttling (ms) for scroll & rapid UI state debouncing
    EVENT_THROTTLE_MS = 40

    # --- NVDA Features ---
    INPUT_HELP_MODE = False
    PUNCTUATION_VERBOSITY = "none"  # 'none', 'some', 'all'
    ANNOUNCE_CAPITALIZATION = "prefix"  # 'prefix', 'pitch', 'none'
    SPEECH_RATE = 1.0  # 0.5 to 2.0
    SPEECH_PITCH = 1.0  # 0.5 to 2.0

    # --- AI Studio Gemini API Features ---
    GEMINI_API_KEY = ""
    TRANSLATION_TARGET_LANGUAGE = "Hindi"
    AUTO_TRANSLATE_ENABLED = False
    SIMPLIFY_TEXT_ENABLED = False

    # --- Custom Gesture Mapping Dictionary ---
    # Gesture ID to Custom Action Mapping
    # Actions: 'focus_next', 'focus_prev', 'cycle_granularity_up', 'cycle_granularity_down',
    #          'click', 'long_click', 'ai_summarize', 'ai_translate', 'read_status',
    #          'toggle_input_help', 'global_back', 'global_home', 'global_recents'
    GESTURE_MAP = {
        1: "focus_next",                # SWIPE_RIGHT
        2: "focus_prev",                # SWIPE_LEFT
        3: "cycle_granularity_up",      # SWIPE_UP
        4: "cycle_granularity_down",    # SWIPE_DOWN
        17: "click",                    # DOUBLE_TAP
        18: "long_click",               # DOUBLE_TAP_HOLD
        5: "global_back",               # SWIPE_LEFT_THEN_UP
        6: "global_home",               # SWIPE_LEFT_THEN_DOWN
        7: "global_recents",            # SWIPE_RIGHT_THEN_DOWN
        8: "global_notifications",      # SWIPE_RIGHT_THEN_UP
        9: "ai_summarize",              # SWIPE_UP_THEN_RIGHT
        10: "read_status",              # SWIPE_DOWN_THEN_RIGHT
    }


# Global active settings instance
active_settings = Settings()
