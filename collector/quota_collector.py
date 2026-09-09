#!/usr/bin/env python3
"""Quota collector: reads Claude and Codex usage on this Mac and posts it to the worker.

Zero third-party dependencies. Credentials never leave this machine: only percentages
and reset times are sent. The device token is stored in ~/.quota/config.json and is
never printed to the log.

Run once by hand to pair, then every 180 s by the LaunchAgent (see install.sh).
"""

from __future__ import annotations

import base64
import json
import os
import secrets
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

HOME = Path.home()
CONFIG_DIR = HOME / ".quota"
CONFIG_PATH = CONFIG_DIR / "config.json"
PAIRING_PATH = CONFIG_DIR / "pairing.txt"

DEFAULT_WORKER_URL = "https://quota.mcp-tools-lab.workers.dev"

CLAUDE_USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
CLAUDE_KEYCHAIN_SERVICE = "Claude Code-credentials"
CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
CODEX_AUTH_PATH = HOME / ".codex" / "auth.json"

TIMEOUT = 20
# Cloudflare answers 1010 to the default urllib User-Agent, so always send our own.
USER_AGENT = "quota-collector/1.0"


def log(message: str) -> None:
    """Timestamped stderr line. Never pass a token or a raw credential in here."""
    print(f"{datetime.now(timezone.utc).isoformat(timespec='seconds')} {message}", file=sys.stderr)


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def iso_from_unix(value: Any) -> str | None:
    if not isinstance(value, (int, float)) or value <= 0:
        return None
    return iso(datetime.fromtimestamp(float(value), tz=timezone.utc))


def normalize_iso(value: Any) -> str | None:
    """The Claude API returns +00:00 offsets with microseconds; clients want plain Z."""
    if not isinstance(value, str) or not value:
        return None
    try:
        return iso(datetime.fromisoformat(value.replace("Z", "+00:00")))
    except ValueError:
        return None


def get_json(url: str, headers: dict[str, str]) -> Any:
    headers.setdefault("User-Agent", USER_AGENT)
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.loads(response.read())


# --------------------------------------------------------------------------- config


def load_config() -> dict[str, Any]:
    """Reads ~/.quota/config.json, creating a token and pairing URL on first run."""
    CONFIG_DIR.mkdir(mode=0o700, exist_ok=True)
    config: dict[str, Any] = {}
    if CONFIG_PATH.exists():
        try:
            config = json.loads(CONFIG_PATH.read_text())
        except json.JSONDecodeError:
            log("config.json unreadable, regenerating")
            config = {}

    changed = False
    if not config.get("token"):
        config["token"] = base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip("=")
        changed = True
    if not config.get("worker_url"):
        config["worker_url"] = os.environ.get("QUOTA_WORKER_URL", DEFAULT_WORKER_URL)
        changed = True

    if changed:
        CONFIG_PATH.write_text(json.dumps(config, indent=2) + "\n")
        CONFIG_PATH.chmod(0o600)
        pairing_url = f"{config['worker_url'].rstrip('/')}/#t={config['token']}"
        PAIRING_PATH.write_text(pairing_url + "\n")
        PAIRING_PATH.chmod(0o600)
        print("Quota paired. Open this URL on any screen (it contains your device token):")
        print(pairing_url)
        print(f"Saved to {PAIRING_PATH}")

    return config


# --------------------------------------------------------------------------- Claude


def claude_credentials() -> tuple[str, str | None]:
    """(access token, subscription type) from the login keychain. Never log either."""
    raw = subprocess.run(
        ["security", "find-generic-password", "-s", CLAUDE_KEYCHAIN_SERVICE, "-w"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    oauth = json.loads(raw).get("claudeAiOauth", {})
    token = oauth.get("accessToken")
    if not token:
        raise RuntimeError("no accessToken in the keychain entry")
    return token, oauth.get("subscriptionType")


def collect_claude() -> dict[str, Any]:
    token, plan = claude_credentials()
    data = get_json(
        CLAUDE_USAGE_URL,
        {"Authorization": f"Bearer {token}", "anthropic-beta": "oauth-2025-04-20"},
    )
    windows = []
    for window_id, label, key in (
        ("session", "5 h", "five_hour"),
        ("weekly", "Week", "seven_day"),
    ):
        block = data.get(key) or {}
        windows.append(
            {
                "id": window_id,
                "label": label,
                "percent": round(float(block.get("utilization") or 0), 1),
                "resets_at": normalize_iso(block.get("resets_at")),
            }
        )
    extra_usage = data.get("extra_usage") or {}
    return {
        "plan": plan,
        "windows": windows,
        "extra": {"extra_usage_enabled": bool(extra_usage.get("is_enabled"))},
    }


# ---------------------------------------------------------------------------- Codex


def collect_codex() -> dict[str, Any]:
    auth = json.loads(CODEX_AUTH_PATH.read_text()).get("tokens", {})
    access_token, account_id = auth.get("access_token"), auth.get("account_id")
    if not access_token or not account_id:
        raise RuntimeError("no access_token/account_id in ~/.codex/auth.json")

    data = get_json(
        CODEX_USAGE_URL,
        {
            "Authorization": f"Bearer {access_token}",
            "ChatGPT-Account-Id": account_id,
            "User-Agent": "codex-cli",
        },
    )

    def window(block: Any, window_id: str, label: str) -> dict[str, Any] | None:
        if not isinstance(block, dict):
            return None
        return {
            "id": window_id,
            "label": label,
            "percent": round(float(block.get("used_percent") or 0), 1),
            "resets_at": iso_from_unix(block.get("reset_at")),
        }

    rate_limit = data.get("rate_limit") or {}
    windows = [
        w
        for w in (
            window(rate_limit.get("primary_window"), "session", "5 h"),
            window(rate_limit.get("secondary_window"), "weekly", "Week"),
        )
        if w
    ]

    for entry in data.get("additional_rate_limits") or []:
        if entry.get("limit_name") != "gpt-reserve":
            continue
        reserve = window((entry.get("rate_limit") or {}).get("primary_window"), "reserve", "Reserve")
        if reserve:
            windows.append(reserve)

    astra = (data.get("model_usage") or {}).get("gpt-6-astra") or {}
    return {
        "plan": data.get("plan_type") or None,
        "windows": windows,
        "extra": {
            "astra_available": bool(astra.get("available")),
            "limit_reached": bool(rate_limit.get("limit_reached")),
        },
    }


# ----------------------------------------------------------------------------- main


def describe(error: BaseException) -> str:
    """A short, credential-free reason a provider failed."""
    if isinstance(error, urllib.error.HTTPError):
        return f"HTTP {error.code}"
    if isinstance(error, urllib.error.URLError):
        return f"unreachable: {error.reason}"
    if isinstance(error, subprocess.CalledProcessError):
        return "keychain entry not found"
    if isinstance(error, FileNotFoundError):
        return "credentials file missing"
    return f"{type(error).__name__}: {error}"


def collect() -> dict[str, Any]:
    """One provider failing must never take the other down."""
    providers: dict[str, Any] = {}
    for name, collector in (("claude", collect_claude), ("codex", collect_codex)):
        try:
            providers[name] = collector()
        except Exception as error:  # noqa: BLE001 - a partial report beats no report
            reason = describe(error)
            log(f"{name}: {reason}")
            providers[name] = {"plan": None, "windows": [], "extra": {"error": reason}}
    return {"collected_at": iso(datetime.now(timezone.utc)), "providers": providers}


def send(config: dict[str, Any], snapshot: dict[str, Any]) -> None:
    request = urllib.request.Request(
        f"{config['worker_url'].rstrip('/')}/v1/ingest",
        data=json.dumps(snapshot).encode(),
        headers={
            "Authorization": f"Bearer {config['token']}",
            "Content-Type": "application/json",
            "User-Agent": USER_AGENT,
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        response.read()


def summary(snapshot: dict[str, Any]) -> str:
    parts = []
    for name, provider in snapshot["providers"].items():
        if provider["windows"]:
            parts.append(
                f"{name} " + " ".join(f"{w['id']}={w['percent']}%" for w in provider["windows"])
            )
        else:
            parts.append(f"{name} error")
    return " | ".join(parts)


def main() -> int:
    config = load_config()
    snapshot = collect()
    if not any(p["windows"] for p in snapshot["providers"].values()):
        log("no provider returned data, not sending")
        return 1
    try:
        send(config, snapshot)
    except Exception as error:  # noqa: BLE001 - the next run in 180 s retries
        log(f"ingest failed: {describe(error)}")
        return 1
    log(f"sent: {summary(snapshot)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
