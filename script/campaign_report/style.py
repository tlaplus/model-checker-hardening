"""Chart style: palette, Matplotlib defaults and shared drawing helpers.

Colors follow the reference palette of the dataviz guidance (light surface):
categorical slots in fixed order, a one-hue blue ramp for magnitude, and
recessive ink for axes and grid. An entity keeps its slot in every figure.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from enum import Enum
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt  # noqa: E402  (the backend must be chosen first)
import numpy as np  # noqa: E402
from matplotlib.axes import Axes  # noqa: E402
from matplotlib.colors import LinearSegmentedColormap  # noqa: E402
from matplotlib.figure import Figure  # noqa: E402
from matplotlib.patches import Patch  # noqa: E402

SURFACE = "#fcfcfb"
INK = "#0b0b0b"
SECONDARY = "#52514e"
MUTED = "#898781"
GRID = "#e1e0d9"
AXIS = "#c3c2b7"

CATEGORICAL = (
    "#2a78d6",  # blue
    "#eb6834",  # orange
    "#1baf7a",  # aqua
    "#eda100",  # yellow
    "#e87ba4",  # magenta
    "#008300",  # green
    "#6250d6",  # violet
    "#e34948",  # red
)
SEQUENTIAL = LinearSegmentedColormap.from_list(
    "report-blue",
    ["#cde2fb", "#9ec5f4", "#6da7ec", "#3987e5", "#256abf", "#184f95", "#0d366b"],
)
SEQUENTIAL_DARK_FROM = 0.55  # Above this fraction of the ramp, text on a cell is white.

# Verdicts and their colors are fixed so a verdict looks the same in every figure.
VERDICTS = ("pass", "counterexample", "fail", "crashed")
DPI = 150

RC = {
    "figure.facecolor": SURFACE,
    "axes.facecolor": SURFACE,
    "savefig.facecolor": SURFACE,
    "font.size": 8.5,
    "axes.titlesize": 9.5,
    "axes.titleweight": "bold",
    "axes.titlelocation": "left",
    "axes.titlecolor": INK,
    "axes.labelcolor": SECONDARY,
    "axes.edgecolor": AXIS,
    "axes.linewidth": 0.75,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "axes.axisbelow": True,
    "grid.color": GRID,
    "grid.linewidth": 0.75,
    "grid.linestyle": "-",
    "xtick.color": MUTED,
    "ytick.color": MUTED,
    "xtick.labelcolor": SECONDARY,
    "ytick.labelcolor": SECONDARY,
    "text.color": INK,
    "legend.frameon": False,
    "legend.fontsize": 8,
    "legend.labelcolor": SECONDARY,
    "lines.linewidth": 1.5,
    "lines.solid_capstyle": "round",
    "lines.solid_joinstyle": "round",
}
plt.rcParams.update(RC)


def color_of(member: Enum) -> str:
    """The categorical slot of an enum constant: its declaration index."""
    return CATEGORICAL[list(type(member)).index(member) % len(CATEGORICAL)]


def verdict_color(verdict: str) -> str:
    return CATEGORICAL[VERDICTS.index(verdict)] if verdict in VERDICTS else MUTED


def figure(
    rows: int,
    columns: int,
    width: float,
    height: float,
    height_ratios: Sequence[float] | None = None,
) -> tuple[Figure, np.ndarray]:
    fig, axes = plt.subplots(
        rows, columns, figsize=(width, height), squeeze=False, height_ratios=height_ratios
    )
    fig.set_layout_engine("constrained")
    return fig, axes


def save(fig: Figure, path: Path) -> None:
    fig.savefig(path, dpi=DPI, metadata={"Software": None})
    plt.close(fig)


def empty(ax: Axes, message: str) -> None:
    ax.text(0.5, 0.5, message, ha="center", va="center", color=MUTED, transform=ax.transAxes)
    ax.set_xticks([])
    ax.set_yticks([])
    ax.grid(False)


def ecdf(ax: Axes, values: np.ndarray, label: str, color: str) -> None:
    """A step ECDF over distinct values, so large integer samples stay cheap to draw."""
    if values.size == 0:
        return
    distinct, counts = np.unique(values, return_counts=True)
    shares = np.cumsum(counts) / values.size
    ax.step(
        np.concatenate(([distinct[0]], distinct)),
        np.concatenate(([0.0], shares)),
        where="post",
        label=f"{label} (n={values.size:,})",
        color=color,
    )
    ax.set_ylim(0, 1.02)
    ax.set_ylabel("share of inputs")


def log_bins(groups: Iterable[np.ndarray], count: int = 40) -> np.ndarray:
    positive = [values[values > 0] for values in groups]
    positive = [values for values in positive if values.size]
    if not positive:
        return np.array([1.0, 10.0])
    low = min(values.min() for values in positive)
    high = max(values.max() for values in positive)
    return np.logspace(np.log10(low), np.log10(max(high, low * 10)), count)


def step_histograms(
    ax: Axes, series: Sequence[tuple[str, str, np.ndarray]], bins: np.ndarray
) -> None:
    """Overlaid histograms, one outline per (label, color, values) series.

    Each bar is the share of its series in the bin, so series of different sizes
    and logarithmic bins compare directly.
    """
    for label, color, values in series:
        if values.size:
            ax.hist(
                values,
                bins=bins,
                weights=np.full(values.size, 1 / values.size),
                histtype="step",
                linewidth=1.5,
                color=color,
                label=f"{label} (n={values.size:,})",
            )
    ax.set_ylabel("share of inputs")


def symlog_counts(ax: Axes) -> None:
    """A logarithmic count axis that keeps 0: linear below 1, from 0 upwards."""
    ax.set_xscale("symlog", linthresh=1)
    ax.set_xlim(left=0)


def horizontal_bars(
    ax: Axes, labels: Sequence[str], values: Sequence[float], colors: Sequence[str] | str
) -> None:
    """Horizontal bars, largest on top, with the value at each tip."""
    positions = np.arange(len(labels))[::-1]
    ax.barh(positions, values, height=0.62, color=colors)
    ax.set_yticks(positions, labels)
    ax.grid(axis="y", visible=False)
    ax.tick_params(axis="y", length=0)
    limit = max(values) if len(values) else 1
    for position, value in zip(positions, values):
        text = f"{value:,.0f}" if float(value).is_integer() else f"{value:.2f}"
        ax.text(value + limit * 0.01, position, text, va="center", color=SECONDARY, fontsize=7.5)
    ax.set_xlim(0, limit * 1.12)


def thin(size: int, points: int = 4000) -> np.ndarray:
    """Indices of at most `points` evenly spaced samples, keeping the last one."""
    if size <= points:
        return np.arange(size)
    return np.unique(np.linspace(0, size - 1, points).astype(int))


def swatch(color: str, label: str) -> Patch:
    return Patch(color=color, label=label)


def compact(value: float, _position: int) -> str:
    for threshold, suffix in ((1e6, "M"), (1e3, "K")):
        if abs(value) >= threshold:
            return f"{value / threshold:g}{suffix}"
    return f"{value:g}"
