import sys
from event_handler import event_handler_instance
from settings import active_settings
from ai_service import ai_service_instance


def on_accessibility_event(service, event):
    """Main callback invoked by IndianScreenReaderService.kt for every accessibility event."""
    event_handler_instance.process_event(service, event)


def on_gesture(service, gesture_id):
    """Callback invoked when user performs a touch gesture."""
    return event_handler_instance.handle_gesture(service, gesture_id)


def on_interrupt():
    """Callback invoked when speech or service is interrupted."""
    event_handler_instance.on_interrupt()


# --- Standard Option Setters ---
def set_verbosity(level):
    if level in ["high", "medium", "low"]:
        active_settings.VERBOSITY_LEVEL = level


def set_announce_element_types(enabled):
    active_settings.ANNOUNCE_ELEMENT_TYPES = bool(enabled)


def set_announce_element_state(enabled):
    active_settings.ANNOUNCE_ELEMENT_STATE = bool(enabled)


def set_read_window_changes(enabled):
    active_settings.READ_WINDOW_CHANGES = bool(enabled)


def set_filter_duplicates(enabled):
    active_settings.FILTER_DUPLICATE_SPEECH = bool(enabled)


# --- NVDA Specific Options ---
def toggle_input_help(service):
    """Toggles NVDA Input Help Mode (Practice Mode) ON or OFF."""
    active_settings.INPUT_HELP_MODE = not active_settings.INPUT_HELP_MODE
    status = "ON" if active_settings.INPUT_HELP_MODE else "OFF"
    msg = f"Input Help {status}"
    if hasattr(service, "speak"):
        service.speak(msg)
    return active_settings.INPUT_HELP_MODE


def set_punctuation_verbosity(level):
    """Sets punctuation verbosity mode: 'none', 'some', 'all'."""
    if level in ["none", "some", "all"]:
        active_settings.PUNCTUATION_VERBOSITY = level


def set_capitalization_mode(mode):
    """Sets capitalization mode: 'prefix' ('Cap A'), 'pitch', 'none'."""
    if mode in ["prefix", "pitch", "none"]:
        active_settings.ANNOUNCE_CAPITALIZATION = mode


def set_speech_rate(service, rate):
    """Dynamically adjusts TTS speech rate (0.5 to 2.0)."""
    try:
        r = float(rate)
        active_settings.SPEECH_RATE = r
        if hasattr(service, "setSpeechRate"):
            service.setSpeechRate(r)
    except Exception as e:
        print(f"Error setting speech rate: {e}", file=sys.stderr)


def set_speech_pitch(service, pitch):
    """Dynamically adjusts TTS speech pitch (0.5 to 2.0)."""
    try:
        p = float(pitch)
        active_settings.SPEECH_PITCH = p
        if hasattr(service, "setPitch"):
            service.setPitch(p)
    except Exception as e:
        print(f"Error setting speech pitch: {e}", file=sys.stderr)


def read_device_status(service):
    """Queries device status (battery, time, network) and speaks it."""
    if hasattr(service, "getDeviceStatusString"):
        status_str = service.getDeviceStatusString()
        if status_str and hasattr(service, "speak"):
            service.speak(f"Status: {status_str}")
            return True
    return False


# --- AI Studio Gemini API Features ---
def set_gemini_api_key(key):
    """Sets Google AI Studio Gemini API Key."""
    active_settings.GEMINI_API_KEY = str(key).strip()


def set_translation_target_language(language):
    """Sets target language for AI Translation (e.g. 'Hindi', 'Tamil', 'Telugu', 'Bengali', 'English')."""
    active_settings.TRANSLATION_TARGET_LANGUAGE = str(language).strip()


def toggle_auto_translate(service=None):
    """Toggles AI Auto-Translation ON or OFF."""
    active_settings.AUTO_TRANSLATE_ENABLED = not active_settings.AUTO_TRANSLATE_ENABLED
    status = "ON" if active_settings.AUTO_TRANSLATE_ENABLED else "OFF"
    msg = f"AI Auto Translation {status} ({active_settings.TRANSLATION_TARGET_LANGUAGE})"
    if service and hasattr(service, "speak"):
        service.speak(msg)
    return active_settings.AUTO_TRANSLATE_ENABLED


def toggle_text_simplification(service=None):
    """Toggles AI Text Simplification ON or OFF."""
    active_settings.SIMPLIFY_TEXT_ENABLED = not active_settings.SIMPLIFY_TEXT_ENABLED
    status = "ON" if active_settings.SIMPLIFY_TEXT_ENABLED else "OFF"
    msg = f"AI Text Simplification {status}"
    if service and hasattr(service, "speak"):
        service.speak(msg)
    return active_settings.SIMPLIFY_TEXT_ENABLED


def ai_summarize_screen(service):
    """Summarizes current screen text using Gemini API."""
    if hasattr(service, "getRootInActiveWindow"):
        root = service.getRootInActiveWindow()
        if root:
            from node_parser import get_node_raw_text
            screen_text = get_node_raw_text(root)
            summary = ai_service_instance.summarize_screen(screen_text)
            if hasattr(service, "speak"):
                service.speak(f"AI Screen Summary: {summary}")
            return summary
    return "Could not retrieve screen text."


def ai_translate_text(service, text, target_language="Hindi"):
    """Translates arbitrary text into target_language using Gemini API."""
    translated = ai_service_instance.translate_text(text, target_language)
    if hasattr(service, "speak"):
        service.speak(translated)
    return translated


def ai_rewrite_simplified(service, text):
    """Simplifies complex text into easy language using Gemini API."""
    simplified = ai_service_instance.rewrite_simplified(text)
    if hasattr(service, "speak"):
        service.speak(simplified)
    return simplified


def ai_describe_image_b64(service, base64_image):
    """Describes an image using Gemini Vision API."""
    description = ai_service_instance.describe_image_b64(base64_image)
    if hasattr(service, "speak"):
        service.speak(f"AI Image Description: {description}")
    return description


def perform_global_action(service, action_name):
    """Dispatches global shortcuts: 'back', 'home', 'recents', 'notifications', 'quick_settings'."""
    action_name = str(action_name).lower()
    if action_name == "back" and hasattr(service, "performGlobalBack"):
        return service.performGlobalBack()
    elif action_name == "home" and hasattr(service, "performGlobalHome"):
        return service.performGlobalHome()
    elif action_name == "recents" and hasattr(service, "performGlobalRecents"):
        return service.performGlobalRecents()
    elif action_name == "notifications" and hasattr(service, "performGlobalNotifications"):
        return service.performGlobalNotifications()
    elif action_name == "quick_settings" and hasattr(service, "performGlobalQuickSettings"):
        return service.performGlobalQuickSettings()
    return False
