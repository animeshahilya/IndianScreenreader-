# Helper module for parsing Android AccessibilityNodeInfo objects

CLASS_ROLE_MAP = {
    # Standard Android Widgets
    "android.widget.Button": "Button",
    "android.widget.ImageButton": "Button",
    "android.widget.CheckBox": "Checkbox",
    "android.widget.RadioButton": "Radio button",
    "android.widget.ToggleButton": "Toggle button",
    "android.widget.Switch": "Switch",
    "android.widget.EditText": "Editing box",
    "android.widget.SeekBar": "Slider",
    "android.widget.RatingBar": "Rating bar",
    "android.widget.ImageView": "Image",
    "android.widget.ProgressBar": "Progress bar",
    "android.widget.Spinner": "Drop down menu",
    "android.widget.ListView": "List",
    "android.widget.GridView": "Grid",
    "android.widget.TabWidget": "Tab bar",
    # AppCompat Widgets
    "androidx.appcompat.widget.AppCompatButton": "Button",
    "androidx.appcompat.widget.AppCompatCheckBox": "Checkbox",
    "androidx.appcompat.widget.AppCompatRadioButton": "Radio button",
    "androidx.appcompat.widget.SwitchCompat": "Switch",
    "androidx.appcompat.widget.AppCompatEditText": "Editing box",
    "androidx.recyclerview.widget.RecyclerView": "List",
    "androidx.viewpager.widget.ViewPager": "Page view",
}


def get_role_description(node):
    """Maps Android view class names to friendly spoken roles."""
    if node is None:
        return ""

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    return CLASS_ROLE_MAP.get(class_name, "")


def is_heading(node):
    """Checks if node is an accessibility heading or title."""
    if node is None:
        return False

    if hasattr(node, "isHeading") and node.isHeading():
        return True

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    if "TextView" in class_name and hasattr(node, "isClickable") and not node.isClickable():
        return True

    return False


def is_control(node):
    """Checks if node is an interactive control (button, checkbox, switch, input)."""
    if node is None:
        return False

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    if class_name in CLASS_ROLE_MAP:
        return True

    if hasattr(node, "isClickable") and node.isClickable():
        return True

    if hasattr(node, "isCheckable") and node.isCheckable():
        return True

    return False


def get_state_description(node):
    """Returns spoken state information like checked, selected, disabled, or password."""
    if node is None:
        return []

    states = []

    if hasattr(node, "isPassword") and node.isPassword():
        text_val = get_node_raw_text(node)
        if text_val:
            states.append(f"Password field, {len(text_val)} characters")
        else:
            states.append("Password field")

    if hasattr(node, "isCheckable") and node.isCheckable():
        if hasattr(node, "isChecked") and node.isChecked():
            states.append("Checked")
        else:
            states.append("Not checked")

    if hasattr(node, "isSelected") and node.isSelected():
        states.append("Selected")

    if hasattr(node, "isEnabled") and not node.isEnabled():
        states.append("Disabled")

    return states


def get_node_raw_text(node):
    """Extracts raw text, content description, hint, or error message from a node."""
    if node is None:
        return ""

    # Priority 1: Error text
    if hasattr(node, "getError"):
        err = node.getError()
        if err:
            return f"Error: {err}".strip()

    # Priority 2: Content Description
    if hasattr(node, "getContentDescription"):
        content_desc = node.getContentDescription()
        if content_desc:
            return str(content_desc).strip()

    # Priority 3: Text
    if hasattr(node, "getText"):
        text = node.getText()
        if text:
            return str(text).strip()

    # Priority 4: Hint Text
    if hasattr(node, "getHintText"):
        hint = node.getHintText()
        if hint:
            return str(hint).strip()

    # Priority 5: Fallback to child text
    if hasattr(node, "getChildCount") and node.getChildCount() > 0:
        child_texts = []
        for i in range(min(node.getChildCount(), 5)):
            child = node.getChild(i)
            if child:
                child_text = get_node_raw_text(child)
                if child_text:
                    child_texts.append(child_text)
                if hasattr(child, "recycle"):
                    child.recycle()
        if child_texts:
            return " ".join(child_texts)

    return ""


def get_words(text):
    """Splits text into words for word-granularity navigation."""
    if not text:
        return []
    return [w for w in text.split() if w]


def get_characters(text):
    """Splits text into characters for character-granularity navigation."""
    if not text:
        return []
    return list(text)


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

    if settings.VERBOSITY_LEVEL == "low":
        return raw_text if raw_text else role

    return ", ".join(parts)
