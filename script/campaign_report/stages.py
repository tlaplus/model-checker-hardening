"""Workflow stages and the outcome groups of corpus entries."""

from __future__ import annotations

from enum import Enum
from typing import Any

from triager import CrashKind


class Stage(Enum):
    """A workflow stage, keyed by its name in the database's `stage.stage` column.

    Declaration order is pipeline order, as in Java's `corpus.CorpusStage`; tables
    and plots list stages in this order.
    """

    PARSER = ("parser", "Parser", "parser", CrashKind.PARSER)
    TLC = ("tlc", "TLC", "tlc", CrashKind.TLC)
    TLCN = ("tlcn", "TLC-n", "tlcn", CrashKind.TLCN)
    APALACHE = ("apalache", "Apalache", "apalache", CrashKind.APALACHE)
    AGGREGATOR = ("aggregator", "Aggregator", None, None)
    QUALITY = ("quality", "Quality gate", None, None)

    def __new__(
        cls,
        name: str,
        label: str,
        workflow_table: str | None,
        crash_kind: CrashKind | None,
    ) -> Stage:
        member = object.__new__(cls)
        member._value_ = name
        member.label = label
        # The `[workflow.<table>]` of config.toml with this stage's timeout.
        member.workflow_table = workflow_table
        member.crash_kind = crash_kind
        return member

    def timeout_sec(self, config: dict[str, Any]) -> int | None:
        if self.workflow_table is None:
            return None
        table = config.get("workflow", {}).get(self.workflow_table, {})
        timeout = table.get("timeout_sec")
        return timeout if isinstance(timeout, int) else None

    @property
    def checks(self) -> bool:
        """Whether the stage runs a tool on the input (and so can crash)."""
        return self.crash_kind is not None


# Stages whose exploration metrics describe an input's state space, in order of
# preference: TLC with one worker, else TLC-n (ADR 0018). Apalache records only
# trace lengths.
EXPLORERS = (Stage.TLC, Stage.TLCN)


class Outcome(Enum):
    """Mutually exclusive groups of checked entries, by the directory they end in."""

    SELECTED = ("selected", "04quality-pass", ("04quality-pass",))
    AGREEING = (
        "agreeing",
        "agreeing, not selected",
        ("03aggregator-pass", "04quality-fail"),
    )
    DISAGREEING = ("disagreeing", "disagreeing", ("03aggregator-fail",))
    CRASHED = (
        "crashed",
        "crashed",
        tuple(kind.value for kind in CrashKind),
    )

    def __new__(cls, key: str, label: str, directories: tuple[str, ...]) -> Outcome:
        member = object.__new__(cls)
        member._value_ = key
        member.label = label
        member.directories = directories
        return member

    @classmethod
    def sql_case(cls, column: str) -> str:
        """An SQL expression mapping a directory column to an outcome key, else NULL."""
        return cls._sql_case(column, lambda rank, outcome: f"'{outcome.value}'")

    @classmethod
    def sql_rank(cls, column: str) -> str:
        """An SQL expression mapping a directory column to the outcome's declaration index."""
        return cls._sql_case(column, lambda rank, outcome: str(rank))

    @classmethod
    def _sql_case(cls, column: str, result) -> str:
        arms = " ".join(
            f"WHEN {column} IN ({', '.join(repr(d) for d in outcome.directories)}) "
            f"THEN {result(rank, outcome)}"
            for rank, outcome in enumerate(cls)
        )
        return f"CASE {arms} END"


# Outcomes on which the checkers agree, selected first.
AGREEING = (Outcome.SELECTED, Outcome.AGREEING)
