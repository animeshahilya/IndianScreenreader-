import sys
from node_parser import format_node_speech, get_node_raw_text
from settings import active_settings


class EventHandler:

    def __init__(self):
        self.last_spoken_text = ""

    def process_event(self, service, event):
        if event is None:
            return

        try:
            from android.view.accessibility import AccessibilityEvent

            event_type = event.getEventType()

            # Handle focus, hover (touch exploration), or click
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

                    node.recycle()

            # Handle window state changes (app/dialog transitions)
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
                    node.recycle()

            # Handle text changes (e.g. typing in edit fields)
            elif event_type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                node = event.getSource()
                if node:
                    text = get_node_raw_text(node)
                    if text and text.strip():
                        service.speak(text)
                        self.last_spoken_text = text
                    node.recycle()

        except Exception as e:
            print(f"Error processing accessibility event: {e}", file=sys.stderr)

    def on_interrupt(self, service=None):
        self.last_spoken_text = ""


# Global singleton event handler instance
event_handler_instance = EventHandler()
