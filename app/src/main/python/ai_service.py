import json
import re
import sys
import threading
import urllib.request
from settings import active_settings

# Pre-compiled script detection regular expressions for speed
RE_DEVANAGARI = re.compile(r"[\u0900-\u097F]")
RE_TAMIL = re.compile(r"[\u0B80-\u0BFF]")
RE_TELUGU = re.compile(r"[\u0C00-\u0C7F]")
RE_BENGALI = re.compile(r"[\u0980-\u09FF]")


class GeminiAIService:

    GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    def __init__(self):
        self._lock = threading.Lock()
        self._is_summarizing = False

    def get_api_key(self):
        return getattr(active_settings, "GEMINI_API_KEY", "").strip()

    def detect_script_language(self, text):
        """Ultra-fast compiled script language detection."""
        if not text:
            return "en"

        if RE_DEVANAGARI.search(text):
            return "hi"
        elif RE_TAMIL.search(text):
            return "ta"
        elif RE_TELUGU.search(text):
            return "te"
        elif RE_BENGALI.search(text):
            return "bn"

        return "en"

    def make_gemini_request(self, prompt, base64_image=None):
        """Helper to send a JSON payload to Google AI Studio Gemini API using urllib with strict timeout handling."""
        api_key = self.get_api_key()
        if not api_key:
            return "Google AI Studio API Key not set. Please set your API key in settings."

        url = f"{self.GEMINI_API_URL}?key={api_key}"

        parts = []
        if base64_image:
            parts.append({
                "inline_data": {
                    "mime_type": "image/jpeg",
                    "data": base64_image
                }
            })

        parts.append({"text": prompt})

        payload = {
            "contents": [{
                "parts": parts
            }]
        }

        try:
            data_bytes = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                url,
                data=data_bytes,
                headers={"Content-Type": "application/json"},
                method="POST"
            )

            with urllib.request.urlopen(req, timeout=5) as response:
                res_body = response.read().decode("utf-8")
                res_json = json.loads(res_body)

                candidates = res_json.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "").strip()

                return "AI response empty."

        except Exception as e:
            print(f"Error calling Gemini API: {e}", file=sys.stderr)
            return "AI Service unavailable."

    def summarize_screen_async(self, screen_text, callback, error_callback=None):
        """Asynchronously summarizes screen text without blocking UI or Accessibility loops."""
        with self._lock:
            if self._is_summarizing:
                if error_callback:
                    error_callback("Already summarizing. Please wait.")
                return
            self._is_summarizing = True

        def run():
            try:
                summary = self.summarize_screen(screen_text)
                callback(summary)
            except Exception as e:
                if error_callback:
                    error_callback(f"Error: {str(e)}")
            finally:
                with self._lock:
                    self._is_summarizing = False

        threading.Thread(target=run, daemon=True).start()

    def summarize_screen(self, screen_text):
        if not screen_text or not screen_text.strip():
            return "Screen has no readable text to summarize."

        prompt = (
            "You are an AI assistant for a blind screen reader user. "
            "Summarize the following mobile app screen content into 1 or 2 concise, clear sentences:\n\n"
            f"{screen_text}"
        )
        return self.make_gemini_request(prompt)

    def translate_text(self, text, target_language="Hindi"):
        if not text or not text.strip():
            return text

        prompt = (
            f"Translate the following text accurately into {target_language}. "
            "Output ONLY the translated string without commentary:\n\n"
            f"{text}"
        )
        return self.make_gemini_request(prompt)

    def rewrite_simplified(self, text):
        if not text or not text.strip():
            return text

        prompt = (
            "You are an accessibility assistant for visually impaired users. "
            "Simplify and rewrite the following text so it is very easy to understand:\n\n"
            f"{text}"
        )
        return self.make_gemini_request(prompt)

    def describe_unlabeled_icon(self, class_name, context_text=""):
        prompt = (
            "An unlabeled UI button was tapped in a mobile app. "
            f"Widget type: '{class_name}'. Nearby context: '{context_text}'. "
            "In 3 to 5 words, describe what this button likely does."
        )
        return self.make_gemini_request(prompt)

    def describe_image_b64(self, base64_jpeg):
        prompt = (
            "Describe what is in this image concisely in 1 or 2 sentences for a blind user."
        )
        return self.make_gemini_request(prompt, base64_image=base64_jpeg)


# Global AI Service instance
ai_service_instance = GeminiAIService()
