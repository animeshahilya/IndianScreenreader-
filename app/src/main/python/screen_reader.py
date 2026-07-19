import sys
from event_handler import event_handler_instance
from settings import active_settings


def on_accessibility_event(service, event):
    """Main callback invoked by IndianScreenReaderService.kt for every accessibility event."""
    event_handler_instance.process_event(service, event)


def on_gesture(service, gesture_id):
    """Callback invoked when user performs a touch gesture."""
    return event_handler_instance.handle_gesture(service, gesture_id)


def on_interrupt():
    """Callback invoked when speech or service is interrupted."""
    event_handler_instance.on_interrupt()


def set_verbosity(level):
    """Dynamically set verbosity level ('high', 'medium', 'low')."""
    if level in ["high", "medium", "low"]:
        active_settings.VERBOSITY_LEVEL = level


def set_announce_element_types(enabled):
    """Enable or disable element type announcements (e.g. 'Button')."""
    active_settings.ANNOUNCE_ELEMENT_TYPES = bool(enabled)


def set_announce_element_state(enabled):
    """Enable or disable state announcements (e.g. 'Checked')."""
    active_settings.ANNOUNCE_ELEMENT_STATE = bool(enabled)


def set_read_window_changes(enabled):
    """Enable or disable reading window title changes."""
    active_settings.READ_WINDOW_CHANGES = bool(enabled)


def set_filter_duplicates(enabled):
    """Enable or disable duplicate speech filtering."""
    active_settings.FILTER_DUPLICATE_SPEECH = bool(enabled)


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
