"""Types shared by the figures and their registry."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from matplotlib.figure import Figure as MplFigure

from .queries import CorpusQueries
from .sources import WorkflowStats


class Section(Enum):
    CAMPAIGN = "campaign"
    FINDINGS = "findings"
    QUALITY = "quality"


@dataclass(frozen=True)
class PlotInputs:
    queries: CorpusQueries
    config: dict
    stats: WorkflowStats | None


@dataclass(frozen=True)
class Figure:
    name: str
    title: str
    section: Section
    caption: str
    draw: Callable[[PlotInputs], MplFigure | None]

    @property
    def file_name(self) -> str:
        return f"{self.name}.png"


@dataclass(frozen=True)
class RenderedFigure:
    figure: Figure
    path: Path | None  # None when the corpus has no data for the figure
