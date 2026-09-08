# Source-reduction verification

[`source_size.py`](source_size.py) counts production source in the working tree,
including untracked production helpers, or at a Git revision. Tests,
fixtures, documentation and build configuration are excluded. The counter itself
remains excluded verification tooling after its move to `script/`. Audit newly added
paths separately if production code is ever introduced outside `src/main`, `bin`
and `script`; changing the layout must not hide code from the budget.

Run these commands from the repository root:

```sh
python3 -m venv /tmp/fuzztla-verification
/tmp/fuzztla-verification/bin/pip install -r script/requirements.txt
/tmp/fuzztla-verification/bin/python script/source_size.py --revision a3218a2
/tmp/fuzztla-verification/bin/python script/source_size.py
/tmp/fuzztla-verification/bin/python -m unittest discover -s script -p 'test_*.py'
mvn --offline --batch-mode --no-transfer-progress verify
```

Offline Maven verification assumes the dependencies and pinned Apalache release
are already cached. It keeps those snapshot dependencies stable between builds.
An uncached environment must first resolve the dependencies.
