#!/usr/bin/env python3
"""Send a prompt plus N images to the Gemini generateContent API and print the answer.

Usage:
    GEMINI_API_KEY=... python3 tools/gemini-critique.py MODEL PROMPT_FILE IMAGE [IMAGE ...]

Stdlib only. Exits non-zero with the API error body on failure.
"""
import base64
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"


def build_parts(prompt: str, images: list[str]) -> list[dict]:
    parts: list[dict] = [{"text": prompt}]
    for path in images:
        mime = mimetypes.guess_type(path)[0] or "image/png"
        with open(path, "rb") as handle:
            data = base64.b64encode(handle.read()).decode("ascii")
        parts.append({"inline_data": {"mime_type": mime, "data": data}})
        parts.append({"text": f"(previous image: {os.path.basename(path)})"})
    return parts


def extract_text(payload: dict) -> str:
    chunks = []
    for candidate in payload.get("candidates", []):
        for part in candidate.get("content", {}).get("parts", []):
            if "text" in part and not part.get("thought"):
                chunks.append(part["text"])
    return "\n".join(chunks).strip()


def main(argv: list[str]) -> int:
    if len(argv) < 4:
        print(__doc__, file=sys.stderr)
        return 2
    model, prompt_file, images = argv[1], argv[2], argv[3:]

    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        print("GEMINI_API_KEY is not set in the environment", file=sys.stderr)
        return 2

    missing = [p for p in images if not os.path.isfile(p)]
    if missing:
        print("missing image files: " + ", ".join(missing), file=sys.stderr)
        return 2

    with open(prompt_file, encoding="utf-8") as handle:
        prompt = handle.read()

    body = json.dumps(
        {
            "contents": [{"role": "user", "parts": build_parts(prompt, images)}],
            "generationConfig": {"temperature": 0.4, "maxOutputTokens": 32768},
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        ENDPOINT.format(model=model),
        data=body,
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=900) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        print(f"HTTP {error.code} from Gemini API:", file=sys.stderr)
        print(error.read().decode("utf-8", "replace"), file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(f"network error calling Gemini API: {error.reason}", file=sys.stderr)
        return 1

    text = extract_text(payload)
    if not text:
        print("no text in Gemini response:", file=sys.stderr)
        print(json.dumps(payload, indent=2)[:4000], file=sys.stderr)
        return 1

    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
