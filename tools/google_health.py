#!/usr/bin/env python3
"""Explore the Google Health API with the owner's account, from the laptop.

Used to check what the API offers before the app depends on it. Standard library only.

Setup: a Desktop OAuth client from the PacingLab Google Cloud project, downloaded as JSON into
.secrets/ (git-ignored). The token is stored next to it; responses are written to
fixtures-private/google-health/ because they hold personal data.

    tools/google_health.py auth                     sign in once in the browser
    tools/google_health.py identity                 check that Google Health is linked
    tools/google_health.py exercises 2026-10-15 2026-10-16
    tools/google_health.py tcx <exercise id>        download the GPS track as TCX
    tools/google_health.py get <path> [key=value ...]   any GET below /v4/
"""

import base64
import datetime as dt
import hashlib
import http.server
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SECRETS = ROOT / ".secrets"
TOKEN_FILE = SECRETS / "google-health-token.json"
OUTPUT = ROOT / "fixtures-private" / "google-health"
API = "https://health.googleapis.com/v4/"
SCOPES = [
    "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
    "https://www.googleapis.com/auth/googlehealth.location.readonly",
    "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
    "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
]


def client():
    files = sorted(SECRETS.glob("client_secret*.json"))
    if not files:
        sys.exit(f"No client_secret*.json in {SECRETS}. Download the Desktop client JSON there first.")
    data = json.loads(files[-1].read_text())
    if "installed" not in data:
        sys.exit(f"{files[-1].name} is not a Desktop app client.")
    return data["installed"]


def save_token(token):
    SECRETS.mkdir(exist_ok=True)
    TOKEN_FILE.write_text(json.dumps(token, indent=1))
    os.chmod(TOKEN_FILE, 0o600)


def post_form(url, fields):
    body = urllib.parse.urlencode(fields).encode()
    with urllib.request.urlopen(urllib.request.Request(url, body)) as response:
        return json.load(response)


def auth():
    """Loopback sign-in with PKCE, as Google recommends for desktop apps."""
    c = client()
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = secrets.token_urlsafe(16)
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if "code" not in query and "error" not in query:
                self.send_response(404)
                self.end_headers()
                return
            result.update({k: v[0] for k, v in query.items()})
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"PacingLab: signed in. You can close this tab.")

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
    redirect = f"http://127.0.0.1:{server.server_port}"
    url = c["auth_uri"] + "?" + urllib.parse.urlencode({
        "client_id": c["client_id"],
        "redirect_uri": redirect,
        "response_type": "code",
        "scope": " ".join(SCOPES),
        "access_type": "offline",
        "prompt": "consent",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "state": state,
    })
    print("Opening the browser for sign-in. If nothing opens, visit:\n" + url, flush=True)
    webbrowser.open(url)
    while not result:
        server.handle_request()
    server.server_close()

    if result.get("state") != state:
        sys.exit("Sign-in failed: state mismatch.")
    if "error" in result:
        sys.exit(f"Sign-in failed: {result['error']}")
    token = post_form(c["token_uri"], {
        "client_id": c["client_id"],
        "client_secret": c["client_secret"],
        "code": result["code"],
        "code_verifier": verifier,
        "grant_type": "authorization_code",
        "redirect_uri": redirect,
    })
    token["expires_at"] = dt.datetime.now(dt.timezone.utc).timestamp() + token["expires_in"]
    save_token(token)
    granted = token.get("scope", "").split()
    print("Signed in. Granted scopes:")
    for scope in SCOPES:
        print(("  yes " if scope in granted else "  NO  ") + scope.rsplit("/", 1)[-1])


def access_token():
    if not TOKEN_FILE.exists():
        sys.exit("Not signed in. Run: tools/google_health.py auth")
    token = json.loads(TOKEN_FILE.read_text())
    if token["expires_at"] - 60 > dt.datetime.now(dt.timezone.utc).timestamp():
        return token["access_token"]
    c = client()
    fresh = post_form(c["token_uri"], {
        "client_id": c["client_id"],
        "client_secret": c["client_secret"],
        "refresh_token": token["refresh_token"],
        "grant_type": "refresh_token",
    })
    token.update(fresh)
    token["expires_at"] = dt.datetime.now(dt.timezone.utc).timestamp() + fresh["expires_in"]
    save_token(token)
    return token["access_token"]


def get(path, params=None, raw=False):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    request = urllib.request.Request(url, headers={"Authorization": "Bearer " + access_token()})
    try:
        with urllib.request.urlopen(request) as response:
            body = response.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code} for {path}\n{e.read().decode(errors='replace')}")
    return body if raw else json.loads(body)


def write(name, content):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / name
    path.write_bytes(content if isinstance(content, bytes) else json.dumps(content, indent=1).encode())
    return path


def exercises(start, end):
    points, token = [], None
    while True:
        params = {
            "filter": f'exercise.interval.civil_start_time >= "{start}" AND exercise.interval.civil_start_time < "{end}"',
            "pageSize": 25,
        }
        if token:
            params["pageToken"] = token
        page = get("users/me/dataTypes/exercise/dataPoints", params)
        points += page.get("dataPoints", [])
        token = page.get("nextPageToken")
        if not token:
            break
    path = write(f"exercises-{start}-{end}.json", points)
    for point in sorted(points, key=lambda p: p.get("exercise", {}).get("interval", {}).get("startTime", "")):
        exercise = point.get("exercise", {})
        interval = exercise.get("interval", {})
        print(
            point["name"].rsplit("/", 1)[-1],
            exercise.get("exerciseType"),
            repr(exercise.get("displayName")),
            interval.get("startTime"),
            interval.get("endTime"),
            "gps" if exercise.get("exerciseMetadata", {}).get("hasGps") else "no-gps",
        )
    print(f"{len(points)} exercises, saved to {path.relative_to(ROOT)}")


def tcx(exercise_id):
    data = get(f"users/me/dataTypes/exercise/dataPoints/{exercise_id}:exportExerciseTcx", {"alt": "media"}, raw=True)
    path = write(f"{exercise_id}.tcx", data)
    text = data.decode(errors="replace")
    print(f"{len(data)} bytes, {text.count('<Trackpoint>')} trackpoints, {text.count('<LatitudeDegrees>')} with position, "
          f"{text.count('<HeartRateBpm>')} with heart rate, saved to {path.relative_to(ROOT)}")


def main(args):
    if not args or args[0] in ("-h", "--help"):
        print(__doc__)
    elif args[0] == "auth":
        auth()
    elif args[0] == "identity":
        print(json.dumps(get("users/me/identity"), indent=1))
    elif args[0] == "exercises" and len(args) == 3:
        exercises(args[1], args[2])
    elif args[0] == "tcx" and len(args) == 2:
        tcx(args[1])
    elif args[0] == "get" and len(args) >= 2:
        params = dict(arg.split("=", 1) for arg in args[2:])
        result = get(args[1], params)
        name = args[1].replace("/", "_").replace(":", "_") + ".json"
        print(json.dumps(result, indent=1)[:4000])
        print(f"saved to {write(name, result).relative_to(ROOT)}")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
