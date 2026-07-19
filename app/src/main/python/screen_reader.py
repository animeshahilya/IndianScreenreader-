# Chaquopy Entry Point for Indian Screenreader
import settings
import node_parser
from event_handler import event_handler_instance
from ai_service import ai_service_instance


def on_accessibility_event(service, event):
    """Main event dispatcher called directly from Kotlin AccessibilityService."""
    event_handler_instance.process_event(service, event)


def on_gesture(service, gesture_id):
    """Main gesture dispatcher called directly from Kotlin AccessibilityService."""
    return event_handler_instance.handle_gesture(service, gesture_id)


def open_indian_menu(service):
    """Opens TalkBack-style Indian Context Menu."""
    event_handler_instance.open_indian_menu(service)


def close_indian_menu(service):
    """Closes Indian Context Menu."""
    event_handler_instance.close_indian_menu(service)


def toggle_screen_curtain(service):
    """Privacy Feature: Toggles Screen Curtain (blacks out screen display for privacy)."""
    settings.active_settings.SCREEN_CURTAIN_ENABLED = not settings.active_settings.SCREEN_CURTAIN_ENABLED
    state = settings.active_settings.SCREEN_CURTAIN_ENABLED
    
    # Notify Kotlin service to toggle overlay view
    if hasattr(service, "setScreenCurtainEnabled"):
        service.setScreenCurtainEnabled(state)
        
    msg = "Screen curtain ON. Screen display is hidden for privacy." if state else "Screen curtain OFF. Screen display is visible."
    service.speak(msg)


def toggle_input_help(service):
    """NVDA Feature: Toggles Input Help (Practice Mode)."""
    settings.active_settings.INPUT_HELP_MODE = not settings.active_settings.INPUT_HELP_MODE
    state = "ON" if settings.active_settings.INPUT_HELP_MODE else "OFF"
    msg = f"Input Help {state}. In this mode, gestures announce their action without executing."
    service.speak(msg)


def toggle_punctuation_verbosity(service):
    """NVDA Feature: Cycles punctuation verbosity (all -> some -> none)."""
    current = settings.active_settings.PUNCTUATION_VERBOSITY
    if current == "all":
        new_mode = "some"
    elif current == "some":
        new_mode = "none"
    else:
        new_mode = "all"
    settings.active_settings.PUNCTUATION_VERBOSITY = new_mode
    service.speak(f"Punctuation verbosity set to {new_mode}")


def read_device_status(service):
    """NVDA Feature: Reads Time & Battery Status."""
    status_str = service.getDeviceStatusString()
    service.speak(f"Device Status: {status_str}")


def ai_summarize_screen(service):
    """Google AI Studio Gemini Feature: Summarizes current active screen content."""
    root = service.getRootInActiveWindow()
    if root is None:
        service.speak("Cannot capture screen content.")
        return

    screen_text = node_parser.get_node_raw_text(root)
    if hasattr(root, "recycle"):
        root.recycle()

    if not screen_text:
        service.speak("No text found on screen to summarize.")
        return

    service.speak("Summarizing screen content using Gemini AI...")

    def on_summary_ready(summary):
        service.speak(f"AI Screen Summary: {summary}")

    ai_service_instance.summarize_screen_async(screen_text, on_summary_ready)


def set_gemini_api_key(key):
    """Sets Google AI Studio Gemini API key dynamically."""
    settings.active_settings.GEMINI_API_KEY = key


def remap_gesture(gesture_id, action_name):
    """Remaps a gesture ID to a custom action string name."""
    settings.active_settings.GESTURE_MAP[gesture_id] = action_name


def on_interrupt():
    """Service interruption handler."""
    pass
