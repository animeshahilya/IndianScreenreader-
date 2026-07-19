# Helper module for parsing Android AccessibilityNodeInfo objects
import functools
import re

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

# Pre-formatted replacement tuples for ultra-fast punctuation expansion
PUNCTUATION_TUPLES_ALL = (
    (".", " period "),
    (",", " comma "),
    (":", " colon "),
    (";", " semicolon "),
    ("?", " question mark "),
    ("!", " exclamation mark "),
    ("/", " slash "),
    ("\\", " backslash "),
    ("@", " at sign "),
    ("#", " hash "),
    ("$", " dollar "),
    ("%", " percent "),
    ("-", " dash "),
    ("_", " underscore "),
    ("+", " plus "),
    ("=", " equals "),
    ("*", " star "),
    ("&", " ampersand "),
    ("(", " open paren "),
    (")", " close paren "),
)

PUNCTUATION_TUPLES_SOME = (
    ("@", " at sign "),
    ("#", " hash "),
    ("$", " dollar "),
    ("%", " percent "),
    ("/", " slash "),
    ("\\", " backslash "),
    ("+", " plus "),
    ("=", " equals "),
)


@functools.lru_cache(maxsize=256)
def get_role_by_class_name(class_name):
    """Fast LRU cached lookup for view class roles."""
    if not class_name:
        return ""
    return CLASS_ROLE_MAP.get(class_name, "")


def apply_punctuation_verbosity(text, mode):
    if not text or mode == "none":
        return text

    tuples = PUNCTUATION_TUPLES_ALL if mode == "all" else PUNCTUATION_TUPLES_SOME
    for char, spoken in tuples:
        if char in text:
            text = text.replace(char, spoken)
    return " ".join(text.split())


def format_capitalization(text, mode):
    if not text or mode == "none":
        return text

    if len(text) == 1 and text.isupper():
        if mode == "prefix":
            return f"Cap {text}"

    return text


def get_role_description(node):
    if node is None:
        return ""

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    return get_role_by_class_name(class_name)


def is_heading(node):
    if node is None:
        return False

    if hasattr(node, "isHeading") and node.isHeading():
        return True

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    if "TextView" in class_name and hasattr(node, "isClickable") and not node.isClickable():
        return True

    return False


def is_control(node):
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


def get_grid_or_list_position(node, settings):
    if node is None:
        return []

    positions = []

    if settings.ANNOUNCE_GRID_POSITION and hasattr(node, "getCollectionItemInfo"):
        item_info = node.getCollectionItemInfo()
        if item_info:
            row = getattr(item_info, "getRowIndex", lambda: -1)()
            col = getattr(item_info, "getColumnIndex", lambda: -1)()
            if row >= 0 and col >= 0:
                positions.append(f"Row {row + 1}, Column {col + 1}")
            elif row >= 0:
                positions.append(f"Row {row + 1}")

    if settings.ANNOUNCE_LIST_COUNT and hasattr(node, "getCollectionInfo"):
        collection_info = node.getCollectionInfo()
        if collection_info:
            count = getattr(collection_info, "getItemCount", lambda: -1)()
            if count > 0:
                positions.append(f"List with {count} items")

    return positions


def get_view_id_resource_name(node, settings):
    if settings.ANNOUNCE_VIEW_IDS and hasattr(node, "getViewIdResourceName"):
        res_name = node.getViewIdResourceName()
        if res_name:
            short_id = res_name.split("/")[-1] if "/" in res_name else res_name
            return f"ID: {short_id}"
    return ""


def get_node_raw_text(node):
    """Combines text, content description, hint, or error message cleanly without skipping attributes."""
    if node is None:
        return ""

    if hasattr(node, "getError"):
        err = node.getError()
        if err:
            return f"Error: {err}".strip()

    text_parts = []

    if hasattr(node, "getText"):
        text = node.getText()
        if text:
            text_str = str(text).strip()
            if text_str:
                text_parts.append(text_str)

    if hasattr(node, "getContentDescription"):
        content_desc = node.getContentDescription()
        if content_desc:
            desc_str = str(content_desc).strip()
            if desc_str and desc_str not in text_parts:
                text_parts.append(desc_str)

    if hasattr(node, "getHintText") and not text_parts:
        hint = node.getHintText()
        if hint:
            hint_str = str(hint).strip()
            if hint_str:
                text_parts.append(hint_str)

    if text_parts:
        return ", ".join(text_parts)

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
    if not text:
        return []
    return [w for w in text.split() if w]


def get_characters(text):
    if not text:
        return []
    return list(text)


def format_node_speech(node, settings):
    if node is None:
        return ""

    class_name = str(node.getClassName()) if hasattr(node, "getClassName") and node.getClassName() else ""
    raw_text = get_node_raw_text(node)

    if settings.IGNORE_DECORATIVE_IMAGES and "ImageView" in class_name and not raw_text:
        return ""

    if raw_text and getattr(settings, "GEMINI_API_KEY", ""):
        from ai_service import ai_service_instance
        if getattr(settings, "AUTO_TRANSLATE_ENABLED", False):
            target_lang = getattr(settings, "TRANSLATION_TARGET_LANGUAGE", "Hindi")
            raw_text = ai_service_instance.translate_text(raw_text, target_lang)
        elif getattr(settings, "SIMPLIFY_TEXT_ENABLED", False):
            raw_text = ai_service_instance.rewrite_simplified(raw_text)

    raw_text = apply_punctuation_verbosity(raw_text, settings.PUNCTUATION_VERBOSITY)
    
    role = get_role_description(node) if settings.ANNOUNCE_ELEMENT_TYPES else ""
    states = get_state_description(node) if settings.ANNOUNCE_ELEMENT_STATE else []
    positions = get_grid_or_list_position(node, settings)
    view_id = get_view_id_resource_name(node, settings)

    parts = []

    if raw_text:
        parts.append(raw_text)

    if role:
        parts.append(role)

    if states:
        parts.extend(states)

    if positions:
        parts.extend(positions)

    if view_id:
        parts.append(view_id)

    if settings.VERBOSITY_LEVEL == "low":
        return raw_text if raw_text else role

    return ", ".join(parts)
