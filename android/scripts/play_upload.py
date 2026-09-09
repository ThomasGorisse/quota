#!/usr/bin/env python3
"""Upload an AAB to a Google Play track.

Stdlib only, plus the system `openssl` for the RS256 signature — the same shape as
ar-model-viewer's `store/scripts/sync-play.py`, so this machine needs no pip install and
no `google-api-python-client`. Driven by `sync-play.sh`; run it directly only if you know
which edit you are opening.

    play_upload.py --aab <path> --package <id> --track internal [--draft] [--dry-run]

Auth: a service-account JSON, from `PLAY_SERVICE_ACCOUNT_JSON` or the default path below.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://androidpublisher.googleapis.com"
TOKEN_URL = "https://oauth2.googleapis.com/token"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

# The service account already used for AR Model Viewer / Will It Fit. It lives outside
# every repo, in the private profile clone. Override with PLAY_SERVICE_ACCOUNT_JSON.
DEFAULT_SA = os.path.expanduser(
    "~/Projects/ThomasGorisse/profile-private/credentials/arcamera/play-service-account.json"
)


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def load_sa() -> dict:
    path = Path(os.path.expanduser(os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", DEFAULT_SA)))
    if not path.is_file():
        raise SystemExit(
            f"error: no service-account JSON at {path}\n"
            "  Set PLAY_SERVICE_ACCOUNT_JSON=/path/to/key.json, or create one:\n"
            "  GCP console -> IAM -> Service Accounts -> Keys -> JSON, enable the\n"
            "  'Google Play Android Developer API', then invite the service-account\n"
            "  email in Play Console -> Users and permissions."
        )
    return json.loads(path.read_text())


def sign_rs256(message: bytes, private_key_pem: str) -> bytes:
    """RS256 via the openssl CLI, so no crypto dependency is needed."""
    try:  # If google-auth happens to be installed, use it and skip the subprocess.
        from google.auth.crypt import RSASigner  # type: ignore

        return RSASigner.from_string(private_key_pem).sign(message)
    except ImportError:
        pass
    tmp = tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False)
    try:
        tmp.write(private_key_pem)
        tmp.close()
        return subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", tmp.name],
            input=message, capture_output=True, check=True,
        ).stdout
    finally:
        os.unlink(tmp.name)


class Play:
    def __init__(self, sa: dict) -> None:
        self._sa = sa
        self._token: str | None = None

    @property
    def token(self) -> str:
        if self._token is None:
            now = int(time.time())
            claims = {
                "iss": self._sa["client_email"], "scope": SCOPE,
                "aud": TOKEN_URL, "iat": now, "exp": now + 3600,
            }
            unsigned = (
                b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
                + "." + b64url(json.dumps(claims).encode())
            ).encode()
            assertion = unsigned + b"." + b64url(
                sign_rs256(unsigned, self._sa["private_key"])
            ).encode()
            body = urllib.parse.urlencode({
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": assertion.decode(),
            }).encode()
            with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, data=body)) as r:
                self._token = json.loads(r.read())["access_token"]
        return self._token

    def call(self, method: str, path: str, body: dict | None = None) -> dict | None:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        if data:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req) as r:
                raw = r.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            if e.code == 404:
                return None
            if e.code == 401:
                raise SystemExit(
                    f"error: 401 from Play. The service account "
                    f"({self._sa.get('client_email')}) is authenticated but not authorised "
                    f"for this app, or the Play Developer API is not enabled.\n{detail}"
                )
            if e.code == 403:
                raise SystemExit(
                    f"error: 403 from Play on {method} {path}.\n"
                    f"  Usually the service account has no access to this package yet:\n"
                    f"  Play Console -> Users and permissions -> invite "
                    f"{self._sa.get('client_email')} with 'Release to testing tracks'.\n"
                    f"  (Play also returns 403 for validation failures such as an\n"
                    f"  over-long release note, so read the body before assuming access.)\n"
                    f"{detail}"
                )
            raise SystemExit(f"error: HTTP {e.code} on {method} {path}\n{detail}")

    def upload_bundle(self, pkg: str, edit_id: str, aab: Path) -> int:
        url = (f"{BASE}/upload/androidpublisher/v3/applications/{pkg}"
               f"/edits/{edit_id}/bundles?uploadType=media")
        req = urllib.request.Request(url, data=aab.read_bytes(), method="POST")
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Content-Type", "application/octet-stream")
        try:
            with urllib.request.urlopen(req) as r:
                return json.loads(r.read())["versionCode"]
        except urllib.error.HTTPError as e:
            raise SystemExit(f"error: upload failed, HTTP {e.code}\n{e.read().decode()}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--aab", required=True)
    p.add_argument("--package", required=True)
    p.add_argument("--track", default="internal")
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--release-name", default=None)
    p.add_argument("--draft", action="store_true",
                   help="Play refuses any status but draft on an app that has never "
                        "been published; use this for the very first upload.")
    p.add_argument("--dry-run", action="store_true",
                   help="Open an edit, check the versionCode, then delete the edit.")
    a = p.parse_args()

    aab = Path(a.aab)
    if not aab.is_file():
        raise SystemExit(f"error: AAB not found at {aab}")

    api = Play(load_sa())
    pkg = a.package

    edit = api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits", body={})
    if edit is None:
        raise SystemExit(
            f"error: Play does not know the package '{pkg}'.\n"
            "  The app must be created in the Play Console first — that step is manual."
        )
    edit_id = edit["id"]

    committed = False
    version_code: int | None = None
    try:
        # Duplicate versionCode: Play would reject it at commit, but only after the whole
        # bundle has gone up the wire. Check the already-uploaded bundles first.
        existing = api.call(
            "GET", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/bundles") or {}
        codes = {b["versionCode"] for b in existing.get("bundles", [])}
        if a.version_code in codes:
            raise SystemExit(
                f"error: versionCode {a.version_code} is already on Play "
                f"(seen: {sorted(codes)[-5:]}).\n"
                f"  Bump it: ./gradlew :app:bundleRelease -PVERSION_CODE={max(codes) + 1}\n"
                f"  or re-run sync-play.sh with --version-code {max(codes) + 1}."
            )

        if a.dry_run:
            print(f"dry run: versionCode {a.version_code} is free on '{pkg}' "
                  f"(existing: {sorted(codes) or 'none'}); nothing uploaded.")
            api.call("DELETE", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}")
            committed = True  # nothing to warn about
            return

        print(f"uploading {aab.name} ({aab.stat().st_size:,} bytes) into edit {edit_id}")
        version_code = api.upload_bundle(pkg, edit_id, aab)
        print(f"uploaded, versionCode={version_code} (NOT published yet)")

        release: dict = {
            "name": a.release_name or f"{a.version_code}",
            "versionCodes": [str(version_code)],
            "status": "draft" if a.draft else "completed",
        }
        api.call(
            "PUT",
            f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}/tracks/{a.track}",
            body={"track": a.track, "releases": [release]},
        )
        api.call("POST", f"/androidpublisher/v3/applications/{pkg}/edits/{edit_id}:commit")
        committed = True
    finally:
        if not committed:
            # An AAB uploaded into an edit that never commits DOES NOT EXIST on Play: the
            # bundle dies with the edit. Without this the last line on screen is
            # "uploaded, versionCode=N", which reads exactly like success.
            print(
                f"\nFAILED before commit — edit {edit_id} is abandoned.\n"
                + (f"versionCode {version_code} went into that edit and therefore does not\n"
                   "exist on Play. Nothing was published.\n" if version_code else
                   "Nothing was uploaded.\n")
                + "Fix the cause and re-run the same command.",
                file=sys.stderr,
            )

    if not a.dry_run:
        state = "as a DRAFT (release it from the console)" if a.draft else "and rolled out"
        print(f"committed to track '{a.track}' {state}.")
        print(f"verify: https://play.google.com/console -> {pkg} -> Testing -> Internal testing")


if __name__ == "__main__":
    main()
