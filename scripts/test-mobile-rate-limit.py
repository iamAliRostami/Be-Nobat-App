#!/usr/bin/env python3
"""Verify the shared auth limit using an isolated loopback client address."""
import http.client
import json
import os
import secrets
from urllib.parse import urlsplit


url = urlsplit(os.environ["API_SMOKE_URL"])
if url.scheme != "http" or url.hostname not in ("127.0.0.1", "localhost"):
    raise SystemExit("This isolated-IP check requires the local development HTTP server.")
source = (f"127.254.{secrets.randbelow(255)}.{secrets.randbelow(254) + 1}", 0)
results = []
for index in range(9):
    connection = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=20, source_address=source)
    path = "/api/v1/auth/login" if index < 8 else "/api/v1/auth/register"
    body = {"email": f"api-rate-check-{secrets.token_hex(8)}@example.invalid", "password": "NotAnAccount123"}
    if index == 8:
        body.update(displayName="Rate limit fixture", phoneNumber="09123456789")
    connection.request("POST", path, json.dumps(body), {"Content-Type": "application/json"})
    response = connection.getresponse()
    payload = json.loads(response.read())
    results.append((response.status, payload["code"], response.getheader("Retry-After")))
    connection.close()
assert [result[0] for result in results] == [401] * 8 + [429], results
assert results[-1][1:] == ("rate_limited", "60")
print("PASS: 9 isolated-IP HTTP checks verify the shared login/register limit and Retry-After without locking an account")
