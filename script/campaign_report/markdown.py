"""Renders `report.md` from the report's inputs and the rendered figures."""

from __future__ import annotations

import os
import tempfile
from collections.abc import Sequence
from pathlib import Path

from . import campaign_section, findings_section, quality_section
from .inputs import ReportInputs
from .plots import RenderedFigure, Section

__all__ = ["ReportInputs", "render", "write_report"]


def write_report(path: Path, inputs: ReportInputs, figures: Sequence[RenderedFigure]) -> None:
    text = render(inputs, figures)
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False
    ) as output:
        output.write(text)
    os.replace(output.name, path)


def render(inputs: ReportInputs, figures: Sequence[RenderedFigure]) -> str:
    corpus = inputs.settings.path
    parts = [
        f"# Campaign report: {corpus.name}",
        f"Generated {inputs.generated_at:%Y-%m-%d %H:%M} UTC by `script/report.py` from the export "
        f"of {inputs.provenance.exported_at}. Corpus: `{corpus}`.",
        "## Summary",
        campaign_section.summary(inputs),
        "## 1. Campaign",
        *campaign_section.setup(inputs),
        *campaign_section.time(inputs),
        *campaign_section.stages(inputs),
        *campaign_section.generator(inputs),
        *campaign_section.entries(inputs),
        *_figures(figures, Section.CAMPAIGN, "1.6"),
        "## 2. Findings",
        *findings_section.verdicts(inputs),
        *findings_section.triage(inputs),
        *findings_section.known_defects(inputs),
        *findings_section.data_quality(inputs),
        *_figures(figures, Section.FINDINGS, "2.5"),
        "## 3. Input quality",
        "Exploration metrics come from the "
        f"{inputs.queries.explorer.label if inputs.queries.explorer else 'first TLC'} stage. "
        "*Selected* inputs are those in `04quality-pass`; *agreeing, not selected* are the other "
        "inputs on which the checkers agree.",
        *_figures(figures, Section.QUALITY, "3"),
        *quality_section.constructs(inputs, _next_number(figures, Section.QUALITY, "3")),
        *quality_section.construct_pairs(inputs, _next_number(figures, Section.QUALITY, "3", 1)),
    ]
    return "\n\n".join(parts) + "\n"


def _next_number(
    figures: Sequence[RenderedFigure], section: Section, number: str, offset: int = 0
) -> str:
    """The number of a subsection that follows the section's plots."""
    plotted = sum(1 for f in figures if f.figure.section is section)
    return f"{number}.{plotted + 1 + offset}"


def _figures(figures: Sequence[RenderedFigure], section: Section, number: str) -> list[str]:
    selected = [f for f in figures if f.figure.section is section]
    parts = [f"### {number}. Plots"] if section is not Section.QUALITY else []
    for index, rendered in enumerate(selected, start=1):
        figure = rendered.figure
        heading = "###" if section is Section.QUALITY else "####"
        parts.append(f"{heading} {number}.{index}. {figure.title}")
        parts.append(figure.caption)
        if rendered.path is None:
            parts.append("*No data for this plot in this corpus.*")
        else:
            parts.append(f"![{figure.title}](.report/{rendered.path.name})")
    return parts
