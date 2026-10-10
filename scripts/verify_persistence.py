"""Verify slot booking, cancellation history, and persistence after restart.

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


def request_json(path, method="GET", body=None, expected_status=200):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = Request(
        BASE_URL + path,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
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
            request_json("/api/appointments")
            return
        except (RuntimeError, URLError, OSError, ValueError) as error:
            last_error = error
            time.sleep(2)

    raise RuntimeError(
        "Application did not become ready within 120 seconds. "
        "Check it with: sudo docker compose logs --tail=100 app"
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


def main():
    subprocess.run(
        ["docker", "compose", "config", "--quiet"],
        cwd=PROJECT_DIR,
        check=True,
    )

    print("Waiting for the application...", flush=True)
    wait_until_ready()

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

    print(f"Verified cancellation and rebooking of slot {slot_id}. Restarting app...", flush=True)
    subprocess.run(
        ["docker", "compose", "restart", "app"],
        cwd=PROJECT_DIR,
        check=True,
    )

    wait_until_ready()
    after_restart = request_json(detail_path)
    if after_restart != before_restart:
        raise RuntimeError("The appointment changed after restarting the app.")
    if request_json(replacement_path) != replacement:
        raise RuntimeError("The new booking changed after restarting the app.")
    check_slot_availability(doctor_id, slot_id, False)

    print(
        f"PASS: slot {slot_id}, cancellation history, and active booking survived restart.",
        flush=True,
    )


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, URLError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
