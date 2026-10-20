#!/usr/bin/env python3
"""Write a campaign report of a FuzzTLA corpus to <corpus>/report.md.

Images and the exported database go to <corpus>/.report. See
docs/manual/campaign-report.md.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence

from campaign_report import plots, sources
from campaign_report.errors import ReportError
from campaign_report.markdown import ReportInputs, write_report
from campaign_report.queries import CorpusQueries
from campaign_report.triage import load_triage

REPORT_FILE = "report.md"
REPORT_DIRECTORY = ".report"
DATABASE_FILE = "corpus.sqlite"


def parse_arguments(arguments: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Write a campaign report of a FuzzTLA corpus.")
    parser.add_argument("corpus", type=Path, help="FuzzTLA corpus directory")
    parser.add_argument(
        "--db",
        type=Path,
        help="use this exported database instead of running fuzztla export-db",
    )
    parser.add_argument(
        "--no-lock",
        action="store_true",
        help="export without the corpus lock, while a run uses the corpus",
    )
    parser.add_argument("--max-cpus", type=int, help="threads of fuzztla export-db")
    parser.add_argument(
        "--fuzztla",
        type=Path,
        default=sources.REPOSITORY / "bin" / "fuzztla",
        help="fuzztla launcher (default: %(default)s)",
    )
    return parser.parse_args(arguments)


def generate(options: argparse.Namespace) -> Path:
    corpus = options.corpus.resolve()
    if not (corpus / sources.CONFIG_FILE).is_file():
        raise ReportError(f"not a corpus directory (no {sources.CONFIG_FILE}): {corpus}")
    report_directory = corpus / REPORT_DIRECTORY
    report_directory.mkdir(exist_ok=True)
    database = options.db
    if database is None:
        database = report_directory / DATABASE_FILE
        export = sources.ExportOptions(options.fuzztla, not options.no_lock, options.max_cpus)
        print(sources.export_database(corpus, database, export), file=sys.stderr)

    connection = sources.open_database(database)
    try:
        settings = sources.load_settings(corpus)
        stats = sources.load_stats(corpus)
        triage = load_triage(connection, corpus, sources.REPOSITORY)
        queries = CorpusQueries(connection, settings.checkers)
        config_copy = report_directory / sources.CONFIG_FILE
        shutil.copyfile(settings.config_path, config_copy)
        figures = plots.render_all(plots.PlotInputs(queries, settings.config, stats), report_directory)
        inputs = ReportInputs(
            settings=settings,
            stats=stats,
            provenance=sources.load_provenance(connection),
            triage=triage,
            queries=queries,
            generated_at=datetime.now(timezone.utc),
            config_copy=f"{REPORT_DIRECTORY}/{sources.CONFIG_FILE}",
        )
        report = corpus / REPORT_FILE
        write_report(report, inputs, figures)
        return report
    finally:
        connection.close()


def main(arguments: Sequence[str] | None = None) -> int:
    try:
        report = generate(parse_arguments(arguments))
    except ReportError as error:
        print(f"report: {error}", file=sys.stderr)
        return 1
    print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
