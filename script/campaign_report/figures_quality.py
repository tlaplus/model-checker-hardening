"""Figures of the input-quality section: sizes, state spaces, values, operators."""

from __future__ import annotations

import numpy as np
from matplotlib.figure import Figure as MplFigure

from . import style
from .figure import PlotInputs

from matplotlib.colors import ListedColormap, LogNorm

from .queries import MIN_LIFT_SUPPORT, STATE_METRICS, CorpusQueries, lifts
from .stages import AGREEING, Outcome

TOP_OPERATORS = 25
TOP_LIFTS = 15


def input_size(inputs: PlotInputs) -> MplFigure | None:
    rows = inputs.queries.input_sizes()
    if not rows:
        return None
    fig, axes = style.figure(1, 3, 12, 3.4)
    for ax, column, label in ((axes[0, 0], 1, "input bytes"), (axes[0, 1], 2, "evaluated nodes")):
        series = [
            (outcome.label, style.color_of(outcome), _column(rows, column, outcome.value))
            for outcome in Outcome
        ]
        ax.set_xscale("log")
        style.step_histograms(ax, series, style.log_bins(values for _, _, values in series))
        ax.set_xlabel(label)
        ax.set_title(f"{label.capitalize()} per outcome")
    fig.legend(*axes[0, 0].get_legend_handles_labels(), loc="outside upper center", ncols=len(Outcome))
    ax = axes[0, 2]
    pairs = np.array(
        [(r[2], r[3]) for r in rows if r[0] in {outcome.value for outcome in AGREEING} and r[2] and r[3] is not None], dtype=float
    )
    ax.set_title("Code size vs distinct states (agreeing)")
    if pairs.size:
        cells = ax.hexbin(pairs[:, 0], pairs[:, 1] + 1, xscale="log", yscale="log", gridsize=40,
                          bins="log", cmap=style.SEQUENTIAL, mincnt=1, linewidths=0)
        fig.colorbar(cells, ax=ax, label="inputs", shrink=0.85)
        ax.set_xlabel("evaluated nodes")
        ax.set_ylabel("distinct states + 1")
        ax.grid(False)
    else:
        style.empty(ax, "no agreeing inputs with metrics")
    return fig


STATE_PANELS = (
    ("distinctStates", "distinct states", True),
    ("generatedStates", "generated states", True),
    ("projectedStates", "distinct states without the step variable", True),
    ("depth", "depth", False),
    ("projectedDepth", "projected depth", False),
    ("discoveringShare", "share of actions that discover a state", False),
)


def state_space(inputs: PlotInputs) -> MplFigure | None:
    metrics = _state_metrics(inputs.queries)
    if metrics is None:
        return None
    fig, axes = style.figure(2, 3, 12, 6.2)
    for ax, (metric, label, logarithmic) in zip(axes.flat, STATE_PANELS):
        for outcome in AGREEING:
            style.ecdf(ax, metrics[outcome][metric], outcome.label, style.color_of(outcome))
        if logarithmic:
            style.symlog_counts(ax)
        ax.set_xlabel(label)
        ax.set_title(label.capitalize())
    fig.legend(*axes[0, 0].get_legend_handles_labels(), loc="outside upper center", ncols=2)
    return fig


def shallow_and_values(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    metrics = _state_metrics(queries)
    patterns = queries.shallow_patterns()
    if metrics is None or not patterns:
        return None
    fig, axes = style.figure(2, 3, 12, 6.2)
    ax = axes[0, 0]
    style.horizontal_bars(ax, [p for p, _ in patterns], [n for _, n in patterns], style.CATEGORICAL[0])
    ax.set_title("Shallow patterns of agreeing inputs (ADR 0008)")
    ax.xaxis.set_major_formatter(style.compact)
    for ax, metric, label in (
        (axes[0, 1], "maxCardinality", "largest collection in a state"),
        (axes[1, 0], "maxStateNodes", "value nodes of the largest state"),
    ):
        for outcome in AGREEING:
            style.ecdf(ax, metrics[outcome][metric], outcome.label, style.color_of(outcome))
        style.symlog_counts(ax)
        ax.set_title(label.capitalize())
        ax.set_xlabel(label)
    ax = axes[0, 2]
    series = [(o.label, style.color_of(o), metrics[o]["maxNesting"]) for o in AGREEING]
    deepest = max((v.max() for _, _, v in series if v.size), default=0)
    style.step_histograms(ax, series, np.arange(0, deepest + 2) - 0.5)
    ax.set_title("Deepest value nesting in a state")
    ax.set_xlabel("nesting depth")
    fig.legend(*axes[0, 1].get_legend_handles_labels(), loc="outside upper center", ncols=2)
    cohorts = queries.cohorts()
    ax = axes[1, 1]
    if cohorts:
        ax.bar([c for c, _ in cohorts], [n for _, n in cohorts], width=0.6, color=style.CATEGORICAL[0])
        ax.set_xticks([c for c, _ in cohorts])
        ax.yaxis.set_major_formatter(style.compact)
        ax.grid(axis="x", visible=False)
    else:
        style.empty(ax, "no richness cohorts")
    ax.set_title("Admitted inputs per richness cohort")
    ax.set_xlabel("cohort")
    ax = axes[1, 2]
    richness = queries.richness()
    if richness.size:
        ax.set_xscale("log")
        style.step_histograms(ax, [("checked inputs", style.CATEGORICAL[0], richness)], style.log_bins([richness]))
    else:
        style.empty(ax, "no richness scores")
    ax.set_title("Richness score (ADR 0002)")
    ax.set_xlabel("richness")
    return fig


def operator_coverage(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    by_outcome, totals = queries.operator_counts()
    total = sum(totals.values())
    if not total:
        return None
    counts: dict[str, int] = {}
    for names in by_outcome.values():
        for name, n in names.items():
            counts[name] = counts.get(name, 0) + n
    fig, axes = style.figure(2, 2, 12, 9.5)
    ax = axes[0, 0]
    rarest = sorted(counts.items(), key=lambda item: (item[1], item[0]))[:TOP_OPERATORS][::-1]
    style.horizontal_bars(ax, [n for n, _ in rarest], [100 * c / total for _, c in rarest], style.CATEGORICAL[0])
    ax.set_title(f"The {TOP_OPERATORS} rarest constructs (% of checked inputs)")
    for ax, outcome in ((axes[0, 1], Outcome.DISAGREEING), (axes[1, 0], Outcome.CRASHED)):
        group = [g for g in lifts(counts, total, by_outcome.get(outcome.value, {}),
                                  totals.get(outcome.value, 0)) if g.lift > 1]
        ax.set_title(f"Over-represented in {outcome.label} inputs (lift, top {TOP_LIFTS})")
        if group:
            shown = group[:TOP_LIFTS]
            style.horizontal_bars(ax, [f"{g.name} ({g.support})" for g in shown], [g.lift for g in shown],
                                  style.color_of(outcome))
            ax.axvline(1, color=style.MUTED, linewidth=0.75)
        else:
            style.empty(ax, f"no construct over-represented in ≥ {MIN_LIFT_SUPPORT} {outcome.label} inputs")
    ax = axes[1, 1]
    ax.set_title("Operator edges seen, cumulative")
    drawn = False
    for label, outcome, color in (
        ("checked inputs", None, style.CATEGORICAL[0]),
        ("selected inputs", Outcome.SELECTED, style.CATEGORICAL[1]),
    ):
        rows = queries.new_edges_by_generation(outcome)
        if rows:
            generation, new = np.array(rows, dtype=float).T
            ax.step(generation, np.cumsum(new), where="post", color=color, label=label)
            drawn = True
    if drawn:
        ax.set_xlabel("generation")
        ax.set_ylabel("distinct (parent, child) edges")
        ax.legend(loc="lower right")
    else:
        style.empty(ax, "no generations")
    return fig


# Index ticks of the pair heatmap: the first construct and every fifth.
INDEX_TICK_STEP = 5


def construct_pairs(inputs: PlotInputs) -> MplFigure | None:
    coverage = inputs.queries.construct_pairs()
    count = coverage.constructs
    if count < 2:
        return None
    share = coverage.together / coverage.inputs
    fig, axes = style.figure(1, 1, 8.4, 7.2)
    ax = axes[0, 0]
    # Cell (a, b) is centred on the 1-based indices of the two constructs.
    extent = (0.5, count + 0.5, count + 0.5, 0.5)
    norm = LogNorm(vmin=1 / coverage.inputs, vmax=1)
    image = ax.imshow(np.where(share > 0, share, np.nan), cmap=style.SEQUENTIAL, norm=norm,
                      extent=extent, interpolation="nearest")
    missing = coverage.together == 0
    if missing.any():
        ax.imshow(np.where(missing, 1.0, np.nan), cmap=ListedColormap([style.UNCOVERED]),
                  extent=extent, interpolation="nearest")
        fig.legend(handles=[style.swatch(style.UNCOVERED, f"never together ({len(coverage.missing):,} pairs)")],
                   loc="outside lower center")
    ticks = [1, *range(INDEX_TICK_STEP, count + 1, INDEX_TICK_STEP)]
    ax.set_xticks(ticks)
    ax.set_yticks(ticks)
    ax.grid(False)
    ax.set_xlabel("construct index")
    ax.set_ylabel("construct index")
    bar = fig.colorbar(image, ax=ax, shrink=0.85, label="share of checked inputs with both constructs")
    bar.ax.yaxis.set_major_formatter(lambda value, _: f"{100 * value:g}%")
    return fig


def _state_metrics(queries: CorpusQueries) -> dict[Outcome, dict[str, np.ndarray]] | None:
    rows = queries.state_metrics()
    if not rows:
        return None
    index = {metric: 1 + i for i, metric in enumerate(STATE_METRICS)}
    result = {}
    for outcome in AGREEING:
        selected = [row for row in rows if row[0] == outcome.value]
        metrics = {
            metric: np.array([r[i] for r in selected if r[i] is not None], dtype=float)
            for metric, i in index.items()
        }
        metrics["discoveringShare"] = np.array(
            [r[index["actionsDiscovering"]] / r[index["actions"]] for r in selected
             if r[index["actions"]] and r[index["actionsDiscovering"]] is not None],
            dtype=float,
        )
        result[outcome] = metrics
    return result


def _column(rows: list[tuple], column: int, outcome: str) -> np.ndarray:
    return np.array([r[column] for r in rows if r[0] == outcome and r[column] is not None], dtype=float)
