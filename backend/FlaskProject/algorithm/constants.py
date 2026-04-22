"""
Constants for the nurse scheduling algorithm.
"""

from enum import Enum
from datetime import time

# Minimum hours between two consecutive shifts for the same nurse (hard constraint)
MIN_HOURS_BETWEEN_SHIFTS = 12

# Maximum shifts per nurse per week (hard constraint)
MAX_SHIFTS_PER_WEEK = 6

# One shift per nurse per day (hard constraint) - enforced by data structure


class ShiftType(str, Enum):
    """Shift types."""
    MORNING = "MORNING"
    AFTERNOON = "AFTERNOON"
    EVENING = "EVENING"


# Shift start/end times for gap calculation (same calendar day; EVENING ends next day)
# Used to enforce MIN_HOURS_BETWEEN_SHIFTS
SHIFT_START_TIME = {
    ShiftType.MORNING: time(6, 0),    # 06:00
    ShiftType.AFTERNOON: time(14, 0), # 14:00
    ShiftType.EVENING: time(22, 0),   # 22:00
}

SHIFT_END_TIME = {
    ShiftType.MORNING: time(14, 0),   # 14:00
    ShiftType.AFTERNOON: time(22, 0), # 22:00
    ShiftType.EVENING: time(6, 0),    # 06:00 next day
}

# Fallback when no Nurse is available for a slot (rare). Per-nurse targets: Nurse.fairness_shift_targets().
# 100%: 3/2/1; 75%: 2/1/1; 50%: 1/1/1 — see models.Nurse.fairness_shift_targets.
DEFAULT_FAIRNESS_TARGET = {
    ShiftType.MORNING: 3,
    ShiftType.AFTERNOON: 2,
    ShiftType.EVENING: 1,
}
