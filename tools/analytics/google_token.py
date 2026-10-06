import base64
import json
import os
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request


def _b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def access_token(key_path, scope):
    """Returns (token, client_email) for a service-account key, signing the JWT with openssl."""
    key = json.load(open(key_path))
    now = int(time.time())
    header = _b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = _b64(json.dumps({
        "iss": key["client_email"],
        "scope": scope,
        "aud": key["token_uri"],
        "iat": now,
        "exp": now + 3600,
    }).encode())
    signing_input = f"{header}.{claims}".encode()
    with tempfile.NamedTemporaryFile("w", delete=False) as pem:
        pem.write(key["private_key"])
    try:
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", pem.name],
            input=signing_input, capture_output=True, check=True,
        ).stdout
    finally:
        os.unlink(pem.name)
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": f"{header}.{claims}.{_b64(signature)}",
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(key["token_uri"], data=body)) as response:
        return json.load(response)["access_token"], key["client_email"]


def call(token, method, url, body=None):
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    })
    with urllib.request.urlopen(request) as response:
        return json.load(response)
