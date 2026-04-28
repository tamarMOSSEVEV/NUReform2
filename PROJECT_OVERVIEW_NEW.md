# NUReform — Project Overview

Nurse-shift scheduling system. Nurses submit weekly availability through an Android app; managers trigger an optimization algorithm on a Flask backend that builds a fair schedule and writes the result back to Firestore.

---

## 1. Architecture

```
┌──────────────────────────────────┐
│        Android App (Kotlin)      │
│   Role-based UI (Manager/Nurse)  │
└────────────┬─────────────────────┘
             │ Retrofit HTTP (port 3001)
             ▼
┌──────────────────────────────────┐
│       Flask Backend (Python)     │
│  Multi-start VNS + Greedy Init   │
└────────────┬─────────────────────┘
             │ firebase-admin SDK
             ▼
┌──────────────────────────────────┐
│            Firebase              │
│   Auth  │  Firestore Database    │
└──────────────────────────────────┘
```

The Android app and the backend share Firestore. The backend is invoked only to *trigger* scheduling — all reads/writes happen through Firestore. The Android client also listens to Firestore for live status updates instead of polling the HTTP endpoint.

---

## 2. Repository Layout

```
NUReform2/
├── backend/
│   └── FlaskProject/
│       ├── app.py               Flask app + Firestore I/O glue
│       ├── requirements.txt     Flask 3.1.2, firebase-admin 7.2.0
│       ├── firebase_service_account.json   (gitignored credential)
│       ├── algorithm/           pure-Python scheduling library
│       │   ├── __init__.py
│       │   ├── constants.py     ShiftType enum, hours, caps
│       │   ├── models.py        Nurse, QuotaRequirement, Assignment, Schedule
│       │   ├── constraints.py   hard + soft constraint checking
│       │   ├── greedy_solution.py   greedy initial schedule
│       │   ├── neighborhood.py  2-exchange / reassign / add neighborhoods
│       │   └── vns.py           Variable Neighborhood Search driver
│       └── utilities/
│           └── register_users.py    one-off user/nurse seeding script
└── android/
    └── NuReform/                Gradle project (Kotlin DSL)
        ├── app/
        │   ├── build.gradle.kts
        │   ├── google-services.json
        │   └── src/main/java/com/example/nureform/
        │       ├── NureformApplication.kt
        │       ├── SplashActivity.kt
        │       ├── LoginActivity.kt
        │       ├── HomeActivity.kt
        │       ├── di/AppModule.kt
        │       ├── data/
        │       │   ├── api/        Retrofit client + service
        │       │   ├── model/      User, Nurse, ShiftType, schedules, ...
        │       │   └── repository/ Auth / Nurses / Shifts / Scheduling
        │       ├── ui/
        │       │   ├── auth/           login screen
        │       │   ├── home/           dashboard + action cards
        │       │   ├── nurses/         add / delete / list nurses
        │       │   ├── nursesrequests/ manager view of submissions
        │       │   ├── shifts/         nurse preferences entry
        │       │   ├── scheduling/     trigger scheduling (manager)
        │       │   ├── schedule/       view + edit final schedule
        │       │   └── dailyschedule/  per-day shift summary (both roles)
        │       └── utils/ShiftConstants.kt
        └── settings.gradle.kts
```

---

## 3. Backend — `backend/FlaskProject/`

### Stack
- Python 3, Flask 3.1.2
- firebase-admin 7.2.0 (Firestore + Auth admin)
- Server: `app.run(host="0.0.0.0", port=3001, debug=True)`

### HTTP API

```
GET /shifts/current-week
```

Despite the name, this triggers scheduling for **next** week (`get_next_week_doc_id()` adds one ISO week to today).

Behavior:
1. Computes `week_doc_id` like `"2026_18"` (ISO year + week).
2. Reads `nurses_shifts/{week_doc_id}`:
   - `status == "success"` → `409` (already scheduled).
   - `status == "running"` → `409` (in progress).
3. Reads all nurses + their `weekly_shifts/{week_doc_id}` preferences from Firestore.
4. Writes `{ status: "running", startedAt }` to `nurses_shifts/{week_doc_id}`.
5. Spawns a daemon thread that runs the VNS algorithm and updates the same Firestore doc with either:
   - `{ status: "success", assignments, createdAt, feasible, totalObjective }`, or
   - `{ status: "error", message }`.
6. Returns immediately: `200 { status: "running", week }`.

The Android client listens to that Firestore document for the result.

### CLI Mode

```
python app.py run 2026_17           # dry run, prints schedule JSON
python app.py run 2026_17 --save    # also writes nurses_shifts/2026_17
```

### Data Mapping (Firestore ↔ Algorithm)

| Concept | Firestore | Algorithm |
|---|---|---|
| Shift names | `morning`, `noon`, `evening` | `MORNING`, `AFTERNOON`, `EVENING` |
| Display | `בוקר`, `צהריים`, `ערב` (Hebrew) | — |
| Day | `Sunday`…`Saturday` | ISO date `YYYY-MM-DD` |
| Schedule output cell | `"בוקר"` / `"X"` (no shift) | `Assignment(nurse_id, day, shift)` |

### Per-Shift Quota (minimum nurses required)

| Shift | Quota |
|---|---|
| Morning | 5 |
| Afternoon (`noon`) | 4 |
| Evening | 3 |

### Scheduling Algorithm — `algorithm/`

Pure-Python package; no Flask coupling.

**Core types** (`models.py`):
- `Nurse(id, name, availability: Set[(date, shift)], position_percentage)`
  - `100% → 5 target / 6 max shifts`
  - `75%  → 4 / 4`
  - `50%  → 3 / 3`
  - Per-tier preferred MORNING/AFTERNOON/EVENING split for fairness.
- `QuotaRequirement(requirements: {(day, shift): int})`
- `Assignment(nurse_id, day, shift)` and `Schedule(assignments: [Assignment])`

**Hard constraints** (`constraints.py`):
- One shift per nurse per day.
- ≥ `MIN_HOURS_BETWEEN_SHIFTS` (12h) between any two consecutive shifts (e.g. no evening → morning next day).
- ≤ `MAX_SHIFTS_PER_WEEK` per nurse, capped further by job percentage.
- Personal availability — never schedule a nurse outside their submitted preferences.
- Per-(day, shift) quota — flagged as a hard violation but enforced via penalty, not filtering.

**Soft constraints**:
- Under-target on weekly contractual shift count.
- Tier inversions (e.g. a 50% nurse with more shifts than a 100%).
- Per-shift-type fairness (deviation from target morning/afternoon/evening counts).

**Objective** (`vns.py`):

```
total = HARD_VIOLATION_WEIGHT  * quota_deficit²
      + VIOLATION_COUNT_WEIGHT * #hard_violations
      + OVERSTAFFING_WEIGHT    * overstaffing_penalty
      + soft_cost
```

With `HARD_VIOLATION_WEIGHT = 500_000`, `VIOLATION_COUNT_WEIGHT = 5_000`, `OVERSTAFFING_WEIGHT = 120_000`.

**Search**:
1. **Greedy initial** schedule (`greedy_solution.py`), seeded.
2. **VNS loop** for up to `max_iterations` (default 200), each iteration tries in order:
   - best-improving 2-exchange (swap two assignments between nurses),
   - best-improving reassign (move an assignment to another nurse),
   - best-improving add (fill an unstaffed slot).
   - If none improves, **shake**: pick a random swap from the top-K stress-sorted neighbors.
3. **Multi-start** (`multi_start_runs`, default 1): re-run greedy + VNS with different seeds; pick the run with fewest hard violations, ties broken by lowest soft score.

Entry point: `run_vns(nurses, quota, max_iterations, seed, multi_start_runs) -> (Schedule, total_cost, info_dict)`.

---

## 4. Android App — `android/NuReform/`

### Stack
- Kotlin, Coroutines, View Binding
- `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, JVM target 11
- Firebase BOM: Auth, Firestore, Analytics, Crashlytics, Config, Storage
- Retrofit + OkHttp + Gson, with `HttpLoggingInterceptor(BODY)`
- Koin for DI
- Navigation Component (`safeargs`)
- Glide (with OkHttp3 integration)
- Realm (`androidx.realm`)
- ZXing (QR scanning)
- Apache POI 5.2.5 (`poi`, `poi-ooxml`) — reason for `minSdk 26`
- Timber (logging)
- Hebrew UI / RTL layout

### Package Layout — `com.example.nureform`

| Layer | Package | Notes |
|---|---|---|
| App entry | `NureformApplication` | Koin start, RTL forced |
| Activities | `SplashActivity`, `LoginActivity`, `HomeActivity` | Splash → Login or Home |
| DI | `di/AppModule.kt` | All singletons + ViewModels |
| API | `data/api/RetrofitClient.kt`, `SchedulingApiService.kt` | Base URL hardcoded (see below) |
| Models | `data/model/` | `User`, `UserRole`, `Nurse`, `ShiftType`, `DayShifts`, `WeeklyShiftSelection`, `NurseShiftRequest`, `NurseSchedule` (with `jobPercentage`), `SchedulingResponse` |
| Repos | `data/repository/` | `AuthRepository`, `NursesRepository`, `ShiftsRepository`, `SchedulingRepository` |
| UI | `ui/auth`, `ui/home`, `ui/nurses`, `ui/nursesrequests`, `ui/shifts`, `ui/scheduling`, `ui/schedule`, `ui/dailyschedule` | One sealed `XState` per feature |

### Navigation

```
SplashActivity
   ├── not authenticated  →  LoginActivity
   └── authenticated      →  HomeActivity
                              └── HomeFragment (cards filtered by role)
                                  ├── [Manager] AddNurseFragment
                                  ├── [Manager] DeleteNurseFragment
                                  ├── [Manager] NursesRequestsFragment
                                  ├── [Manager] RunSchedulingFragment
                                  ├── [Nurse]   ChooseShiftsFragment
                                  ├── [Both]    NursesListFragment
                                  ├── [Both]    ScheduleViewFragment
                                  └── [Both]    DailyScheduleFragment
```

### Roles & Capabilities

| Role | Capabilities |
|---|---|
| **Manager** | add/delete nurses (with explicit password), view all submissions, trigger scheduling, view & **edit** final schedule, view daily summary |
| **Nurse** | submit weekly preferences, view own/all schedules, view daily summary |

### Repositories (DI singletons)

- **`AuthRepository`** — `login`, `fetchUserData`, `getCurrentUser`, `logout` (Firebase Auth + `users/{uid}`).
- **`NursesRepository`** — CRUD on `nurses/{uid}`. `addNurse()` uses a **secondary FirebaseAuth instance** so the manager's session is preserved while creating the new account. Password is set explicitly by the manager (min 6 chars).
- **`ShiftsRepository`** — submit / read `nurses/{uid}/weekly_shifts/{year_week}`; aggregate manager view; `getScheduleForWeek()` joins the `nurses` collection to populate `jobPercentage` per nurse; `updateScheduleAssignments()` writes manager edits back to Firestore; submission window helper (currently always open — testing mode).
- **`SchedulingRepository`** — thin wrapper around `SchedulingApiService.runScheduling()` (the `GET /shifts/current-week` call).

### ViewModels

| ViewModel | Purpose |
|---|---|
| `LoginViewModel` | Firebase Auth login |
| `HomeViewModel` | Load user data, shift-window guard |
| `AddNurseViewModel` | Create nurse account (password validated locally + Firebase) |
| `DeleteNurseViewModel` | Remove nurse from Firestore |
| `NursesListViewModel` | Fetch nurse list |
| `ChooseShiftsViewModel` | Submit weekly preferences |
| `ScheduleViewModel` | Load schedule for both weeks; `EditScheduleState` + `saveEditedSchedule()` for manager edits |
| `NursesRequestsViewModel` | Load all nurses' preference submissions |
| `RunSchedulingViewModel` | Trigger backend + listen for result |
| `DailyScheduleViewModel` | Load both weeks, transform per-nurse data → per-day `DaySummary` lists |

### Networking

```kotlin
// RetrofitClient.kt
BASE_URL = "http://192.168.68.64:3001/"   // dev machine LAN IP
timeouts = 30s connect / read / write
logging  = HttpLoggingInterceptor.Level.BODY
```

> The base URL is currently a hardcoded LAN IP. For an emulator change to `http://10.0.2.2:3001/`; for production it should be configurable.

---

## 5. Firestore Schema

```
firestore/
├── users/{uid}
│     uid, name, role            # role ∈ {NURSE, MANAGER}
│
├── nurses/{uid}
│     idNumber, name, phone, email, userId, createdAt, jobPercentage
│     └── weekly_shifts/{year_week}                  # nurse preferences
│           nurseId, weekNumber, year, submittedAt
│           shifts: { Sunday: ["morning","noon"], ... }
│
└── nurses_shifts/{year_week}                        # finalized schedule
      week, status, startedAt | createdAt
      status ∈ { "running", "success", "error" }
      # on success:
      feasible, totalObjective
      lastEditedAt                                   # set on manager edits
      assignments: [
        { nurseName, sunday, monday, ..., saturday } # values: "בוקר"/"צהריים"/"ערב"/"X"
      ]
```

`year_week` format: `"<isoYear>_<isoWeek>"`, e.g. `"2026_18"`.

---

## 6. End-to-End Workflow

```
1. Nurse opens ChooseShiftsFragment
   └── 7-day × 3-shift checkbox grid
       └── Firestore: nurses/{uid}/weekly_shifts/{week}

2. Manager opens NursesRequestsFragment
   └── Sees who has submitted

3. Manager taps "Run Scheduling" (RunSchedulingFragment)
   └── HTTP GET http://<host>:3001/shifts/current-week
       ├── Backend writes status="running" to nurses_shifts/{week}
       ├── Background thread: greedy + VNS
       └── Backend writes status="success" + assignments
   └── Android UI listens to nurses_shifts/{week} for live status

4. Anyone opens ScheduleViewFragment
   └── Reads nurses_shifts/{week} → renders RTL week grid
       columns: שם | משרה% | ראשון…שבת (each with date)
   └── [Manager only] taps "ערוך" → enters edit mode
       ├── Tap any day cell → dialog (בוקר / צהריים / ערב / ללא משמרת)
       ├── Cell updates immediately in the grid
       ├── "עדכן" → saves all changes to Firestore (assignments + lastEditedAt)
       └── "ביטול" → discards all changes and exits

5. Anyone opens DailyScheduleFragment (סיכום יומי)
   └── Two tabs: current week / next week
       └── 7 day-cards, each showing:
           בוקר / צהריים / ערב sections
           nurse name + job% per slot

6. Manager opens PDF export
   └── RTL layout: שם (right) | משרה% | ראשון…שבת (left)
       header block: title, date range, generated-on, nurse count
       day columns include date (e.g. "ראשון\n27/04")
```

---

## 7. Key Features Added (v2)

| Feature | Files Changed |
|---|---|
| **`jobPercentage` in schedule grid** — separate column from name | `NurseSchedule.kt`, `ShiftsRepository.kt`, `ScheduleAdapter.kt`, `item_nurse_schedule.xml`, `fragment_schedule_view.xml` |
| **Manager-set nurse password** — explicit field, min 6 chars | `AddNurseViewModel.kt`, `AddNurseFragment.kt`, `fragment_add_nurse.xml` |
| **Manager edit schedule** — tap-cell dialog, instant visual update, Update/Cancel | `ScheduleViewFragment.kt`, `ScheduleViewModel.kt`, `fragment_schedule_view.xml` |
| **PDF RTL** — שם rightmost, שבת leftmost, per-day dates, report header | `ScheduleViewFragment.kt` |
| **Daily Summary fragment** — per-day shift cards for both roles | `ui/dailyschedule/` (State, ViewModel, Adapter, Fragment), `fragment_daily_schedule.xml`, `item_day_summary.xml`, `nav_graph_home.xml`, `HomeFragment.kt`, `ActionCard.kt` |

---

## 8. Running the Project

### Backend
```bash
cd backend/FlaskProject
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# Place firebase_service_account.json in this directory
python app.py                          # starts Flask on :3001
# or one-shot:
python app.py run 2026_17 --save
```

### Android
1. Open `android/NuReform/` in Android Studio.
2. Ensure `app/google-services.json` is present.
3. Edit `BASE_URL` in `RetrofitClient.kt` if needed.
4. Run on device/emulator (minSdk 26).

---

## 9. Known Limitations

| Issue | Where | Notes |
|---|---|---|
| `BASE_URL` is a hardcoded LAN IP | `RetrofitClient.kt` | Should be build-config driven |
| Submission window always open | `ShiftsRepository` | Production should restrict to Sun–Mon |
| Secondary Auth instance to add nurses | `NursesRepository` | Workaround to avoid logging the manager out |
| Service account JSON in repo path | `backend/FlaskProject/` | Must remain gitignored |
| Single-threaded scheduling per process | `app.py` | One daemon thread per request; concurrent triggers rely on Firestore status check for idempotency |
| Name-based jobPct join | `ShiftsRepository.getScheduleForWeek` | `assignments` only stores `nurseName`; if two nurses share a name, jobPct could be wrong |


Nurse-shift scheduling system. Nurses submit weekly availability through an Android app; managers trigger an optimization algorithm on a Flask backend that builds a fair schedule and writes the result back to Firestore.

---

## 1. Architecture

```
┌──────────────────────────────────┐
│        Android App (Kotlin)      │
│   Role-based UI (Manager/Nurse)  │
└────────────┬─────────────────────┘
             │ Retrofit HTTP (port 3001)
             ▼
┌──────────────────────────────────┐
│       Flask Backend (Python)     │
│  Multi-start VNS + Greedy Init   │
└────────────┬─────────────────────┘
             │ firebase-admin SDK
             ▼
┌──────────────────────────────────┐
│            Firebase              │
│   Auth  │  Firestore Database    │
└──────────────────────────────────┘
```

The Android app and the backend share Firestore. The backend is invoked only to *trigger* scheduling — all reads/writes happen through Firestore. The Android client also listens to Firestore for live status updates instead of polling the HTTP endpoint.

---

## 2. Repository Layout

```
NUReform2/
├── backend/
│   └── FlaskProject/
│       ├── app.py               Flask app + Firestore I/O glue
│       ├── requirements.txt     Flask 3.1.2, firebase-admin 7.2.0
│       ├── firebase_service_account.json   (gitignored credential)
│       ├── algorithm/           pure-Python scheduling library
│       │   ├── __init__.py
│       │   ├── constants.py     ShiftType enum, hours, caps
│       │   ├── models.py        Nurse, QuotaRequirement, Assignment, Schedule
│       │   ├── constraints.py   hard + soft constraint checking
│       │   ├── greedy_solution.py   greedy initial schedule
│       │   ├── neighborhood.py  2-exchange / reassign / add neighborhoods
│       │   └── vns.py           Variable Neighborhood Search driver
│       └── utilities/
│           └── register_users.py    one-off user/nurse seeding script
└── android/
    └── NuReform/                Gradle project (Kotlin DSL)
        ├── app/
        │   ├── build.gradle.kts
        │   ├── google-services.json
        │   └── src/main/java/com/example/nureform/
        │       ├── NureformApplication.kt
        │       ├── SplashActivity.kt
        │       ├── LoginActivity.kt
        │       ├── HomeActivity.kt
        │       ├── di/AppModule.kt
        │       ├── data/
        │       │   ├── api/        Retrofit client + service
        │       │   ├── model/      User, Nurse, ShiftType, schedules, ...
        │       │   └── repository/ Auth / Nurses / Shifts / Scheduling
        │       ├── ui/
        │       │   ├── auth/       login screen
        │       │   ├── home/       dashboard + action cards
        │       │   ├── nurses/     add / delete / list nurses
        │       │   ├── nursesrequests/  manager view of submissions
        │       │   ├── shifts/     nurse preferences entry
        │       │   ├── scheduling/ trigger scheduling (manager)
        │       │   └── schedule/   view final schedule
        │       └── utils/ShiftConstants.kt
        └── settings.gradle.kts
```

---

## 3. Backend — `backend/FlaskProject/`

### Stack
- Python 3, Flask 3.1.2
- firebase-admin 7.2.0 (Firestore + Auth admin)
- Server: `app.run(host="0.0.0.0", port=3001, debug=True)`

### HTTP API

```
GET /shifts/current-week
```

Despite the name, this triggers scheduling for **next** week (`get_next_week_doc_id()` adds one ISO week to today).

Behavior:
1. Computes `week_doc_id` like `"2026_18"` (ISO year + week).
2. Reads `nurses_shifts/{week_doc_id}`:
   - `status == "success"` → `409` (already scheduled).
   - `status == "running"` → `409` (in progress).
3. Reads all nurses + their `weekly_shifts/{week_doc_id}` preferences from Firestore.
4. Writes `{ status: "running", startedAt }` to `nurses_shifts/{week_doc_id}`.
5. Spawns a daemon thread that runs the VNS algorithm and updates the same Firestore doc with either:
   - `{ status: "success", assignments, createdAt, feasible, totalObjective }`, or
   - `{ status: "error", message }`.
6. Returns immediately: `200 { status: "running", week }`.

The Android client listens to that Firestore document for the result.

### CLI Mode

```
python app.py run 2026_17           # dry run, prints schedule JSON
python app.py run 2026_17 --save    # also writes nurses_shifts/2026_17
```

### Data Mapping (Firestore ↔ Algorithm)

| Concept | Firestore | Algorithm |
|---|---|---|
| Shift names | `morning`, `noon`, `evening` | `MORNING`, `AFTERNOON`, `EVENING` |
| Display | `בוקר`, `צהריים`, `ערב` (Hebrew) | — |
| Day | `Sunday`…`Saturday` | ISO date `YYYY-MM-DD` |
| Schedule output cell | `"בוקר"` / `"X"` (no shift) | `Assignment(nurse_id, day, shift)` |

### Per-Shift Quota (minimum nurses required)

| Shift | Quota |
|---|---|
| Morning | 5 |
| Afternoon (`noon`) | 4 |
| Evening | 3 |

### Scheduling Algorithm — `algorithm/`

Pure-Python package; no Flask coupling.

**Core types** (`models.py`):
- `Nurse(id, name, availability: Set[(date, shift)], position_percentage)`
  - `100% → 5 target / 6 max shifts`
  - `75%  → 4 / 4`
  - `50%  → 3 / 3`
  - Per-tier preferred MORNING/AFTERNOON/EVENING split for fairness.
- `QuotaRequirement(requirements: {(day, shift): int})`
- `Assignment(nurse_id, day, shift)` and `Schedule(assignments: [Assignment])`

**Hard constraints** (`constraints.py`):
- One shift per nurse per day.
- ≥ `MIN_HOURS_BETWEEN_SHIFTS` (12h) between any two consecutive shifts (e.g. no evening → morning next day).
- ≤ `MAX_SHIFTS_PER_WEEK` per nurse, capped further by job percentage.
- Personal availability — never schedule a nurse outside their submitted preferences.
- Per-(day, shift) quota — flagged as a hard violation but enforced via penalty, not filtering.

**Soft constraints**:
- Under-target on weekly contractual shift count.
- Tier inversions (e.g. a 50% nurse with more shifts than a 100%).
- Per-shift-type fairness (deviation from target morning/afternoon/evening counts).

**Objective** (`vns.py`):

```
total = HARD_VIOLATION_WEIGHT  * quota_deficit²
      + VIOLATION_COUNT_WEIGHT * #hard_violations
      + OVERSTAFFING_WEIGHT    * overstaffing_penalty
      + soft_cost
```

With `HARD_VIOLATION_WEIGHT = 500_000`, `VIOLATION_COUNT_WEIGHT = 5_000`, `OVERSTAFFING_WEIGHT = 120_000`.

**Search**:
1. **Greedy initial** schedule (`greedy_solution.py`), seeded.
2. **VNS loop** for up to `max_iterations` (default 200), each iteration tries in order:
   - best-improving 2-exchange (swap two assignments between nurses),
   - best-improving reassign (move an assignment to another nurse),
   - best-improving add (fill an unstaffed slot).
   - If none improves, **shake**: pick a random swap from the top-K stress-sorted neighbors.
3. **Multi-start** (`multi_start_runs`, default 1): re-run greedy + VNS with different seeds; pick the run with fewest hard violations, ties broken by lowest soft score.

Entry point: `run_vns(nurses, quota, max_iterations, seed, multi_start_runs) -> (Schedule, total_cost, info_dict)`.

---

## 4. Android App — `android/NuReform/`

### Stack
- Kotlin, Coroutines, View Binding
- `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, JVM target 11
- Firebase BOM: Auth, Firestore, Analytics, Crashlytics, Config, Storage
- Retrofit + OkHttp + Gson, with `HttpLoggingInterceptor(BODY)`
- Koin for DI
- Navigation Component (`safeargs`)
- Glide (with OkHttp3 integration)
- Realm (`androidx.realm`)
- ZXing (QR scanning)
- Apache POI 5.2.5 (`poi`, `poi-ooxml`) — reason for `minSdk 26`
- Timber (logging)
- Hebrew UI / RTL layout

### Package Layout — `com.example.nureform`

| Layer | Package | Notes |
|---|---|---|
| App entry | `NureformApplication` | Koin start, RTL forced |
| Activities | `SplashActivity`, `LoginActivity`, `HomeActivity` | Splash → Login or Home |
| DI | `di/AppModule.kt` | All singletons + ViewModels |
| API | `data/api/RetrofitClient.kt`, `SchedulingApiService.kt` | Base URL hardcoded (see below) |
| Models | `data/model/` | `User`, `UserRole`, `Nurse`, `ShiftType`, `DayShifts`, `WeeklyShiftSelection`, `NurseShiftRequest`, `NurseSchedule`, `SchedulingResponse` |
| Repos | `data/repository/` | `AuthRepository`, `NursesRepository`, `ShiftsRepository`, `SchedulingRepository`, `Constants` |
| UI | `ui/auth`, `ui/home`, `ui/nurses`, `ui/nursesrequests`, `ui/shifts`, `ui/scheduling`, `ui/schedule` | One sealed `XState` per feature |

### Navigation

```
SplashActivity
   ├── not authenticated  →  LoginActivity
   └── authenticated      →  HomeActivity
                              └── HomeFragment (cards filtered by role)
                                  ├── [Manager] AddNurseFragment
                                  ├── [Manager] DeleteNurseFragment
                                  ├── [Manager] NursesRequestsFragment
                                  ├── [Manager] RunSchedulingFragment
                                  ├── [Nurse]   ChooseShiftsFragment
                                  ├── [Both]    NursesListFragment
                                  └── [Both]    ScheduleViewFragment
```

### Roles & Capabilities

| Role | Capabilities |
|---|---|
| **Manager** | add/delete nurses, view all submissions, trigger scheduling, view schedule + nurse list |
| **Nurse** | submit weekly preferences, view schedule + nurse list |

### Repositories (DI singletons)

- **`AuthRepository`** — `login`, `fetchUserData`, `getCurrentUser`, `logout` (Firebase Auth + `users/{uid}`).
- **`NursesRepository`** — CRUD on `nurses/{uid}`. `addNurse()` uses a **secondary FirebaseAuth instance** so the manager's session is preserved while creating the new account.
- **`ShiftsRepository`** — submit / read `nurses/{uid}/weekly_shifts/{year_week}`; aggregate manager view; submission window helper (currently always open — testing mode).
- **`SchedulingRepository`** — thin wrapper around `SchedulingApiService.runScheduling()` (the `GET /shifts/current-week` call).

### ViewModels

`LoginViewModel`, `HomeViewModel`, `AddNurseViewModel`, `DeleteNurseViewModel`, `NursesListViewModel`, `ChooseShiftsViewModel`, `ScheduleViewModel`, `NursesRequestsViewModel`, `RunSchedulingViewModel`. Each exposes a sealed `State` (`Idle | Loading | Success | Error`) via `LiveData`.

### Networking

```kotlin
// RetrofitClient.kt
BASE_URL = "http://192.168.68.64:3001/"   // dev machine LAN IP
timeouts = 30s connect / read / write
logging  = HttpLoggingInterceptor.Level.BODY
```

> The base URL is currently a hardcoded LAN IP. For an emulator change to `http://10.0.2.2:3001/`; for production it should be configurable.

---

## 5. Firestore Schema

```
firestore/
├── users/{uid}
│     uid, name, role            # role ∈ {NURSE, MANAGER}
│
├── nurses/{uid}
│     idNumber, name, phone, email, userId, createdAt, jobPercentage
│     └── weekly_shifts/{year_week}                  # nurse preferences
│           nurseId, weekNumber, year, submittedAt
│           shifts: { Sunday: ["morning","noon"], ... }
│
└── nurses_shifts/{year_week}                        # finalized schedule
      week, status, startedAt | createdAt
      status ∈ { "running", "success", "error" }
      # on success:
      feasible, totalObjective
      assignments: [
        { nurseName, sunday, monday, ..., saturday }   # values: "בוקר"/"צהריים"/"ערב"/"X"
      ]
```

`year_week` format: `"<isoYear>_<isoWeek>"`, e.g. `"2026_18"`.

---

## 6. End-to-End Workflow

```
1. Nurse opens ChooseShiftsFragment
   └── 7-day × 3-shift checkbox grid
       └── Firestore: nurses/{uid}/weekly_shifts/{week}

2. Manager opens NursesRequestsFragment
   └── Sees who has submitted

3. Manager taps "Run Scheduling" (RunSchedulingFragment)
   └── HTTP GET http://<host>:3001/shifts/current-week
       ├── Backend writes status="running" to nurses_shifts/{week}
       ├── Background thread: greedy + VNS
       └── Backend writes status="success" + assignments
   └── Android UI listens to nurses_shifts/{week} for live status

4. Anyone opens ScheduleViewFragment
   └── Reads nurses_shifts/{week} → renders week grid
```

---

## 7. Running the Project

### Backend
```bash
cd backend/FlaskProject
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# Place firebase_service_account.json in this directory
python app.py                          # starts Flask on :3001
# or one-shot:
python app.py run 2026_17 --save
```

### Android
1. Open `android/NuReform/` in Android Studio.
2. Ensure `app/google-services.json` is present.
3. Edit `BASE_URL` in `RetrofitClient.kt` if needed.
4. Run on device/emulator (minSdk 26).

---

## 8. Known Limitations

| Issue | Where | Notes |
|---|---|---|
| `BASE_URL` is a hardcoded LAN IP | `RetrofitClient.kt` | Should be build-config driven |
| Submission window always open | `ShiftsRepository` | Production should restrict to Sun–Mon |
| Secondary Auth instance to add nurses | `NursesRepository` | Workaround to avoid logging the manager out |
| Service account JSON in repo path | `backend/FlaskProject/` | Must remain gitignored |
| Single-threaded scheduling per process | `app.py` | One daemon thread per request; concurrent triggers rely on Firestore status check for idempotency |
