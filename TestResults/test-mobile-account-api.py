#!/usr/bin/env python3
"""Focused real-HTTP checks for atomic mobile credential and profile changes.

Uses API_SMOKE_URL, API_SMOKE_EMAIL and API_SMOKE_PASSWORD and a disposable
development database, like test-mobile-api.py. Tokens/passwords are never logged.
"""
import base64
import runpy
from pathlib import Path


shared = runpy.run_path(str(Path(__file__).with_name("test-mobile-api.py")))
request = shared["request"]
suffix = shared["suffix"]
password = shared["password"]
email = f"api-credentials-{suffix}@example.invalid"
changed_email = f"api-credentials-updated-{suffix}@example.invalid"
session = request("POST", "/auth/register", {"displayName": "Account API fixture", "email": email,
                  "phoneNumber": "09123456789", "password": password}, expected=201)
token = session["accessToken"]
user_id = session["user"]["id"]

request("PATCH", "/me", {"displayName": "Bad\nname", "phoneNumber": "09123456789"}, token, 400)
profile = request("PATCH", "/me", {"displayName": "  Updated fixture  ", "phoneNumber": "+۹۸۹۱۲۳۴۵۶۷۸۹"}, token)
assert profile["displayName"] == "Updated fixture" and profile["phoneNumber"] == "09123456789"
assert request("GET", "/me", token=token)["displayName"] == "Updated fixture"
bad_avatar = base64.b64encode(b"<svg onload=alert(1)>").decode()
assert request("POST", "/me/avatar", {"contentType": "image/png", "dataBase64": bad_avatar}, token, 400)["code"] == "invalid_avatar"
png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a+j8AAAAASUVORK5CYII="
assert request("POST", "/me/avatar", {"contentType": "image/png", "dataBase64": png}, token)["avatarUrl"].startswith("/media/avatar/" + user_id)
assert request("DELETE", "/me/avatar", token=token)["avatarUrl"] is None

assert request("POST", "/me/email", {"email": changed_email, "currentPassword": password + "wrong"}, token, 400)["code"] == "password_mismatch"
assert request("GET", "/me", token=token)["email"] == email
assert request("POST", "/me/email", {"email": shared["ADMIN_EMAIL"], "currentPassword": password}, token, 409)["code"] == "duplicate_email"
assert request("GET", "/me", token=token)["email"] == email
assert request("POST", "/me/password", {"currentPassword": password, "newPassword": "abcdef"}, token, 400)["code"] == "weak_password"
request("GET", "/me", token=token)

replacement = request("POST", "/me/email", {"email": changed_email, "currentPassword": password}, token)
assert replacement["user"]["email"] == changed_email
request("GET", "/me", token=token, expected=401)
token = replacement["accessToken"]
assert request("GET", "/me", token=token)["email"] == changed_email
assert request("POST", "/me/password", {"currentPassword": password + "wrong", "newPassword": password + "x"}, token, 400)["code"] == "password_mismatch"
request("GET", "/me", token=token)
replacement = request("POST", "/me/password", {"currentPassword": password, "newPassword": password + "x"}, token)
request("GET", "/me", token=token, expected=401)
token = replacement["accessToken"]
request("GET", "/me", token=token)
request("POST", "/auth/logout", {}, token, 204)
request("GET", "/me", token=token, expected=401)

new_session = request("POST", "/auth/login", {"email": changed_email, "password": password + "x"})
admin = request("POST", "/auth/login", {"email": shared["ADMIN_EMAIL"], "password": shared["ADMIN_PASSWORD"]})["accessToken"]
request("PUT", "/platform/users/" + user_id + "/active", {"active": False}, admin)
request("GET", "/me", token=new_session["accessToken"], expected=401)
assert request("POST", "/auth/login", {"email": changed_email, "password": password + "x"}, expected=401)["code"] == "account_locked"
print(f"PASS: {request.__globals__['checks']} real HTTP account checks (profile, avatar, validation rollback, email/password session replacement, logout, disabled sign-in)")
