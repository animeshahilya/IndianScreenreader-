import sys
import time
from node_parser import (
    format_node_speech,
    get_node_raw_text,
    get_words,
    get_characters,
    format_capitalization,
    is_heading,
    is_control,
)
from settings import active_settings


class EventHandler:

    def __init__(self):
        self.last_spoken_text = ""
        self.last_event_time_ms = 0
        self.current_granularity_index = 0
        self.granular_text_index = 0
        self.current_node_text = ""

    def is_throttled(self):
        """Returns True if events are arriving faster than EVENT_THROTTLE_MS."""
        now = time.time() * 1000
        if (now - self.last_event_time_ms) < active_settings.EVENT_THROTTLE_MS:
            return True
        self.last_event_time_ms = now
        return False

    def process_event(self, service, event):
        if event is None:
            return

        if self.is_throttled():
            return

        try:
            from android.view.accessibility import AccessibilityEvent

            event_type = event.getEventType()

            # Focus, Hover, Click events
            if (
                event_type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                or event_type == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
                or event_type == AccessibilityEvent.TYPE_VIEW_CLICKED
            ):

                node = event.getSource()
                if node:
                    speech_text = format_node_speech(node, active_settings)

                    if speech_text and speech_text.strip():
                        if (
                            not active_settings.FILTER_DUPLICATE_SPEECH
                            or speech_text != self.last_spoken_text
                        ):
                            service.speak(speech_text)
                            self.last_spoken_text = speech_text
                            self.current_node_text = get_node_raw_text(node)
                            self.granular_text_index = 0

                    if hasattr(node, "recycle"):
                        node.recycle()

            # Window state changes
            elif (
                event_type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                and active_settings.READ_WINDOW_CHANGES
            ):

                node = event.getSource()
                if node:
                    window_text = get_node_raw_text(node)
                    if window_text and window_text.strip():
                        announcement = f"{active_settings.WINDOW_TITLE_PREFIX}: {window_text}"
                        if (
                            not active_settings.FILTER_DUPLICATE_SPEECH
                            or announcement != self.last_spoken_text
                        ):
                            service.speak(announcement)
                            self.last_spoken_text = announcement
                    if hasattr(node, "recycle"):
                        node.recycle()

            # Text changes in edit controls
            elif event_type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                node = event.getSource()
                if node:
                    text = get_node_raw_text(node)
                    if text and text.strip():
                        service.speak(text)
                        self.last_spoken_text = text
                    if hasattr(node, "recycle"):
                        node.recycle()

            # Scroll position announcements
            elif event_type == AccessibilityEvent.TYPE_VIEW_SCROLLED:
                if hasattr(event, "getItemCount") and hasattr(event, "getFromIndex"):
                    item_count = event.getItemCount()
                    from_index = event.getFromIndex()
                    to_index = getattr(event, "getToIndex", lambda: -1)()
                    if item_count > 0 and from_index >= 0:
                        if to_index >= from_index:
                            scroll_msg = f"Items {from_index + 1} to {to_index + 1} of {item_count}"
                        else:
                            scroll_msg = f"Item {from_index + 1} of {item_count}"

                        if scroll_msg != self.last_spoken_text:
                            service.speak(scroll_msg)
                            self.last_spoken_text = scroll_msg

        except Exception as e:
            print(f"Error processing accessibility event: {e}", file=sys.stderr)

    def handle_gesture(self, service, gesture_id):
        """Processes gesture IDs and dispatches custom mapped actions."""
        try:
            action_name = active_settings.GESTURE_MAP.get(gesture_id, "")

            # NVDA Input Help Mode (Practice Mode)
            if active_settings.INPUT_HELP_MODE:
                action_desc = action_name.replace("_", " ").title() if action_name else f"Gesture {gesture_id}"
                if hasattr(service, "speak"):
                    service.speak(f"Input Help: Gesture {gesture_id} performs {action_desc}")
                return True

            # Dispatch mapped custom action
            if action_name == "focus_next":
                return self.navigate_forward(service)
            elif action_name == "focus_prev":
                return self.navigate_backward(service)
            elif action_name == "cycle_granularity_up":
                self.cycle_granularity(service, delta=1)
                return True
            elif action_name == "cycle_granularity_down":
                self.cycle_granularity(service, delta=-1)
                return True
            elif action_name == "click":
                if hasattr(service, "performNodeClick"):
                    service.performNodeClick()
                return True
            elif action_name == "long_click":
                if hasattr(service, "performNodeLongClick"):
                    service.performNodeLongClick()
                return True
            elif action_name == "ai_summarize":
                from screen_reader import ai_summarize_screen
                ai_summarize_screen(service)
                return True
            elif action_name == "read_status":
                from screen_reader import read_device_status
                read_device_status(service)
                return True
            elif action_name == "toggle_input_help":
                from screen_reader import toggle_input_help
                toggle_input_help(service)
                return True
            elif action_name == "global_back" and hasattr(service, "performGlobalBack"):
                return service.performGlobalBack()
            elif action_name == "global_home" and hasattr(service, "performGlobalHome"):
                return service.performGlobalHome()
            elif action_name == "global_recents" and hasattr(service, "performGlobalRecents"):
                return service.performGlobalRecents()
            elif action_name == "global_notifications" and hasattr(service, "performGlobalNotifications"):
                return service.performGlobalNotifications()

            # Fallback gesture defaults
            if gesture_id == 1:
                return self.navigate_forward(service)
            elif gesture_id == 2:
                return self.navigate_backward(service)

        except Exception as e:
            print(f"Error handling gesture {gesture_id}: {e}", file=sys.stderr)

        return False

    def cycle_granularity(self, service, delta):
        """Cycles through navigation granularities."""
        granularities = active_settings.GRANULARITIES
        self.current_granularity_index = (
            self.current_granularity_index + delta
        ) % len(granularities)
        active_settings.CURRENT_GRANULARITY_INDEX = self.current_granularity_index
        mode_name = granularities[self.current_granularity_index].capitalize()

        if hasattr(service, "speak"):
            service.speak(f"Navigation: {mode_name}")

    def get_current_granularity(self):
        return active_settings.GRANULARITIES[self.current_granularity_index]

    def navigate_forward(self, service):
        mode = self.get_current_granularity()

        if mode in ["default", "control", "heading"]:
            if hasattr(service, "performFocusNext"):
                return service.performFocusNext()

        elif mode == "word":
            words = get_words(self.current_node_text)
            if words and self.granular_text_index < len(words):
                word = words[self.granular_text_index]
                self.granular_text_index += 1
                if hasattr(service, "speak"):
                    service.speak(word)
                return True

        elif mode == "character":
            chars = get_characters(self.current_node_text)
            if chars and self.granular_text_index < len(chars):
                char = chars[self.granular_text_index]
                self.granular_text_index += 1
                char_speech = format_capitalization(
                    char, active_settings.ANNOUNCE_CAPITALIZATION
                )
                if hasattr(service, "speak"):
                    service.speak(char_speech)
                return True

        return False

    def navigate_backward(self, service):
        mode = self.get_current_granularity()

        if mode in ["default", "control", "heading"]:
            if hasattr(service, "performFocusPrevious"):
                return service.performFocusPrevious()

        elif mode == "word":
            words = get_words(self.current_node_text)
            if words and self.granular_text_index > 0:
                self.granular_text_index -= 1
                word = words[self.granular_text_index]
                if hasattr(service, "speak"):
                    service.speak(word)
                return True

        elif mode == "character":
            chars = get_characters(self.current_node_text)
            if chars and self.granular_text_index > 0:
                self.granular_text_index -= 1
                char = chars[self.granular_text_index]
                char_speech = format_capitalization(
                    char, active_settings.ANNOUNCE_CAPITALIZATION
                )
                if hasattr(service, "speak"):
                    service.speak(char_speech)
                return True

        return False

    def on_interrupt(self, service=None):
        self.last_spoken_text = ""


# Global singleton event handler instance
event_handler_instance = EventHandler()
