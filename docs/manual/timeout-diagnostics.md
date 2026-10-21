# Timeout diagnostics

> **Status:** Implemented.
> [ADR 0021](../decisions/0021-timeout-diagnostics.md) records the design.
> Sidecars written before it hold only the line
> `<Tool> worker timed out after <duration>`.

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
	at java.lang.StringConcatHelper.prepend(StringConcatHelper.java:765)
	...
	at tlc2.value.impl.FcnLambdaValue.toString(FcnLambdaValue.java:780)
	at tlc2.value.impl.Value.toStringImpl(Value.java:400)
	at tlc2.value.impl.Value.toString(Value.java:375)
	at tlc2.value.impl.FcnLambdaValue.apply(FcnLambdaValue.java:189)
	...
	[537 more frames]
TLC2 Version 2026.10.21.070023
...
Progress(1) at 2026-10-21 09:00:27: 1 states generated (1 s/min), ...
```

This is corpus67's `5319b006`, replayed: TLC was formatting the message of a
function-domain error
([tlc-performance-002](../../findings/tlc-performance/tlc-performance-002.md)).
The sidecar has three parts:

1. **The timeout line**, with the stage's limit.
2. **The tool's threads** when the worker was stopped: every thread that was
   running or waiting for a lock, by name, innermost frame first, at most 256
   frames per thread and 64 KiB in all. Waiting threads, such as TLC's main
   thread during exploration, and the JVM's own threads are omitted. Frames read
   as in an exception trace.
3. **The tool's output** for the input, up to the point where it was stopped.

The stacks are one sample at the deadline. They show where the tool was, not where
it spent its time. A sidecar has no stacks if the worker could not run its
shutdown hook within 2 seconds, or if it ran out of heap first. A worker that
ends its JVM itself, as TLC does in
[tlc-019](../../findings/TLC/tlc-019.md), prints the same two parts after the line
`<Tool> worker exited while processing the input`.

## 2. Triage

`script/triager.py` matches a timeout sidecar against the signatures declared for
timeouts only. The first match, in catalog order, labels the entry with its
finding. A timeout that no signature matches is labeled `TIMEOUT`. Signatures for
completed tool runs are never applied to timeouts, and timeout signatures never to
other crashes.

| Frames in a TLC thread | Label |
| --- | --- |
| `Value.toString` called from `FcnLambdaValue.apply`, no `CallStackTool` frame | [tlc-performance-002](../../findings/tlc-performance/tlc-performance-002.md) |
| The same pair and a `CallStackTool` frame | [tlc-018](../../findings/TLC/tlc-018.md) |

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
3. **Add the signature** to `SIGNATURES` in `script/triager.py` with
   `timeout_finding`, which names the finding and the patterns over the frames.
   The tlc-performance-002 signature reads:

   ```python
   TLC_FORMATTING_DOMAIN_ERROR = (
       r"^\tat tlc2\.value\.impl\.Value\.toString\([^)\n]*\)\n"
       r"\tat tlc2\.value\.impl\.FcnLambdaValue\.apply\("
   )
   TLC_CALL_STACK_FRAME = r"^\tat tlc2\.tool\.impl\.CallStackTool\."

   timeout_finding("tlc-performance-002.md", Tool.TLC,
                   all_of(TLC_FORMATTING_DOMAIN_ERROR,
                          rf"\A(?![\s\S]*{TLC_CALL_STACK_FRAME})")),
   ```

   Match class and method names, not line numbers, which change with the tool's
   version.
4. **Add a test** to `script/test_triager.py` with a sidecar of the finding and
   one of an unrelated timeout, and record the instances in the finding's corpus
   evidence.
