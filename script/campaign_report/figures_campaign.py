"""Figures of the campaign section: throughput, generations, times, discovery."""

from __future__ import annotations

import numpy as np
from matplotlib.figure import Figure as MplFigure

from . import style
from .figure import PlotInputs
from matplotlib.dates import AutoDateLocator, ConciseDateFormatter

from .queries import AGGREGATOR_FAIL_DIRECTORY, to_datetime64
from .stages import Stage
from .triage import LabelClass

RATE_MINUTES = 10
RATE_BIN = np.timedelta64(RATE_MINUTES, "m")


def throughput(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    stages = queries.stages()
    if not stages:
        return None
    fig, axes = style.figure(2, 1, 8, 5.4)
    cumulative, rate = axes[0, 0], axes[1, 0]
    ends = {stage: queries.stage_end_times(stage) for stage in stages}
    start = min(times[0] for times in ends.values() if times.size)
    stop = max(times[-1] for times in ends.values() if times.size)
    bins = np.arange(start, stop + RATE_BIN, RATE_BIN)
    for stage, times in ends.items():
        index = style.thin(times.size)
        color = style.color_of(stage)
        cumulative.step(times[index], index + 1, where="post", color=color, label=stage.label)
        counts, _ = np.histogram(times.astype("int64"), bins=bins.astype("int64"))
        rate.step(bins[:-1], counts / RATE_MINUTES, where="post", color=color)
    cumulative.set_title("Stage records, cumulative")
    cumulative.yaxis.set_major_formatter(style.compact)
    rate.set_title(f"Stage records per minute ({RATE_MINUTES}-minute bins)")
    rate.set_ylim(bottom=0)
    rate.set_xlabel("wall clock (UTC)")
    for ax in (cumulative, rate):
        locator = AutoDateLocator()
        ax.xaxis.set_major_locator(locator)
        ax.xaxis.set_major_formatter(ConciseDateFormatter(locator))
    fig.legend(loc="outside upper center", ncols=len(stages))
    return fig


def generation_funnel(inputs: PlotInputs) -> MplFigure | None:
    rows = inputs.queries.generation_funnel()
    if len(rows) < 2:
        return None
    columns = np.array(rows, dtype=float).T
    fig, axes = style.figure(2, 1, 8, 5.2)
    top, bottom = axes[0, 0], axes[1, 0]
    series = ("admitted", "mutants", "agreeing", "selected (04quality-pass)")
    for slot, (label, values) in enumerate(zip(series, columns[1:])):
        top.plot(columns[0], np.where(values > 0, values, np.nan), color=style.CATEGORICAL[slot], label=label)
    top.set_yscale("log")
    top.yaxis.set_major_formatter(style.compact)
    top.set_ylabel("inputs per generation")
    top.set_title("Admitted, mutated, agreeing and selected inputs")
    fig.legend(loc="outside upper center", ncols=len(series))
    medians = inputs.queries.selected_states_by_generation()
    if medians:
        generation, states = np.array(medians).T
        bottom.plot(generation, states, color=style.CATEGORICAL[0])
        bottom.set_yscale("log")
        bottom.yaxis.set_major_formatter(style.compact)
        bottom.yaxis.set_minor_formatter(style.compact)
    else:
        style.empty(bottom, "no selected inputs with exploration metrics")
    bottom.set_title("Median distinct states of the selected inputs")
    bottom.set_ylabel("distinct states")
    bottom.set_xlabel("generation")
    return fig


def checker_times(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    stages = [stage for stage in queries.stages() if stage.checks]
    if not stages:
        return None
    fig, axes = style.figure(1, len(stages), 3.4 * len(stages), 3.2)
    for ax, stage in zip(axes[0], stages):
        durations = queries.durations_by_verdict(stage)
        for verdict in style.VERDICTS:
            if verdict in durations:
                style.ecdf(ax, durations[verdict] / 1000, verdict, style.verdict_color(verdict))
        timeout = stage.timeout_sec(inputs.config)
        limit = max(values.max() for values in durations.values()) / 1000
        label = "duration (s, whole seconds)"
        if timeout is not None and timeout <= 1.5 * max(limit, 1):
            ax.axvline(timeout, color=style.MUTED, linewidth=0.75)
            ax.text(timeout, 0.02, " timeout", color=style.SECONDARY, fontsize=7.5)
        elif timeout is not None:
            label = f"duration (s, whole seconds; timeout {timeout} s)"
        ax.set_title(stage.label)
        ax.set_xlabel(label)
        ax.legend(loc="lower right", fontsize=7)
    return fig


def discovery(inputs: PlotInputs) -> MplFigure | None:
    queries = inputs.queries
    if Stage.PARSER not in queries.stages():
        return None
    checked = queries.stage_end_times(Stage.PARSER)
    disagreements: list[tuple[str, str]] = []
    first_crash: dict[str, tuple[str, str]] = {}
    for entry_hash, directory, label, time in queries.triage_events():
        if directory == AGGREGATOR_FAIL_DIRECTORY:
            disagreements.append((time, label))
        elif entry_hash not in first_crash or time < first_crash[entry_hash][0]:
            first_crash[entry_hash] = (time, label)
    fig, axes = style.figure(1, 2, 9, 3.4)
    panels = (
        (axes[0, 0], "Disagreements (03aggregator-fail)", disagreements),
        (axes[0, 1], "Crashed inputs (any crash directory)", list(first_crash.values())),
    )
    for ax, title, events in panels:
        ax.set_title(title)
        if not events:
            style.empty(ax, "none")
            continue
        for label_class in LabelClass:
            times = sorted(time for time, label in events if LabelClass.of(label) is label_class)
            if not times:
                continue
            x = np.searchsorted(checked, to_datetime64(times), side="right")
            ax.step(
                np.concatenate(([0], x, [checked.size])),
                np.concatenate(([0], np.arange(1, len(x) + 1), [len(x)])),
                where="post",
                color=style.color_of(label_class),
                label=f"{label_class.value} ({len(x):,})",
            )
        ax.set_xlabel("inputs parsed")
        ax.set_ylabel("inputs (cumulative)")
        ax.xaxis.set_major_formatter(style.compact)
        ax.legend(loc="upper left")
    return fig
