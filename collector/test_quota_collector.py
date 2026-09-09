#!/usr/bin/env python3
"""Unit tests for the collector's Gemini branch, against a recorded Code Assist response.

    python3 collector/test_quota_collector.py

Stdlib only, like the collector itself. Nothing here touches the network, the keychain or
`~/.gemini` — the two HTTP helpers are replaced with canned answers.
"""

import json
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import quota_collector as qc  # noqa: E402

# Recorded from `POST cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota`, shape as
# documented by the prior art (tingyi365/agy-quota). `remainingAmount` is omitted at 100 %.
RETRIEVE_USER_QUOTA = {
    "buckets": [
        {"tokenType": "REQUESTS", "modelId": "gemini-2.5-flash", "remainingFraction": 1.0,
         "resetTime": "2026-09-10T00:00:00Z"},
        {"tokenType": "REQUESTS", "modelId": "gemini-3-pro", "remainingFraction": 0.38,
         "resetTime": "2026-09-09T23:15:00Z"},
        {"tokenType": "REQUESTS", "modelId": "gemini-2.5-pro", "remainingFraction": 0.715,
         "resetTime": "2026-09-10T00:00:00Z"},
        {"tokenType": "POOL_FRACTION", "modelId": "gemini-3-flash", "remainingFraction": 0.9,
         "resetTime": "2026-09-09T21:00:00Z"},
        # A malformed bucket must be skipped, not crash the run.
        {"tokenType": "REQUESTS", "modelId": "gemini-broken"},
    ]
}

LOAD_CODE_ASSIST = {
    "currentTier": {"id": "STANDARD", "name": "Code Assist Standard"},
    "cloudaicompanionProject": "projects/quota-demo",
}

QUOTA_REQUESTS = []


def fake_code_assist(endpoint, _access_token, body=None):
    if endpoint == "retrieveUserQuota":
        QUOTA_REQUESTS.append(body)
        return RETRIEVE_USER_QUOTA
    if endpoint == "loadCodeAssist":
        return LOAD_CODE_ASSIST
    raise AssertionError(f"unexpected endpoint {endpoint}")


def http_error(code):
    return urllib.error.HTTPError("https://cloudcode-pa.googleapis.com", code, "no", None, None)


class GeminiLabels(unittest.TestCase):
    def test_window_id_is_url_safe_and_stable(self):
        self.assertEqual(qc.gemini_window_id("gemini-2.5-pro"), "gemini-2-5-pro")
        self.assertEqual(qc.gemini_window_id("models/gemini-3-pro"), "models-gemini-3-pro")
        self.assertEqual(qc.gemini_window_id(""), "quota")

    def test_window_label_drops_the_brand_the_card_already_carries(self):
        self.assertEqual(qc.gemini_window_label("gemini-2.5-pro"), "2.5 Pro")
        self.assertEqual(qc.gemini_window_label("gemini-3-flash"), "3 Flash")
        self.assertEqual(qc.gemini_window_label("models/gemini-3-pro"), "3 Pro")


class GeminiCollection(unittest.TestCase):
    def setUp(self):
        QUOTA_REQUESTS.clear()

    def collect(self, credentials=None, code_assist=fake_code_assist):
        credentials = credentials if credentials is not None else {"refresh_token": "not-a-real-token"}
        with mock.patch.object(qc.Path, "read_text", return_value=json.dumps(credentials)), \
             mock.patch.object(qc, "gemini_access_token", return_value="fake-access-token"), \
             mock.patch.object(qc, "code_assist", side_effect=code_assist):
            return qc.collect_gemini()

    def test_headline_is_the_most_consumed_bucket(self):
        provider = self.collect()
        self.assertEqual(provider["windows"][0]["id"], "gemini-3-pro")
        self.assertEqual(provider["windows"][0]["label"], "3 Pro")
        # 1 - 0.38 = 62 % used.
        self.assertEqual(provider["windows"][0]["percent"], 62)
        self.assertNotIn("gemini", provider["windows"][0]["label"].lower())
        self.assertEqual(provider["windows"][0]["resets_at"], "2026-09-09T23:15:00Z")

    def test_secondary_windows_follow_in_descending_use(self):
        windows = self.collect()["windows"]
        # One decimal like the other two providers: 1 - 0.715 is 28.5, not 29.
        self.assertEqual([w["percent"] for w in windows], [62, 28.5, 10])
        self.assertEqual([w["id"] for w in windows],
                         ["gemini-3-pro", "gemini-2-5-pro", "gemini-3-flash"])

    def test_at_most_three_windows_reach_the_clients(self):
        self.assertLessEqual(len(self.collect()["windows"]), qc.GEMINI_MAX_WINDOWS)

    def test_plan_is_the_tier_name(self):
        self.assertEqual(self.collect()["plan"], "Standard")

    def test_missing_refresh_token_is_a_skip_not_an_error(self):
        with self.assertRaises(qc.GeminiUnavailable):
            self.collect(credentials={"access_token": "x"})

    def test_absent_credential_file_is_a_skip_not_an_error(self):
        with mock.patch.object(qc.Path, "read_text", side_effect=FileNotFoundError):
            with self.assertRaises(qc.GeminiUnavailable):
                qc.collect_gemini()

    def test_unreadable_or_corrupt_credential_is_a_skip_not_an_error(self):
        for failure in (PermissionError(13, "Permission denied"), OSError(5, "I/O error")):
            with self.subTest(failure=failure):
                with mock.patch.object(qc.Path, "read_text", side_effect=failure):
                    with self.assertRaises(qc.GeminiUnavailable):
                        qc.collect_gemini()
        with mock.patch.object(qc.Path, "read_text", return_value="{ not json"):
            with self.assertRaises(qc.GeminiUnavailable):
                qc.collect_gemini()

    def test_an_account_without_code_assist_is_a_skip_not_an_error(self):
        # The translation lives inside `code_assist`, so the fake goes one layer lower.
        for code in (401, 403):
            with self.subTest(code=code):
                with mock.patch.object(qc, "post_json", side_effect=http_error(code)):
                    with self.assertRaises(qc.GeminiUnavailable):
                        qc.code_assist("retrieveUserQuota", "fake-access-token")

    def test_a_code_assist_server_error_is_a_real_failure(self):
        with mock.patch.object(qc, "post_json", side_effect=http_error(500)):
            with self.assertRaises(urllib.error.HTTPError):
                qc.code_assist("retrieveUserQuota", "fake-access-token")

    def test_a_refused_refresh_is_a_skip_not_an_error(self):
        with mock.patch.object(qc, "gemini_oauth_clients", return_value=[("id", "secret")]), \
             mock.patch.object(qc, "post_json", side_effect=http_error(400)):
            with self.assertRaises(qc.GeminiUnavailable):
                qc.gemini_access_token("stale-refresh-token")

    def test_no_bucket_at_all_draws_no_card(self):
        with self.assertRaises(qc.GeminiUnavailable):
            self.collect(code_assist=lambda endpoint, token, body=None:
                         {"buckets": []} if endpoint == "retrieveUserQuota" else LOAD_CODE_ASSIST)

    def test_a_non_object_quota_response_draws_no_card(self):
        with self.assertRaises(qc.GeminiUnavailable):
            self.collect(code_assist=lambda endpoint, token, body=None:
                         None if endpoint == "retrieveUserQuota" else LOAD_CODE_ASSIST)

    def test_the_project_from_load_code_assist_is_sent_with_the_quota_request(self):
        self.collect()
        self.assertEqual(QUOTA_REQUESTS, [{"cloudaicompanionProject": "projects/quota-demo"}])

    def test_an_account_with_no_project_still_asks_for_its_quota(self):
        def without_project(endpoint, token, body=None):
            if endpoint == "loadCodeAssist":
                return {"currentTier": {"id": "FREE"}}
            QUOTA_REQUESTS.append(body)
            return RETRIEVE_USER_QUOTA
        self.assertEqual(self.collect(code_assist=without_project)["plan"], "Free")
        self.assertEqual(QUOTA_REQUESTS, [{}])

    def test_out_of_range_and_non_numeric_fractions_never_leave_the_ring(self):
        buckets = {"buckets": [
            {"modelId": "gemini-3-pro", "remainingFraction": -0.2},
            {"modelId": "gemini-3-flash", "remainingFraction": 1.4},
            {"modelId": "gemini-2.5-pro", "remainingFraction": True},
            {"modelId": "gemini-2.5-flash", "remainingFraction": float("nan")},
            {"modelId": "gemini-legacy", "remainingFraction": "0.5"},
        ]}
        windows = self.collect(code_assist=lambda endpoint, token, body=None:
                               buckets if endpoint == "retrieveUserQuota" else LOAD_CODE_ASSIST)["windows"]
        self.assertEqual([w["id"] for w in windows], ["gemini-3-pro", "gemini-3-flash"])
        self.assertEqual([w["percent"] for w in windows], [100, 0])


class CollectSkipsGemini(unittest.TestCase):
    def test_provider_absent_from_the_payload_when_gemini_is_not_set_up(self):
        with mock.patch.object(qc, "collect_claude", return_value={"plan": "Max 20x", "windows": [], "extra": {}}), \
             mock.patch.object(qc, "collect_codex", return_value={"plan": "Pro 20x", "windows": [], "extra": {}}), \
             mock.patch.object(qc, "collect_gemini", side_effect=qc.GeminiUnavailable("not signed in")), \
             mock.patch.object(qc, "log"):
            snapshot = qc.collect()
        self.assertEqual(sorted(snapshot["providers"]), ["claude", "codex"])

    def test_a_transient_gemini_failure_still_reports_an_error_card(self):
        with mock.patch.object(qc, "collect_claude", return_value={"plan": None, "windows": [], "extra": {}}), \
             mock.patch.object(qc, "collect_codex", return_value={"plan": None, "windows": [], "extra": {}}), \
             mock.patch.object(qc, "collect_gemini", side_effect=TimeoutError("the read timed out")), \
             mock.patch.object(qc, "log"):
            snapshot = qc.collect()
        self.assertIn("gemini", snapshot["providers"])
        self.assertIn("error", snapshot["providers"]["gemini"]["extra"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
