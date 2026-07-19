import json
import re
import sys
import threading
import urllib.request
from settings import active_settings


class GeminiAIService:

    GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    def __init__(self):
        pass

    def get_api_key(self):
        return getattr(active_settings, "GEMINI_API_KEY", "").strip()

    def detect_script_language(self, text):
        """Detects script type (Devanagari/Hindi, Tamil, Telugu, Bengali, Latin/English)."""
        if not text:
            return "en"

        # Devanagari Unicode Range (\u0900-\u097F)
        if re.search(r"[\u0900-\u097F]", text):
            return "hi"
        # Tamil Unicode Range (\u0B80-\u0BFF)
        elif re.search(r"[\u0B80-\u0BFF]", text):
            return "ta"
        # Telugu Unicode Range (\u0C00-\u0C7F)
        elif re.search(r"[\u0C00-\u0C7F]", text):
            return "te"
        # Bengali Unicode Range (\u0980-\u09FF)
        elif re.search(r"[\u0980-\u09FF]", text):
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

            # Strict 5-second timeout for responsiveness
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
            return f"AI Service unavailable."

    def summarize_screen_async(self, screen_text, callback):
        """Asynchronously summarizes screen text without blocking UI or Accessibility loops."""
        def run():
            summary = self.summarize_screen(screen_text)
            callback(summary)

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
