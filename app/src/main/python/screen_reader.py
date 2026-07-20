# Chaquopy Entry Point for Indian Screenreader
import threading
import time
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
    # Use property access for Kotlin properties mapped to Python
    root = service.rootInActiveWindow
    if root is None:
        service.speak("Cannot capture screen content.")
        return

    screen_text = node_parser.get_node_raw_text(root)
    if hasattr(root, "recycle"):
        try:
            root.recycle()
        except Exception:
            pass

    if not screen_text:
        service.speak("No text found on screen to summarize.")
        return

    service.speak("Summarizing screen content using Gemini AI...")

    def on_summary_ready(summary):
        service.speak(f"AI Screen Summary: {summary}")
        
    def on_summary_error(err):
        service.speak(f"Summary failed: {err}")

    ai_service_instance.summarize_screen_async(screen_text, on_summary_ready, on_summary_error)


def read_from_top(service):
    """Feature: Continuous Reading Mode - Read from top of screen."""
    if getattr(settings.active_settings, "CONTINUOUS_READING_ACTIVE", False):
        return  # Already reading
        
    service.speak("Reading from top")
    settings.active_settings.CONTINUOUS_READING_ACTIVE = True
    
    # Simple background loop for continuous reading
    def read_loop():
        # Reset focus by clearing it, so the next FocusNext starts at the top
        if hasattr(service, "clearFocus"):
            service.clearFocus()
            time.sleep(0.1) # Wait for focus to clear
        
        while settings.active_settings.CONTINUOUS_READING_ACTIVE:
            success = service.performFocusNext()
            if not success:
                settings.active_settings.CONTINUOUS_READING_ACTIVE = False
                break
            # Wait a fixed amount or ideally sync with TTS (stubbed here with fixed delay)
            time.sleep(2.5) 
            
    threading.Thread(target=read_loop, daemon=True).start()


def read_from_here(service):
    """Feature: Continuous Reading Mode - Read from current focus."""
    if getattr(settings.active_settings, "CONTINUOUS_READING_ACTIVE", False):
        return  # Already reading
        
    service.speak("Reading from here")
    settings.active_settings.CONTINUOUS_READING_ACTIVE = True
    
    def read_loop():
        time.sleep(1) # wait for the initial speech
        while settings.active_settings.CONTINUOUS_READING_ACTIVE:
            success = service.performFocusNext()
            if not success:
                settings.active_settings.CONTINUOUS_READING_ACTIVE = False
                break
            time.sleep(2.5)
            
    threading.Thread(target=read_loop, daemon=True).start()


def start_voice_command(service):
    """Feature: Voice Command Mode."""
    service.speak("Voice command mode is listening. Say a command like summarize screen or go home.")
    # Implementation requires Android SpeechRecognizer which must run on main thread.
    # Stubbed for now until Kotlin SpeechRecognizer wrapper is added.


def set_gemini_api_key(key):
    """Sets Google AI Studio Gemini API key dynamically."""
    settings.active_settings.GEMINI_API_KEY = key


def remap_gesture(gesture_id, action_name):
    """Remaps a gesture ID to a custom action string name."""
    settings.active_settings.GESTURE_MAP[gesture_id] = action_name


def on_interrupt():
    """Service interruption handler."""
    settings.active_settings.CONTINUOUS_READING_ACTIVE = False
