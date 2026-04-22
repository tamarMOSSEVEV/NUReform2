"""
Neighborhood operators for VNS (penalty-based search):
- 2-exchange: swap two assignments. By default, swaps that would break minimum rest
  between shifts are omitted from local search; shake can disable this filter.
  Neighbors are sorted by descending swap-priority (stressed slots, tight availability nurses first).
- reassign: same (day, shift), different nurse
- add: only into slots still below minimum quota
"""

import math
from typing import Dict, List, Tuple

from .models import Assignment, Nurse, QuotaRequirement, Schedule
from .constraints import swap_breaks_min_rest
from .greedy_solution import _nurse_quota_availability_count


# Mirrors greedy “bottleneck first”: nurses who can fill fewer quota slots get higher swap priority.
_AVAILABILITY_QUOTA_STRESS = 22.0


def _swap_anchor_stress(
    a: Assignment,
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> float:
    """
    Higher = assignment sits in a more "stressed" context (under-filled slot, contractual
    pressure on that nurse, **fewer eligible quota slots for that nurse**). Used to prioritize
    2-exchange moves consistently with greedy availability ranking.
    """
    w = 0.0
    required = quota.get(a.day, a.shift)
    actual_slot = schedule.count_for_day_shift(a.day, a.shift)
    if actual_slot < required:
        d = required - actual_slot
        w += d * d * 50.0
    nurse = nurses_by_id.get(a.nurse_id)
    if nurse:
        tot = len(schedule.assignments_for_nurse(a.nurse_id))
        tgt = nurse.target_shifts_per_week()
        mx = nurse.max_shifts_per_week()
        du = max(0, tgt - tot)
        w += du * du * 15.0
        over = max(0, tot - mx)
        w += over * over * 40.0
        avail_in_quota = _nurse_quota_availability_count(nurse, quota)
        quota_span = max(1, len(quota.requirements))
        # More weight when the nurse can cover fewer (day, shift) cells in this instance.
        w += (quota_span - avail_in_quota) * _AVAILABILITY_QUOTA_STRESS
    return w


def _swap_pair_priority(
    schedule: Schedule,
    a1: Assignment,
    a2: Assignment,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> float:
    return _swap_anchor_stress(a1, schedule, nurses_by_id, quota) + _swap_anchor_stress(
        a2, schedule, nurses_by_id, quota
    )


def _schedule_after_swap(
    schedule: Schedule,
    a1: Assignment,
    a2: Assignment,
) -> Schedule:
    """Return a new schedule with a1 and a2 swapped (nurse_id swap only)."""
    new = Schedule()
    for a in schedule.assignments:
        if a == a1:
            new.add(Assignment(nurse_id=a2.nurse_id, day=a1.day, shift=a1.shift))
        elif a == a2:
            new.add(Assignment(nurse_id=a1.nurse_id, day=a2.day, shift=a2.shift))
        else:
            new.add(Assignment(nurse_id=a.nurse_id, day=a.day, shift=a.shift))
    return new


def two_exchange_neighbors(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    *,
    filter_min_rest: bool = True,
) -> List[Tuple[Schedule, str, tuple]]:
    """
    All 2-exchange moves (different nurses). Other hard violations still use the objective;
    when ``filter_min_rest`` is True, swaps that would break minimum rest between shifts
    are omitted (saves cost evals). Set False for shake / aggressive exploration.
    """
    neighbors = []
    assignments = list(schedule.assignments)
    for i in range(len(assignments)):
        for j in range(i + 1, len(assignments)):
            a1, a2 = assignments[i], assignments[j]
            if a1.nurse_id == a2.nurse_id:
                continue
            if filter_min_rest and swap_breaks_min_rest(schedule, a1, a2):
                continue
            new_schedule = _schedule_after_swap(schedule, a1, a2)
            pri = _swap_pair_priority(schedule, a1, a2, nurses_by_id, quota)
            neighbors.append((new_schedule, "swap", (a1, a2, pri)))

    neighbors.sort(key=lambda t: -t[2][2])
    return neighbors


def reassign_neighbors(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> List[Tuple[Schedule, str, tuple]]:
    """
    Move one assignment to another nurse at the same (day, shift).
    No is_feasible check — only avoid duplicate (nurse, day, shift) for the receiver.
    """
    neighbors = []

    for a in schedule.assignments:
        for to_nurse_id, to_nurse in nurses_by_id.items():
            if to_nurse_id == a.nurse_id:
                continue
            if not to_nurse.is_available(a.day, a.shift):
                continue
            without_a = [x for x in schedule.assignments if x != a]
            if any(
                x.nurse_id == to_nurse_id and x.day == a.day and x.shift == a.shift
                for x in without_a
            ):
                continue

            new_schedule = schedule.copy()
            new_schedule.remove(a)
            new_schedule.add(Assignment(nurse_id=to_nurse_id, day=a.day, shift=a.shift))
            neighbors.append(
                (new_schedule, "reassign", (a.nurse_id, to_nurse_id, a.day, a.shift))
            )

    return neighbors


def add_neighbors(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> List[Tuple[Schedule, str, tuple]]:
    """
    Add one assignment only into slots that are still UNDER minimum quota.
    (Does not inflate staffing above requirement via add — over-capacity is discouraged
    by overstaffing penalty on the objective, not by growing headcount here.)
    """
    neighbors = []
    occupied = schedule.get_assignment_set()

    for nurse_id, nurse in nurses_by_id.items():
        for (day, shift) in quota.requirements.keys():
            if schedule.count_for_day_shift(day, shift) >= quota.get(day, shift):
                continue
            if (nurse_id, day, shift) in occupied:
                continue
            if not nurse.is_available(day, shift):
                continue
            if any(a.nurse_id == nurse_id and a.day == day for a in schedule.assignments):
                continue

            new_schedule = schedule.copy()
            new_schedule.add(Assignment(nurse_id=nurse_id, day=day, shift=shift))
            neighbors.append((new_schedule, "add", (nurse_id, day, shift)))

    return neighbors


def best_improving_2exchange(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    current_cost: float,
    evaluate,
) -> Tuple[Schedule, float, bool]:
    neighbors = two_exchange_neighbors(schedule, nurses_by_id, quota)
    if not neighbors:
        return schedule, current_cost, False

    best_cost = float("inf")
    best_schedule = schedule
    best_pri = -1.0

    for new_schedule, move_type, move_info in neighbors:
        cost = evaluate(new_schedule)
        pri = move_info[2] if len(move_info) >= 3 else 0.0
        if cost < best_cost - 1e-12:
            best_cost = cost
            best_schedule = new_schedule
            best_pri = pri
        elif math.isclose(cost, best_cost, rel_tol=0.0, abs_tol=1e-9) and pri > best_pri:
            best_schedule = new_schedule
            best_pri = pri

    improved = best_cost < current_cost - 1e-12
    if not improved:
        return schedule, current_cost, False
    return best_schedule, best_cost, True


def best_improving_reassign(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    current_cost: float,
    evaluate,
) -> Tuple[Schedule, float, bool]:
    neighbors = reassign_neighbors(schedule, nurses_by_id, quota)
    best_schedule = schedule
    best_cost = current_cost

    for new_schedule, move_type, move_info in neighbors:
        cost = evaluate(new_schedule)
        if cost < best_cost:
            best_cost = cost
            best_schedule = new_schedule

    improved = best_cost < current_cost - 1e-12
    return best_schedule, best_cost, improved


def best_improving_add(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    current_cost: float,
    evaluate,
) -> Tuple[Schedule, float, bool]:
    neighbors = add_neighbors(schedule, nurses_by_id, quota)
    best_schedule = schedule
    best_cost = current_cost

    for new_schedule, move_type, move_info in neighbors:
        cost = evaluate(new_schedule)
        if cost < best_cost:
            best_cost = cost
            best_schedule = new_schedule

    improved = best_cost < current_cost - 1e-12
    return best_schedule, best_cost, improved
