# Repository instructions

This project is under active development. All input and output formats may change
without notice, backward compatibility, and legacy support.

## Architecture

Read the relevant documents in [`docs/architecture`](docs/architecture/) before
planning or implementing architectural changes:

- [`fuzzing-workflows.md`](docs/architecture/fuzzing-workflows.md) defines the
  overall fuzzing pipeline and corpus flow.
- [`ir-generators.md`](docs/architecture/ir-generators.md) defines the design
  constraints for `io.github.tlaplus.hardening.gen` and its `engine` package.

Follow the documented architecture when modifying these areas. If a change
intentionally revises the architecture, ask the user, then update the corresponding
document in the same change and state the deviation explicitly.

## Technical writing

Write architecture and design documents for senior software engineers and
computer scientists. Keep the text concise and to the point. State purpose and
scope early, use precise terms and direct sentences, and distinguish implemented
behavior from proposals.

## Coding rules

**Represent a concept once.** If `parser`, `tlc` and `apalache` (or any other fixed
set of alternatives) appear as parallel fields, accessors, or copy-pasted branches,
key a map or an enum by the concept instead. Per-alternative facts belong on the
enum constant, not in a `switch` far from it. Adding a fourth alternative must be a
local change.

**No copied helpers.** Before writing a small utility, search for it. One home each,
in a leaf package everything may depend on. Two records that differ only in a
message or a default are one record.

**Write the third copy as a shared abstraction.** Two similar bodies may stay; the
third is a template method, a shared record, or a common loop. Duplicated logic
drifts.

**One reason to change per class.** Layout, config parsing, locking, persistence,
transactions and recovery are separate types even behind one façade. Treat >400
lines in a class, >60 in a method, or >5 parameters as a design signal: group
related parameters into a record rather than adding another positional argument.

**Type the boundaries.** Do not pass an enum as a `String` across an internal API,
and do not widen an enum's visibility with a string instead. Repeated field-name
literals get one named constant. Prefer encodings that fail at compile time over
hand-maintained arithmetic (for example CBOR definite-length map sizes) — if such
arithmetic is unavoidable, cover it with a round-trip test.

**Pin implicit contracts with tests.** Where declaration order, catalog order, or
enum ordinals are part of a stored format or wire encoding, a comment is not
enough; add a test that fails on reorder.

**Keep the layering.** Dependencies run `checker`, `gen` → `config` → `corpus` →
`workflow` → `cli`. No upward imports. Validation and parsing policy do not belong
in the storage layer; storage returns bytes and lets the caller interpret them.

**Keep CLI classes thin.** Rendering and formatting live in testable types that do
not depend on picocli; the command wires them together.

**Do not poll where you can signal.** Cancellation and shutdown paths propagate an
explicit signal rather than waking on a timer.

**Document lifecycle differences in the interface.** If implementations of an
abstraction differ in a way callers depend on (for example per-input versus
long-lived worker processes), state it on the interface or remove the dependency.
