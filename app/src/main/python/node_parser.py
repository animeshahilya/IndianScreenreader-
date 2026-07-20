# Helper module for parsing Android AccessibilityNodeInfo objects
# Optimized with LRU caching, safe null checks, and proper node recycling.
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
    "android.widget.TextView": "Text",
    # AppCompat / AndroidX Widgets
    "androidx.appcompat.widget.AppCompatButton": "Button",
    "androidx.appcompat.widget.AppCompatCheckBox": "Checkbox",
    "androidx.appcompat.widget.AppCompatRadioButton": "Radio button",
    "androidx.appcompat.widget.SwitchCompat": "Switch",
    "androidx.appcompat.widget.AppCompatEditText": "Editing box",
    "androidx.recyclerview.widget.RecyclerView": "List",
    "androidx.viewpager.widget.ViewPager": "Page view",
    # Material Design Components
    "com.google.android.material.button.MaterialButton": "Button",
    "com.google.android.material.textfield.TextInputEditText": "Editing box",
    "com.google.android.material.switchmaterial.SwitchMaterial": "Switch",
    "com.google.android.material.chip.Chip": "Chip",
    "com.google.android.material.tabs.TabLayout": "Tab bar",
    "com.google.android.material.floatingactionbutton.FloatingActionButton": "Action button",
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
    ("<", " less than "),
    (">", " greater than "),
    ("[", " open bracket "),
    ("]", " close bracket "),
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


@functools.lru_cache(maxsize=512)
def get_role_by_class_name(class_name):
    """Fast LRU cached lookup for view class roles."""
    if not class_name:
        return ""
    return CLASS_ROLE_MAP.get(class_name, "")


@functools.lru_cache(maxsize=1024)
def apply_pronunciation_dict_cached(text, dict_items_tuple):
    """Apply pronunciation dictionary substitutions (LRU cached by text + dict snapshot)."""
    if not text or not dict_items_tuple:
        return text
    result = text
    lower = text.lower()
    for key, replacement in dict_items_tuple:
        if key in lower:
            # Case-insensitive whole-word replacement
            try:
                result = re.sub(r'(?i)\b' + re.escape(key) + r'\b', replacement, result)
            except Exception:
                pass
    return result


def apply_pronunciation_dict(text, settings):
    """Apply custom pronunciation dictionary to text."""
    if not text:
        return text
    pron_dict = getattr(settings, "PRONUNCIATION_DICT", {})
    if not pron_dict:
        return text
    # Convert to frozen tuple for LRU cache key
    dict_tuple = tuple(sorted(pron_dict.items()))
    return apply_pronunciation_dict_cached(text, dict_tuple)


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


def _safe_str(obj):
    """Safely convert Java/Kotlin object to Python string."""
    try:
        if obj is None:
            return ""
        return str(obj).strip()
    except Exception:
        return ""


def _safe_bool(obj, attr_name, default=False):
    """Safely call a boolean getter or attribute on a Java/Kotlin object."""
    try:
        val = getattr(obj, attr_name, default)
        if callable(val):
            return bool(val())
        return bool(val)
    except Exception:
        return default


def get_role_description(node):
    if node is None:
        return ""

    try:
        class_name = _safe_str(node.getClassName()) if hasattr(node, "getClassName") else ""
        return get_role_by_class_name(class_name)
    except Exception:
        return ""


def is_heading(node):
    if node is None:
        return False

    try:
        if _safe_bool(node, "isHeading"):
            return True
    except Exception:
        pass

    return False


def is_control(node):
    if node is None:
        return False

    try:
        class_name = _safe_str(node.getClassName()) if hasattr(node, "getClassName") else ""
        if class_name in CLASS_ROLE_MAP:
            return True
        if _safe_bool(node, "isClickable"):
            return True
        if _safe_bool(node, "isCheckable"):
            return True
    except Exception:
        pass

    return False


def get_state_description(node):
    if node is None:
        return []

    states = []
    try:
        if _safe_bool(node, "isPassword"):
            text_val = get_node_raw_text(node)
            if text_val:
                states.append(f"Password field, {len(text_val)} characters")
            else:
                states.append("Password field")

        if _safe_bool(node, "isCheckable"):
            if _safe_bool(node, "isChecked"):
                states.append("Checked")
            else:
                states.append("Not checked")

        if _safe_bool(node, "isSelected"):
            states.append("Selected")

        if hasattr(node, "isEnabled") and not _safe_bool(node, "isEnabled", True):
            states.append("Disabled")
    except Exception:
        pass

    return states


def get_grid_or_list_position(node, settings):
    if node is None:
        return []

    positions = []
    try:
        if settings.ANNOUNCE_GRID_POSITION and hasattr(node, "getCollectionItemInfo"):
            item_info = node.getCollectionItemInfo()
            if item_info:
                try:
                    row = item_info.getRowIndex()
                    col = item_info.getColumnIndex()
                    if row >= 0 and col >= 0:
                        positions.append(f"Row {row + 1}, Column {col + 1}")
                    elif row >= 0:
                        positions.append(f"Row {row + 1}")
                except Exception:
                    pass

        if settings.ANNOUNCE_LIST_COUNT and hasattr(node, "getCollectionInfo"):
            collection_info = node.getCollectionInfo()
            if collection_info:
                try:
                    count = collection_info.getItemCount()
                    if count > 0:
                        positions.append(f"List with {count} items")
                except Exception:
                    pass
    except Exception:
        pass

    return positions


def get_view_id_resource_name(node, settings):
    try:
        if settings.ANNOUNCE_VIEW_IDS and hasattr(node, "getViewIdResourceName"):
            res_name = _safe_str(node.getViewIdResourceName())
            if res_name:
                short_id = res_name.split("/")[-1] if "/" in res_name else res_name
                return f"ID: {short_id}"
    except Exception:
        pass
    return ""


def get_node_raw_text(node):
    """Combines text, content description, hint, or error message cleanly.
    
    IMPORTANT: Does NOT recycle the node — caller is responsible for recycling.
    """
    if node is None:
        return ""

    try:
        text_parts = []

        if hasattr(node, "getError"):
            err = node.getError()
            if err:
                err_str = _safe_str(err)
                if err_str:
                    text_parts.append(f"Error: {err_str}")

        if hasattr(node, "getText"):
            text = node.getText()
            if text:
                text_str = _safe_str(text)
                if text_str:
                    text_parts.append(text_str)

        if hasattr(node, "getContentDescription"):
            content_desc = node.getContentDescription()
            if content_desc:
                desc_str = _safe_str(content_desc)
                if desc_str and desc_str not in text_parts:
                    text_parts.append(desc_str)

        if hasattr(node, "getHintText") and not text_parts:
            hint = node.getHintText()
            if hint:
                hint_str = _safe_str(hint)
                if hint_str:
                    text_parts.append(hint_str)

        if text_parts:
            return ", ".join(text_parts)

        # Recurse into children only if no direct text, with limit to avoid deep stack
        if hasattr(node, "getChildCount"):
            try:
                child_count = node.getChildCount()
                if child_count and child_count > 0:
                    child_texts = []
                    for i in range(min(child_count, 4)):
                        child = node.getChild(i)
                        if child is not None:
                            try:
                                child_text = get_node_raw_text(child)
                                if child_text:
                                    child_texts.append(child_text)
                            finally:
                                try:
                                    child.recycle()
                                except Exception:
                                    pass
                    if child_texts:
                        return " ".join(child_texts)
            except Exception:
                pass
    except Exception:
        pass

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
    """Formats a node into a spoken string. Does NOT recycle the node."""
    if node is None:
        return ""

    try:
        class_name = _safe_str(node.getClassName()) if hasattr(node, "getClassName") else ""
        raw_text = get_node_raw_text(node)

        if settings.IGNORE_DECORATIVE_IMAGES and "ImageView" in class_name and not raw_text:
            return ""

        # Apply pronunciation dictionary (fast cached path)
        raw_text = apply_pronunciation_dict(raw_text, settings)

        # Apply punctuation verbosity
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
    except Exception:
        return ""
