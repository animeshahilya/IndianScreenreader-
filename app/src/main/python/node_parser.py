# Helper module for parsing Android AccessibilityNodeInfo objects

CLASS_ROLE_MAP = {
    "android.widget.Button": "Button",
    "android.widget.ImageButton": "Button",
    "android.widget.CheckBox": "Checkbox",
    "android.widget.RadioButton": "Radio button",
    "android.widget.ToggleButton": "Toggle button",
    "android.widget.Switch": "Switch",
    "android.widget.EditText": "Editing box",
    "android.widget.SeekBar": "Slider",
    "android.widget.ImageView": "Image",
    "android.widget.ProgressBar": "Progress bar",
    "android.widget.Spinner": "Drop down menu",
    "android.widget.ListView": "List",
    "android.widget.GridView": "Grid",
    "android.widget.TabWidget": "Tab bar",
}


def get_role_description(node):
    """Maps Android view class names to friendly spoken roles."""
    if node is None:
        return ""

    class_name = str(node.getClassName()) if node.getClassName() else ""
    return CLASS_ROLE_MAP.get(class_name, "")


def get_state_description(node):
    """Returns spoken state information like checked, selected, disabled, or password."""
    if node is None:
        return []

    states = []

    # Password check
    if hasattr(node, "isPassword") and node.isPassword():
        states.append("Password field")

    # Checkable / Checked check
    if hasattr(node, "isCheckable") and node.isCheckable():
        if hasattr(node, "isChecked") and node.isChecked():
            states.append("Checked")
        else:
            states.append("Not checked")

    # Selected check
    if hasattr(node, "isSelected") and node.isSelected():
        states.append("Selected")

    # Enabled / Disabled check
    if hasattr(node, "isEnabled") and not node.isEnabled():
        states.append("Disabled")

    return states


def get_node_raw_text(node):
    """Extracts raw text or content description from a node."""
    if node is None:
        return ""

    # Priority 1: Content Description
    content_desc = node.getContentDescription()
    if content_desc:
        return str(content_desc).strip()

    # Priority 2: Text
    text = node.getText()
    if text:
        return str(text).strip()

    # Priority 3: Fallback to child text if container has text children
    if hasattr(node, "getChildCount") and node.getChildCount() > 0:
        child_texts = []
        for i in range(min(node.getChildCount(), 5)):  # limit to first 5 children for speed
            child = node.getChild(i)
            if child:
                child_text = get_node_raw_text(child)
                if child_text:
                    child_texts.append(child_text)
                child.recycle()
        if child_texts:
            return " ".join(child_texts)

    return ""


def format_node_speech(node, settings):
    """Combines text, role, and state into a clean spoken phrase based on settings."""
    if node is None:
        return ""

    raw_text = get_node_raw_text(node)
    role = get_role_description(node) if settings.ANNOUNCE_ELEMENT_TYPES else ""
    states = get_state_description(node) if settings.ANNOUNCE_ELEMENT_STATE else []

    parts = []

    if raw_text:
        parts.append(raw_text)

    if role:
        parts.append(role)

    if states:
        parts.extend(states)

    # Low verbosity: return text only if available
    if settings.VERBOSITY_LEVEL == "low":
        return raw_text if raw_text else role

    return ", ".join(parts)
