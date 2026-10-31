"""The summary and section 1 of the report: setup, time, stages, generator, entries."""

from __future__ import annotations

from datetime import timedelta

from . import fmt
from .findings_section import LISTED_LABELS
from .inputs import ReportInputs
from .queries import AGGREGATOR_FAIL_DIRECTORY
from .sources import NOT_REPRODUCIBLE_FILE, CorpusSettings
from .stages import Outcome, Stage
from .style import VERDICTS
from .triage import LabelClass


# The keys of the [seeds] table, in the order the setup table shows them (ADR 0026).
SEED_KEYS = ("generator", "tlc_fp", "tlc_seed")

# Configuration values the setup table shows, as dotted paths into config.toml.
CONFIG_KEYS = (
    "generator.kind",
    "generator.max_nodes",
    "generator.max_expression_depth",
    "generator.max_variables",
    "generator.max_actions",
    "generator.max_steps",
    "generator.integer_literals",
    "workflow.max_entries",
    "workflow.checkers",
    "workflow.parser.timeout_sec",
    "workflow.tlc.timeout_sec",
    "workflow.tlc.workers",
    "workflow.tlcn.timeout_sec",
    "workflow.tlcn.workers",
    "workflow.apalache.timeout_sec",
    "workflow.apalache.workers",
    "workflow.inputs.known_defects",
    "pbt.max_input_bytes",
    "pbt.richness_cohorts",
    "mutator.generation_size",
    "mutator.select_fraction",
    "mutator.feedback_ratio",
    "mutator.max_edits",
    "mutator.cell_capacity",
)


def summary(inputs: ReportInputs) -> str:
    queries = inputs.queries
    corpus = inputs.settings.path
    origins = queries.origins()
    aggregated = queries.aggregated()
    aggregated_total = sum(aggregated.values())
    sessions = queries.sessions()
    span = fmt.duration((sessions[-1].end - sessions[0].start).total_seconds()) if sessions else "–"
    selected = dict(queries.directory_counts()).get(Outcome.SELECTED.directories[0], 0)

    disagreements: dict[str, set[str]] = {}
    crashes: dict[str, set[str]] = {}
    documents: dict[str, set[str]] = {}
    for entry_hash, directory, label in queries.triage_labels():
        kind = disagreements if directory == AGGREGATOR_FAIL_DIRECTORY else crashes
        kind.setdefault(LabelClass.of(label).value, set()).add(entry_hash)
        if LabelClass.of(label) is LabelClass.DOCUMENTED:
            documents.setdefault(label, set()).add(entry_hash)
    to_investigate = [
        f"{fmt.count(len(disagreements.get(c.value, set()) | crashes.get(c.value, set())))} "
        f"`{c.triage_label}`"
        for c in LISTED_LABELS
    ]
    matched = inputs.stats.generator.known_defects if inputs.stats else {}
    lines = [
        f"- **Inputs:** {fmt.count(sum(origins.values()))} admitted ("
        + ", ".join(f"{fmt.count(n)} {origin}" for origin, n in origins.items())
        + f") over {span} of wall-clock time; {fmt.count(aggregated_total)} aggregated; "
        f"{fmt.count(selected)} selected into `04quality-pass`.",
        f"- **Disagreements:** {fmt.count(aggregated.get('fail', 0))} "
        f"({fmt.share(aggregated.get('fail', 0), aggregated_total)} of aggregated inputs)"
        + _by_class(disagreements),
        f"- **Crashed inputs:** {fmt.count(len(set().union(*crashes.values())) if crashes else 0)}"
        + _by_class(crashes),
        f"- **Documented defects reproduced:** {len(documents)} document(s)"
        + (": " + ", ".join(
            f"{fmt.link(name, inputs.triage.document(name), corpus) if inputs.triage.document(name) else name}"
            f" ({fmt.count(len(hashes))})"
            for name, hashes in sorted(documents.items(), key=lambda item: (-len(item[1]), item[0]))
        ) if documents else "."),
        f"- **To investigate:** {', '.join(to_investigate)} (section 2.2).",
        f"- **Filtered before checking:** {fmt.count(sum(matched.values()))} candidates matched "
        f"{len(matched)} known-defect signature(s) (section 2.3).",
    ]
    return "\n".join(lines)


def _by_class(classes: dict[str, set[str]]) -> str:
    if not classes:
        return "."
    return ": " + ", ".join(
        f"{fmt.count(len(classes[c.value]))} {c.value}" for c in LabelClass if c.value in classes
    ) + "."


def setup(inputs: ReportInputs) -> list[str]:
    provenance = inputs.provenance
    commit = provenance.fuzztla_commit or "unknown"
    if provenance.dirty:
        commit += " (uncommitted changes)"
    checkers = inputs.settings.checkers
    rows = [
        ("technique", provenance.technique),
        ("checkers", ", ".join(checkers) or "unknown"),
        ("FuzzTLA", f"{provenance.fuzztla_version}, commit {commit}"),
    ]
    if {Stage.TLC.value, Stage.TLCN.value} & set(checkers):
        rows.append(("TLC", f"tla2tools {provenance.tla2tools_version or 'unknown'}"))
    if Stage.APALACHE.value in checkers:
        rows.append(("Apalache", provenance.apalache_version or "unknown"))
    rows.extend(_reproducibility(inputs.settings))
    unused = {
        f"workflow.{stage.workflow_table}."
        for stage in Stage
        if stage.workflow_table and stage is not Stage.PARSER
        and stage.value not in inputs.settings.checkers
    }
    config = [
        (key, fmt.format_value(value))
        for key in CONFIG_KEYS
        if not any(key.startswith(prefix) for prefix in unused)
        and (value := fmt.lookup(inputs.settings.config, key)) is not None
    ]
    return [
        "### 1.1. Setup",
        fmt.table(("property", "value"), rows),
        "The corpus records no tool versions. The versions above are those of the checkout that "
        "generated this report, which need not be those of the run.",
        f"Selected configuration values; the full file is [`config.toml`]({inputs.config_copy}).",
        fmt.table(("key", "value"), config),
    ]


def _reproducibility(settings: CorpusSettings) -> list[tuple[str, str]]:
    """The seeds of the corpus, and whether a run continued it after it was no longer fresh."""
    seeds = settings.config.get("seeds", {})
    recorded = ", ".join(f"{key} `{seeds[key]}`" for key in SEED_KEYS if key in seeds)
    continued = settings.continued_runs
    return [
        ("seeds", recorded or "not recorded"),
        ("continued runs", f"{continued}, so the corpus is not reproducible (`{NOT_REPRODUCIBLE_FILE}`)"
         if continued else "none: every run started from a fresh corpus"),
    ]


def time(inputs: ReportInputs) -> list[str]:
    sessions = inputs.queries.sessions()
    parts = ["### 1.2. Time"]
    if sessions:
        span = sessions[-1].end - sessions[0].start
        active = sum((s.duration for s in sessions), start=timedelta())
        parts.append(
            f"Stage records span {fmt.duration(span.total_seconds())} of wall-clock time, from "
            f"{sessions[0].start:%Y-%m-%d %H:%M} to {sessions[-1].end:%Y-%m-%d %H:%M} UTC, in "
            f"{len(sessions)} session(s) separated by pauses of more than 15 minutes "
            f"({fmt.duration(active.total_seconds())} active)."
        )
        parts.append(
            fmt.table(
                ("session", "start (UTC)", "end (UTC)", "duration", "stage records"),
                [
                    (i + 1, f"{s.start:%Y-%m-%d %H:%M}", f"{s.end:%Y-%m-%d %H:%M}",
                     fmt.duration(s.duration.total_seconds()), fmt.count(s.stage_records))
                    for i, s in enumerate(sessions)
                ],
            )
        )
    stats = inputs.stats
    if stats is None:
        parts.append("The corpus has no `.workflow-stats.cbor`, so cumulative run times are unknown.")
    else:
        parts.append(
            f"`.workflow-stats.cbor` records {fmt.duration(stats.total_ns / 1e9)} of run time over all "
            "controlled exits of `fuzztla run`, and "
            f"{fmt.duration(stats.generator_ns / 1e9)} of generator worker time. A killed run "
            "contributes nothing. Stage worker times are in the next table; they add up the time "
            "of concurrent workers, so they can exceed the run time."
        )
    return parts


def stages(inputs: ReportInputs) -> list[str]:
    queries = inputs.queries
    timeouts = {
        directory: entries
        for directory, label, entries in queries.triage_counts()
        if LabelClass.of(label) is LabelClass.TIMEOUT
    }
    rows = []
    for stage in queries.stages():
        summary = queries.stage_summary(stage)
        worker_ns = inputs.stats.stage_ns.get(stage.value) if inputs.stats else None
        rows.append(
            (
                stage.label,
                fmt.count(summary.records),
                *(fmt.count(summary.verdicts.get(verdict, 0)) for verdict in VERDICTS),
                fmt.seconds(summary.percentile_ms(50)),
                fmt.seconds(summary.percentile_ms(95)),
                fmt.seconds(summary.percentile_ms(100)),
                fmt.count(timeouts.get(stage.crash_kind.value, 0)) if stage.crash_kind else "",
                fmt.duration(worker_ns / 1e9) if worker_ns is not None else "",
            )
        )
    return [
        "### 1.3. Stages",
        "One record per input and stage. Durations are whole seconds; timeouts are crashes the "
        "triager labels `TIMEOUT`.",
        fmt.table(
            ("stage", "records", *VERDICTS, "median", "p95", "max", "timeouts", "worker time"),
            rows,
        ),
    ]


def generator(inputs: ReportInputs) -> list[str]:
    parts = ["### 1.4. Generator"]
    origins = inputs.queries.origins()
    admitted = sum(origins.values())
    stats = inputs.stats
    if stats is not None:
        generator = stats.generator
        known = sum(generator.known_defects.values())
        rows = [
            ("attempts", fmt.count(generator.attempts)),
            ("rejected by the generator", fmt.count(generator.rejected)),
            ("rejected for low richness", fmt.count(generator.richness_rejected)),
            ("duplicates of stored inputs", fmt.count(generator.duplicates)),
            ("mutants identical to their parent (clones)", fmt.count(generator.clones)),
            ("matched a known-defect signature", fmt.count(known)),
            ("admitted to the corpus", fmt.count(admitted)),
        ]
        parts.append(fmt.table(("candidates", "count"), rows))
        parts.append(
            f"Richness of {fmt.count(generator.richness_samples)} admissions: min "
            f"{generator.minimum_richness:,.1f}, mean {generator.average_richness:,.1f}, max "
            f"{generator.maximum_richness:,.1f}."
        )
    parts.append(fmt.table(("origin", "admitted inputs"), [(o, fmt.count(n)) for o, n in origins.items()]))
    return parts


def entries(inputs: ReportInputs) -> list[str]:
    queries = inputs.queries
    generations, latest = queries.generations()
    counts = queries.directory_counts()
    return [
        "### 1.5. Entries",
        f"{fmt.count(queries.distinct_inputs())} distinct inputs in {fmt.count(sum(n for _, n in counts))} "
        f"entry files; {generations} generation(s)"
        + (f", the latest {latest}." if latest is not None else "."),
        fmt.table(("directory", "entries"), [(f"`{d}`", fmt.count(n)) for d, n in counts]),
    ]
