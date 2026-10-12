#!/usr/bin/env python3
"""Real HTTP/PostgreSQL API checks. Run against a disposable development database.

Required environment: API_SMOKE_URL, API_SMOKE_EMAIL, API_SMOKE_PASSWORD.
The account must be a platform administrator. No credentials/tokens are printed.
The runner creates uniquely named test records and disables its fixture accounts
and business after checking customer and management operations.
"""
import datetime as dt
import json
import os
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo


BASE = os.environ["API_SMOKE_URL"].rstrip("/") + "/api/v1"
ADMIN_EMAIL = os.environ["API_SMOKE_EMAIL"]
ADMIN_PASSWORD = os.environ["API_SMOKE_PASSWORD"]
suffix = secrets.token_hex(6)
password = "ApiTest-" + secrets.token_urlsafe(14) + "9"
checks = 0


def request(method, route, data=None, token=None, expected=200):
    global checks
    body = None if data is None else json.dumps(data).encode()
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + route, data=body, method=method, headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as err:
        response = err
    status = response.status if hasattr(response, "status") else response.code
    raw = response.read()
    result = json.loads(raw) if raw else None
    allowed = {expected} if isinstance(expected, int) else set(expected)
    if status not in allowed:
        # Never serialize sessions or request bodies into assertion failures.
        code = result.get("code", "unknown") if isinstance(result, dict) else "non-json"
        raise AssertionError(f"{method} {route}: status {status}, expected {sorted(allowed)}, code {code}")
    if status >= 400:
        assert isinstance(result, dict) and isinstance(result.get("code"), str), "Error lacks stable code"
    checks += 1
    return result


def new_account(admin, label):
    return request("POST", "/platform/users", {
        "displayName": "API test " + label + " " + suffix,
        "email": f"api-{label}-{suffix}@example.invalid", "phoneNumber": "09123456789",
        "role": "Customer",
    }, admin, 201)


def run():
    admin_session = request("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    admin = admin_session["accessToken"]
    assert "PlatformAdmin" in admin_session["user"]["roles"]
    request("GET", "/me", expected=401)
    request("GET", "/businesses")
    request("GET", "/categories")
    request("GET", "/missing-test-route", expected=404)

    category = request("POST", "/platform/categories", {"name": "API category " + suffix, "kind": "Business", "sortOrder": 1000, "isActive": True}, admin, 201)
    request("GET", "/platform/categories", token=admin)
    catalog = request("POST", "/platform/catalog", {"name": "API catalog " + suffix, "slug": "api-catalog-" + suffix, "category": "عمومی", "suggestedDurationMinutes": 30, "isPublished": True}, admin, 201)
    request("GET", "/platform/catalog", token=admin)
    request("GET", "/catalog")

    manager = new_account(admin, "manager")
    assert any(u["id"] == manager["id"] for u in request("GET", "/platform/users?q=" + suffix, token=admin)["items"])
    business = request("POST", "/platform/businesses", {
        "name": "API test " + suffix, "slug": "api-test-" + suffix, "category": category["name"],
        "city": "تهران", "description": "Synthetic API integration fixture", "requiresApproval": False,
    }, admin, 201)
    branch = request("GET", "/admin/branches?businessId=" + business["id"], token=admin)["items"][0]
    membership = request("POST", "/admin/memberships", {"branchId": branch["id"], "userId": manager["id"], "role": "Manager"}, admin, 201)
    second_branch = request("POST", "/admin/branches", {"businessId": business["id"], "name": "Staff-only branch " + suffix}, admin, 201)
    request("POST", "/admin/memberships", {"branchId": second_branch["id"], "userId": manager["id"], "role": "Staff"}, admin, 201)
    provider = request("POST", "/admin/resources", {"branchId": branch["id"], "name": "Test provider " + suffix, "kind": "staff", "userId": manager["id"]}, admin, 201)
    service = request("POST", "/admin/services", {
        "businessId": business["id"], "branchId": branch["id"], "resourceIds": [provider["id"]],
        "name": "Test service " + suffix, "durationMinutes": 30, "price": 120000, "currency": "IRR",
    }, admin, 201)
    request("GET", "/admin/services?branchId=" + branch["id"], token=admin)
    request("GET", "/admin/resources?branchId=" + branch["id"], token=admin)
    request("GET", f"/admin/branches/{branch['id']}/services", token=admin)
    manager_session = request("POST", "/auth/login", {"email": manager["email"], "password": manager["temporaryPassword"]})
    manager_token = manager_session["accessToken"]
    assert "Manager" in manager_session["user"]["roles"]
    context = request("GET", "/admin/context", token=manager_token)
    assert context["canManageBusiness"] and not context["isPlatformAdmin"]
    staff_context = next(x for x in context["memberships"] if x["branchId"] == second_branch["id"])
    assert staff_context["role"] == "Staff" and not staff_context["canManageBusiness"]
    request("GET", "/platform/users", token=manager_token, expected=403)
    request("PUT", "/admin/branches/" + second_branch["id"], {"businessId": business["id"], "name": "Unauthorized edit"}, manager_token, (403, 404))
    request("POST", "/admin/memberships", {"branchId": branch["id"], "userId": manager["id"], "role": "Owner"}, manager_token, (403, 404))
    request("GET", "/admin/availability?branchId=" + branch["id"], token=manager_token)
    request("GET", "/admin/calendar?branchId=" + branch["id"], token=manager_token)

    customers = []
    for label in ("customer", "other"):
        session = request("POST", "/auth/register", {
            "displayName": "API " + label, "email": f"api-{label}-{suffix}@example.invalid",
            "phoneNumber": "۰۹۱۲۳۴۵۶۷۸۹", "password": password,
        }, expected=201)
        assert session["user"]["phoneNumber"] == "09123456789"
        assert session["user"]["roles"] == ["Customer"]
        customers.append(session)
    customer, other = [x["accessToken"] for x in customers]
    request("PUT", "/favorites/" + business["id"], {}, customer, 204)
    favorites = request("GET", "/favorites", token=customer)
    assert any(b["id"] == business["id"] for b in favorites["items"])
    request("GET", "/businesses/" + business["id"])
    request("GET", f"/businesses/{business['id']}/branches/{branch['id']}/services")
    request("GET", f"/businesses/{business['id']}/branches/{branch['id']}/providers?serviceIds={service['id']}")
    date = (dt.datetime.now(ZoneInfo(branch["timeZoneId"])) + dt.timedelta(days=2)).date().isoformat()
    query = urllib.parse.urlencode({"businessId": business["id"], "branchId": branch["id"], "serviceIds": service["id"], "date": date})
    slots = request("GET", "/slots?" + query)
    assert slots["slots"], "Fixture has no available slots"
    booking = {"businessId": business["id"], "branchId": branch["id"], "serviceIds": [service["id"]], "resourceId": provider["id"],
               "startsAt": slots["slots"][0]["startsAt"], "phoneNumber": "09123456789", "customerNote": "API integration",
               "termsAccepted": True, "expectedPrice": slots["totalPrice"], "expectedDurationMinutes": slots["totalDurationMinutes"]}
    changed_terms = dict(booking, expectedPrice=1)
    assert request("POST", "/appointments", changed_terms, customer, 409)["code"] == "terms_changed"
    appointment = request("POST", "/appointments", booking, customer, 201)
    assert appointment["status"] == "Confirmed" and appointment["finalPrice"] == slots["totalPrice"]
    assert appointment["resourceId"] == provider["id"]
    assert request("POST", "/appointments", booking, other, 409)["code"] == "slot_unavailable"
    route = "/appointments/" + appointment["id"]
    request("GET", route, token=other, expected=404)
    request("POST", route + "/cancel", {}, other, 404)
    request("POST", route + "/review", {"rating": 5, "comment": "Wrong owner"}, other, 404)
    request("POST", route + "/review", {"rating": 5, "comment": "Too soon"}, customer, 409)
    own = request("GET", "/appointments", token=customer)
    assert any(a["id"] == appointment["id"] for a in own["items"])
    cancelled = request("POST", route + "/cancel", {}, customer)
    assert cancelled["status"] == "Cancelled"
    request("POST", route + "/cancel", {}, customer, 409)
    db_container = os.environ.get("API_SMOKE_DB_CONTAINER")
    if db_container:
        # Past appointments cannot be fabricated through the production booking API.
        # This explicitly enabled disposable-database fixture affects only this run's
        # UUID and verifies the unique synthetic slug before changing its status.
        import uuid
        appointment_id = str(uuid.UUID(appointment["id"]))
        customer_id = str(uuid.UUID(customers[0]["user"]["id"]))
        sql = f'''UPDATE benobat."Appointments" a SET "Status"='Completed',
                    "StartsAt"=CURRENT_TIMESTAMP-INTERVAL '3 days',
                    "EndsAt"=CURRENT_TIMESTAMP-INTERVAL '3 days'+INTERVAL '30 minutes'
                  FROM benobat."Branches" br JOIN benobat."Businesses" b ON b."Id"=br."BusinessId"
                  WHERE a."Id"='{appointment_id}' AND a."CustomerId"='{customer_id}'
                    AND a."BranchId"=br."Id" AND b."Slug"='api-test-{suffix}';'''
        fixture = subprocess.run(["docker", "exec", "-i", db_container, "psql", "-U", "benobat", "-d", "benobat_public_review", "-v", "ON_ERROR_STOP=1", "-tA"],
                                 input=sql, text=True, capture_output=True, check=True)
        assert fixture.stdout.strip() == "UPDATE 1", "The disposable historical appointment fixture did not match"
        reviewed = request("POST", route + "/review", {"rating": 5, "comment": "Customer fixture review"}, customer, 201)
        public_reviews = "/businesses/" + business["id"] + "/reviews"
        assert not request("GET", public_reviews)["items"], "Pending review leaked publicly"
        request("GET", "/admin/reviews?businessId=" + business["id"], token=manager_token)
        request("PUT", "/admin/reviews/" + reviewed["id"] + "/moderation", {"status": "Published", "managerReply": "Management fixture response"}, manager_token)
        published = request("GET", public_reviews)["items"]
        assert len(published) == 1 and published[0]["managerReply"] == "Management fixture response"
        evaluation = request("POST", "/admin/customer-reviews", {"appointmentId": appointment_id, "rating": 4, "comment": "Private customer evaluation"}, manager_token, 201)
        request("GET", "/admin/customer-reviews?businessId=" + business["id"], token=manager_token)
        assert len(request("GET", public_reviews)["items"]) == 1, "Private evaluation leaked publicly"
        request("PUT", "/admin/customer-reviews/" + evaluation["id"], {"appointmentId": appointment_id, "rating": 5, "comment": "Updated private evaluation"}, manager_token)
        request("DELETE", "/admin/reviews/" + reviewed["id"], token=manager_token, expected=204)
        assert not request("GET", public_reviews)["items"]
        request("PUT", "/admin/reviews/" + reviewed["id"] + "/active", {"active": True}, manager_token)
        assert len(request("GET", public_reviews)["items"]) == 1
        assert request("POST", route + "/review", {"rating": 5, "comment": "Duplicate"}, customer, 409)["code"] == "review_exists"
    request("DELETE", "/favorites/" + business["id"], token=customer, expected=204)
    request("POST", "/auth/logout", {}, other, 204)
    request("GET", "/me", token=other, expected=401)

    replacement = request("POST", "/me/password", {"currentPassword": password, "newPassword": password + "x"}, customer)
    request("GET", "/me", token=customer, expected=401)
    request("GET", "/me", token=replacement["accessToken"])
    request("PUT", "/admin/memberships/" + membership["id"], {"branchId": branch["id"], "userId": manager["id"], "role": "Staff"}, admin)
    request("GET", "/admin/context", token=manager_token, expected=401)
    manager_session = request("POST", "/auth/login", {"email": manager["email"], "password": manager["temporaryPassword"]})
    manager_token = manager_session["accessToken"]
    context = request("GET", "/admin/context", token=manager_token)
    assert not context["canManageBusiness"] and context["canManageAppointments"]
    request("PUT", "/admin/branches/" + branch["id"], {"businessId": business["id"], "name": "Stale manager edit"}, manager_token, (403, 404))
    request("PUT", "/platform/users/" + manager["id"] + "/active", {"active": False}, admin)
    request("GET", "/admin/context", token=manager_token, expected=401)
    request("PUT", "/platform/users/" + manager["id"] + "/active", {"active": True}, admin)
    request("GET", "/admin/context", token=manager_token, expected=401)
    request("PUT", "/platform/users/" + manager["id"] + "/active", {"active": False}, admin)
    request("DELETE", "/platform/businesses/" + business["id"], token=admin, expected=(200, 204))
    history = request("GET", route, token=replacement["accessToken"])
    assert history["businessName"] == business["name"] and history["branchName"] == branch["name"]
    request("GET", "/admin/appointments/" + appointment["id"], token=admin)
    for session in customers:
        request("PUT", "/platform/users/" + session["user"]["id"] + "/active", {"active": False}, admin)
    request("DELETE", "/platform/catalog/" + catalog["id"], token=admin, expected=(200, 204))
    request("DELETE", "/platform/categories/" + category["id"], token=admin, expected=(200, 204))
    print(f"PASS: {checks} real HTTP checks (discovery, booking, ownership, branch scope, stale roles, token revocation, disabled users)")


if __name__ == "__main__":
    run()
