"""Everything a report section reads."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from .queries import CorpusQueries
from .sources import CorpusSettings, Provenance, WorkflowStats
from .triage import Triage


@dataclass(frozen=True)
class ReportInputs:
    settings: CorpusSettings
    stats: WorkflowStats | None
    provenance: Provenance
    triage: Triage
    queries: CorpusQueries
    generated_at: datetime
    config_copy: str  # path of the copied config.toml, relative to the corpus
