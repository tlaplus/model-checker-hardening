# Repository instructions

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
