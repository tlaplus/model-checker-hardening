# Timeout diagnostics

> **Status:** Proposed, not implemented.
> [ADR 0021](../decisions/0021-timeout-diagnostics.md) records the design. Today a
> timeout's sidecar holds only the line `<Tool> worker timed out after <duration>`.

A parser or checker worker that exceeds its stage's `timeout_sec` is stopped, and
the input gets a crash verdict. This page describes what the crash's
`.stacktrace` sidecar contains, how the triager classifies it, and how to add a
signature for a slow defect.

## 1. Reading a timeout sidecar

```text
TLC worker timed out after PT30S
TLC worker stderr:
Tool threads at shutdown:
"TLCWorkerThread-000" RUNNABLE
	at tlc2.value.impl.FcnRcdValue.toString(FcnRcdValue.java:968)
	at tlc2.value.impl.FcnLambdaValue.toString(FcnLambdaValue.java:780)
	at tlc2.value.impl.Value.toString(Value.java:375)
	at tlc2.value.impl.FcnLambdaValue.apply(FcnLambdaValue.java:189)
	...
	[514 more frames]
TLC2 Version 2026.10.21.060939
...
Progress(1) at 2026-10-21 08:09:42: 1 states generated, ...
```

1. **The timeout line**, with the stage's limit.
2. **The tool's threads** when the worker was stopped: every thread that was
   running or waiting for a lock, innermost frame first, at most 256 frames per
   thread and 64 KiB in all. Waiting threads and JVM service threads are omitted.
3. **The tool's output** for the input, up to the point where it was stopped.

The stacks are one sample at the deadline. They show where the tool was, not where
it spent its time. A sidecar has no stacks if the worker could not run its
shutdown hook within 2 seconds, or if it ran out of heap first.

## 2. Triage

`script/triager.py` matches a timeout sidecar against the signatures declared for
timeouts only. The first match, in catalog order, labels the entry with its
finding. A timeout that no signature matches is labeled `TIMEOUT`, as before.
Signatures for completed tool runs are never applied to timeouts, and timeout
signatures never to other crashes.

The campaign report ([manual](campaign-report.md)) uses the same signatures, so
its triage table lists the timeout classes.

## 3. Writing a timeout signature

1. **Reproduce the slow phase** with the corpus's settings, for example by
   rendering the entry and running TLC standalone:

   ```sh
   bin/fuzztla print --corpus corpus67 --spec corpus67/02tlc-crash/<sha256>.cbor > FuzzInput.tla
   ```

2. **Sample the stacks** a few times during the slow phase, with `jstack <pid>` or
   from timeout sidecars of the same cause. Pick a sequence of frames that every
   sample contains and that names the cause, such as `Value.toString` called from
   `FcnLambdaValue.apply` for
   [tlc-performance-002](../../findings/tlc-performance/tlc-performance-002.md).
   Frames that appear in every slow input, such as `Tool.getNextStates`, do not
   name a cause.
3. **Add the signature** to `script/triager.py` as a timeout signature that
   names the finding, with patterns over the frames:

   ```python
   timeout_finding("tlc-performance-002.md", Tool.TLC,
       all_of(r"^\tat tlc2\.value\.impl\.Value\.toString\(.*\)\n"
              r"\tat tlc2\.value\.impl\.FcnLambdaValue\.apply\(",
              r"\A(?![\s\S]*tlc2\.tool\.impl\.CallStackTool)")),
   ```

   Match class and method names, not line numbers, which change with the tool's
   version.
4. **Add a test** to `script/test_triager.py` with a sidecar of the finding and
   one of an unrelated timeout, and record the instances in the finding's corpus
   evidence.
