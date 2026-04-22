"""
Nurse scheduling algorithm package.
Pure Python – no Flask or server dependencies.
"""

from .constants import (
    ShiftType,
    MIN_HOURS_BETWEEN_SHIFTS,
    MAX_SHIFTS_PER_WEEK,
    DEFAULT_FAIRNESS_TARGET,
)
from .models import Nurse, QuotaRequirement, Assignment, Schedule
from .constraints import (
    check_hard_constraints,
    describe_soft_issues,
    evaluate_soft_constraints,
    is_feasible,
    validate_staffing_targets_vs_quota,
)
from .greedy_solution import build_greedy_solution
from .vns import objective_breakdown, run_vns

__all__ = [
    "ShiftType",
    "MIN_HOURS_BETWEEN_SHIFTS",
    "MAX_SHIFTS_PER_WEEK",
    "DEFAULT_FAIRNESS_TARGET",
    "Nurse",
    "QuotaRequirement",
    "Assignment",
    "Schedule",
    "check_hard_constraints",
    "describe_soft_issues",
    "evaluate_soft_constraints",
    "is_feasible",
    "validate_staffing_targets_vs_quota",
    "build_greedy_solution",
    "objective_breakdown",
    "run_vns",
]
