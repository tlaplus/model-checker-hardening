"""Figures of the findings section: verdicts, triage labels, signatures, failure codes."""

from __future__ import annotations

import numpy as np
from matplotlib.figure import Figure as MplFigure

from . import style
from .figure import PlotInputs
from itertools import combinations

from matplotlib.colors import LogNorm

from .stages import Stage
from .triage import LabelClass

TOP_SIGNATURES = 20


def verdict_matrix(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    pairs = list(combinations(range(len(queries.checkers)), 2))
    rows = queries.verdict_pairs()
    if not pairs or not rows:
        return None
    fig, axes = style.figure(1, len(pairs), 3.6 * len(pairs), 3.3)
    for ax, (first, second) in zip(axes[0], pairs):
        verdicts = [v for v in style.VERDICTS if any(v in (r[1 + first], r[1 + second]) for r in rows)]
        matrix = np.zeros((len(verdicts), len(verdicts)))
        for row in rows:
            a, b = row[1 + first], row[1 + second]
            if a in verdicts and b in verdicts:
                matrix[verdicts.index(a), verdicts.index(b)] += row[-1]
        _heatmap(ax, matrix, verdicts)
        ax.set_ylabel(queries.checkers[first].label)
        ax.set_xlabel(queries.checkers[second].label)
        ax.set_title(f"{queries.checkers[first].label} × {queries.checkers[second].label}")
    return fig


def _heatmap(ax, matrix: np.ndarray, labels: list[str]) -> None:
    norm = LogNorm(vmin=1, vmax=max(matrix.max(), 10))
    ax.imshow(np.where(matrix > 0, matrix, np.nan), cmap=style.SEQUENTIAL, norm=norm)
    ax.set_xticks(range(len(labels)), labels, rotation=20)
    ax.set_yticks(range(len(labels)), labels)
    ax.grid(False)
    for (i, j), value in np.ndenumerate(matrix):
        dark = value > 0 and norm(value) > style.SEQUENTIAL_DARK_FROM
        text = f"{value:,.0f}" if value else "·"
        ax.text(j, i, text, ha="center", va="center", fontsize=7.5, color="white" if dark else style.INK)


def triage_labels(inputs: PlotInputs) -> MplFigure | None:
    counts: dict[str, list[tuple[str, int]]] = {}
    for directory, label, entries in inputs.queries.triage_counts():
        counts.setdefault(directory, []).append((label, entries))
    if not counts:
        return None
    height = 1.0 + 0.25 * sum(len(rows) for rows in counts.values()) + 0.6 * len(counts)
    ratios = [len(rows) + 1 for rows in counts.values()]
    fig, axes = style.figure(len(counts), 1, 7.5, height, height_ratios=ratios)
    for ax, (directory, rows) in zip(axes[:, 0], counts.items()):
        labels = [label for label, _ in rows]
        colors = [style.color_of(LabelClass.of(label)) for label in labels]
        style.horizontal_bars(ax, labels, [entries for _, entries in rows], colors)
        ax.set_title(directory)
    present = [c for c in LabelClass if any(LabelClass.of(label) is c
                                            for rows in counts.values() for label, _ in rows)]
    fig.legend(
        handles=[style.swatch(style.color_of(c), c.value) for c in present],
        loc="outside lower center",
        ncols=len(present),
    )
    return fig


def known_defects(inputs: PlotInputs) -> MplFigure | None:
    matched = inputs.stats.generator.known_defects if inputs.stats else {}
    if not matched:
        return None
    top = sorted(matched.items(), key=lambda item: (-item[1], item[0]))[:TOP_SIGNATURES]
    fig, axes = style.figure(1, 1, 8, 0.8 + 0.24 * len(top))
    ax = axes[0, 0]
    style.horizontal_bars(ax, [name for name, _ in top], [n for _, n in top], style.CATEGORICAL[0])
    ax.set_xlabel("candidates matched (stored or not)")
    ax.xaxis.set_major_formatter(style.compact)
    return fig


# Segments of a checker's records: verdict, and failure code for fails (ADR 0003).
# A verdict keeps its color of the other figures; failure codes take further slots.
CODE_SEGMENTS = (
    ("pass", "pass", None, style.verdict_color("pass")),
    ("counterexample", "counterexample", None, style.verdict_color("counterexample")),
    ("fail 75 (evaluation)", "fail", 75, style.verdict_color("fail")),
    ("fail 120 (type check)", "fail", 120, style.CATEGORICAL[4]),
    ("fail 150 (parse)", "fail", 150, style.CATEGORICAL[5]),
    ("fail, other code", "fail", "other", style.CATEGORICAL[6]),
    ("crashed", "crashed", None, style.verdict_color("crashed")),
)


def failure_codes(inputs: PlotInputs) -> MplFigure | None:
    rows = inputs.queries.failure_codes()
    stages = [stage for stage in Stage if any(row[0] == stage.value for row in rows)]
    if not stages:
        return None
    known_codes = {code for _, verdict, code, _ in CODE_SEGMENTS if verdict == "fail"}
    counts = np.zeros((len(stages), len(CODE_SEGMENTS)))
    for stage_name, verdict, code, records in rows:
        if verdict == "fail" and code not in known_codes:
            code = "other"
        segment = next(i for i, s in enumerate(CODE_SEGMENTS) if s[1] == verdict and s[2] == code)
        counts[[s.value for s in stages].index(stage_name), segment] += records
    shares = counts / counts.sum(axis=1, keepdims=True)
    fig, axes = style.figure(1, 1, 8, 1.2 + 0.45 * len(stages))
    ax = axes[0, 0]
    positions = np.arange(len(stages))[::-1]
    left = np.zeros(len(stages))
    for index, (label, _, _, color) in enumerate(CODE_SEGMENTS):
        if counts[:, index].any():
            ax.barh(positions, shares[:, index], left=left, height=0.6, label=label,
                    color=color, edgecolor=style.SURFACE, linewidth=1)
        left += shares[:, index]
    ax.set_yticks(positions, [f"{s.label} ({counts[i].sum():,.0f})" for i, s in enumerate(stages)])
    ax.set_xlim(0, 1)
    ax.xaxis.set_major_formatter(lambda value, _: f"{value:.0%}")
    ax.grid(axis="y", visible=False)
    ax.set_xlabel("share of stage records")
    fig.legend(loc="outside lower center", ncols=int(counts.any(axis=0).sum()))
    return fig
