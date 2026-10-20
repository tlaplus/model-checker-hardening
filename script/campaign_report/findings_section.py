"""Section 2 of the report: verdicts, triage labels, known defects, export problems."""

from __future__ import annotations

from collections.abc import Sequence
from pathlib import Path

from . import fmt
from .inputs import ReportInputs
from .stages import Stage
from .triage import LabelClass


SHORT_HASH = 8
# Triaged entries listed individually per directory, for the labels that need a look.
LISTED_LABELS = (LabelClass.NEW, LabelClass.STATE_COUNT)
LISTED_PER_DIRECTORY = 25
# References listed per known-defect signature before the rest are counted.
MAX_REFERENCES = 3


def verdicts(inputs: ReportInputs) -> list[str]:
    queries = inputs.queries
    aggregated = queries.aggregated()
    total = sum(aggregated.values())
    parts = ["### 2.1. Verdicts"]
    if not total:
        return parts + ["No input reached the aggregator."]
    parts.append(
        f"{fmt.count(total)} inputs were aggregated: the checkers agree on "
        f"{fmt.count(aggregated.get('pass', 0))} and disagree on {fmt.count(aggregated.get('fail', 0))} "
        f"({fmt.share(aggregated.get('fail', 0), total)})."
    )
    headers = ("aggregator", *(s.label for s in queries.checkers), "inputs")
    parts.append(fmt.table(headers, [(*row[:-1], fmt.count(row[-1])) for row in queries.verdict_pairs()]))
    return parts


def triage(inputs: ReportInputs) -> list[str]:
    queries = inputs.queries
    parts = [
        "### 2.2. Crashes and disagreements",
        "`script/triager.py` labels each crash and each disagreement with the finding or "
        "conformance document whose signature it matches, or with a class:",
        fmt.table(("class", "meaning"), [(c.value, c.meaning) for c in LabelClass]),
    ]
    counts = queries.triage_counts()
    if not counts:
        return parts + ["The corpus has no crashes and no disagreements."]
    corpus = inputs.settings.path
    rows = []
    for directory, label, entries in counts:
        document = inputs.triage.document(label)
        rows.append(
            (f"`{directory}`", fmt.link(label, document, corpus) if document else f"`{label}`",
             LabelClass.of(label).value, fmt.count(entries))
        )
    parts.append(fmt.table(("directory", "label", "class", "entries"), rows))
    listed = queries.triaged_entries(
        tuple(c.triage_label for c in LISTED_LABELS), LISTED_PER_DIRECTORY
    )
    if listed:
        parts.append(
            f"Entries labelled {' or '.join(f'`{c.triage_label}`' for c in LISTED_LABELS)}, "
            f"at most {LISTED_PER_DIRECTORY} per directory:"
        )
        parts.append(
            fmt.table(
                ("entry", "directory", "label", "results"),
                [(f"`{e.hash[:SHORT_HASH]}`", f"`{e.directory}`", e.label, _results(e.results))
                 for e in listed],
            )
        )
    return parts


def known_defects(inputs: ReportInputs) -> list[str]:
    matched = inputs.stats.generator.known_defects if inputs.stats else {}
    samples = inputs.queries.known_defect_samples()
    names = sorted(set(matched) | set(samples), key=lambda n: (-matched.get(n, 0), n))
    parts = ["### 2.3. Known defects"]
    if not names:
        return parts + ["No candidate matched a known-defect signature."]
    corpus = inputs.settings.path
    rows = []
    for name in names:
        signature = inputs.settings.signatures.get(name)
        references = _references(signature.references, corpus) if signature else ""
        description = fmt.clip(signature.description) if signature else ""
        rows.append((f"`{name}`", fmt.count(matched.get(name, 0)), fmt.count(samples.get(name, 0)), references, description))
    parts.append(
        "Candidates that matched a signature were filtered before checking (ADR 0006). *Matched* "
        "counts every such candidate; *stored* counts the samples kept in `00-known-defects`."
    )
    parts.append(fmt.table(("signature", "matched", "stored", "references", "description"), rows))
    return parts


def data_quality(inputs: ReportInputs) -> list[str]:
    queries = inputs.queries
    unreadable = queries.unreadable()
    errors = queries.replay_errors()
    parts = ["### 2.4. Export problems"]
    if not unreadable and not errors:
        return parts + ["Every entry decoded and replayed."]
    if unreadable:
        parts.append(fmt.table(("directory", "unreadable entries"), [(f"`{d}`", fmt.count(n)) for d, n in unreadable]))
    if errors:
        parts.append(fmt.table(("replay error", "entries"), [(fmt.clip(e), fmt.count(n)) for e, n in errors]))
    return parts


def _references(references: Sequence[Path], corpus: Path) -> str:
    shown = ", ".join(fmt.link(r.name, r, corpus) for r in references[:MAX_REFERENCES])
    rest = len(references) - MAX_REFERENCES
    return shown + (f" and {rest} more" if rest > 0 else "")


def _results(results: Sequence[tuple[str, str, int | None, str | None]]) -> str:
    labels = {stage.value: stage.label for stage in Stage}
    shown = []
    for stage, verdict, code, detail in results:
        text = f"{labels.get(stage, stage)}: {verdict}"
        if code is not None:
            text += f" {code}"
        if detail:
            text += f" ({detail})"
        shown.append(text)
    return "; ".join(shown)
