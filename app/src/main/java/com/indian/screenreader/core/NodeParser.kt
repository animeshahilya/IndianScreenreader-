package com.indian.screenreader.core

import android.view.accessibility.AccessibilityNodeInfo

object NodeParser {

    private val CLASS_ROLE_MAP = mapOf(
        // Standard Android Widgets
        "android.widget.Button" to "Button",
        "android.widget.ImageButton" to "Button",
        "android.widget.CheckBox" to "Checkbox",
        "android.widget.RadioButton" to "Radio button",
        "android.widget.ToggleButton" to "Toggle button",
        "android.widget.Switch" to "Switch",
        "android.widget.EditText" to "Editing box",
        "android.widget.SeekBar" to "Slider",
        "android.widget.RatingBar" to "Rating bar",
        "android.widget.ImageView" to "Image",
        "android.widget.ProgressBar" to "Progress bar",
        "android.widget.Spinner" to "Drop down menu",
        "android.widget.ListView" to "List",
        "android.widget.GridView" to "Grid",
        "android.widget.TabWidget" to "Tab bar",
        "android.widget.TextView" to "Text",
        // AppCompat / AndroidX Widgets
        "androidx.appcompat.widget.AppCompatButton" to "Button",
        "androidx.appcompat.widget.AppCompatCheckBox" to "Checkbox",
        "androidx.appcompat.widget.AppCompatRadioButton" to "Radio button",
        "androidx.appcompat.widget.SwitchCompat" to "Switch",
        "androidx.appcompat.widget.AppCompatEditText" to "Editing box",
        "androidx.recyclerview.widget.RecyclerView" to "List",
        "androidx.viewpager.widget.ViewPager" to "Page view",
        // Material Design Components
        "com.google.android.material.button.MaterialButton" to "Button",
        "com.google.android.material.textfield.TextInputEditText" to "Editing box",
        "com.google.android.material.switchmaterial.SwitchMaterial" to "Switch",
        "com.google.android.material.chip.Chip" to "Chip",
        "com.google.android.material.tabs.TabLayout" to "Tab bar",
        "com.google.android.material.floatingactionbutton.FloatingActionButton" to "Action button"
    )

    private val PUNCTUATION_MAP_ALL = mapOf(
        "." to " period ", "," to " comma ", ":" to " colon ", ";" to " semicolon ",
        "?" to " question mark ", "!" to " exclamation mark ", "/" to " slash ",
        "\\" to " backslash ", "@" to " at sign ", "#" to " hash ", "$" to " dollar ",
        "%" to " percent ", "-" to " dash ", "_" to " underscore ", "+" to " plus ",
        "=" to " equals ", "*" to " star ", "&" to " ampersand ", "(" to " open paren ",
        ")" to " close paren ", "<" to " less than ", ">" to " greater than ",
        "[" to " open bracket ", "]" to " close bracket "
    )

    private val PUNCTUATION_MAP_SOME = mapOf(
        "@" to " at sign ", "#" to " hash ", "$" to " dollar ", "%" to " percent ",
        "/" to " slash ", "\\" to " backslash ", "+" to " plus ", "=" to " equals "
    )

    private fun getRoleByClassName(className: CharSequence?): String {
        return className?.let { CLASS_ROLE_MAP[it.toString()] } ?: ""
    }

    private fun applyPronunciationDict(text: String): String {
        var result = text
        Settings.PRONUNCIATION_DICT.forEach { (key, replacement) ->
            // Use word-boundary regex to avoid replacing substrings inside words
            // e.g. "pan" should not match inside "Japan"
            result = result.replace(Regex("(?i)\\b${Regex.escape(key)}\\b"), replacement)
        }
        return result
    }

    private fun applyPunctuationVerbosity(text: String, mode: String): String {
        if (text.isBlank() || mode == "none") return text
        var result = text
        val map = if (mode == "all") PUNCTUATION_MAP_ALL else PUNCTUATION_MAP_SOME
        map.forEach { (char, spoken) ->
            result = result.replace(char, spoken)
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun formatCapitalization(text: String, mode: String): String {
        if (text.isBlank() || mode == "none") return text
        // Single uppercase letter: announce "Cap X"
        if (text.length == 1 && text.first().isUpperCase()) {
            if (mode == "prefix") return "Cap $text"
        }
        // All-caps word (2+ uppercase letters, no lowercase): announce "Caps: WORD"
        if (mode == "prefix" && text.length > 1 && text.all { it.isUpperCase() || !it.isLetter() } && text.any { it.isUpperCase() }) {
            return "Caps: $text"
        }
        return text
    }

    private fun getRoleDescription(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        return getRoleByClassName(node.className)
    }

    fun isHeading(node: AccessibilityNodeInfo?): Boolean {
        return node?.isHeading == true
    }

    fun isControl(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val role = getRoleByClassName(node.className)
        if (role.isNotEmpty()) return true
        return node.isClickable || node.isCheckable
    }

    private fun getStateDescription(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val states = mutableListOf<String>()

        if (node.isPassword) {
            val pwText = node.text?.toString() ?: ""
            if (pwText.isNotEmpty()) {
                states.add("Password field, ${pwText.length} characters")
            } else {
                states.add("Password field")
            }
        }

        if (node.isCheckable) {
            if (node.isChecked) states.add("Checked") else states.add("Not checked")
        }

        if (node.isSelected) {
            states.add("Selected")
        }

        if (!node.isEnabled) {
            states.add("Disabled")
        }

        return states
    }

    private fun getGridOrListPosition(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val positions = mutableListOf<String>()

        if (Settings.ANNOUNCE_GRID_POSITION) {
            val itemInfo = node.collectionItemInfo
            if (itemInfo != null) {
                val row = itemInfo.rowIndex
                val col = itemInfo.columnIndex
                if (row >= 0 && col >= 0) {
                    positions.add("Row ${row + 1}, Column ${col + 1}")
                } else if (row >= 0) {
                    positions.add("Row ${row + 1}")
                }
            }
        }

        if (Settings.ANNOUNCE_LIST_COUNT) {
            val collInfo = node.collectionInfo
            if (collInfo != null) {
                val count = collInfo.rowCount * collInfo.columnCount
                if (count > 0) {
                    positions.add("List with $count items")
                }
            }
        }

        return positions
    }

    private fun getViewIdResourceName(node: AccessibilityNodeInfo?): String {
        if (node == null || !Settings.ANNOUNCE_VIEW_IDS) return ""
        val resName = node.viewIdResourceName?.toString() ?: ""
        if (resName.isNotEmpty()) {
            val shortId = if (resName.contains("/")) resName.substringAfterLast("/") else resName
            return "ID: $shortId"
        }
        return ""
    }

    fun getNodeRawText(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 10) return ""

        val textParts = mutableListOf<String>()

        node.error?.let { textParts.add("Error: $it") }
        node.text?.let { textParts.add(it.toString()) }
        node.contentDescription?.let { if (!textParts.contains(it.toString())) textParts.add(it.toString()) }
        node.stateDescription?.let { if (!textParts.contains(it.toString())) textParts.add(it.toString()) }
        node.hintText?.let { if (!textParts.contains(it.toString())) textParts.add(it.toString()) }

        if (textParts.isNotEmpty()) {
            return textParts.joinToString(", ")
        }

        // Recursively search children if no direct text — no arbitrary child limit
        val childCount = node.childCount
        if (childCount > 0) {
            val childTexts = mutableListOf<String>()
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        val childText = getNodeRawText(child, depth + 1)
                        if (childText.isNotBlank()) {
                            childTexts.add(childText)
                        }
                    } finally {
                        child.recycle()
                    }
                }
            }
            if (childTexts.isNotEmpty()) {
                return childTexts.joinToString(" ")
            }
        }

        return ""
    }

    fun formatNodeSpeech(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val className = node.className?.toString() ?: ""
        var rawText = getNodeRawText(node)

        if (Settings.IGNORE_DECORATIVE_IMAGES && className.contains("ImageView") && rawText.isBlank()) {
            return ""
        }

        rawText = applyPronunciationDict(rawText)
        rawText = applyPunctuationVerbosity(rawText, Settings.PUNCTUATION_VERBOSITY)
        rawText = formatCapitalization(rawText, Settings.CAPITALIZATION_ANNOUNCEMENT)

        val role = if (Settings.ANNOUNCE_ELEMENT_TYPES) getRoleDescription(node) else ""
        val states = if (Settings.ANNOUNCE_ELEMENT_STATE) getStateDescription(node) else emptyList()
        val positions = getGridOrListPosition(node)
        val viewId = getViewIdResourceName(node)

        var isUnlabeled = false
        if (rawText.isBlank() && (node.isFocusable || node.isClickable)) {
            rawText = "Unlabeled"
            isUnlabeled = true
        }

        if (Settings.VERBOSITY_LEVEL == "low") {
            if (isUnlabeled && role.isNotEmpty()) {
                return "$rawText, $role"
            }
            return if (rawText.isNotBlank()) rawText else role
        }

        val parts = mutableListOf<String>()
        if (rawText.isNotBlank()) parts.add(rawText)
        if (role.isNotBlank()) parts.add(role)
        parts.addAll(states)
        parts.addAll(positions)
        if (viewId.isNotBlank()) parts.add(viewId)

        return parts.joinToString(", ")
    }
}
