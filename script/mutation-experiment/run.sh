#!/bin/sh
# Exploration vs. exploitation experiment for generational mutation (ADR 0010).
# Runs one corpus per arm, sequentially, then exports each to SQLite.
#
# Usage: script/mutation-experiment/run.sh OUT_DIR [MAX_CPUS] [SEED] [GENERATIONS]
# Run from the repository root after `make package`.

set -eu

out=${1:?usage: run.sh OUT_DIR [MAX_CPUS] [SEED] [GENERATIONS]}
cpus=${2:-$(nproc)}
seed=${3:-2028}
generations=${4:-30}
generation_size=1000
entries=$((generations * generation_size))

# name feedback_ratio select_fraction
arms='e0-pbt 0 0.25
e1-exploit 0.75 0.05
e2-balanced 0.5 0.25
e3-explore 0.25 0.25
e4-all-parents 0.5 1.0'

mkdir -p "$out"
{
    echo "fuzztla commit: $(git rev-parse HEAD)"
    git diff --quiet HEAD || echo "fuzztla working tree: dirty"
    echo "cpus=$cpus seed=$seed generations=$generations generation_size=$generation_size"
    for jar in target/fuzztla.jar target/apalache.jar; do
        echo "== $jar"
        unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null | grep -Ei 'version|revision|commit|build' || true
    done
} > "$out/manifest.txt"

echo "$arms" | while read -r name feedback select; do
    corpus="$out/$name"
    if [ -f "$corpus/corpus.sqlite" ]; then
        echo "skip $name: already exported"
        continue
    fi
    if [ ! -f "$corpus/config.toml" ]; then
        bin/fuzztla init --corpus "$corpus"
        sed -i \
            -e "s/^max_entries = .*/max_entries = $entries/" \
            -e "s/^generation_size = .*/generation_size = $generation_size/" \
            -e "s/^feedback_ratio = .*/feedback_ratio = $feedback/" \
            -e "s/^select_fraction = .*/select_fraction = $select/" \
            "$corpus/config.toml"
    fi
    echo "run $name (feedback_ratio=$feedback, select_fraction=$select)"
    # A rerun resumes an interrupted corpus at its highest generation.
    bin/fuzztla run --corpus "$corpus" --how=pbt --seed="$seed" --max-cpus="$cpus" \
        >> "$corpus.log" 2>&1
    python3 script/triager.py "$corpus" >> "$corpus.log" 2>&1
    bin/fuzztla export-db --corpus "$corpus" --max-cpus="$cpus"
done

echo "done; analyze with: script/mutation-experiment/analyze.sh $out"
