import sys

def get_text_from_node(node):
    if node is None:
        return ""
    
    # Try to get content description
    content_desc = node.getContentDescription()
    if content_desc:
        return str(content_desc)
    
    # Try to get text
    text = node.getText()
    if text:
        return str(text)
    
    return ""

def on_accessibility_event(service, event):
    try:
        from android.view.accessibility import AccessibilityEvent

        event_type = event.getEventType()

        # We care about focus changes, clicks, and hover enter (touch exploration)
        if event_type == AccessibilityEvent.TYPE_VIEW_FOCUSED or \
           event_type == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER or \
           event_type == AccessibilityEvent.TYPE_VIEW_CLICKED:
            
            node = event.getSource()
            if node:
                text_to_speak = get_text_from_node(node)
                
                # If there's text, ask the service to speak it
                if text_to_speak.strip():
                    service.speak(text_to_speak)
                
                # Recycle the node to avoid memory leaks
                node.recycle()

        elif event_type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            # Maybe read the window title or the first focusable element
            node = event.getSource()
            if node:
                text = get_text_from_node(node)
                if text.strip():
                    service.speak(f"Window: {text}")
                node.recycle()

    except Exception as e:
        # Print to android logcat
        print(f"Python error in on_accessibility_event: {e}", file=sys.stderr)

def on_interrupt():
    print("Python on_interrupt called", file=sys.stdout)
    pass
