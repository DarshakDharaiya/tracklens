"""Measures how much a sudden process death loses (plan Task 23: at most 250 ms of events).

Needs a device with the debug sample installed (``./gradlew :sample:installDebug``) and ``adb`` on the PATH or in
ANDROID_HOME. Each run starts the sample's 10 ms ticker, kills the process with ``kill -9`` (no ON_STOP, no crash
handler: the worst case), copies the database out and compares the newest tick in logcat (which survives the kill)
with the newest tick stored. Both timestamps come from the device clock.

    python sample/qa/measure_kill_loss.py [runs]
"""

import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time

PACKAGE = "io.github.tracklens.sample"
ACTIVITY = PACKAGE + "/.MainActivity"
DB = "no_backup/tracklens.db"
TAG = "TrackLensTicker"


def adb_path():
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if root:
            candidate = os.path.join(root, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
            if os.path.exists(candidate):
                return candidate
    return "adb"


ADB = adb_path()


def adb(*args, binary=False):
    result = subprocess.run([ADB, *args], capture_output=True, check=False)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def last_logged_tick():
    """(index, epoch ms) of the newest tick in logcat."""
    lines = adb("logcat", "-d", "-v", "epoch", "-s", TAG + ":I").splitlines()
    ticks = [line for line in lines if " tick " in line]
    if not ticks:
        return None
    fields = ticks[-1].split()
    return int(fields[-1]), round(float(fields[0]) * 1000)


def last_stored_tick(directory):
    """(index, epoch ms) of the newest tick in the copied database (its WAL is replayed on open)."""
    for suffix in ("", "-wal", "-shm"):
        data = adb("exec-out", "run-as", PACKAGE, "cat", DB + suffix, binary=True)
        if data:
            with open(os.path.join(directory, "tracklens.db" + suffix), "wb") as out:
                out.write(data)
    connection = sqlite3.connect(os.path.join(directory, "tracklens.db"))
    try:
        row = connection.execute(
            "SELECT CAST(json_extract(params_json, '$.index') AS INTEGER), timestamp FROM events "
            "WHERE name = 'tick' ORDER BY seq DESC LIMIT 1"
        ).fetchone()
    finally:
        connection.close()
    return row


def run_once(warm_up_seconds):
    adb("shell", "pm", "clear", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "flow", "ticker")
    time.sleep(warm_up_seconds)
    pid = adb("shell", "pidof", PACKAGE).strip()
    adb("shell", "run-as", PACKAGE, "kill", "-9", pid)
    time.sleep(0.5)
    logged = last_logged_tick()
    directory = tempfile.mkdtemp(prefix="tracklens-kill-")
    try:
        stored = last_stored_tick(directory)
    finally:
        shutil.rmtree(directory, ignore_errors=True)
    if logged is None or stored is None:
        return None
    return {"logged": logged[0], "stored": stored[0], "lost_ticks": logged[0] - stored[0], "lost_ms": logged[1] - stored[1]}


def main():
    runs = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    results = []
    for run in range(runs):
        # Vary the moment of death relative to the flush timer.
        result = run_once(warm_up_seconds=2.0 + run * 0.137)
        print(f"run {run + 1}: {result}")
        if result:
            results.append(result)
    if results:
        lost = sorted(r["lost_ms"] for r in results)
        print(f"lost ms over {len(lost)} runs: min {lost[0]}, median {lost[len(lost) // 2]}, max {lost[-1]}")


if __name__ == "__main__":
    main()
