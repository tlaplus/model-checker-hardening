"""Markdown formatting helpers of the report."""

from __future__ import annotations

import os
from collections.abc import Iterable, Sequence
from pathlib import Path
from typing import Any


DESCRIPTION_WIDTH = 140


def table(headers: Sequence[Any], rows: Iterable[Sequence[Any]]) -> str:
    lines = ["| " + " | ".join(str(h) for h in headers) + " |",
             "| " + " | ".join("---" for _ in headers) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(cell(value) for value in row) + " |")
    return "\n".join(lines)


def cell(value: Any) -> str:
    return "" if value is None else str(value).replace("|", "\\|").replace("\n", " ")


def count(value: int) -> str:
    return f"{value:,}"


def share(part: int, whole: int) -> str:
    return f"{100 * part / whole:.3g}%" if whole else "–"


def seconds(millis: float) -> str:
    return f"{millis / 1000:g} s"


def duration(seconds: float) -> str:
    seconds = int(round(seconds))
    days, seconds = divmod(seconds, 86400)
    hours, seconds = divmod(seconds, 3600)
    minutes, seconds = divmod(seconds, 60)
    if days:
        return f"{days} d {hours} h {minutes:02d} min"
    if hours:
        return f"{hours} h {minutes:02d} min"
    if minutes:
        return f"{minutes} min {seconds:02d} s"
    return f"{seconds} s"


def clip(text: str) -> str:
    text = " ".join(text.split())
    return text if len(text) <= DESCRIPTION_WIDTH else text[: DESCRIPTION_WIDTH - 1] + "…"


def link(text: str, target: Path, corpus: Path) -> str:
    return f"[{text}]({Path(os.path.relpath(target, corpus)).as_posix()})"


def lookup(config: dict[str, Any], dotted: str) -> Any:
    value: Any = config
    for key in dotted.split("."):
        if not isinstance(value, dict) or key not in value:
            return None
        value = value[key]
    return value


def format_value(value: Any) -> str:
    if isinstance(value, list):
        return ", ".join(f"`{v}`" for v in value) if value else "none"
    return f"`{value}`"
