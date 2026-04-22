"""
Hard and soft constraint checking for nurse scheduling.
Pure Python – no server dependencies.
"""

from collections import defaultdict
from datetime import date, datetime, time, timedelta
from typing import Dict, List, Optional, Set, Tuple

from .constants import (
    ShiftType,
    MIN_HOURS_BETWEEN_SHIFTS,
    MAX_SHIFTS_PER_WEEK,
    SHIFT_END_TIME,
    SHIFT_START_TIME,
    DEFAULT_FAIRNESS_TARGET,
)
from .models import Assignment, Nurse, QuotaRequirement, Schedule


def _parse_date(day_str: str) -> date:
    return date.fromisoformat(day_str)


def _shift_end_datetime(day_str: str, shift_str: str) -> datetime:
    """End time of the shift (as datetime). EVENING ends at 06:00 next day."""
    d = _parse_date(day_str)
    shift = ShiftType(shift_str)
    end_time = SHIFT_END_TIME[shift]
    if shift == ShiftType.EVENING:
        return datetime.combine(d + timedelta(days=1), end_time)
    return datetime.combine(d, end_time)


def _shift_start_datetime(day_str: str, shift_str: str) -> datetime:
    """Start time of the shift (as datetime)."""
    d = _parse_date(day_str)
    shift = ShiftType(shift_str)
    return datetime.combine(d, SHIFT_START_TIME[shift])


def _hours_between(assignment1: Assignment, assignment2: Assignment) -> float:
    """Hours between end of first shift and start of second (must be >= MIN_HOURS_BETWEEN_SHIFTS)."""
    end1 = _shift_end_datetime(assignment1.day, assignment1.shift)
    start2 = _shift_start_datetime(assignment2.day, assignment2.shift)
    delta = start2 - end1
    return delta.total_seconds() / 3600.0


def _iso_week(day_str: str) -> Tuple[int, int]:
    """Return (year, week number) for the day."""
    d = _parse_date(day_str)
    return d.isocalendar()[0], d.isocalendar()[1]


# ---------- Hard constraints ----------


def check_personal_availability(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
) -> List[str]:
    """Returns list of violation messages (empty if OK)."""
    violations = []
    for a in schedule.assignments:
        nurse = nurses_by_id.get(a.nurse_id)
        if not nurse:
            violations.append(f"Unknown nurse id: {a.nurse_id}")
        elif not nurse.is_available(a.day, a.shift):
            violations.append(
                f"Nurse {a.nurse_id} not available on {a.day} {a.shift}"
            )
    return violations


def check_minimum_quotas(
    schedule: Schedule,
    quota: QuotaRequirement,
) -> List[str]:
    """Returns list of violation messages (empty if OK)."""
    violations = []
    for (day, shift), min_count in quota.requirements.items():
        count = schedule.count_for_day_shift(day, shift)
        if count < min_count:
            violations.append(
                f"Day {day} shift {shift}: has {count}, required {min_count}"
            )
    return violations


def overstaffing_penalty(schedule: Schedule, quota: QuotaRequirement) -> float:
    """
    Quadratic penalty for staffing above minimum quota per slot.
    Hard constraints only enforce minimums; this soft/penalty term discourages bloat.
    """
    penalty = 0.0
    for (day, shift), required in quota.requirements.items():
        actual = schedule.count_for_day_shift(day, shift)
        if actual > required:
            extra = actual - required
            penalty += extra * extra
    return penalty


def check_one_shift_per_day(schedule: Schedule) -> List[str]:
    """One shift per nurse per calendar day."""
    violations = []
    by_nurse_day = defaultdict(set)
    for a in schedule.assignments:
        key = (a.nurse_id, a.day)
        if by_nurse_day[key]:
            violations.append(
                f"Nurse {a.nurse_id} has more than one shift on {a.day}"
            )
        by_nurse_day[key].add(a.shift)
    return violations


def check_weekly_ceiling(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
) -> List[str]:
    """Max shifts per nurse per planning week (100%=6, 75%=4, 50%=3).
    Uses total count per nurse in the schedule (planning week = Sun-Sat), NOT ISO week,
    because ISO week (Mon-Sun) splits Sunday from the rest, allowing 7 shifts (6+1)."""
    violations = []
    by_nurse = defaultdict(int)
    for a in schedule.assignments:
        by_nurse[a.nurse_id] += 1
    for nurse_id, count in by_nurse.items():
        nurse = nurses_by_id.get(nurse_id)
        max_allowed = nurse.max_shifts_per_week() if nurse else MAX_SHIFTS_PER_WEEK
        if count > max_allowed:
            violations.append(
                f"Nurse {nurse_id} has {count} shifts (max {max_allowed})"
            )
    return violations


def check_forbidden_sequences(schedule: Schedule) -> List[str]:
    """No two shifts with less than MIN_HOURS_BETWEEN_SHIFTS between them."""
    violations = []
    by_nurse = defaultdict(list)
    for a in schedule.assignments:
        by_nurse[a.nurse_id].append(a)
    for nurse_id, assignments_list in by_nurse.items():
        # Sort by (day, shift order)
        order = {ShiftType.MORNING: 0, ShiftType.AFTERNOON: 1, ShiftType.EVENING: 2}
        sorted_assignments = sorted(
            assignments_list,
            key=lambda x: (x.day, order.get(ShiftType(x.shift), 0)),
        )
        for i in range(len(sorted_assignments) - 1):
            a1, a2 = sorted_assignments[i], sorted_assignments[i + 1]
            rest_hours = _hours_between(a1, a2)
            if rest_hours < MIN_HOURS_BETWEEN_SHIFTS:
                violations.append(
                    f"Nurse {nurse_id}: only {rest_hours:.1f}h rest between {a1.day} {a1.shift} and {a2.day} {a2.shift} (min {MIN_HOURS_BETWEEN_SHIFTS}h)"
                )
    return violations


def schedule_after_swap_assignments(
    schedule: Schedule, a1: Assignment, a2: Assignment
) -> Schedule:
    """New schedule with nurse_id swapped between two assignments (same as 2-exchange)."""
    new = Schedule()
    for a in schedule.assignments:
        if a == a1:
            new.add(Assignment(nurse_id=a2.nurse_id, day=a1.day, shift=a1.shift))
        elif a == a2:
            new.add(Assignment(nurse_id=a1.nurse_id, day=a2.day, shift=a2.shift))
        else:
            new.add(Assignment(nurse_id=a.nurse_id, day=a.day, shift=a.shift))
    return new


def swap_breaks_min_rest(schedule: Schedule, a1: Assignment, a2: Assignment) -> bool:
    """
    True if swapping nurses on ``a1`` and ``a2`` would violate minimum rest between shifts
    for any nurse (even if other hard checks on that swap might still fail).
    """
    if a1.nurse_id == a2.nurse_id:
        return True
    new_sched = schedule_after_swap_assignments(schedule, a1, a2)
    return bool(check_forbidden_sequences(new_sched))


def validate_staffing_targets_vs_quota(
    nurses: List[Nurse],
    quota: QuotaRequirement,
) -> List[str]:
    """
    Warnings when contractual targets or weekly caps cannot match total demand.
    Does not prove infeasibility of a specific schedule, but flags strained instances.
    """
    warnings: List[str] = []
    total_req = sum(quota.requirements.values())
    total_target = sum(n.target_shifts_per_week() for n in nurses)
    total_max = sum(n.max_shifts_per_week() for n in nurses)
    if total_target < total_req:
        warnings.append(
            f"Sum of contractual shift targets ({total_target}) is less than total required "
            f"shifts ({total_req}); some nurses may stay under target unless workloads exceed targets."
        )
    if total_max < total_req:
        warnings.append(
            f"Sum of per-nurse max weekly shifts ({total_max}) is less than required shifts "
            f"({total_req}); quotas cannot be fully staffed respecting caps."
        )
    return warnings


def check_hard_constraints(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> List[str]:
    """All hard constraint violations. Empty list means feasible."""
    violations = []
    violations.extend(
        check_personal_availability(schedule, nurses_by_id)
    )
    violations.extend(check_minimum_quotas(schedule, quota))
    violations.extend(check_one_shift_per_day(schedule))
    violations.extend(check_weekly_ceiling(schedule, nurses_by_id))
    violations.extend(check_forbidden_sequences(schedule))
    return violations


def is_feasible(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> bool:
    """True if schedule satisfies all hard constraints."""
    return len(check_hard_constraints(schedule, nurses_by_id, quota)) == 0


# ---------- Soft constraints (objective components) ----------

# Contractual fulfillment: high priority. Weight so it dominates fairness/sequence.
CONTRACTUAL_DEFICIT_WEIGHT = 100.0   # penalty per nurse under target
CONTRACTUAL_INVERSION_WEIGHT = 200.0  # penalty when higher-tier nurse has fewer shifts than lower-tier


def _tier_rank(nurse: Nurse) -> int:
    """Higher = more shifts expected. 100%=3, 75%=2, 50%=1."""
    if nurse.position_percentage >= 99:
        return 3
    if nurse.position_percentage >= 70:
        return 2
    if nurse.position_percentage >= 45:
        return 1
    return 0


def _contractual_fulfillment_penalty(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
) -> float:
    """
    High-priority penalty for contractual fulfillment.
    - Penalty when nurse is under target (deficit squared).
    - Penalty when higher-tier nurse has fewer shifts than lower-tier (inversion).
    """
    by_nurse_week = defaultdict(lambda: defaultdict(int))
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nurse_week[a.nurse_id][wk] += 1

    penalty = 0.0
    nurse_shifts = {}
    for nurse_id, nurse in nurses_by_id.items():
        total = sum(by_nurse_week[nurse_id].values())
        nurse_shifts[nurse_id] = total
        target = nurse.target_shifts_per_week()
        deficit = max(0, target - total)
        penalty += deficit * deficit * CONTRACTUAL_DEFICIT_WEIGHT

    # Inversion penalty: higher-tier nurse with fewer shifts than lower-tier
    nurse_list = list(nurses_by_id.items())
    for i in range(len(nurse_list)):
        for j in range(i + 1, len(nurse_list)):
            id_i, nurse_i = nurse_list[i]
            id_j, nurse_j = nurse_list[j]
            shifts_i = nurse_shifts.get(id_i, 0)
            shifts_j = nurse_shifts.get(id_j, 0)
            rank_i = _tier_rank(nurse_i)
            rank_j = _tier_rank(nurse_j)
            if rank_i > rank_j and shifts_i < shifts_j:
                penalty += (shifts_j - shifts_i) * CONTRACTUAL_INVERSION_WEIGHT
            elif rank_j > rank_i and shifts_j < shifts_i:
                penalty += (shifts_i - shifts_j) * CONTRACTUAL_INVERSION_WEIGHT

    return penalty


def _fairness_penalty(
    schedule: Schedule,
    fairness_target: Optional[Dict[ShiftType, int]] = None,
    nurses_by_id: Optional[Dict[str, Nurse]] = None,
) -> float:
    """
    Penalty for deviation from preferred distribution per nurse per ISO week.
    Lower is better. Targets come from ``Nurse.fairness_shift_targets()`` when
    ``nurses_by_id`` is set; otherwise a single ``fairness_target`` (default global).
    """
    fallback = fairness_target or DEFAULT_FAIRNESS_TARGET
    by_nurse_week = defaultdict(lambda: defaultdict(int))
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nurse_week[(a.nurse_id, wk)][ShiftType(a.shift)] += 1
    penalty = 0.0
    for (nurse_id, wk), counts in by_nurse_week.items():
        if nurses_by_id and nurse_id in nurses_by_id:
            tgt_map = nurses_by_id[nurse_id].fairness_shift_targets()
        else:
            tgt_map = fallback
        for shift_type, target in tgt_map.items():
            actual = counts.get(shift_type, 0)
            penalty += (actual - target) ** 2
    return penalty


def _sequence_bonus(schedule: Schedule) -> float:
    """
    Negative penalty = bonus for consecutive same-type shifts.
    We want to maximize consecutive same-type, so we return negative value:
    more consecutive -> more negative (better). So soft score = -penalty + sequence_bonus.
    Actually: let's define as penalty: lack of consecutive same-type is bad.
    So: for each nurse, count runs of same shift type; higher runs = better.
    Return negative so that minimizing total soft cost = we subtract this (add bonus).
    Convention: evaluate_soft_constraints returns a single number to MINIMIZE.
    So: fairness_penalty (minimize) + sequence_penalty (minimize).
    Sequence penalty = - (number of consecutive pairs of same type). So more pairs = lower penalty.
    So sequence_penalty = - count_consecutive_same_type_pairs. Minimizing that = maximizing pairs.
    """
    by_nurse = defaultdict(list)
    for a in schedule.assignments:
        by_nurse[a.nurse_id].append(a)
    order = {ShiftType.MORNING: 0, ShiftType.AFTERNOON: 1, ShiftType.EVENING: 2}
    total_pairs = 0
    for nurse_id, assignments_list in by_nurse.items():
        sorted_assignments = sorted(
            assignments_list,
            key=lambda x: (_parse_date(x.day), order.get(ShiftType(x.shift), 0)),
        )
        for i in range(len(sorted_assignments) - 1):
            if sorted_assignments[i].shift == sorted_assignments[i + 1].shift:
                total_pairs += 1
    # We minimize soft cost; more pairs = better = lower cost. So return negative.
    return -total_pairs


def evaluate_soft_constraints(
    schedule: Schedule,
    fairness_target: Optional[Dict[ShiftType, int]] = None,
    nurses_by_id: Optional[Dict[str, Nurse]] = None,
) -> float:
    """
    Combined soft constraint score to MINIMIZE.
    Lower is better. Combines: contractual fulfillment (high priority), fairness, sequence bonus.
    """
    total = 0.0
    if nurses_by_id:
        total += _contractual_fulfillment_penalty(schedule, nurses_by_id)
    total += _fairness_penalty(schedule, fairness_target, nurses_by_id)
    total += _sequence_bonus(schedule)  # negative = bonus for consecutive same-type
    return total


def soft_issue_counts(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    fairness_target: Optional[Dict[ShiftType, int]] = None,
) -> Tuple[int, int, int]:
    """
    Discrete soft “issues” for logging (not the same units as evaluate_soft_constraints).
    Returns:
        under_target_nurses — count of nurses with fewer shifts than contractual target
        tier_inversion_pairs — count of nurse pairs where higher job-tier has fewer shifts
        fairness_deviation_cells — (nurse, week) × shift-type cells that differ from fairness target
    """
    by_nurse_week_totals: Dict[str, Dict[Tuple[int, int], int]] = defaultdict(
        lambda: defaultdict(int)
    )
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nurse_week_totals[a.nurse_id][wk] += 1

    nurse_shifts: Dict[str, int] = {}
    for nurse_id, nurse in nurses_by_id.items():
        nurse_shifts[nurse_id] = sum(by_nurse_week_totals[nurse_id].values())

    under_target = 0
    for nurse_id, nurse in nurses_by_id.items():
        if nurse_shifts.get(nurse_id, 0) < nurse.target_shifts_per_week():
            under_target += 1

    tier_inversions = 0
    nurse_list = list(nurses_by_id.items())
    for i in range(len(nurse_list)):
        for j in range(i + 1, len(nurse_list)):
            id_i, nurse_i = nurse_list[i]
            id_j, nurse_j = nurse_list[j]
            shifts_i = nurse_shifts.get(id_i, 0)
            shifts_j = nurse_shifts.get(id_j, 0)
            rank_i = _tier_rank(nurse_i)
            rank_j = _tier_rank(nurse_j)
            if rank_i > rank_j and shifts_i < shifts_j:
                tier_inversions += 1
            elif rank_j > rank_i and shifts_j < shifts_i:
                tier_inversions += 1

    by_nw_shift: Dict[Tuple[str, Tuple[int, int]], Dict[ShiftType, int]] = defaultdict(
        lambda: defaultdict(int)
    )
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nw_shift[(a.nurse_id, wk)][ShiftType(a.shift)] += 1

    fairness_deviation_cells = 0
    for (nurse_id, _wk), counts in by_nw_shift.items():
        nurse = nurses_by_id.get(nurse_id)
        tgt_map = nurse.fairness_shift_targets() if nurse else (fairness_target or DEFAULT_FAIRNESS_TARGET)
        for shift_type, target in tgt_map.items():
            actual = counts.get(shift_type, 0)
            if actual != target:
                fairness_deviation_cells += 1

    return under_target, tier_inversions, fairness_deviation_cells


def describe_soft_issues(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    fairness_target: Optional[Dict[ShiftType, int]] = None,
) -> List[str]:
    """
    Human-readable soft issues (contract below target, tier inversions, fairness deviations).
    Does not enumerate the sequence-preference term in ``evaluate_soft_constraints``.
    """
    lines: List[str] = []

    by_nurse_week_totals: Dict[str, Dict[Tuple[int, int], int]] = defaultdict(
        lambda: defaultdict(int)
    )
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nurse_week_totals[a.nurse_id][wk] += 1

    nurse_shifts: Dict[str, int] = {}
    for nurse_id, nurse in nurses_by_id.items():
        nurse_shifts[nurse_id] = sum(by_nurse_week_totals[nurse_id].values())

    for nurse_id, nurse in nurses_by_id.items():
        total = nurse_shifts.get(nurse_id, 0)
        target = nurse.target_shifts_per_week()
        if total < target:
            lines.append(
                f"Contract: nurse {nurse_id} ({nurse.name}) has {total} shifts, target {target} "
                f"({nurse.position_percentage}% position)"
            )

    nurse_list = list(nurses_by_id.items())
    for i in range(len(nurse_list)):
        for j in range(i + 1, len(nurse_list)):
            id_i, nurse_i = nurse_list[i]
            id_j, nurse_j = nurse_list[j]
            shifts_i = nurse_shifts.get(id_i, 0)
            shifts_j = nurse_shifts.get(id_j, 0)
            rank_i = _tier_rank(nurse_i)
            rank_j = _tier_rank(nurse_j)
            if rank_i > rank_j and shifts_i < shifts_j:
                lines.append(
                    f"Tier inversion: nurse {id_i} (higher contract tier, {shifts_i} shifts) vs "
                    f"nurse {id_j} ({shifts_j} shifts)"
                )
            elif rank_j > rank_i and shifts_j < shifts_i:
                lines.append(
                    f"Tier inversion: nurse {id_j} (higher contract tier, {shifts_j} shifts) vs "
                    f"nurse {id_i} ({shifts_i} shifts)"
                )

    by_nw_shift: Dict[Tuple[str, Tuple[int, int]], Dict[ShiftType, int]] = defaultdict(
        lambda: defaultdict(int)
    )
    for a in schedule.assignments:
        wk = _iso_week(a.day)
        by_nw_shift[(a.nurse_id, wk)][ShiftType(a.shift)] += 1

    for (nurse_id, wk), counts in by_nw_shift.items():
        nurse = nurses_by_id.get(nurse_id)
        tgt_map = nurse.fairness_shift_targets() if nurse else (fairness_target or DEFAULT_FAIRNESS_TARGET)
        for shift_type, target in tgt_map.items():
            actual = counts.get(shift_type, 0)
            if actual != target:
                lines.append(
                    f"Fairness profile: nurse {nurse_id} ISO week {wk[1]} "
                    f"{shift_type.value}: actual {actual}, target {target}"
                )

    return lines