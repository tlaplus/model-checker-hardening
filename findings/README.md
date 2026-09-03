# Findings

This directory contains confirmed defects found by the testing framework. Each
finding file is the source of truth for one GitHub issue. Its globally unique
basename is also the stable synchronization ID, for example
`apalache-printer-001.md`.

Every finding starts with this metadata:

```yaml
---
state: open
---
```

The filename is lowercase kebab case with a letter prefix and a three-digit
number. It is globally unique and permanent. Move the file without renaming it,
and do not reuse its name for a different finding. The `state` is either `open`
or `closed` and controls the corresponding GitHub issue state.

After the metadata, the file has exactly one H1 title and one `## Summary`
section. The title and Summary are copied to the issue. The issue links back to
the complete report; the remaining sections stay in this repository.

## New finding template

Create the file as `findings/<subsystem>/<subsystem>-NNN.md`, choosing the next
unused globally unique number for that subsystem. Start from this template:

````markdown
---
state: open
---

# Concise defect title

## Summary

Describe the failure, the affected component, and the observed tool version.

## Reproduction

Provide a minimal input and the exact command needed to reproduce the failure.

```text
reproduction output
```

## Expected behavior

Describe the expected result.

## Impact

Describe the affected users or automated workflows and the severity.
````

The synchronization workflow runs after relevant changes reach `main`. It also
supports manual dispatch. It creates or updates issues, applies the `finding`
label, and closes issues whose finding IDs have been removed. Changes to a
managed issue's title, summary, or state must be made in its Markdown source.

Validate the complete catalog and run the synchronization tests locally with:

```sh
node .github/scripts/sync-findings.cjs
node --test .github/scripts/sync-findings.test.cjs
```
