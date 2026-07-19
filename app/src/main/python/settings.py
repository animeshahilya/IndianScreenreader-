# Configurable options for Indian Screenreader

class Settings:
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

    # Audio & Haptic Feedback Toggles
    AUDIO_FEEDBACK_ENABLED = True
    HAPTIC_FEEDBACK_ENABLED = True

    # Granular Navigation Modes
    GRANULARITIES = ["default", "control", "heading", "word", "character"]
    CURRENT_GRANULARITY_INDEX = 0

    # Continuous Reading State ("Read from top")
    CONTINUOUS_READING_ACTIVE = False

    # Event Throttling (ms) for scroll & rapid UI state debouncing
    EVENT_THROTTLE_MS = 40


# Global active settings instance
active_settings = Settings()
