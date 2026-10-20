"""The report's figures, in report order, and their rendering.

Each figure is a function from `PlotInputs` to a Matplotlib figure, or None when
the corpus has no data for it. Adding a figure is one function in the module of
its section and one entry in `FIGURES`.
"""

from __future__ import annotations

from pathlib import Path

from . import style
from .figure import Figure, PlotInputs, RenderedFigure, Section
from .figures_campaign import checker_times, discovery, generation_funnel, throughput
from .figures_findings import TOP_SIGNATURES, failure_codes, known_defects, triage_labels, verdict_matrix
from .figures_quality import (
    construct_pairs,
    input_size,
    operator_coverage,
    shallow_and_values,
    state_space,
)
from .queries import MIN_LIFT_SUPPORT

__all__ = ["FIGURES", "Figure", "PlotInputs", "RenderedFigure", "Section", "render_all"]


def render_all(inputs: PlotInputs, directory: Path) -> list[RenderedFigure]:
    for stale in directory.glob("*.png"):
        stale.unlink()
    rendered = []
    for figure in FIGURES:
        drawn = figure.draw(inputs)
        path = None
        if drawn is not None:
            path = directory / figure.file_name
            style.save(drawn, path)
        rendered.append(RenderedFigure(figure, path))
    return rendered


FIGURES = (
    Figure("throughput", "Throughput", Section.CAMPAIGN,
           "Stage records by end time, cumulative and per minute. Flat segments and dips are "
           "pauses or stalls; a stage whose curve falls behind the one before it is a bottleneck.",
           throughput),
    Figure("generation-funnel", "Generation funnel", Section.CAMPAIGN,
           "Per generation (ADR 0010): inputs admitted, mutants among them, inputs on which the "
           "checkers agree, and inputs the quality gate selects; below, the median distinct "
           "states of the selected inputs. Coinciding lines hide each other: when every admitted "
           "input is aggregated and agreed on, the agreeing line covers the admitted one.",
           generation_funnel),
    Figure("checker-times", "Checker times", Section.CAMPAIGN,
           "ECDF of stage durations per verdict. Stages record whole seconds.", checker_times),
    Figure("discovery", "Discovery curve", Section.CAMPAIGN,
           "Cumulative disagreements and crashed inputs per triage class against inputs parsed. "
           "A curve that flattens means the campaign stopped finding that class.", discovery),
    Figure("verdict-matrix", "Verdict pairs", Section.FINDINGS,
           "Aggregated inputs per pair of checker verdicts. Off-diagonal cells are disagreements.",
           verdict_matrix),
    Figure("triage-labels", "Triage labels", Section.FINDINGS,
           "Entries per triage label in each crash and disagreement directory.", triage_labels),
    Figure("known-defects", "Known-defect signatures", Section.FINDINGS,
           f"The {TOP_SIGNATURES} signatures that filtered the most candidates before checking.",
           known_defects),
    Figure("failure-codes", "Verdicts and failure codes", Section.FINDINGS,
           "Share of each stage's records by verdict and failure code (ADR 0003).", failure_codes),
    Figure("input-size", "Input size", Section.QUALITY,
           "Generator input bytes and evaluated code size per outcome, and code size against the "
           "state space for agreeing inputs.", input_size),
    Figure("state-space", "State-space shape", Section.QUALITY,
           "ECDFs of exploration metrics (ADR 0008) for selected and other agreeing inputs.",
           state_space),
    Figure("shallow-and-values", "Shallow patterns and state values", Section.QUALITY,
           "Shallow patterns of agreeing inputs, the size of state values, and richness.",
           shallow_and_values),
    Figure("operator-coverage", "Operator coverage", Section.QUALITY,
           "Constructs in the evaluated code: the rarest ones, those over-represented in "
           f"disagreements and crashes (lift > 1 with at least {MIN_LIFT_SUPPORT} such inputs; the "
           "count is in parentheses), and distinct (parent, child) operator edges, the quality "
           "gate's coverage features (ADR 0013), by the generation that first produced them.",
           operator_coverage),
    Figure("construct-pairs", "Pairwise construct coverage", Section.QUALITY,
           "Share of checked inputs whose evaluated code contains both constructs. Both axes "
           "index constructs by frequency, as in the Constructs table below, so the most "
           "common constructs are top left. The diagonal is each construct's own share; red "
           "cells are pairs that no input contains.", construct_pairs),
)
