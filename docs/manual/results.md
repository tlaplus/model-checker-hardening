# Results

FuzzTLA campaigns have found defects in TLC and Apalache, and differences between
them that are intended or depend on undefined expressions. Both are documented in the
repository:

- [Filed findings](../../findings/): confirmed defects in TLC, Apalache and SANY,
  grouped by subsystem, each with a reproduction.
- [Conformance reports](../../conformance/): differences between TLC and Apalache that
  a corpus confirmed. Each report gives the share of disagreements it explains, a
  classification, and a minimal TLA<sup>+</sup> module.

The [triager](inspecting.md#triage) attributes the entries of your corpus to these
documents. Entries it reports as `NEW` may be new findings.
