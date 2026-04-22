"""
Data models for nurse scheduling.
Plain Python dataclasses / types – no server or framework dependencies.
"""

from dataclasses import dataclass, field
from datetime import date
from typing import Dict, List, Set, Tuple

from .constants import ShiftType


@dataclass(frozen=True)
class Nurse:
    """A nurse with id, name, availability, and position percentage (job type).
    Job types: 100% = 5 shifts (up to 6 as needed), 75% = 4 shifts, 50% = 2–3 shifts."""
    id: str
    name: str
    # Set of (date, shift) pairs when the nurse is available. date as "YYYY-MM-DD".
    availability: Set[Tuple[str, str]] = field(default_factory=set)
    # Position percentage (job type): 100%, 75%, or 50%.
    position_percentage: float = 100.0

    def is_available(self, day: str, shift: str) -> bool:
        return (day, shift) in self.availability

    def target_shifts_per_week(self) -> int:
        """Base target shifts per week for this job type.
        100% = 5, 75% = 4, 50% → single target 3 (contract allows 2–3; implementation fixes 3)."""
        if self.position_percentage >= 99:
            return 5
        if self.position_percentage >= 70:
            return 4
        if self.position_percentage >= 45:
            return 3
        return round(self.position_percentage / 100.0 * 6)

    def max_shifts_per_week(self) -> int:
        """Maximum shifts this nurse can work per week (hard ceiling).
        100% = 6, 75% = 4, 50% = 3."""
        if self.position_percentage >= 99:
            return 6
        if self.position_percentage >= 70:
            return 4
        if self.position_percentage >= 45:
            return 3
        return min(6, round(self.position_percentage / 100.0 * 6))

    def fairness_shift_targets(self) -> Dict[ShiftType, int]:
        """Preferred MORNING / AFTERNOON / EVENING counts per ISO week (soft fairness). Same brackets as contractual tiers."""
        if self.position_percentage >= 99:
            return {ShiftType.MORNING: 3, ShiftType.AFTERNOON: 2, ShiftType.EVENING: 1}
        if self.position_percentage >= 70:
            return {ShiftType.MORNING: 2, ShiftType.AFTERNOON: 1, ShiftType.EVENING: 1}
        if self.position_percentage >= 45:
            return {ShiftType.MORNING: 1, ShiftType.AFTERNOON: 1, ShiftType.EVENING: 1}
        return {ShiftType.MORNING: 1, ShiftType.AFTERNOON: 1, ShiftType.EVENING: 1}


@dataclass
class QuotaRequirement:
    """Minimum number of nurses required per (day, shift)."""
    # (day_str, shift_str) -> min count
    requirements: Dict[Tuple[str, str], int] = field(default_factory=dict)

    def get(self, day: str, shift: str) -> int:
        return self.requirements.get((day, shift), 0)

    def days(self) -> Set[str]:
        return {d for d, _ in self.requirements}

    def shifts_for_day(self, day: str) -> List[str]:
        return [s for d, s in self.requirements if d == day]


@dataclass
class Assignment:
    """A single assignment: one nurse to one (day, shift)."""
    nurse_id: str
    day: str
    shift: str

    def __hash__(self):
        return hash((self.nurse_id, self.day, self.shift))

    def __eq__(self, other):
        if not isinstance(other, Assignment):
            return False
        return self.nurse_id == other.nurse_id and self.day == other.day and self.shift == other.shift


@dataclass
class Schedule:
    """A complete schedule: list of assignments."""
    assignments: List[Assignment] = field(default_factory=list)

    def copy(self) -> "Schedule":
        return Schedule(assignments=list(self.assignments))

    def assignments_for_nurse(self, nurse_id: str) -> List[Assignment]:
        return [a for a in self.assignments if a.nurse_id == nurse_id]

    def assignments_for_day_shift(self, day: str, shift: str) -> List[Assignment]:
        return [a for a in self.assignments if a.day == day and a.shift == shift]

    def count_for_day_shift(self, day: str, shift: str) -> int:
        return len(self.assignments_for_day_shift(day, shift))

    def get_assignment_set(self) -> set:
        return {(a.nurse_id, a.day, a.shift) for a in self.assignments}

    def add(self, assignment: Assignment) -> None:
        self.assignments.append(assignment)

    def remove(self, assignment: Assignment) -> None:
        self.assignments.remove(assignment)

    def replace(self, old: Assignment, new: Assignment) -> None:
        idx = self.assignments.index(old)
        self.assignments[idx] = new
