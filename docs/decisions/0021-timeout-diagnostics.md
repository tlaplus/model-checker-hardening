# 0021: Diagnostics of a timed-out worker

**Authors:** Igor Konnov and Claude

**Status:** Implemented

**Date:** 2026-10-21

**Implementation.** Branch `igor/timeout-diagnostics` (FuzzTLA `439fd71`, TLC
`932d35e` from tla2tools snapshot `1.8.0-20261015.150947-106`, Apalache 0.62.3
`3eb15b2`). Replayed through the TLC worker with the stage's 30-second limit,
corpus67's `5319b006` stores the TLC worker thread inside `FcnLambdaValue.apply`,
and the triager labels it `tlc-performance-002`.

The [manual][manual] describes the sidecars and timeout signatures for their
users.

## Context

A parser or checker worker that exceeds its stage's `timeout_sec` is retired, and
the input gets a crash verdict ([ADR 0001][adr-0001]). Its `.stacktrace` sidecar
holds one line, such as `TLC worker timed out after PT30S`. The triager files every
such crash under `TIMEOUT`, which keeps it out of `NEW` but says nothing about the
cause. Timeouts are a large share of the crashes: 956 of corpus14's 1,524
Apalache crash outcomes, 642 of corpus54's 845, and 223 of corpus53's 344. A
timeout can also hide a defect: corpus67's only unclassified entry, `5319b006`,
times out because TLC spends minutes building one error message
([tlc-performance-002][tlc-performance-002]). Finding that cause took a standalone
rerun and thread dumps by hand.

The worker already has the means to say more. Each worker main runs under
`ToolWorkerRuntime.main`, whose shutdown hook prints the tool output of the input in
progress to standard error. The parent appends the child's standard error to the
crash diagnostic. The hook was added for TLC calling `System.exit(1)`
([tlc-019][tlc-019]), and it works when the tool ends the JVM itself or when
`kill -TERM` reaches the child from outside.

A timed-out worker loses that output. Replaying `5319b006` through
`IsolatedWorkerProcess`, and a specification that is merely slow, both store only
the timeout line. Sending `SIGTERM` to the same child by hand stores the TLC output.
Two causes are in the parent: items 1 to 3 end the child with `SIGKILL`, and item 4
discards what a child prints after `SIGTERM`.

1. `WorkerChannel.await` reads the result on a virtual thread and cancels it when
   the deadline passes. Interrupting a virtual thread blocked in a
   `java.net.Socket` read closes the socket (JEP 444).
2. `IsolatedWorkerProcess.close` then calls `requestStop`, which fails with
   `SocketException: Socket is closed`.
3. Its `catch (IOException)` calls `process.destroyForcibly()`, which sends
   `SIGKILL`. No shutdown hook runs.
4. Where the parent does send `SIGTERM`, it uses `Process.destroy()`. On Linux and
   macOS, `ProcessImpl.destroy` closes the parent's end of the child's standard
   output and error right after the signal, so what the hook prints is discarded
   as well. A child whose output goes to a file, or that is sent the signal through
   `ProcessHandle.destroy()`, keeps it.

Even with the output, a timeout diagnostic would rarely name its cause: TLC's
output of `5319b006` ends at `Progress(1)`, as for any slow input. What names the
cause is where the tool is when the deadline passes. Thread dumps of `5319b006`
during its first 50 seconds show the TLC worker thread 680 to 780 frames deep.
Within its top 8 to 124 frames, `FcnLambdaValue.apply` calls `Value.toString` to
build a domain-error message.

## Decision

### 1. A timed-out worker receives `SIGTERM`, and its output is kept

After a timeout, the parent does not ask the child to stop over the control
channel, since the child is busy in the tool and the channel is closed. It sends
`SIGTERM`, waits for a grace period, and sends `SIGKILL` only if the child is
still alive. The grace period of a timed-out worker is 2 seconds; the other
terminations keep 500 milliseconds.

Every `SIGTERM` to a worker goes through `ProcessHandle.destroy()`, which sends
the signal and leaves the pipes open, so the parent drains what the child prints
while it shuts down. `close()` keeps the fallback for an unusable channel, but the
fallback becomes the same `SIGTERM`-then-`SIGKILL` sequence instead of `SIGKILL`
alone.

This step alone restores the behavior that the shutdown hook was written for:
the diagnostic of a timeout carries the tool output of the input.

### 2. The shutdown hook prints the tool's stacks

When an input is in progress at shutdown, the hook of `ToolWorkerRuntime.main`
prints a stack section before the pending tool output:

```text
Tool threads at shutdown:
"TLCWorkerThread-000" RUNNABLE
	at tlc2.value.impl.RecordValue.toString(RecordValue.java:748)
	...
	at tlc2.value.impl.Value.toString(Value.java:375)
	at tlc2.value.impl.FcnLambdaValue.apply(FcnLambdaValue.java:189)
	...
	[512 more frames]
```

- **Threads.** Every thread in state `RUNNABLE` or `BLOCKED` with a non-empty
  stack, except the hook's own thread and the threads of the JVM's `system`
  thread group, such as the reference handler. These are the threads doing work;
  the waiting threads of TLC's main loop and state queue are omitted. The rule
  needs no per-tool list of packages, and holds for SANY, TLC, TLC with `n`
  workers, and Apalache, whose solver calls are `RUNNABLE` native frames.
- **Order.** Threads by name, frames innermost first.
- **Frames.** As an exception trace prints them, `tlc2.Tool.eval(Tool.java:7)`.
  The frames that `Thread.getAllStackTraces` returns would also print their class
  loader and module, `app//tlc2.Tool.eval(Tool.java:7)`, so the hook prints a
  copy built from class, method, file and line.
- **Bounds.** At most 256 frames per thread, and at most 64 KiB for the section;
  an omitted tail is marked as above. The frames that identify a cause are near
  the top, and the bound keeps the pending output within the standard-error
  capture.
- **Placement.** The section comes first, so that a long tool output cannot push
  it out of the bounded capture.

The hook runs on every shutdown with an input in progress: a timeout, a tool that
calls `System.exit` ([tlc-019][tlc-019]), or a `SIGTERM` from elsewhere. It does not
run on `SIGKILL` or on `-XX:+ExitOnOutOfMemoryError`, which ends the JVM without
shutdown hooks ([tlc-018][tlc-018]). The JVM samples the stacks at a safepoint;
if it cannot reach one within the grace period, the child is killed and the
diagnostic is what it is today.

The parent's standard-error capture grows from 64 KiB to 64 KiB plus the largest
tool output a result frame carries (1 MiB), so the section and the full pending
output both fit. Only crash sidecars grow.

### 3. Timeout signatures in the triager

A triager signature declares how the tool run it reads ended: `COMPLETED`, the
default and every current signature, or `TIMEOUT`. A diagnostic that starts with
`<Tool> worker timed out after` is matched against `TIMEOUT` signatures only, in
catalog order. A timeout that none matches stays in the `TIMEOUT` bucket. Other
diagnostics are matched against `COMPLETED` signatures as today.

The split keeps today's classifications stable. A `COMPLETED` signature describes
the full output of a tool that returned; applied to a truncated output, it could
match a message that the tool would have followed with a different error.

The first `TIMEOUT` signature is for [tlc-performance-002][tlc-performance-002]:
a TLC thread whose stack shows `Value.toString` called from
`FcnLambdaValue.apply`, with no `CallStackTool` frame. The same frames under
`CallStackTool` are the replay of [tlc-018][tlc-018], and get a `TIMEOUT`
signature of that finding.

### 4. What does not change

The corpus format, the worker protocol and its version, the verdicts, and the
stage records are unchanged. A timeout is still a crash. The campaign report
([ADR 0020][adr-0020]) classifies with the triager's signatures, so it shows the
timeout classes without a change of its own.

## Consequences

- **Classifiable timeouts.** Timeouts get causes, and a timeout no longer hides a
  defect behind `TIMEOUT`. Apalache's hundreds of timeouts per campaign can be
  split into solver time and rewriting or preprocessing time.
- **One sample.** The stack shows where the tool was at the deadline, not where it
  spent its time. A signature therefore names a frame sequence that persists for
  the whole slow phase, as the error formatting of tlc-performance-002 does.
- **Cost.** A timeout costs the stack sampling and up to 2 seconds of grace. A
  run without timeouts is unaffected.
- **Old corpora.** Sidecars written before this change have no stacks. Their
  timeouts stay `TIMEOUT` until the entries are checked again.

## Alternatives considered

- **`jcmd Thread.print` or `SIGQUIT` from the parent.** The JVM prints every
  thread with its full stack to the child's standard output: about 50 KB for the
  TLC worker of `5319b006`, 42 KB of it one thread. With `n` TLC workers it
  exceeds the 64 KiB capture, in an order the parent cannot choose. `SIGQUIT` does not exist on Windows, and `jcmd` needs
  the attach mechanism in the child.
- **A dump request over the control channel.** The child's control loop runs the
  tool on the thread that would read the request, so the child would need a second
  reader thread, and the protocol a new message and version.
- **Periodic sampling.** Sampling the tool's stack during the run would show where
  it spends its time, but it costs every input, not only the slow ones, and it is
  polling where the deadline is already a signal.
- **Reuse `COMPLETED` signatures for timeouts.** Simpler, but it could change the
  class of an existing crash and match truncated output.

[adr-0001]: 0001-stages-and-workers.md
[adr-0020]: 0020-campaign-report.md
[manual]: ../manual/timeout-diagnostics.md
[tlc-018]: ../../findings/TLC/tlc-018.md
[tlc-019]: ../../findings/TLC/tlc-019.md
[tlc-performance-002]: ../../findings/tlc-performance/tlc-performance-002.md
