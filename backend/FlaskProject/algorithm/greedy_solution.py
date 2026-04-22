"""
Greedy construction of an initial feasible schedule.
Fills quotas day-by-day, shift-by-shift, choosing available nurses
without violating hard constraints.
"""

import logging
import random
from collections import defaultdict

log = logging.getLogger(__name__)
from typing import Dict, List, Optional, Set, Tuple

from .constants import ShiftType, MIN_HOURS_BETWEEN_SHIFTS, DEFAULT_FAIRNESS_TARGET

SHIFT_ORDER = (ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.EVENING)
from .models import Assignment, Nurse, QuotaRequirement, Schedule
from .constraints import (
    _shift_end_datetime,
    _shift_start_datetime,
    _iso_week,
    is_feasible,
)


def _parse_date(day_str: str):
    from datetime import date
    return date.fromisoformat(day_str)


def _can_assign(
    nurse_id: str,
    day: str,
    shift: str,
    current_assignments: List[Assignment],
    nurses_by_id: Dict[str, Nurse],
) -> bool:
    """Check if adding (nurse_id, day, shift) keeps hard constraints (availability, 1/day, weekly cap, gap)."""
    nurse = nurses_by_id.get(nurse_id)
    if not nurse or not nurse.is_available(day, shift):
        return False
    # One per day
    for a in current_assignments:
        if a.nurse_id == nurse_id and a.day == day:
            return False
    # Weekly ceiling: total shifts per nurse in planning week (not ISO week - Sun/Sat split would allow 7)
    count_total = sum(1 for a in current_assignments if a.nurse_id == nurse_id)
    if count_total >= nurse.max_shifts_per_week():
        return False
    # Min rest: at least 12h between end of one shift and start of next (no back-to-back, e.g. EVENING→next-day MORNING = 0h forbidden)
    new_start = _shift_start_datetime(day, shift)
    new_end = _shift_end_datetime(day, shift)
    for a in current_assignments:
        if a.nurse_id != nurse_id:
            continue
        existing_start = _shift_start_datetime(a.day, a.shift)
        existing_end = _shift_end_datetime(a.day, a.shift)
        # Require 12h rest between shifts. Handle both orderings.
        if new_start > existing_end:
            # Existing before new: rest from existing end to new start
            rest_hours = (new_start - existing_end).total_seconds() / 3600.0
            if rest_hours < MIN_HOURS_BETWEEN_SHIFTS:
                return False
        elif new_end < existing_start:
            # New before existing: rest from new end to existing start
            rest_hours = (existing_start - new_end).total_seconds() / 3600.0
            if rest_hours < MIN_HOURS_BETWEEN_SHIFTS:
                return False
        else:
            return False  # Overlap (e.g. EVENING ends 06:00, MORNING starts 06:00 same day)
    return True


def _count_shift_in_week(assignments: List[Assignment], nurse_id: str, week: Tuple[int, int], shift_type: ShiftType) -> int:
    """Count how many times this nurse has this shift type in this week (for fairness tie-break)."""
    return sum(
        1 for a in assignments
        if a.nurse_id == nurse_id and _iso_week(a.day) == week and a.shift == shift_type.value
    )


def _count_other_shifts_in_week(assignments: List[Assignment], nurse_id: str, week: Tuple[int, int], exclude_shift_type: ShiftType) -> int:
    """Count assignments for this nurse in this week that are NOT the given shift type (for tie-break: prefer nurse with more variety)."""
    return sum(
        1 for a in assignments
        if a.nurse_id == nurse_id and _iso_week(a.day) == week and a.shift != exclude_shift_type.value
    )


def _count_shifts_in_week(assignments: List[Assignment], nurse_id: str, week: Tuple[int, int]) -> int:
    """Total shifts for this nurse in this week."""
    return sum(1 for a in assignments if a.nurse_id == nurse_id and _iso_week(a.day) == week)


def _nurse_quota_availability_count(nurse: Nurse, quota: QuotaRequirement) -> int:
    """How many (day, shift) quota slots this nurse can work. Lower = tighter bottleneck — prefer first."""
    return sum(1 for (day, shift) in quota.requirements if nurse.is_available(day, shift))


# When several nurses are equally attractive, pick uniformly from this many best (by score tuple).
GREEDY_TOP_K = 3


def _slot_order_priority_first(
    ordered_slots: List[Tuple[str, str]],
    priority: Set[Tuple[str, str]],
    *,
    shuffle_priority: bool,
    shuffle_rest: bool,
) -> List[Tuple[str, str]]:
    """Place slots in ``priority`` first (optional shuffle), then the rest (optional shuffle)."""
    pri = [s for s in ordered_slots if s in priority]
    rest = [s for s in ordered_slots if s not in priority]
    if shuffle_priority:
        random.shuffle(pri)
    if shuffle_rest:
        random.shuffle(rest)
    return pri + rest


def build_greedy_solution(
    nurses: List[Nurse],
    quota: QuotaRequirement,
    fairness_target: Optional[Dict[ShiftType, int]] = None,
    seed: Optional[int] = None,
    priority_failed_slots: Optional[Set[Tuple[str, str]]] = None,
) -> Tuple[Schedule, Set[Tuple[str, str]]]:
    """
    Build a schedule that tries to meet quotas and hard constraints.
    Uses stochastic greedy: shuffled nurse and slot order, top-K random choice among
    best-scoring nurses. Scoring prefers nurses with **fewer** quota-eligible slots
    (bottlenecks) before more flexible nurses; small noise helps explore ties.

    If ``priority_failed_slots`` is set (e.g. from prior restarts), those (day, shift) slots
    are filled first so critical shortages get nurses before easier slots.

    Returns:
        (schedule, failed_slots) where ``failed_slots`` are (day, shift) that hit
        CANNOT FILL during this build (for adaptive retries).
    """
    if seed is not None:
        random.seed(seed)
    fairness_target = fairness_target or DEFAULT_FAIRNESS_TARGET
    nurses_by_id = {n.id: n for n in nurses}
    nurses_order = list(nurses)
    random.shuffle(nurses_order)

    order_idx = {s.value: i for i, s in enumerate(SHIFT_ORDER)}

    def _available_count(slot):
        day, shift = slot
        return sum(1 for n in nurses_order if n.is_available(day, shift))

    def _nurse_sort_key(
        n: Nurse,
        current_assignments_list: List[Assignment],
        day: str,
        shift_type: ShiftType,
        week: Tuple[int, int],
    ) -> Tuple:
        """Lower tuple is better: tighter availability first, then deficit / fairness tie-breaks."""
        quota_tight = _nurse_quota_availability_count(n, quota)
        deficit = n.target_shifts_per_week() - _count_shifts_in_week(
            current_assignments_list, n.id, week
        )
        sc = _count_shift_in_week(current_assignments_list, n.id, week, shift_type)
        oth = _count_other_shifts_in_week(current_assignments_list, n.id, week, shift_type)
        noise = random.uniform(0, 0.01)
        return (quota_tight, -deficit, sc, -oth, noise)

    def _build_with_slot_order(
        slot_order, order_name: str
    ) -> Tuple[Schedule, Set[Tuple[str, str]]]:
        sched = Schedule()
        failed_slots: Set[Tuple[str, str]] = set()
        for (day, shift) in slot_order:
            needed = quota.get(day, shift)
            current_for_slot = sched.assignments_for_day_shift(day, shift)
            to_fill = needed - len(current_for_slot)
            current_assignments_list = list(sched.assignments)
            shift_type = ShiftType(shift)
            week = _iso_week(day)
            for fill_idx in range(to_fill):
                feasible = [
                    n for n in nurses_order
                    if _can_assign(n.id, day, shift, current_assignments_list, nurses_by_id)
                ]
                if not feasible:
                    failed_slots.add((day, shift))
                    log.warning(
                        "[GREEDY %s] Slot (%s, %s): need %d, have %d, CANNOT FILL - no feasible nurses (need %d more)",
                        order_name, day, shift, needed, len(current_for_slot), to_fill - fill_idx
                    )
                    break
                if len(feasible) == 1:
                    chosen = feasible[0].id
                else:
                    scored = sorted(
                        feasible,
                        key=lambda n: _nurse_sort_key(n, current_assignments_list, day, shift_type, week),
                    )
                    k = min(GREEDY_TOP_K, len(scored))
                    chosen = random.choice(scored[:k]).id
                a = Assignment(nurse_id=chosen, day=day, shift=shift)
                sched.add(a)
                current_assignments_list.append(a)
                if log.isEnabledFor(logging.DEBUG):
                    n = nurses_by_id.get(chosen)
                    n_shifts = sum(1 for x in current_assignments_list if x.nurse_id == chosen)
                    log.debug(
                        "[GREEDY %s] Slot (%s, %s): assigned nurse %s (%s) [%d/%d shifts]",
                        order_name, day, shift, chosen, n.name if n else "?", n_shifts, n.max_shifts_per_week() if n else 0
                    )
        return sched, failed_slots

    from .constraints import check_hard_constraints

    all_slots = list(quota.requirements.keys())
    slot_key_set = set(all_slots)
    priority = (priority_failed_slots or set()) & slot_key_set

    failed_from_build: Set[Tuple[str, str]] = set()

    slots_random = _slot_order_priority_first(
        all_slots,
        priority,
        shuffle_priority=True,
        shuffle_rest=True,
    )
    schedule, f_rand = _build_with_slot_order(slots_random, "random_slots")
    failed_from_build |= f_rand
    log.debug(
        "[GREEDY] random_slots: feasible=%s, violations=%d, priority_slots=%d, cannot_fill=%s",
        is_feasible(schedule, nurses_by_id, quota),
        len(check_hard_constraints(schedule, nurses_by_id, quota)),
        len(priority),
        sorted(f_rand) if f_rand else "none",
    )

    if not is_feasible(schedule, nurses_by_id, quota):
        slots_tight_first = sorted(
            all_slots,
            key=lambda x: (_available_count(x), x[0], order_idx.get(x[1], 99)),
        )
        tight_order = _slot_order_priority_first(
            slots_tight_first,
            priority,
            shuffle_priority=True,
            shuffle_rest=True,
        )
        alt, f_tight = _build_with_slot_order(tight_order, "tight_first_fallback")
        failed_from_build |= f_tight
        v0 = len(check_hard_constraints(schedule, nurses_by_id, quota))
        v1 = len(check_hard_constraints(alt, nurses_by_id, quota))
        log.debug(
            "[GREEDY] tight_first_fallback: feasible=%s, violations=%d",
            is_feasible(alt, nurses_by_id, quota),
            v1,
        )
        if is_feasible(alt, nurses_by_id, quota) or v1 < v0:
            schedule = alt
            if is_feasible(alt, nurses_by_id, quota):
                log.debug("[GREEDY] Using tight_first_fallback (better or feasible)")
        else:
            slots_easy_first = sorted(
                all_slots,
                key=lambda x: (-_available_count(x), x[0], order_idx.get(x[1], 99)),
            )
            easy_order = _slot_order_priority_first(
                slots_easy_first,
                priority,
                shuffle_priority=True,
                shuffle_rest=True,
            )
            alt2, f_easy = _build_with_slot_order(easy_order, "easy_first_fallback")
            failed_from_build |= f_easy
            v2 = len(check_hard_constraints(alt2, nurses_by_id, quota))
            log.debug(
                "[GREEDY] easy_first_fallback: feasible=%s, violations=%d",
                is_feasible(alt2, nurses_by_id, quota),
                v2,
            )
            if is_feasible(alt2, nurses_by_id, quota) or v2 < v0:
                schedule = alt2
                if is_feasible(alt2, nurses_by_id, quota):
                    log.debug("[GREEDY] Using easy_first_fallback (better or feasible)")
    # Log nurse shift counts
    by_nurse = defaultdict(int)
    for a in schedule.assignments:
        by_nurse[a.nurse_id] += 1
    for nurse_id, nurse in nurses_by_id.items():
        actual = by_nurse[nurse_id]
        target = nurse.target_shifts_per_week()
        max_s = nurse.max_shifts_per_week()
        if actual < target:
            log.debug(
                "[GREEDY] Nurse %s (%s): %d shifts (target %d, max %d) - UNDER TARGET",
                nurse_id, nurse.name, actual, target, max_s
            )
    # Log slots under quota
    for (day, shift), min_count in quota.requirements.items():
        got = schedule.count_for_day_shift(day, shift)
        if got < min_count:
            log.debug(
                "[GREEDY] Slot (%s, %s): has %d, required %d - UNDER QUOTA",
                day, shift, got, min_count
            )
            failed_from_build.add((day, shift))
    return schedule, failed_from_build
