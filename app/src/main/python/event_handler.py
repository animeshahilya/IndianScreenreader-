import time
from settings import active_settings
import node_parser
from ai_service import ai_service_instance

EVENT_THROTTLE_MS = 40  # Debounce events within 40ms for performance stability


class EventHandler:

    def __init__(self):
        self.last_focus_event_time_ms = 0
        self.last_spoken_text = ""
        self.current_granularity_index = 0

    def is_focus_throttled(self):
        now_ms = int(time.time() * 1000)
        if now_ms - self.last_focus_event_time_ms < EVENT_THROTTLE_MS:
            return True
        self.last_focus_event_time_ms = now_ms
        return False

    def process_event(self, service, event):
        """Processes accessibility events with debouncing and deduplication."""
        event_type = event.getEventType()
        source = event.getSource()

        if source is None:
            return

        try:
            # 8 = TYPE_VIEW_FOCUSED, 128 = TYPE_VIEW_HOVER_ENTER, 32768 = TYPE_VIEW_ACCESSIBILITY_FOCUSED
            if event_type in (8, 128, 32768):
                if self.is_focus_throttled():
                    return

                spoken_text = node_parser.format_node_speech(source, active_settings)

                if active_settings.DEDUPLICATE_SPEECH and spoken_text == self.last_spoken_text:
                    return

                if spoken_text:
                    self.last_spoken_text = spoken_text
                    service.speak(spoken_text)

            elif event_type == 32:  # TYPE_WINDOW_STATE_CHANGED
                if active_settings.ANNOUNCE_WINDOW_CHANGES:
                    pkg = str(event.getPackageName()) if event.getPackageName() else ""
                    
                    if pkg and pkg != active_settings.CURRENT_APP_PACKAGE:
                        active_settings.CURRENT_APP_PACKAGE = pkg
                        
                        # Apply App Profiles dynamically
                        profile = active_settings.APP_PROFILES.get(pkg, None)
                        if profile:
                            if "speech_rate" in profile:
                                service.setSpeechRate(float(profile["speech_rate"]))
                            if "auto_translate" in profile:
                                active_settings.AUTO_TRANSLATE_ENABLED = bool(profile["auto_translate"])
                        else:
                            # Reset to defaults if no profile
                            service.setSpeechRate(float(active_settings.SPEECH_RATE))
                            active_settings.AUTO_TRANSLATE_ENABLED = False

                    window_text = node_parser.get_node_raw_text(source)
                    if window_text and window_text != self.last_spoken_text:
                        self.last_spoken_text = window_text
                        service.speak(f"Window: {window_text}")
            
            elif event_type == 4096:  # TYPE_VIEW_SCROLLED
                try:
                    scroll_y = event.getScrollY()
                    max_scroll_y = event.getMaxScrollY()
                    
                    # Fallback to X axis if Y axis is not scrollable but X is (e.g. horizontal pagers)
                    if max_scroll_y <= 0:
                        scroll_y = event.getScrollX()
                        max_scroll_y = event.getMaxScrollX()

                    if max_scroll_y > 0:
                        percentage = float(scroll_y) / float(max_scroll_y)
                        if hasattr(service, "playDynamicScrollBeep"):
                            service.playDynamicScrollBeep(percentage)
                except Exception:
                    pass

        finally:
            if hasattr(source, "recycle"):
                try:
                    source.recycle()
                except Exception:
                    pass

    # --- INDIAN MENU HANDLER ---
    def open_indian_menu(self, service):
        active_settings.INDIAN_MENU_OPEN = True
        active_settings.INDIAN_MENU_SELECTED_INDEX = 0
        current_item = active_settings.INDIAN_MENU_ITEMS[0]
        service.speak(f"Indian Menu opened. {current_item}. Swipe right for next item, double tap to select.")

    def close_indian_menu(self, service):
        active_settings.INDIAN_MENU_OPEN = False
        service.speak("Indian Menu closed.")

    def navigate_indian_menu(self, service, direction):
        total_items = len(active_settings.INDIAN_MENU_ITEMS)
        if direction > 0:
            active_settings.INDIAN_MENU_SELECTED_INDEX = (active_settings.INDIAN_MENU_SELECTED_INDEX + 1) % total_items
        else:
            active_settings.INDIAN_MENU_SELECTED_INDEX = (active_settings.INDIAN_MENU_SELECTED_INDEX - 1) % total_items

        item_text = active_settings.INDIAN_MENU_ITEMS[active_settings.INDIAN_MENU_SELECTED_INDEX]
        service.speak(item_text)

    def execute_indian_menu_selection(self, service):
        idx = active_settings.INDIAN_MENU_SELECTED_INDEX
        active_settings.INDIAN_MENU_OPEN = False

        import screen_reader

        if idx == 0:  # AI Screen Summary
            screen_reader.ai_summarize_screen(service)
        elif idx == 1:  # AI Language Translation
            active_settings.AUTO_TRANSLATE_ENABLED = not active_settings.AUTO_TRANSLATE_ENABLED
            state = "Enabled" if active_settings.AUTO_TRANSLATE_ENABLED else "Disabled"
            service.speak(f"AI Translation {state}")
        elif idx == 2:  # AI Image Description
            service.speak("Capturing screen for AI Vision description...")
            if hasattr(service, "captureScreenForAI"):
                service.captureScreenForAI()
            else:
                service.speak("Screen capture not supported on this device.")
        elif idx == 3:  # Device Status
            screen_reader.read_device_status(service)
        elif idx == 4:  # Toggle Input Help
            screen_reader.toggle_input_help(service)
        elif idx == 5:  # Granularity Cycle
            self.cycle_granularity(service, 1)
        elif idx == 6:  # Punctuation Verbosity
            screen_reader.toggle_punctuation_verbosity(service)
        elif idx == 7:  # Toggle Screen Curtain
            screen_reader.toggle_screen_curtain(service)
        elif idx == 8:  # Read From Here
            screen_reader.read_from_here(service)
        elif idx == 9:  # Read From Top
            screen_reader.read_from_top(service)
        elif idx == 10: # Voice Command Mode
            screen_reader.start_voice_command(service)
        elif idx == 11: # Close Menu
            service.speak("Indian Menu closed.")

    def handle_gesture(self, service, gesture_id):
        # 1. Check Indian Menu state first
        if active_settings.INDIAN_MENU_OPEN:
            if gesture_id == 1:  # Swipe Right -> Next Menu Item
                self.navigate_indian_menu(service, 1)
                return True
            elif gesture_id == 2:  # Swipe Left -> Prev Menu Item
                self.navigate_indian_menu(service, -1)
                return True
            elif gesture_id in (17, 18):  # Double Tap / Hold -> Execute Selection
                self.execute_indian_menu_selection(service)
                return True

        # Stop continuous reading if active and a gesture is made
        if active_settings.CONTINUOUS_READING_ACTIVE:
            active_settings.CONTINUOUS_READING_ACTIVE = False
            service.speak("Stopped reading")
            return True

        # 2. Check NVDA Practice Mode (Input Help)
        if active_settings.INPUT_HELP_MODE:
            action_desc = self.get_gesture_description(gesture_id)
            service.speak(f"Input Help: Gesture {gesture_id} performs {action_desc}")
            return True

        # 3. Custom Gesture Mapping Lookup
        action_name = active_settings.GESTURE_MAP.get(gesture_id, "")
        
        import screen_reader
        
        if action_name == "open_indian_menu":
            self.open_indian_menu(service)
            return True
        elif action_name == "toggle_screen_curtain":
            screen_reader.toggle_screen_curtain(service)
            return True
        elif action_name == "focus_next":
            return service.performFocusNext()
        elif action_name == "focus_prev":
            return service.performFocusPrevious()
        elif action_name == "granularity_up":
            self.cycle_granularity(service, 1)
            return True
        elif action_name == "granularity_down":
            self.cycle_granularity(service, -1)
            return True
        elif action_name == "click":
            return service.performNodeClick()
        elif action_name == "long_click":
            return service.performNodeLongClick()
        elif action_name == "read_from_top":
            screen_reader.read_from_top(service)
            return True
        elif action_name == "read_from_here":
            screen_reader.read_from_here(service)
            return True
        elif action_name == "voice_command":
            screen_reader.start_voice_command(service)
            return True

        # Fallback to standard gesture routing
        if gesture_id == 1:  # SWIPE_RIGHT
            return service.performFocusNext()
        elif gesture_id == 2:  # SWIPE_LEFT
            return service.performFocusPrevious()
        elif gesture_id == 3:  # SWIPE_UP
            self.cycle_granularity(service, 1)
            return True
        elif gesture_id == 4:  # SWIPE_DOWN
            self.cycle_granularity(service, -1)
            return True
        elif gesture_id == 9:  # SWIPE_UP_AND_RIGHT -> Open Indian Menu
            self.open_indian_menu(service)
            return True

        return False

    def cycle_granularity(self, service, step):
        granularities = active_settings.GRANULARITIES
        self.current_granularity_index = (self.current_granularity_index + step) % len(granularities)
        active_settings.CURRENT_GRANULARITY_INDEX = self.current_granularity_index
        current_name = granularities[self.current_granularity_index].capitalize()
        service.speak(f"Granularity: {current_name}")

    def get_gesture_description(self, gesture_id):
        descriptions = {
            1: "Focus Next",
            2: "Focus Previous",
            3: "Granularity Up",
            4: "Granularity Down",
            9: "Open Indian Context Menu",
            10: "Read From Top",
            11: "Read From Here",
            12: "Voice Command Mode",
            17: "Click / Activate Element",
            18: "Long Click Element"
        }
        return descriptions.get(gesture_id, f"Custom Action {gesture_id}")


# Global event handler instance
event_handler_instance = EventHandler()
