#!/bin/sh
# Prints the per-arm report of the mutation experiment.
# Usage: script/mutation-experiment/analyze.sh OUT_DIR > report.txt

set -eu

out=${1:?usage: analyze.sh OUT_DIR}
sql="$(dirname "$0")/analyze.sql"

cat "$out/manifest.txt"
for db in "$out"/*/corpus.sqlite; do
    arm=$(dirname "$db")
    echo
    echo "######## $(basename "$arm")"
    grep -E '^(feedback_ratio|select_fraction|generation_size) ' "$arm/config.toml"
    work=$(mktemp)
    cp "$db" "$work"
    sqlite3 "$work" ".import --csv $arm/03aggregator-fail-triage.csv triage"
    sqlite3 -header -column "$work" < "$sql"
    rm -f "$work"
done
