#!/usr/bin/env python3
"""Exercise registration, ownership, redirect, analytics, and deactivation."""
import base64
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE = os.getenv("SHORTLINE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
PASSWORD = os.getenv("SHORTLINE_DEMO_PASSWORD", "LocalDemoPassphrase2026!")

def call(path, method="GET", body=None, credentials=None):
    headers = {"Content-Type": "application/json"} if body is not None else {}
    if credentials:
        token = base64.b64encode(f"{credentials[0]}:{credentials[1]}".encode()).decode()
        headers["Authorization"] = f"Basic {token}"
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers=headers, method=method)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, response_headers, new_url):
            return None
    # Observe the 302 response without fetching the destination.
    opener = urllib.request.build_opener(NoRedirect())
    try:
        response = opener.open(request, timeout=10)
        return response.status, response.read().decode(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(), error.headers

username = "demo_" + uuid.uuid4().hex[:10]
status, _, _ = call("/api/v1/auth/register", "POST", {"username": username, "password": PASSWORD})
assert status == 201, f"Registration returned {status}"
owner = (username, PASSWORD)
assert call("/api/v1/links", "POST", {"url": "https://example.com/unauthorized"})[0] == 401
status, _, _ = call("/api/v1/links", "POST", {"url": "https://example.com/shortline-demo", "customCode": username}, owner)
assert status == 201, f"Create returned {status}"
code = username
status, _, _ = call("/api/v1/auth/register", "POST", {"username": "other_" + username[5:], "password": PASSWORD})
assert status == 201, f"Second registration returned {status}"
other = ("other_" + username[5:], PASSWORD)
assert call(f"/api/v1/links/{code}/analytics", credentials=other)[0] == 404
assert call(f"/api/v1/links/{code}", "DELETE", credentials=other)[0] == 404
status, _, headers = call("/" + code)
assert status == 302 and headers.get("Location") == "https://example.com/shortline-demo"
for _ in range(40):
    status, body, _ = call(f"/api/v1/links/{code}/analytics", credentials=owner)
    if status == 200 and json.loads(body)["clicks"] >= 1:
        break
    time.sleep(0.25)
assert status == 200 and json.loads(body)["clicks"] >= 1, "Click event did not appear in analytics"
assert call(f"/api/v1/links/{code}", "DELETE", credentials=owner)[0] == 204
assert call("/" + code)[0] == 410
print(json.dumps({"unauthenticated_create": 401, "created": 201, "redirect": 302, "owner_analytics_clicks": json.loads(body)["clicks"],
                  "other_user_analytics": 404, "other_user_deactivation": 404,
                  "owner_deactivation": 204, "disabled_redirect": 410}, indent=2))
