# 0012: Integer terminal and boundary literals

**Authors:** Igor Konnov and Claude

**Status:** Accepted

**Date:** 2026-09-17

## Context

[ADR 0011][adr-0011] gave collections a base size but left the closed `Int`
terminal at `0` and integer literals as byte payloads. Both shape what the
checkers evaluate:

- **The terminal.** A starved integer request decodes `0`. That is never a valid
  sequence or tuple index, misses base-size domains such as `{1, 2, 3}`, and
  turns a starved divisor into a literal zero, which the `modulo-by-literal-zero`
  and `division-by-literal-zero` signatures quarantine.
- **Literals.** `IntegerExprGenFactory.integerLiteral` decodes a two's-complement
  payload of up to `max_integer_bytes` with continuation markers. Half of random
  literals are `0`; the rest are uniform bytes or multi-byte values, often outside
  TLC's signed 32-bit range.

Hypothesis 6.155 (`hypothesis/internal/conjecture/providers.py`) draws integers
from a distribution weighted towards small values and, with probability 0.05
(`_maybe_draw_constant`), from a list of powers of two and ten, factorials and
primorials, each ±1 and negated, plus constants of the code under test. For the
checkers, the boundaries that matter are those of TLC's `int` values and of
floor division with negative operands.

## Decision

### Integer terminal

The closed `Int` terminal is `integer_base`, default `1`.

### Literal modes

`integer_literals` selects how a literal decodes. Markers are Booleans read before
the value; exhausted input reads even markers.

| Mode | Markers | Value |
| --- | --- | --- |
| `wide` | none | two's-complement payload, as before |
| `small` | even / odd | `integer_base ± integer_literal_spread` / payload |
| `boundary` (default) | even / odd–even / odd–odd | small / `BoundaryInteger` / payload |

A small literal uses the rotated one-byte offset of [ADR 0011][adr-0011]
(`BasicGenerators.offset`). A boundary literal indexes `BoundaryInteger` with one
byte:

`-1, 0, 1, 2, 15, 16, 255, 256, 32767, 32768, 65536, 46340, 46341, 2^30,
2^31 − 1, −2^31, 2^31, −2^31 − 1, 2^63 − 1`

`46340² < 2^31 − 1 < 46341²`. The last three lie outside TLC's range, which is the
known capability difference [integer-outside-tlc-range][tlc-range]; the new
signature `integer-literal-outside-tlc-range` quarantines them as literals.
Overflow reached by arithmetic stays unquarantined. The declaration order is part
of the byte encoding and `IntegerLiteralDecodingTest` pins it.

### Configuration

| Key | Default | Meaning |
| --- | ---: | --- |
| `integer_base` | 1 | Closed integer terminal and centre of small literals. |
| `integer_literal_spread` | 4 | Byte offset range of small literals, `0..127`. |
| `integer_literals` | `"boundary"` | `"wide"`, `"small"` or `"boundary"`. |
| `max_integer_bytes` | 16 | Maximum payload of a wide literal (unchanged). |

`ExpressionLimits` groups the four in `IntegerLimits`.

## Evaluation

Five `module` corpora of 1,000 PBT entries, seed 11, `feedback_ratio = 0`,
`collection_base_size = 3`, 30 s checker timeouts. fuzztla: `6d8747d` plus this
change; TLC: tla2tools `142d0ba`; Apalache: release 0.62.2. "Quarantined" counts
candidates matched by a known-defect signature, from the run statistics.

| Measure | `0`, wide | `1`, wide | `0`, small | `1`, small | `1`, boundary |
| --- | ---: | ---: | ---: | ---: | ---: |
| Candidates quarantined | 570 | 136 | 570 | 142 | 165 |
| TLC fails in `Init` | 74.3% | 75.9% | 74.6% | 75.8% | 75.2% |
| `maxCardinality ≥ 3` | 5.8% | 9.0% | 6.1% | 9.5% | 9.5% |
| `maxStateNodes ≥ 10` | 6.1% | 10.0% | 6.5% | 10.1% | 10.3% |
| `projectedStates ≥ 2` | 3.1% | 2.7% | 3.4% | 2.7% | 2.6% |
| TLC: index 0 of a tuple | 105 | 46 | 109 | 43 | 42 |
| TLC: number too big | 8 | 22 | 3 | 9 | 17 |
| Mean Apalache time (ms) | 1,648 | 3,514 | 1,699 | 3,529 | 3,172 |
| Apalache crashes (timeouts) | 45 (35) | 148 (138) | 37 (27) | 131 (121) | 123 (113) |
| Untriaged disagreements | 0 | 0 | 0 | 0 | 0 |

- Terminal `1` removes most literal-zero divisors: quarantined candidates fall from
  570 to 136 per 1,000 admitted. It halves tuple-index-0 failures and raises
  large state values from 6% to 10%.
- Small literals alone change nothing measurable; terminals dominate literals.
- Boundary literals produced no untriaged disagreement at this size. The new
  signature quarantined 27 candidates, and TLC reported arithmetic outside its
  range 17 times. A defect in division, modulo or overflow handling would be rare,
  so this sample cannot exclude one; the default keeps the values in the corpus
  for longer runs.
- Apalache time roughly doubles and timeouts reach 11–14% of entries with terminal
  `1`. Admitted modules are larger, because modules with a literal-zero divisor are
  no longer quarantined. The cause of the timeouts is not yet analysed.

## Alternatives considered

- **Terminal `1` with wide literals.** Equal on every measure except boundary
  coverage; chosen against because boundary values cost one marker and exercise
  arithmetic limits deliberately.
- **Hypothesis's constant list.** Its values start at 2^16 and extend to 2^66;
  most of those lie outside TLC's range and would hit one known issue.
- **Quarantine every out-of-range literal.** The pattern language matches exact
  literals only; ranges would need a new pattern form.

## Consequences

- **Stored inputs are reinterpreted:** the integer terminal and every literal.
- **Architecture:** [ir-generators.md][ir-generators] §4, §6 and the limits table
  are updated. [ADR 0011][adr-0011]'s statement that `Int` stays `0` is superseded.
- **Known defects:** `integer-literal-outside-tlc-range` joins the shipped database.
- **Apalache timeouts** at terminal `1` need analysis before long corpora.

[adr-0011]: 0011-collection-base-size.md
[ir-generators]: ../architecture/ir-generators.md
[tlc-range]: ../../conformance/integer-outside-tlc-range.md
