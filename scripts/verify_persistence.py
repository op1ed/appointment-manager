"""Verify appointments, events, Agent actions, and persistence after restart.

Run on the Ubuntu server from the project checkout:
    sudo python3 scripts/verify_persistence.py

This test restarts only the app service and leaves test records in MySQL.
"""

import json
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE_URL = "http://127.0.0.1:8080"
PROJECT_DIR = Path(__file__).resolve().parents[1]
ACCESS_TOKEN = None


def request_json(path, method="GET", body=None, expected_status=200):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if ACCESS_TOKEN is not None:
        headers["Authorization"] = "Bearer " + ACCESS_TOKEN
    request = Request(
        BASE_URL + path,
        data=data,
        headers=headers,
        method=method,
    )

    try:
        with urlopen(request, timeout=5) as response:
            if response.status != expected_status:
                raise RuntimeError(
                    f"{method} {path}: expected {expected_status}, got {response.status}"
                )
            return json.load(response)
    except HTTPError as error:
        raise RuntimeError(
            f"{method} {path}: expected {expected_status}, got {error.code}"
        ) from error


def wait_until_ready():
    deadline = time.monotonic() + 120
    last_error = None

    while time.monotonic() < deadline:
        try:
            request_json("/api/doctors")
            return
        except (RuntimeError, URLError, OSError, ValueError) as error:
            last_error = error
            time.sleep(2)

    raise RuntimeError(
        "Application did not become ready within 120 seconds. "
        "Check it with: sudo docker compose logs --tail=100 app"
    ) from last_error


def wait_for_administrator_login(username, password):
    # 首次启动时 HTTP 端口可能早于管理员初始化完成；等待初始化再验证登录。
    deadline = time.monotonic() + 120
    last_error = None
    while time.monotonic() < deadline:
        try:
            return request_json("/api/auth/login", method="POST", body={
                "username": username, "password": password,
            })
        except RuntimeError as error:
            if not isinstance(error.__cause__, HTTPError) or error.__cause__.code != 401:
                raise
            last_error = error
            time.sleep(2)
    raise RuntimeError(
        "Administrator login failed. Check the bootstrap configuration and application logs. "
        "Changing the bootstrap password does not reset an existing account's password."
    ) from last_error


def require_positive_id(data, label):
    if not isinstance(data, dict):
        raise RuntimeError(f"The {label} response must be a JSON object.")
    record_id = data.get("id")
    if type(record_id) is not int or record_id <= 0:
        raise RuntimeError(f"The {label} response must contain a positive integer id.")
    return record_id


def check_slot_availability(doctor_id, slot_id, expected):
    slots = request_json(f"/api/doctors/{doctor_id}/slots")
    if not isinstance(slots, list):
        raise RuntimeError("The slot list response must be a JSON array.")
    matches = [slot for slot in slots if slot.get("id") == slot_id]
    if len(matches) != 1 or matches[0].get("available") is not expected:
        raise RuntimeError(f"Slot {slot_id} must have available={expected}.")


def prepare_agent_booking(slot_id):
    response = request_json("/api/agent/tools/execute", method="POST", body={
        "name": "prepare_booking",
        "arguments": {"customerName": "Agent 部署测试用户", "slotId": slot_id},
    })
    action = response.get("result", {})
    if action.get("status") != "PENDING" or action.get("result") is not None or not action.get("id"):
        raise RuntimeError("Preparing an Agent booking must return a pending action without a business result.")
    if action.get("preview", {}).get("after", {}).get("slotId") != slot_id:
        raise RuntimeError("The Agent preview must identify the selected slot.")
    return action


def main():
    global ACCESS_TOKEN
    compose = subprocess.run(
        ["docker", "compose", "config", "--format", "json"],
        cwd=PROJECT_DIR,
        check=True,
        capture_output=True,
        text=True,
    )
    environment = json.loads(compose.stdout)["services"]["app"]["environment"]
    username = environment.get("APP_ADMIN_USERNAME")
    password = environment.get("APP_ADMIN_PASSWORD")
    if not username or not password:
        raise RuntimeError("Configure the administrator with: python3 scripts/configure_admin.py")

    print("Waiting for the application...", flush=True)
    wait_until_ready()
    login = wait_for_administrator_login(username, password)
    ACCESS_TOKEN = login["accessToken"]
    logged_in_user = request_json("/api/auth/me")
    if logged_in_user.get("role") != "ADMIN":
        raise RuntimeError("The deployment verification requires an administrator.")

    start_time = (
        datetime.now(timezone(timedelta(hours=8))) + timedelta(days=1)
    ).replace(microsecond=0)
    doctor = request_json(
        "/api/doctors",
        method="POST",
        body={"name": "部署测试医生"},
        expected_status=201,
    )
    doctor_id = require_positive_id(doctor, "doctor")
    slot = request_json(
        f"/api/doctors/{doctor_id}/slots",
        method="POST",
        body={
            "startTime": start_time.isoformat(),
            "endTime": (start_time + timedelta(minutes=30)).isoformat(),
        },
        expected_status=201,
    )
    slot_id = require_positive_id(slot, "slot")
    check_slot_availability(doctor_id, slot_id, True)

    created = request_json(
        "/api/appointments",
        method="POST",
        body={
            "customerName": "部署测试用户",
            "slotId": slot_id,
        },
        expected_status=201,
    )

    appointment_id = require_positive_id(created, "appointment")
    if created.get("status") != "BOOKED":
        raise RuntimeError("A new appointment must have status BOOKED.")
    if created.get("slotId") != slot_id or created.get("doctorName") != doctor["name"]:
        raise RuntimeError("The appointment must use the selected slot and doctor.")
    check_slot_availability(doctor_id, slot_id, False)

    detail_path = f"/api/appointments/{appointment_id}"
    before_restart = request_json(detail_path)
    if before_restart != created:
        raise RuntimeError("The saved appointment differs from the create response.")

    cancelled = request_json(detail_path + "/cancel", method="POST")
    expected_cancelled = dict(created, status="CANCELLED")
    if cancelled != expected_cancelled:
        raise RuntimeError("Cancellation must change only the appointment status.")
    check_slot_availability(doctor_id, slot_id, True)

    cancelled_again = request_json(detail_path + "/cancel", method="POST")
    if cancelled_again != cancelled:
        raise RuntimeError("Repeated cancellation must return the same appointment.")

    before_restart = request_json(detail_path)
    if before_restart != cancelled:
        raise RuntimeError("The cancellation was not saved.")

    replacement = request_json(
        "/api/appointments",
        method="POST",
        body={"customerName": "重新预约测试用户", "slotId": slot_id},
        expected_status=201,
    )
    replacement_id = require_positive_id(replacement, "replacement appointment")
    if replacement_id == appointment_id or replacement.get("status") != "BOOKED":
        raise RuntimeError("Rebooking must create a new BOOKED appointment.")

    cancelled_again = request_json(detail_path + "/cancel", method="POST")
    if cancelled_again != cancelled:
        raise RuntimeError("Cancelling the old appointment must remain idempotent.")
    replacement_path = f"/api/appointments/{replacement_id}"
    if request_json(replacement_path) != replacement:
        raise RuntimeError("Cancelling the old appointment changed the new booking.")
    check_slot_availability(doctor_id, slot_id, False)

    target = request_json(
        f"/api/doctors/{doctor_id}/slots",
        method="POST",
        body={
            "startTime": (start_time + timedelta(minutes=30)).isoformat(),
            "endTime": (start_time + timedelta(minutes=60)).isoformat(),
        },
        expected_status=201,
    )
    target_slot_id = require_positive_id(target, "target slot")
    reschedule_body = {"expectedSlotId": slot_id, "targetSlotId": target_slot_id}
    moved = request_json(replacement_path + "/reschedule", method="POST", body=reschedule_body)
    expected_moved = dict(replacement, slotId=target_slot_id,
                          startTime=target["startTime"], endTime=target["endTime"])
    if moved != expected_moved:
        raise RuntimeError("Rescheduling must keep the appointment ID, customer, doctor, and BOOKED status.")
    if request_json(replacement_path + "/reschedule", method="POST", body=reschedule_body) != moved:
        raise RuntimeError("Repeating the same reschedule must return the current appointment.")
    replacement = moved
    check_slot_availability(doctor_id, slot_id, True)
    check_slot_availability(doctor_id, target_slot_id, False)
    original_history = request_json(detail_path + "/history")
    moved_history = request_json(replacement_path + "/history")
    if [event["action"] for event in original_history["content"]] != ["BOOKED", "CANCELLED"]:
        raise RuntimeError("Cancellation retries must not duplicate operation records.")
    if [event["action"] for event in moved_history["content"]] != ["BOOKED", "RESCHEDULED"]:
        raise RuntimeError("Rescheduling retries must not duplicate operation records.")
    if moved_history["content"][1]["fromSlotId"] != slot_id or moved_history["content"][1]["toSlotId"] != target_slot_id:
        raise RuntimeError("The reschedule record must identify both slots.")

    definitions = request_json("/api/agent/tools")
    if not isinstance(definitions, list) or len(definitions) != 9:
        raise RuntimeError("The Agent catalog must contain nine tools.")
    pending_action = prepare_agent_booking(slot_id)
    pending_path = "/api/agent/actions/" + pending_action["id"]
    check_slot_availability(doctor_id, slot_id, True)
    if request_json(replacement_path) != replacement:
        raise RuntimeError("Preparing an Agent action changed an existing appointment.")

    agent_slot = request_json(f"/api/doctors/{doctor_id}/slots", method="POST", body={
        "startTime": (start_time + timedelta(minutes=60)).isoformat(),
        "endTime": (start_time + timedelta(minutes=90)).isoformat(),
    }, expected_status=201)
    agent_slot_id = require_positive_id(agent_slot, "Agent slot")
    confirmed_action = prepare_agent_booking(agent_slot_id)
    confirmed_path = "/api/agent/actions/" + confirmed_action["id"]
    confirmed_action = request_json(confirmed_path + "/confirm", method="POST")
    if confirmed_action.get("status") != "CONFIRMED" or confirmed_action.get("result", {}).get("slotId") != agent_slot_id:
        raise RuntimeError("Confirming an Agent action must book the selected slot.")
    agent_appointment_id = require_positive_id(confirmed_action["result"], "Agent appointment")
    if request_json(confirmed_path + "/confirm", method="POST") != confirmed_action:
        raise RuntimeError("Agent confirmation retries must return the saved result.")

    print(f"Verified cancellation, rebooking, and rescheduling of appointment {replacement_id}. Restarting app...", flush=True)
    subprocess.run(
        ["docker", "compose", "restart", "app"],
        cwd=PROJECT_DIR,
        check=True,
    )

    wait_until_ready()
    if request_json("/api/auth/me") != logged_in_user:
        raise RuntimeError("The persisted login token stopped working after restarting the app.")
    after_restart = request_json(detail_path)
    if after_restart != before_restart:
        raise RuntimeError("The appointment changed after restarting the app.")
    if request_json(replacement_path) != replacement:
        raise RuntimeError("The new booking changed after restarting the app.")
    check_slot_availability(doctor_id, slot_id, True)
    check_slot_availability(doctor_id, target_slot_id, False)
    if request_json(detail_path + "/history") != original_history or request_json(replacement_path + "/history") != moved_history:
        raise RuntimeError("The operation records changed after restarting the app.")

    if request_json(pending_path) != pending_action:
        raise RuntimeError("The pending Agent action changed after restarting the app.")
    if request_json(confirmed_path) != confirmed_action or request_json(confirmed_path + "/confirm", method="POST") != confirmed_action:
        raise RuntimeError("The confirmed Agent result changed after restarting the app.")
    if request_json(f"/api/appointments/{agent_appointment_id}/history")["totalElements"] != 1:
        raise RuntimeError("Agent confirmation retries duplicated operation records.")
    check_slot_availability(doctor_id, agent_slot_id, False)
    confirmed_pending = request_json(pending_path + "/confirm", method="POST")
    if confirmed_pending.get("status") != "CONFIRMED" or confirmed_pending.get("result", {}).get("slotId") != slot_id:
        raise RuntimeError("The pending Agent action cannot be confirmed after restarting the app.")
    check_slot_availability(doctor_id, slot_id, False)

    print(
        f"PASS: appointment {replacement_id}, login, cancellation, rescheduling, events, and Agent actions survived restart.",
        flush=True,
    )


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, URLError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
