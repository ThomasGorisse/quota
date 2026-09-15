#!/usr/bin/env python3
"""Quota collector: reads Claude, Codex and Gemini usage on this Mac and posts it to the worker.

Zero third-party dependencies. Credentials never leave this machine: only percentages
and reset times are sent. The device token is stored in ~/.quota/config.json and is
never printed to the log.

Run once by hand to pair, then every 180 s by the LaunchAgent (see install.sh).
"""

from __future__ import annotations

import base64
import json
import math
import os
import re
import secrets
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

HOME = Path.home()
CONFIG_DIR = HOME / ".quota"
CONFIG_PATH = CONFIG_DIR / "config.json"
PAIRING_PATH = CONFIG_DIR / "pairing.txt"
CACHE_PATH = CONFIG_DIR / "last-good.json"

# Keep the last successful reading for a day during temporary provider failures.
# The Worker exposes its real measured_at and per-provider stale flag; clients grey
# readings after ten minutes, independently of the other providers' refreshes.
CACHE_MAX_AGE = 24 * 60 * 60

DEFAULT_WORKER_URL = "https://quota.sceneview.dev"

CLAUDE_USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
CLAUDE_KEYCHAIN_SERVICE = "Claude Code-credentials"
CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
CODEX_AUTH_PATH = HOME / ".codex" / "auth.json"

# Gemini CLI writes its OAuth credential here once you have run `gemini` and signed in.
# Absent on a Mac that has never done it — the collector then skips Gemini in silence.
GEMINI_CREDS_PATH = HOME / ".gemini" / "oauth_creds.json"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
CODE_ASSIST_HOST = "https://cloudcode-pa.googleapis.com"
# Windows worth a ring: more than three and every client has to invent a layout.
GEMINI_MAX_WINDOWS = 3

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


def post_json(url: str, headers: dict[str, str], body: bytes) -> Any:
    headers.setdefault("User-Agent", USER_AGENT)
    request = urllib.request.Request(url, data=body, headers=headers, method="POST")
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
        block = data.get(key)
        if not isinstance(block, dict):
            continue
        value = block.get("utilization")
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 100:
            continue
        windows.append(
            {
                "id": window_id,
                "label": label,
                "percent": round(float(value), 1),
                "resets_at": normalize_iso(block.get("resets_at")),
            }
        )
    windows.extend(claude_scoped_windows(data.get("limits"), {w["id"] for w in windows}))
    extra_usage = data.get("extra_usage") or {}
    return {
        "plan": plan,
        "windows": windows,
        "extra": {"extra_usage_enabled": bool(extra_usage.get("is_enabled"))},
    }


def claude_scoped_windows(limits: Any, taken: set[str]) -> list[dict[str, Any]]:
    """Per-model weekly windows, from the `limits` array of the usage response.

    `five_hour`/`seven_day` stay the source of the first two windows, so an API that
    stops sending `limits` simply drops the per-model rows instead of emptying the card.
    Anything without a finite 0-100 percent, or without a model display name, is skipped:
    a missing reading is never a full bucket.
    """
    out: list[dict[str, Any]] = []
    if not isinstance(limits, list):
        return out
    for entry in limits:
        if not isinstance(entry, dict) or entry.get("kind") != "weekly_scoped":
            continue
        model = ((entry.get("scope") or {}).get("model") or {}) if isinstance(entry.get("scope"), dict) else {}
        name = model.get("display_name") if isinstance(model, dict) else None
        if not isinstance(name, str) or not name.strip():
            continue
        value = entry.get("percent")
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 100:
            continue
        slug = re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", name.strip().lower())).strip("_")
        window_id = f"weekly_{slug}" if slug else "weekly_scoped"
        if window_id in taken:
            continue
        taken.add(window_id)
        out.append(
            {
                "id": window_id,
                "label": f"Week · {name.strip()}",
                "percent": round(float(value), 1),
                "resets_at": normalize_iso(entry.get("resets_at")),
            }
        )
    return out


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
        value = block.get("used_percent")
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 100:
            return None
        # The plan decides what the primary window is (Plus: 5 h + weekly; Pro right
        # after the upgrade: a single 7-day window), so trust the declared duration
        # over the position.
        seconds = block.get("limit_window_seconds")
        if isinstance(seconds, (int, float)) and seconds > 0 and window_id != "reserve":
            if seconds >= 5 * 86400:
                window_id, label = "weekly", "Week"
            elif seconds <= 6 * 3600:
                window_id, label = "session", "5 h"
            else:
                window_id, label = "session", f"{round(seconds / 3600)} h"
        return {
            "id": window_id,
            "label": label,
            "percent": round(float(value), 1),
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
        "plan": CODEX_PLAN_NAMES.get(data.get("plan_type") or "", data.get("plan_type")) or None,
        "windows": windows,
        "extra": {
            "astra_available": bool(astra.get("available")),
            "limit_reached": bool(rate_limit.get("limit_reached")),
        },
    }


# --------------------------------------------------------------------------- Gemini


class GeminiUnavailable(Exception):
    """Gemini is simply not set up on this Mac. Not an error worth a red card."""


# The Gemini CLI is an installed OAuth app: its client id and secret are constants shipped
# inside the package, not a credential of this account. They are still not written down in
# this repository — they are read back out of the installed CLI at run time, the way
# `agy-quota` reads them out of its own binary. Override for a test with the two env vars.
GOOGLE_CLIENT_ID_RE = re.compile(r"[0-9]{10,}-[a-z0-9]{16,}\.apps\.googleusercontent\.com")
GOOGLE_CLIENT_SECRET_RE = re.compile(r"GOCSPX-[A-Za-z0-9_-]{20,}")


def gemini_cli_roots() -> list[Path]:
    """Directories that may hold the installed `@google/gemini-cli` bundle."""
    roots: list[Path] = []
    override = os.environ.get("QUOTA_GEMINI_CLI_DIR")
    if override:
        roots.append(Path(override))
    executable = shutil.which("gemini")
    if executable:
        # npm installs a shim in .../bin/gemini pointing into .../lib/node_modules/@google/…
        resolved = Path(executable).resolve()
        roots.extend([resolved.parent, *resolved.parents[:4]])
    for parent in (HOME / ".nvm/versions", Path("/usr/local/lib"), Path("/opt/homebrew/lib")):
        roots.append(parent)
    return [root for root in roots if root.exists()]


def gemini_oauth_clients() -> list[tuple[str, str]]:
    """(client_id, client_secret) pairs to try, best first. Never logged, never sent on."""
    env_id = os.environ.get("QUOTA_GEMINI_CLIENT_ID")
    env_secret = os.environ.get("QUOTA_GEMINI_CLIENT_SECRET")
    if env_id and env_secret:
        return [(env_id, env_secret)]

    ids: list[str] = []
    client_secrets: list[str] = []
    scanned = 0
    for root in gemini_cli_roots():
        for path in root.rglob("*.js"):
            if "gemini" not in str(path).lower():
                continue
            scanned += 1
            if scanned > 400:
                break
            try:
                text = path.read_text(errors="ignore")
            except OSError:
                continue
            ids.extend(m for m in GOOGLE_CLIENT_ID_RE.findall(text) if m not in ids)
            client_secrets.extend(
                m for m in GOOGLE_CLIENT_SECRET_RE.findall(text) if m not in client_secrets
            )
        if ids and client_secrets:
            break
    if not ids or not client_secrets:
        raise GeminiUnavailable("Gemini CLI OAuth client not found")
    return [(i, s) for i in ids for s in client_secrets]


def gemini_access_token(refresh_token: str) -> str:
    """Exchange the CLI's refresh token for a short-lived access token."""
    last = "no client candidate"
    for client_id, client_secret in gemini_oauth_clients():
        body = urllib.parse.urlencode(
            {
                "client_id": client_id,
                "client_secret": client_secret,
                "refresh_token": refresh_token,
                "grant_type": "refresh_token",
            }
        ).encode()
        try:
            data = post_json(
                GOOGLE_TOKEN_URL,
                {"Content-Type": "application/x-www-form-urlencoded"},
                body,
            )
        except urllib.error.HTTPError as error:
            last = f"HTTP {error.code}"
            continue
        token = data.get("access_token")
        if token:
            return token
        last = "no access_token in the response"
    # Every candidate was refused: the CLI credential is revoked or expired, which is the
    # same thing to us as never having signed in. A transient network failure is different
    # — urlopen raises URLError straight out of the loop and still earns an error card.
    raise GeminiUnavailable(f"token refresh refused ({last})")


def code_assist(endpoint: str, access_token: str, body: dict[str, Any] | None = None) -> Any:
    """A Code Assist call. 401/403 means this account has no Code Assist: skip, don't shout."""
    try:
        return post_json(
            f"{CODE_ASSIST_HOST}/v1internal:{endpoint}",
            {"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
            json.dumps(body or {}).encode(),
        )
    except urllib.error.HTTPError as error:
        if error.code in (401, 403):
            raise GeminiUnavailable(f"Code Assist refused the credential (HTTP {error.code})") from error
        raise


GEMINI_TIER_NAMES = {"FREE": "Free", "STANDARD": "Standard", "ENTERPRISE": "Enterprise"}


def gemini_onboarding(access_token: str) -> dict[str, Any]:
    """loadCodeAssist, called once: it carries both the tier and the project id.

    Best effort — a malformed answer costs a plan label and a project hint, not the run.
    A 401/403 is not swallowed here: it means the account has no Code Assist at all, and
    `collect_gemini` must skip the provider rather than draw an empty card.
    """
    try:
        data = code_assist("loadCodeAssist", access_token)
    except GeminiUnavailable:
        raise
    except Exception:  # noqa: BLE001 - the quota buckets are the payload that matters
        return {}
    return data if isinstance(data, dict) else {}


def gemini_plan(onboarding: dict[str, Any]) -> str | None:
    """`currentTier.id` from loadCodeAssist. Best effort: a tier is not worth a red card."""
    tier_block = onboarding.get("currentTier")
    tier = tier_block.get("id") if isinstance(tier_block, dict) else None
    if not isinstance(tier, str) or not tier:
        return None
    return GEMINI_TIER_NAMES.get(tier.upper(), tier.replace("_", " ").title())


def gemini_window_id(model_id: str) -> str:
    """A stable, url-safe window id: `gemini-2.5-pro` -> `gemini-2-5-pro`."""
    cleaned = "".join(c if c.isalnum() else "-" for c in model_id.lower()).strip("-")
    while "--" in cleaned:
        cleaned = cleaned.replace("--", "-")
    return cleaned or "quota"


def gemini_window_label(model_id: str) -> str:
    """`gemini-2.5-pro` -> `2.5 Pro`: on a card already titled Gemini, the model is the label."""
    name = model_id.strip()
    for prefix in ("gemini-", "models/gemini-", "models/"):
        if name.lower().startswith(prefix):
            name = name[len(prefix):]
            break
    words = [w for w in name.replace("_", "-").split("-") if w]
    return " ".join(w if w[0].isdigit() else w.capitalize() for w in words) or "Quota"


def collect_gemini() -> dict[str, Any]:
    """Code Assist quota buckets, most-consumed first: the headline window is the worst one."""
    try:
        credentials = json.loads(GEMINI_CREDS_PATH.read_text())
    except FileNotFoundError as error:
        raise GeminiUnavailable("Gemini CLI not signed in") from error
    except OSError as error:  # unreadable: a credential we cannot use is one we do not have
        raise GeminiUnavailable(f"Gemini credential unreadable ({error.strerror})") from error
    except json.JSONDecodeError as error:
        raise GeminiUnavailable("Gemini credential is not valid JSON") from error
    if not isinstance(credentials, dict):
        raise GeminiUnavailable("Gemini credential is not a JSON object")
    refresh_token = credentials.get("refresh_token")
    if not isinstance(refresh_token, str) or not refresh_token:
        raise GeminiUnavailable("no refresh_token in the Gemini credential")

    # The stored access_token is deliberately ignored: it is minutes-lived, and writing a
    # refreshed one back would race the Gemini CLI for its own file. One refresh per run.
    access_token = gemini_access_token(refresh_token)
    onboarding = gemini_onboarding(access_token)
    # Code Assist meters quota per project; `retrieveUserQuota` wants the one loadCodeAssist
    # just handed back. Sending none is only right for an account that has no project.
    project = onboarding.get("cloudaicompanionProject")
    request = {"cloudaicompanionProject": project} if isinstance(project, str) and project else {}
    payload = code_assist("retrieveUserQuota", access_token, request)
    buckets = payload.get("buckets") if isinstance(payload, dict) else None

    windows = []
    for bucket in buckets or []:
        if not isinstance(bucket, dict):
            continue
        fraction = bucket.get("remainingFraction")
        # `isinstance(True, int)` is True, and NaN survives every comparison: exclude both.
        if isinstance(fraction, bool) or not isinstance(fraction, (int, float)):
            continue
        fraction = float(fraction)
        if not math.isfinite(fraction):
            continue
        fraction = min(1.0, max(0.0, fraction))  # a ring cannot go past either end
        model_id = str(bucket.get("modelId") or "quota")
        windows.append(
            {
                "id": gemini_window_id(model_id),
                "label": gemini_window_label(model_id),
                # One decimal, like Claude and Codex: the clients round for display.
                "percent": round((1 - fraction) * 100, 1),
                "resets_at": normalize_iso(bucket.get("resetTime")),
            }
        )
    # Most consumed first: that bucket is the headline every one-ring surface shows.
    windows.sort(key=lambda w: -w["percent"])
    if not windows:
        # A card with no ring is worse than no card: the docstring on `collect` is the rule.
        raise GeminiUnavailable("Code Assist reported no quota bucket")
    return {
        "plan": gemini_plan(onboarding),
        "windows": windows[:GEMINI_MAX_WINDOWS],
        "extra": {"buckets": len(windows)},
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


def read_cache(path: Path = CACHE_PATH) -> dict[str, Any]:
    """Last good reading per provider. A corrupt or missing file is simply no cache."""
    try:
        data = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def write_cache(cache: dict[str, Any], path: Path = CACHE_PATH) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(cache))
    except OSError as error:  # a cache we cannot write is not worth failing a run for
        log(f"cache not written: {error.strerror}")


def from_cache(entry: Any, reason: str, now: datetime) -> dict[str, Any] | None:
    """The cached provider with its original measurement time, or None past retention."""
    if not isinstance(entry, dict) or not entry.get("windows"):
        return None
    measured = normalize_iso(entry.get("measured_at"))
    if not measured:
        return None
    age = (now - datetime.fromisoformat(measured.replace("Z", "+00:00"))).total_seconds()
    if age < 0 or age > CACHE_MAX_AGE:
        return None
    extra = dict(entry.get("extra") or {})
    extra.update({"error": reason, "measured_at": measured, "age_seconds": int(age)})
    return {"plan": entry.get("plan"), "windows": entry["windows"], "extra": extra}


def collect(cache: dict[str, Any] | None = None) -> dict[str, Any]:
    """One provider failing must never take the others down.

    A provider that is not set up on this Mac at all is left **out** of the payload rather
    than reported empty: every client hides a provider it gets no data for, and an empty
    Gemini card would be worse than no card until Thomas has signed the CLI in once.

    A provider that *is* set up but fails transiently — Claude answers HTTP 429 for half
    an hour at a time — is served from the last good reading instead, carrying the age it
    was measured at. Sending it empty made the card vanish from every screen, and made the
    TRMNL board read the absent value as 0% used, i.e. a full ring.
    """
    now = datetime.now(timezone.utc)
    # Only a run that owns the on-disk cache writes it back; tests pass their own.
    owns_cache = cache is None
    cache = read_cache() if owns_cache else cache
    providers: dict[str, Any] = {}
    for name, collector in (("claude", collect_claude), ("codex", collect_codex), ("gemini", collect_gemini)):
        try:
            providers[name] = collector()
            cache[name] = {
                "plan": providers[name].get("plan"),
                "windows": providers[name].get("windows", []),
                "measured_at": iso(now),
            }
        except GeminiUnavailable:
            # Never set up on this Mac: no card, and no line in a log that runs every
            # few minutes forever. `summary()` already says which providers were read.
            pass
        except Exception as error:  # noqa: BLE001 - a partial report beats no report
            reason = describe(error)
            fallback = from_cache(cache.get(name), reason, now)
            log(f"{name}: {reason}" + (f" (serving the reading from {fallback['extra']['measured_at']})" if fallback else ""))
            providers[name] = fallback or {"plan": None, "windows": [], "extra": {"error": reason}}
    if owns_cache:
        write_cache(cache)
    return {"collected_at": iso(now), "providers": providers}


FALLBACK_WORKER_URL = "https://quota.mcp-tools-lab.workers.dev"
# ChatGPT plan slugs as the usage endpoint reports them, mapped to their public names.
CODEX_PLAN_NAMES = {"plus": "Plus", "prolite": "Pro 5x", "pro": "Pro 20x", "free": "Free", "team": "Team", "enterprise": "Enterprise"}


def send(config: dict[str, Any], snapshot: dict[str, Any]) -> None:
    """POST to the worker; on a DNS failure retry once on the workers.dev host.

    macOS keeps a negative DNS cache for a newly created custom domain long after
    the record exists, and flushing it needs sudo; the workers.dev name has no such
    history, so it is the safety net."""
    try:
        _post(config["worker_url"], config, snapshot)
    except urllib.error.URLError as error:
        if "nodename nor servname" not in str(error.reason) or config["worker_url"].startswith(FALLBACK_WORKER_URL):
            raise
        _post(FALLBACK_WORKER_URL, config, snapshot)


def _post(base_url: str, config: dict[str, Any], snapshot: dict[str, Any]) -> None:
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/v1/ingest",
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
        if not provider["windows"]:
            parts.append(f"{name} error")
            continue
        cached = " (cached)" if (provider.get("extra") or {}).get("measured_at") else ""
        parts.append(
            f"{name} " + " ".join(f"{w['id']}={w['percent']}%" for w in provider["windows"]) + cached
        )
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
