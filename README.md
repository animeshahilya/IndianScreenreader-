<h1 align="center">
  <br>
  Indian Screenreader
  <br>
</h1>

<h4 align="center">A Next-Generation, AI-Powered Screenreader for Android. Built specifically for India.</h4>

<p align="center">
  <a href="#key-features">Key Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#installation">Installation</a> •
  <a href="#accessibility-ui">Accessibility UI</a>
</p>

---

**Indian Screenreader** is a highly advanced Android Accessibility Service designed from the ground up for low-vision and blind users in India. While traditional screenreaders (like TalkBack) are rigid, this screenreader introduces an intelligent Python-bridge architecture to inject **Google Gemini AI** directly into the navigation layer. 

Whether you need complex Hindi text translated on the fly, custom phonetic pronunciations for local acronyms (like IRCTC or UPI), or an AI to summarize a cluttered app screen, the Indian Screenreader handles it flawlessly.

## Key Features

### 🧠 Gemini AI Integration
- **Screen Summarization:** Swipe to trigger a Gemini vision summary of the active screen.
- **Auto-Translate:** Dynamically translates complex English apps into Hindi, Tamil, Telugu, or Bengali.
- **Unlabeled Button Inference:** Uses AI to guess the purpose of unlabeled icons based on context.

### ⚡ Atomic Native Navigation
- **Zero Race Conditions:** Features a completely re-architected, atomic single-pass UI tree querying engine for instantaneous, lag-free swipe navigation.
- **Continuous Reading:** A flawless "Read From Top" and "Read From Here" mode that automatically clears focus and scrolls through long articles naturally.

### 🇮🇳 Built for India
- **Custom Pronunciation Dictionary:** Fixes common TTS pronunciation errors for Indian words, brands, and acronyms (e.g., NEFT, UPI, IRCTC).
- **Dynamic App Profiles:** Automatically changes speech rates and verbosity based on the current app (e.g., faster speech for WhatsApp, high verbosity for web browsers).

### 🛡️ Privacy & NVDA Equivalents
- **Screen Curtain:** Double-tap to turn the physical screen entirely black, preventing shoulder-surfing while you navigate securely via audio.
- **Input Help Mode:** A safe practice mode where gestures announce their actions without actually executing them (inspired by NVDA).
- **Deduplicate Speech:** Intelligently drops redundant speech events.

## Accessibility UI

The app features a **Deep Navy Blue Material 3 UI** accessible directly from your home screen or the Android Accessibility menu.

- **100% Blind-Friendly:** Every single button, toggle, and text input enforces a massive **64dp minimum touch target**.
- **High Contrast:** Hand-crafted WCAG AAA compliant color scheme (Navy and White/Cyan).
- **Screenreader Optimized:** Every UI element has extensive `contentDescription` tags explaining exactly what the setting does.

## Tech Stack

- **Kotlin:** The low-level Android `AccessibilityService` engine. Handles atomic node traversal, haptics, ToneGenerator, and TTS lifecycle.
- **Python (Chaquopy):** The brain of the screenreader. Handles AI API calls, caching, Devanagari regex detection, app profiles, and gesture mapping asynchronously.
- **Google Gemini API:** Powers the complex summarization and language intelligence features.

## Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/animeshahilya/IndianScreenreader-.git
   ```
2. **Build with Gradle:**
   Open the project in Android Studio (Giraffe or newer) and build the APK.
3. **Enable the Service:**
   Go to your Android Device Settings -> Accessibility -> Installed Apps -> **Indian Screenreader** and turn it ON.
4. **Add your Gemini API Key:**
   Open the Indian Screenreader app from your home screen and paste your Gemini API key in the AI Settings card.

## License

MIT License. See `LICENSE` for more information.
