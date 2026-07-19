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

    # Announce container context (e.g., "List item", "Grid item") in high verbosity
    ANNOUNCE_CONTAINER_CONTEXT = True

    # Speech mode: 'flush' (interrupt ongoing speech) or 'queue' (add to queue)
    SPEECH_QUEUE_MODE = "flush"


# Global active settings instance
active_settings = Settings()
