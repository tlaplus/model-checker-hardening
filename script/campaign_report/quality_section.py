"""The tables of section 3 of the report: constructs and their pairwise coverage."""

from __future__ import annotations

from . import fmt
from .inputs import ReportInputs
from .stages import Outcome

# Uncovered construct pairs named in the text when there are at most this many.
LISTED_MISSING_PAIRS = 25


def constructs(inputs: ReportInputs, number: str) -> list[str]:
    by_outcome, totals = inputs.queries.operator_counts()
    total = sum(totals.values())
    if not total:
        return []
    counts: dict[str, int] = {}
    for names in by_outcome.values():
        for name, n in names.items():
            counts[name] = counts.get(name, 0) + n
    outcomes = [o for o in Outcome if totals.get(o.value)]
    coverage = inputs.queries.construct_pairs()
    rows = [
        (coverage.index(name), f"`{name}`", fmt.share(counts[name], total),
         *(fmt.share(by_outcome.get(o.value, {}).get(name, 0), totals[o.value]) for o in outcomes))
        for name in coverage.names
    ]
    return [
        f"### {number}. Constructs",
        f"Share of inputs whose evaluated code contains each construct, over {fmt.count(total)} "
        "checked inputs and per outcome. The index orders constructs by frequency; the "
        "pairwise-coverage heatmap uses it for its axes.",
        "<details><summary>All constructs</summary>",
        fmt.table(("index", "construct", "checked inputs", *(o.label for o in outcomes)), rows),
        "</details>",
    ]


def construct_pairs(inputs: ReportInputs, number: str) -> list[str]:
    coverage = inputs.queries.construct_pairs()
    if not coverage.constructs:
        return []
    single = sum(1 for _, _, n in coverage.pairs if n == 1)
    missing = ""
    if 0 < len(coverage.missing) <= LISTED_MISSING_PAIRS:
        missing = " No input contains " + ", ".join(
            f"`{first}` + `{second}`" for first, second in coverage.missing
        ) + "."
    return [
        f"### {number}. Construct pairs",
        "Unordered pairs of constructs that occur together in the evaluated code of one input. "
        "Single constructs saturate early, while such pairs keep growing (ADR 0013). "
        f"{fmt.count(len(coverage.pairs))} of the {fmt.count(coverage.possible)} pairs of the "
        f"{coverage.constructs} constructs seen ({fmt.share(len(coverage.pairs), coverage.possible)}) "
        f"occur in at least one of {fmt.count(coverage.inputs)} checked inputs; "
        f"{fmt.count(single)} of them occur in a single input." + missing,
        "<details><summary>All construct pairs with non-zero coverage</summary>",
        fmt.table(
            ("pair", "indices", "inputs", "% of checked inputs"),
            [(f"`{first}` + `{second}`", f"{coverage.index(first)}, {coverage.index(second)}",
              fmt.count(n), fmt.share(n, coverage.inputs))
             for first, second, n in coverage.pairs],
        ),
        "</details>",
    ]
