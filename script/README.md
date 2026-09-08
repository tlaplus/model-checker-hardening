# Source-reduction verification

[`source_size.py`](source_size.py) freezes the measurement used by
[`source-reduction.md`](../docs/plans/source-reduction.md). It counts the
working tree, including untracked production helpers, or a Git revision. Tests,
fixtures, documentation and build configuration are excluded. The counter itself
remains excluded verification tooling after its move to `script/`. Audit newly added
paths separately if production code is ever introduced outside `src/main`, `bin`
and `script`; changing the layout must not hide code from the budget.

```sh
python3 -m venv /tmp/fuzztla-verification
/tmp/fuzztla-verification/bin/pip install -r script/requirements.txt
/tmp/fuzztla-verification/bin/python script/source_size.py --revision a3218a2
/tmp/fuzztla-verification/bin/python script/source_size.py
/tmp/fuzztla-verification/bin/python -m unittest discover -s script -p 'test_*.py'
mvn --offline --batch-mode --no-transfer-progress verify
```

Offline Maven verification assumes the dependencies and pinned Apalache release
are already cached. It avoids updating snapshot dependencies between the baseline
and candidate. An uncached environment must first resolve those dependencies.

## Cross-revision fixtures

The decoder, configuration and codec replay tests have explicit recording entry
points. Tests only compare with their checked-in fixtures; they never rewrite
expectations. To reproduce the recordings, compile `a3218a2` separately with the
same dependency JARs, compile the current test drivers, and put **baseline
production classes first** on the recording JVM's classpath:

```sh
CP="$BASELINE_CLASSES:target/test-classes:$DEPENDENCY_CLASSPATH"
java -cp "$CP" io.github.tlaplus.hardening.workflow.input.DecoderReplayTest /tmp/decoder-replay.txt
java -cp "$CP" io.github.tlaplus.hardening.config.ConfigReplayTest /tmp/config-replay.txt
java -cp "$CP" io.github.tlaplus.hardening.corpus.CorpusCodecReplayTest /tmp/codec-replay.txt
cmp /tmp/decoder-replay.txt src/test/resources/gen/decoder-replay.txt
cmp /tmp/config-replay.txt src/test/resources/config/replay.txt
cmp /tmp/codec-replay.txt src/test/resources/corpus/codec-replay.txt
```

Run from the repository root. The decoder replay prepares the custom-library
fixture, so the pinned `apalache.jar` must also be beside the baseline classes
directory, as expected by `ApalacheDistribution`.

The triager fixture records ordered identities, codes, alternative boundaries,
regex text and flags from the baseline module. `script/test_triager_catalog.py`
compares every entry, independently of the new declaration helpers.
