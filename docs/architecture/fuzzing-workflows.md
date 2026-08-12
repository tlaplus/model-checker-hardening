# General fuzzing workflows

**Author:** Igor Konnov

This document captures our approach to generating high-quality TLA<sup>+</sup>
specifications. We call this process "fuzzing" in general, independently of whether we are using random generators (in
the spirit of property-based testing), or using genetic algorithms with coverage feedback (in the spirit of AFL and
cargofuzz).

## 1. Processing pipeline

### 1.1. General architecture

Our fuzzing workflows follows the general architecture that is shown in the figure below. In certain workflows, some of
the stages may be missing. In the future, we may add additional stages. Nevertheless, we believe that this architecture
is general enough to encompass many fuzzing frameworks.

In the figure below, the outer boxes are the stages of the pipeline, while the inner boxes are directories in the
corpus. The names of the directories reflect the status of each input within the stage.

```mermaid
flowchart LR
    subgraph inputs["Generated inputs"]
        direction TB
        inp["00-inputs"]
    end

    subgraph parse["Parser"]
        direction TB
        parse_pass["01parser-pass"]
        parse_fail["01parser-fail"]
        parse_crash["01parser-crash"]
    end

    subgraph apalache["Apalache"]
        direction TB
        apalache_pass["02apa-pass"]
        apalache_fail["02apa-fail"]
        apalache_crash["02apa-crash"]
    end

    subgraph tlc["TLC"]
        direction TB
        tlc_pass["02tlc-pass"]
        tlc_fail["02tlc-fail"]
        tlc_crash["02tlc-crash"]
    end

    subgraph conformance["Aggregator"]
        direction TB
        aggregator_pass["03aggregator-pass"]
        aggregator_fail["03aggregator-fail"]
    end

    subgraph quality["Quality gate"]
        direction TB
        quality_pass["04quality-pass"]
        quality_fail["04quality-fail"]
    end

    subgraph mutator["Mutator"]
        direction TB
        mutator_pass["05mutator-pass"]
    end

    subgraph test_suite["Final test suite"]
        direction LR
        tests_pass["99tests-pass"]
        tests_fail["99tests-fail"]
        tests_crash["99tests-crash"]
    end

    inp --> parse
    parse_pass --> tlc
    parse_pass --> apalache
    tlc_pass --> conformance
    apalache_pass --> conformance
    tlc_fail --> conformance
    apalache_fail --> conformance
    aggregator_pass --> quality
    quality_pass --> mutator
    mutator_pass --> inp
    quality_pass --> tests_pass
    aggregator_fail --> tests_fail
    parse_crash --> tests_crash
    tlc_crash --> tests_crash
    apalache_crash --> tests_crash
```

### 1.2. Conformance testing of TLC vs. Apalache with random inputs

In this workflow, the goal is to collect a differential testing suite. This test suite contains three kinds of test
inputs:

- **Positive tests.** These specifications show that TLC and Apalache agree on model checking of the specifications.
- **Negative tests.** These specifications show that one of the model checkers produces a counterexample, while another
  model checker does not.
- **Crash tests.** One of the model checkers crashes on the input.

This workflow specializes the general workflow as follows:

- **Aggregator.** At this stage, the input is moved to `pass`, when both TLC and Apalache pass, or both fail.
- **Mutator.** The mutator is no-operation. It does not generate new inputs.
- **Quality gate.** Good quality gates are to be found.

### 1.3. Metamorphic testing of TLC and Apalache

In this workflow, the goal is to collect a metamorphic test suite. This test suite contains two kinds of test inputs:

- **Positive tests.** These tests demonstrate that the model checker preserves equivalent transformations, as expected.
- **Negative tests.** These tests demonstrate soundness issues in the model checker. They present two equivalent
  expressions that are not equal in the model checker's interpretation.
- **Crash tests.** The model checker crashes on the input.

This workflow specializes the general workflow as follows:

- **Aggregator.** At this stage, the input is move to `pass`, when both TLC and Apalache pass.
- **Mutator.** The mutator applies equivalent transformations to some operators of the specification that corresponds to
  the input.
- **Quality gate.** Good quality gates are to be found.

### 1.4. Coverage-based fuzzing

In this workflow, the goal is to generate a test suite that produces a high coverage of the model checker's source code.
This test suite contains one kind of test inputs:

- **Positive tests.** These tests do not fail the model checker and increase the source code coverage of the model
  checker.
- **Crash tests.** The model checker crashes on the input.

It is not clear to us, what kind of negative tests we can produce in this case.

This workflow specializes the general workflow as follows:

- **Aggregator.** At this stage, the input is move to `pass`, when both TLC and Apalache pass.
- **Mutator.** The mutator transforms the input (the byte array) with genetic mutations.
- **Quality gate.** The input increases the source code coverage.

## 2. Encoding corpus inputs

### 2.1. Motivation

Fuzzers usually store corpus inputs in the binary form, without adding any metadata.
Since our fuzzing workflow moves inputs along various stages, we have to add metadata.

Importantly, we need a flexible format: Adding new fuzzing stages
should not require modification of the other stages. Hence, rigid format that require
fixed schemas and serialization/deserialization would be a bad choice here. JSON
is an obvious candidate, but it's a text format, and it makes it inconvenient to
store binary inputs.

[CBOR] is a binary analogue of JSON. In the following, we specify the general
rules of using CBOR to encode inputs and their metadata in our fuzzing framework.
We use the CBOR diagnostic notation to explain the format. Use [cbor playground][]
to experiment with the format.

### 2.2. Minimal fields

A minimalistic input with metadata looks as follows:

```cbor
{
    "kind": "expr",
    "input": h'0123af'
}
```

The fields have the following meaning:

 - The field `"input"` contains a byte array that is decoded by the IR generators.
 - The field `"kind"` tells the fuzzer how to decode `"input"`:
    - When `"kind"` is `"expr"`, the field `"input"` encodes a single TLA<sup>+</sup> expression.
    - When `"kind"` is `"module"`, the field `"input"` encodes a single TLA<sup>+</sup> module.

### 2.3. Stage

When an input passes through a stage, this stage stores its metadata under `stages.<stage name>`.
The metadata depends on the stage. The minimal set of fields is:

 - The field `"verdict"` contains the status of passing through the stage. The
   typical values are: `"pass"`, `"fail"`, `"crashed"`.
 - The field `"startTime"` contains an [epoch-based date/time][] of the moment
   when a stage worker started to process the input. This timestamp must be in UTC.
 - The field `"endTime"` contains an [epoch-based date/time][] of the moment     
   when a stage worker stopped to process the input. This timestamp must be in UTC.
   If there is no meaningful difference between the start and end times, then
   `"startTime"` and `"endTime"` must be equal.

```cbor
{
    "kind": "expr",
    "input": h'0123af',
    "stages": {
      "parser": {
        "verdict": "pass",
        "startTime": 1(1786635967),
        "endTime": 1(1786635990)
      }
    }
}
```

[CBOR]: https://cbor.io/
[cbor playground]: https://cbor.me
[epoch-based date/time]: https://www.rfc-editor.org/rfc/rfc8949.html#name-epoch-based-date-time