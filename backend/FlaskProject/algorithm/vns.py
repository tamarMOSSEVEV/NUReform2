"""
Variable Neighborhood Search (VNS) with penalty-based objective:

- Hard constraints are not enforced by filtering neighbors; they incur a large penalty
  in the objective (quota deficit + violation count), plus soft constraints.
- Search can start from an infeasible greedy solution and improve toward feasibility.
- VNS main loop: each iteration runs at most one best-improving 2-exchange, then one
  best-improving reassign, then one best-improving add (each only if it strictly lowers
  objective). If none of the three improves, a random shake swap from the top-K neighbors
  escapes local optima.
  Runs for exactly up to max_iterations (stops early only if no swap neighbors exist at all).

Initial schedule: greedy construction(s) with multi-start option, then VNS as above.
Instance validation warns when sum of targets or max weekly caps is below required shifts.
"""

import logging
import random
from typing import Dict, List, Optional, Tuple

log = logging.getLogger(__name__)

from .constants import DEFAULT_FAIRNESS_TARGET
from .models import Nurse, QuotaRequirement, Schedule
from .constraints import (
    describe_soft_issues,
    evaluate_soft_constraints,
    is_feasible,
    check_hard_constraints,
    overstaffing_penalty,
    soft_issue_counts,
    validate_staffing_targets_vs_quota,
)
from .greedy_solution import build_greedy_solution
from .neighborhood import (
    best_improving_2exchange,
    best_improving_add,
    best_improving_reassign,
    two_exchange_neighbors,
)

# -----------------------------
# Tuning parameters
# -----------------------------

HARD_VIOLATION_WEIGHT = 500_000.0  # quota deficit term; dominates soft costs
VIOLATION_COUNT_WEIGHT = 5_000.0   # per hard-violation message (includes quota msgs)
OVERSTAFFING_WEIGHT = 120_000.0    # quadratic extra nurses above quota minimum per slot
SHAKE_TOP_NEIGHBORS = 10  # when stuck, random shake picks among first K swap neighbors (stress-sorted)


def _log_vns_progress(
    tag: str,
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    fairness_target,
    evaluate_total,
    evaluate_soft,
) -> None:
    """Detailed progress line (DEBUG): global-best schedule stats."""
    hard_list = check_hard_constraints(schedule, nurses_by_id, quota)
    hard_n = len(hard_list)
    total = evaluate_total(schedule)
    soft_pen = evaluate_soft(schedule)
    u, inv, fair = soft_issue_counts(schedule, nurses_by_id, fairness_target)
    soft_issues_total = u + inv + fair
    log.debug(
        "[VNS] %s | total_objective=%.2f | hard_violations=%d | soft_penalty=%.2f | "
        "soft_issues(total=%d: under_target_nurses=%d tier_inversions=%d fairness_cells=%d)",
        tag,
        total,
        hard_n,
        soft_pen,
        soft_issues_total,
        u,
        inv,
        fair,
    )


def _quota_deficit_penalty(schedule: Schedule, quota: QuotaRequirement) -> float:
    penalty = 0.0
    for (day, shift), required in quota.requirements.items():
        actual = schedule.count_for_day_shift(day, shift)
        if actual < required:
            deficit = required - actual
            penalty += deficit * deficit
    return penalty


def _hard_violation_penalty(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
) -> float:
    """
    Quota *deficit* is penalized as squared shortfall, and again via violation count
    (check_minimum_quotas messages). That double emphasis on understaffing is intentional.
    """
    violations = check_hard_constraints(schedule, nurses_by_id, quota)
    quota_penalty = _quota_deficit_penalty(schedule, quota)
    other_penalty = float(len(violations))
    return HARD_VIOLATION_WEIGHT * quota_penalty + VIOLATION_COUNT_WEIGHT * other_penalty


def _total_score(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    fairness_target,
) -> float:
    """Full objective: hard penalties + overstaffing + soft constraints. Lower is better."""
    soft = evaluate_soft_constraints(
        schedule,
        fairness_target=fairness_target,
        nurses_by_id=nurses_by_id,
    )
    hard = _hard_violation_penalty(schedule, nurses_by_id, quota)
    over = overstaffing_penalty(schedule, quota)
    return hard + OVERSTAFFING_WEIGHT * over + soft


def objective_breakdown(
    schedule: Schedule,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    fairness_target=None,
) -> dict:
    """
    Decompose the VNS objective: hard (quota + violation-count penalty), weighted
    overstaffing, soft score; plus violation/issue lists and counts.
    """
    fairness_target = fairness_target or DEFAULT_FAIRNESS_TARGET
    soft = evaluate_soft_constraints(
        schedule,
        fairness_target=fairness_target,
        nurses_by_id=nurses_by_id,
    )
    hard_pen = _hard_violation_penalty(schedule, nurses_by_id, quota)
    over_raw = overstaffing_penalty(schedule, quota)
    over_w = OVERSTAFFING_WEIGHT * over_raw
    hard_messages = check_hard_constraints(schedule, nurses_by_id, quota)
    soft_messages = describe_soft_issues(schedule, nurses_by_id, fairness_target)
    total = hard_pen + over_w + soft
    return {
        "soft_cost": soft,
        "hard_violation_penalty": hard_pen,
        "overstaffing_penalty": over_w,
        "hard_side_total": hard_pen + over_w,
        "total_objective": total,
        "n_hard_violations": len(hard_messages),
        "n_soft_issues_enumerated": len(soft_messages),
        "hard_messages": hard_messages,
        "soft_messages": soft_messages,
    }


def _vns_single_start(
    start_schedule: Schedule,
    max_iterations: int,
    rng: random.Random,
    nurses_by_id: Dict[str, Nurse],
    quota: QuotaRequirement,
    fairness_target,
    evaluate_total,
    evaluate_soft,
    *,
    run_index: int,
    multi_start_runs: int,
    log_each_iteration: bool,
) -> Tuple[Schedule, float, int]:
    """VNS from a fixed initial schedule. Returns (global_best_schedule, global_best_total, iterations_done)."""
    global_best_schedule = start_schedule.copy()
    global_best_total = evaluate_total(global_best_schedule)

    current_schedule = global_best_schedule.copy()
    current_total = global_best_total

    log.debug(
        "[VNS] Single run: up to %d iterations — swap→reassign→add (each if improving) or shake.",
        max_iterations,
    )
    _log_vns_progress(
        "start (before VNS loop)",
        global_best_schedule,
        nurses_by_id,
        quota,
        fairness_target,
        evaluate_total,
        evaluate_soft,
    )

    iterations_done = 0
    for it in range(max_iterations):
        step_parts: List[str] = []
        progressed = False

        new_schedule, new_total, improved = best_improving_2exchange(
            current_schedule,
            nurses_by_id,
            quota,
            current_total,
            evaluate_total,
        )
        if improved:
            current_schedule = new_schedule.copy()
            current_total = new_total
            progressed = True
            step_parts.append("swap")
            if new_total < global_best_total:
                global_best_schedule = new_schedule.copy()
                global_best_total = new_total

        new_schedule, new_total, improved = best_improving_reassign(
            current_schedule,
            nurses_by_id,
            quota,
            current_total,
            evaluate_total,
        )
        if improved:
            current_schedule = new_schedule.copy()
            current_total = new_total
            progressed = True
            step_parts.append("reassign")
            if new_total < global_best_total:
                global_best_schedule = new_schedule.copy()
                global_best_total = new_total

        new_schedule, new_total, improved = best_improving_add(
            current_schedule,
            nurses_by_id,
            quota,
            current_total,
            evaluate_total,
        )
        if improved:
            current_schedule = new_schedule.copy()
            current_total = new_total
            progressed = True
            step_parts.append("add")
            if new_total < global_best_total:
                global_best_schedule = new_schedule.copy()
                global_best_total = new_total

        if not progressed:
            neigh = two_exchange_neighbors(
                current_schedule, nurses_by_id, quota, filter_min_rest=False
            )
            if not neigh:
                neigh = two_exchange_neighbors(
                    global_best_schedule, nurses_by_id, quota, filter_min_rest=False
                )
            if not neigh:
                log.warning("[VNS] No 2-exchange neighbors — cannot shake/stop early.")
                break
            k_shake = min(SHAKE_TOP_NEIGHBORS, len(neigh))
            shaken_schedule, _, _ = rng.choice(neigh[:k_shake])
            current_schedule = shaken_schedule.copy()
            current_total = evaluate_total(current_schedule)
            step = "shake"
        else:
            step = "+".join(step_parts)

        iterations_done = it + 1
        if log.isEnabledFor(logging.DEBUG):
            _log_vns_progress(
                f"iter {iterations_done} [{step}] explorer_total={current_total:.2f} "
                f"global_best_total={global_best_total:.2f}",
                global_best_schedule,
                nurses_by_id,
                quota,
                fairness_target,
                evaluate_total,
                evaluate_soft,
            )
        elif log_each_iteration:
            gh = len(check_hard_constraints(global_best_schedule, nurses_by_id, quota))
            gs = evaluate_soft(global_best_schedule)
            log.info(
                "[VNS] run %d/%d iter %d/%d: %s | explorer_obj=%.1f global_best_obj=%.1f | "
                "global_hard=%d global_soft=%.1f",
                run_index + 1,
                multi_start_runs,
                iterations_done,
                max_iterations,
                step,
                current_total,
                global_best_total,
                gh,
                gs,
            )

    return global_best_schedule, global_best_total, iterations_done


def run_vns(
    nurses: List[Nurse],
    quota: QuotaRequirement,
    max_iterations: int = 200,
    fairness_target: Optional[Dict] = None,
    seed: Optional[int] = None,
    multi_start_runs: int = 1,
    log_each_iteration: bool = False,
) -> Tuple[Schedule, float, dict]:
    """
    Optional **multi-start**: repeat greedy + VNS ``multi_start_runs`` times with different
    greedy seeds. The winning run is chosen **feasibility first**: minimize hard-violation
    count, then break ties by lowest soft score (contract / fairness / sequence term).
    Within each run, VNS still optimizes the full penalty objective. Each run uses up to
    ``max_iterations`` VNS iterations.

    Swaps that would break minimum rest between shifts are never evaluated in local search
    (shake may still use them with ``filter_min_rest=False``).

    If ``log_each_iteration`` is True, emit one INFO log line after each VNS iteration
    (step taken, explorer vs global-best objective, hard-count and soft score on global best).
    """
    rng = random.Random(seed)
    fairness_target = fairness_target or DEFAULT_FAIRNESS_TARGET
    nurses_by_id = {n.id: n for n in nurses}

    def evaluate_total(s: Schedule) -> float:
        return _total_score(s, nurses_by_id, quota, fairness_target)

    def evaluate_soft(s: Schedule) -> float:
        return evaluate_soft_constraints(
            s,
            fairness_target=fairness_target,
            nurses_by_id=nurses_by_id,
        )

    validation_warnings = validate_staffing_targets_vs_quota(nurses, quota)
    for msg in validation_warnings:
        log.warning("[VNS] Instance check: %s", msg)

    if multi_start_runs < 1:
        raise ValueError("multi_start_runs must be >= 1")

    overall_best_schedule: Optional[Schedule] = None
    overall_best_total = float("inf")
    best_selection_key: Optional[Tuple[int, float]] = None  # (hard_violations, soft) lower wins
    total_iterations_executed = 0
    best_run_index = 0
    best_greedy_seed = 0
    initial_total = 0.0
    initial_soft = 0.0
    was_feasible_greedy = False

    for run_idx in range(multi_start_runs):
        if seed is not None:
            greedy_seed = seed + run_idx * 1_000_003
        else:
            greedy_seed = rng.randint(0, 10**9)

        schedule, failed_greedy = build_greedy_solution(
            nurses,
            quota,
            fairness_target=fairness_target,
            seed=greedy_seed,
            priority_failed_slots=None,
        )
        run_initial_total = evaluate_total(schedule)
        feasible_g = is_feasible(schedule, nurses_by_id, quota)
        log.info(
            "[VNS] Multi-start %d/%d greedy seed=%d feasible=%s objective=%.2f",
            run_idx + 1,
            multi_start_runs,
            greedy_seed,
            feasible_g,
            run_initial_total,
        )
        log.debug(
            "[VNS] Greedy cannot_fill: %s",
            sorted(failed_greedy) if failed_greedy else "none",
        )

        gb_sched, gb_total, iters_done = _vns_single_start(
            schedule,
            max_iterations,
            rng,
            nurses_by_id,
            quota,
            fairness_target,
            evaluate_total,
            evaluate_soft,
            run_index=run_idx,
            multi_start_runs=multi_start_runs,
            log_each_iteration=log_each_iteration,
        )
        total_iterations_executed += iters_done

        run_hard_n = len(check_hard_constraints(gb_sched, nurses_by_id, quota))
        run_soft = evaluate_soft(gb_sched)
        candidate_key: Tuple[int, float] = (run_hard_n, run_soft)

        if best_selection_key is None or candidate_key < best_selection_key:
            best_selection_key = candidate_key
            overall_best_total = gb_total
            overall_best_schedule = gb_sched.copy()
            best_run_index = run_idx
            best_greedy_seed = greedy_seed
            initial_total = run_initial_total
            initial_soft = evaluate_soft(schedule)
            was_feasible_greedy = feasible_g

    assert overall_best_schedule is not None
    assert best_selection_key is not None

    final_feasible = is_feasible(overall_best_schedule, nurses_by_id, quota)
    final_soft = evaluate_soft(overall_best_schedule)

    log.info(
        "[VNS] Done: runs=%d total_iter=%d chosen_run=%d hard_violations=%d soft=%.2f "
        "total_objective=%.2f feasible=%s",
        multi_start_runs,
        total_iterations_executed,
        best_run_index + 1,
        best_selection_key[0],
        best_selection_key[1],
        overall_best_total,
        final_feasible,
    )

    info = {
        "initial_seed": best_greedy_seed,
        "initial_total_objective": initial_total,
        "initial_soft_cost": initial_soft,
        "initial_greedy_feasible": was_feasible_greedy,
        "multi_start_runs": multi_start_runs,
        "max_iterations_per_run": max_iterations,
        "iterations": total_iterations_executed,
        "best_run_index": best_run_index,
        "selection_criterion": "hard_violations_then_soft",
        "best_run_hard_violations": best_selection_key[0],
        "validation_warnings": validation_warnings,
        "feasible": final_feasible,
        "soft_cost": final_soft,
        "total_objective": overall_best_total,
        "message": (
            "Multi-start: pick run with fewest hard violations, then lowest soft score; "
            "within each run, swap/reassign/add or shake; min-rest-breaking swaps filtered in local search."
        ),
    }

    return overall_best_schedule, overall_best_total, info
