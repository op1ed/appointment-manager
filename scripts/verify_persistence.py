"""Create an appointment, restart the Compose app, and verify it survives.

Run on the Ubuntu server from the project checkout:
    sudo python3 scripts/verify_persistence.py

This test restarts only the app service and leaves a test appointment in MySQL.
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
    created = request_json(
        "/api/appointments",
        method="POST",
        body={
            "customerName": "部署测试用户",
            "doctorName": "部署测试医生",
            "startTime": start_time.isoformat(),
        },
        expected_status=201,
    )

    if not isinstance(created, dict):
        raise RuntimeError("The create response must be a JSON object.")

    appointment_id = created.get("id")
    if type(appointment_id) is not int or appointment_id <= 0:
        raise RuntimeError("The create response must contain a positive integer id.")

    detail_path = f"/api/appointments/{appointment_id}"
    before_restart = request_json(detail_path)
    if before_restart != created:
        raise RuntimeError("The saved appointment differs from the create response.")

    print(f"Created appointment {appointment_id}. Restarting app...", flush=True)
    subprocess.run(
        ["docker", "compose", "restart", "app"],
        cwd=PROJECT_DIR,
        check=True,
    )

    wait_until_ready()
    after_restart = request_json(detail_path)
    if after_restart != before_restart:
        raise RuntimeError("The appointment changed after restarting the app.")

    print(
        f"PASS: appointment {appointment_id} survived the application restart.",
        flush=True,
    )


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, URLError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
