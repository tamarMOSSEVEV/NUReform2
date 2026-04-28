import datetime
import logging
import threading
from collections import defaultdict

import firebase_admin
from firebase_admin import credentials, firestore
from flask import Flask, jsonify

from algorithm import Nurse, QuotaRequirement, run_vns

# ── Algorithm Configuration ─────────────────────────────────────────
VNS_MAX_ITERATIONS = 700
VNS_SEED = 8787631
VNS_MULTI_START_RUNS = 1

# ── Shift Quota (min nurses per shift per day) ──────────────────────
QUOTA_MORNING = 5
QUOTA_AFTERNOON = 4
QUOTA_EVENING = 3

# ── Shift Name Mappings ─────────────────────────────────────────────
# Firestore stores: "morning", "noon", "evening"
# Algorithm uses:   "MORNING", "AFTERNOON", "EVENING"
SHIFT_MAP_TO_ALGO = {"morning": "MORNING", "noon": "AFTERNOON", "evening": "EVENING"}
SHIFT_MAP_FROM_ALGO = {"MORNING": "morning", "AFTERNOON": "noon", "EVENING": "evening"}

# Hebrew display names for the finalized schedule (read by Android NurseSchedule)
SHIFT_DISPLAY = {"morning": "בוקר", "noon": "צהריים", "evening": "ערב"}

# Day ordering (Sunday-first, matching Israeli work week)
DAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
DAY_KEYS = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]

# ── Firebase Init ───────────────────────────────────────────────────
cred = credentials.Certificate("firebase_service_account.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@app.before_request
def log_request():
    from flask import request
    logger.info(f">>> {request.method} {request.path} from {request.remote_addr}")


def get_next_week_doc_id():
    """Returns the Firestore document ID for next week, e.g. '2026_18'.
    Nurses choose shifts for next week, manager schedules for next week."""
    today = datetime.date.today()
    next_week = today + datetime.timedelta(weeks=1)
    iso_year, iso_week, _ = next_week.isocalendar()
    return f"{iso_year}_{iso_week}"


def get_next_week_dates():
    """Returns a dict mapping day names to ISO date strings for next week.
    e.g. {"Sunday": "2026-04-26", "Monday": "2026-04-27", ...}
    """
    today = datetime.date.today()
    # Find the most recent Sunday
    days_since_sunday = (today.weekday() + 1) % 7
    sunday = today - datetime.timedelta(days=days_since_sunday)
    # Move to next week's Sunday
    sunday = sunday + datetime.timedelta(weeks=1)

    day_map = {}
    for i, day_name in enumerate(DAY_NAMES):
        day_map[day_name] = (sunday + datetime.timedelta(days=i)).isoformat()
    return day_map


def build_nurses_from_firestore(week_doc_id, day_map):
    """Read nurses and their preferences from Firestore, convert to algorithm Nurse objects."""
    nurses_ref = db.collection("nurses")
    nurses_docs = list(nurses_ref.stream())

    nurses = []
    nurses_by_id = {}

    for nurse_doc in nurses_docs:
        uid = nurse_doc.id
        data = nurse_doc.to_dict()
        name = data.get("name", "")
        job_percentage = float(data.get("jobPercentage", 100))

        # Read this nurse's shift preferences for the current week
        week_doc = (
            nurses_ref
            .document(uid)
            .collection("weekly_shifts")
            .document(week_doc_id)
            .get()
        )

        availability = set()
        if week_doc.exists:
            shifts = week_doc.to_dict().get("shifts", {})
            # shifts format: {"Sunday": ["morning", "noon"], "Tuesday": ["evening"], ...}
            for day_name, shift_list in shifts.items():
                iso_date = day_map.get(day_name)
                if iso_date is None:
                    continue
                for shift in shift_list:
                    algo_shift = SHIFT_MAP_TO_ALGO.get(shift)
                    if algo_shift:
                        availability.add((iso_date, algo_shift))

        nurse = Nurse(
            id=uid,
            name=name,
            availability=availability,
            position_percentage=job_percentage,
        )
        nurses.append(nurse)
        nurses_by_id[uid] = nurse

    return nurses, nurses_by_id


def build_quota(day_map):
    """Build QuotaRequirement for the 7 days of the current week."""
    requirements = {}
    for day_name in DAY_NAMES:
        iso_date = day_map[day_name]
        requirements[(iso_date, "MORNING")] = QUOTA_MORNING
        requirements[(iso_date, "AFTERNOON")] = QUOTA_AFTERNOON
        requirements[(iso_date, "EVENING")] = QUOTA_EVENING
    return QuotaRequirement(requirements=requirements)


def convert_schedule_to_firestore(schedule, nurses_by_id, day_map):
    """Convert algorithm Schedule output to the NurseSchedule format for Firestore.

    Returns a list of dicts like:
    [{"nurseName": "דנה כהן", "sunday": "בוקר", "monday": "X", ...}, ...]
    """
    # Invert day_map: iso_date -> lowercase day key
    iso_to_day_key = {}
    for i, day_name in enumerate(DAY_NAMES):
        iso_to_day_key[day_map[day_name]] = DAY_KEYS[i]

    # Group assignments by nurse_id
    nurse_assignments = defaultdict(list)
    for assignment in schedule.assignments:
        nurse_assignments[assignment.nurse_id].append(assignment)

    result = []
    for nurse_id, assignments in nurse_assignments.items():
        nurse = nurses_by_id.get(nurse_id)
        entry = {"nurseName": nurse.name if nurse else nurse_id}

        # Initialize all days to "X"
        for day_key in DAY_KEYS:
            entry[day_key] = "X"

        # Fill in assigned shifts
        for assignment in assignments:
            day_key = iso_to_day_key.get(assignment.day)
            if day_key:
                firestore_shift = SHIFT_MAP_FROM_ALGO.get(assignment.shift, assignment.shift)
                entry[day_key] = SHIFT_DISPLAY.get(firestore_shift, firestore_shift)

        result.append(entry)

    return result


def run_scheduling_background(week_doc_id, nurses, quota, nurses_by_id, day_map):
    """Background thread: run the VNS algorithm and save results to Firestore."""
    try:
        logger.info(f"Starting VNS algorithm for week {week_doc_id} "
                     f"(iterations={VNS_MAX_ITERATIONS}, seed={VNS_SEED}, "
                     f"multi_start={VNS_MULTI_START_RUNS})")

        schedule, cost, info = run_vns(
            nurses=nurses,
            quota=quota,
            max_iterations=VNS_MAX_ITERATIONS,
            seed=VNS_SEED,
            multi_start_runs=VNS_MULTI_START_RUNS,
        )

        assignments = convert_schedule_to_firestore(schedule, nurses_by_id, day_map)

        db.collection("nurses_shifts").document(week_doc_id).update({
            "status": "success",
            "assignments": assignments,
            "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "feasible": info.get("feasible", False),
            "totalObjective": cost,
        })

        logger.info(f"Scheduling completed for week {week_doc_id}. "
                     f"Feasible: {info.get('feasible')}, Cost: {cost:.1f}, "
                     f"Assignments: {len(assignments)} nurses")

    except Exception as e:
        logger.error(f"Scheduling failed for week {week_doc_id}: {e}")
        try:
            db.collection("nurses_shifts").document(week_doc_id).update({
                "status": "error",
                "message": str(e),
            })
        except Exception as update_err:
            logger.error(f"Failed to update error status in Firestore: {update_err}")


@app.route('/shifts/current-week', methods=['GET'])
def get_current_week_shifts():
    """Trigger scheduling for the current week.

    Fire-and-forget: saves status 'running' to Firestore, starts the algorithm
    in a background thread, and returns immediately.
    Android listens to the Firestore document for real-time status updates.
    """
    week_doc_id = get_next_week_doc_id()

    # Check if scheduling already exists or is in progress
    nurses_shifts_ref = db.collection("nurses_shifts").document(week_doc_id)
    existing_doc = nurses_shifts_ref.get()
    if existing_doc.exists:
        status = existing_doc.to_dict().get("status", "")
        if status == "success":
            return jsonify({
                "status": "error",
                "message": f"Schedule for week '{week_doc_id}' already exists."
            }), 409
        if status == "running":
            return jsonify({
                "status": "error",
                "message": f"Scheduling for week '{week_doc_id}' is already in progress."
            }), 409

    # Build data for the algorithm
    day_map = get_next_week_dates()
    nurses, nurses_by_id = build_nurses_from_firestore(week_doc_id, day_map)
    quota = build_quota(day_map)

    # Save "running" status to Firestore immediately
    nurses_shifts_ref.set({
        "week": week_doc_id,
        "status": "running",
        "startedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    })

    # Start algorithm in background thread
    thread = threading.Thread(
        target=run_scheduling_background,
        args=(week_doc_id, nurses, quota, nurses_by_id, day_map),
        daemon=True,
    )
    thread.start()

    logger.info(f"Scheduling started for week {week_doc_id} with {len(nurses)} nurses")

    return jsonify({
        "status": "running",
        "week": week_doc_id,
    }), 200


def get_week_dates_for(week_doc_id):
    """Like get_week_dates() but for a specific year_week string (e.g. '2026_17').
    Computes the Sunday–Saturday dates for that ISO week."""
    year, week = week_doc_id.split("_")
    year, week = int(year), int(week)
    # ISO week 1 starts on the Monday that contains Jan 4.
    # We want the Sunday before that Monday.
    jan4 = datetime.date(year, 1, 4)
    # Monday of ISO week 1
    monday_w1 = jan4 - datetime.timedelta(days=jan4.weekday())
    # Monday of the target week
    monday = monday_w1 + datetime.timedelta(weeks=week - 1)
    # Sunday = day before Monday
    sunday = monday - datetime.timedelta(days=1)

    day_map = {}
    for i, day_name in enumerate(DAY_NAMES):
        day_map[day_name] = (sunday + datetime.timedelta(days=i)).isoformat()
    return day_map


def run_local(week_doc_id, save_to_firestore=False):
    """Run the algorithm locally for a specific week. Prints output to console.

    Usage:
        python app.py run <week>              # dry run, print only
        python app.py run <week> --save       # run and save to Firestore

    Example:
        python app.py run 2026_17
        python app.py run 2026_17 --save
    """
    import json

    day_map = get_week_dates_for(week_doc_id)

    print(f"=== INPUT ===")
    print(f"Week: {week_doc_id}")
    print(f"Days: {day_map['Sunday']} (Sun) → {day_map['Saturday']} (Sat)")

    nurses, nurses_by_id = build_nurses_from_firestore(week_doc_id, day_map)
    quota = build_quota(day_map)

    nurses_with_avail = [n for n in nurses if len(n.availability) > 0]
    print(f"Nurses total: {len(nurses)} ({len(nurses_with_avail)} with preferences)")
    print(f"Config: iterations={VNS_MAX_ITERATIONS}, seed={VNS_SEED}, multi_start={VNS_MULTI_START_RUNS}")
    print(f"Quota/day: morning={QUOTA_MORNING}, afternoon={QUOTA_AFTERNOON}, evening={QUOTA_EVENING}")
    print()

    print("Running VNS algorithm...")
    schedule, cost, info = run_vns(
        nurses=nurses,
        quota=quota,
        max_iterations=VNS_MAX_ITERATIONS,
        seed=VNS_SEED,
        multi_start_runs=VNS_MULTI_START_RUNS,
    )

    print(f"\n=== RESULT ===")
    print(f"Feasible: {info.get('feasible')}")
    print(f"Total objective: {cost:.1f}")
    print(f"Iterations: {info.get('iterations')}")
    print(f"Total assignments: {len(schedule.assignments)}")
    print()

    assignments = convert_schedule_to_firestore(schedule, nurses_by_id, day_map)

    print(f"=== SCHEDULE ({len(assignments)} nurses) ===")
    print(json.dumps(assignments, indent=2, ensure_ascii=False))

    if save_to_firestore:
        print(f"\nSaving to Firestore nurses_shifts/{week_doc_id}...")
        db.collection("nurses_shifts").document(week_doc_id).set({
            "week": week_doc_id,
            "status": "success",
            "assignments": assignments,
            "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "feasible": info.get("feasible", False),
            "totalObjective": cost,
        })
        print("Saved!")


if __name__ == '__main__':
    import sys

    if len(sys.argv) >= 3 and sys.argv[1] == "run":
        week = sys.argv[2]
        save = "--save" in sys.argv
        run_local(week, save_to_firestore=save)
    else:
        app.run(host="0.0.0.0", port=3001, debug=True)
