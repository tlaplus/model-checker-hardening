# SANY crashes while formatting a level error for `%`

## Summary

SANY throws `FrontEndException` instead of returning a semantic-analysis or
level-checking failure when a diagnostic names the infix `%` operator. The
already-rendered diagnostic contains `%:`, which is interpreted as a format
conversion and causes `UnknownFormatConversionException`.

Observed with `org.lamport:tla2tools:1.8.0-20260731.185822-52`, revision
`30cc3601321c3fc02e044d0ecb5c58d8921e18df`, on OpenJDK 25.0.3.

## Reproduction

Parse the following module with full semantic analysis and level checking via
`SANY.parse` and `SimpleSanyOutput`:

```tla
------------------------------- MODULE FuzzInput -------------------------------

EXTENDS Integers

VARIABLE exprValue

Init == exprValue = ((<>TRUE) % 1)

Next == UNCHANGED exprValue

===============================================================================
```

The temporal expression in the first argument of `%` intentionally violates
the operator's level constraint. SANY constructs this diagnostic:

```text
Level error in applying operator %:
The level of argument 1 exceeds the maximum level allowed by the operator.
```

Instead of reporting that level error, `SANY.parse` throws the following
exception:

```text
java.util.UnknownFormatConversionException: Conversion = ':'
tla2sany.drivers.FrontEndException: java.util.UnknownFormatConversionException: Conversion = ':'
	at tla2sany.drivers.SANY.parse(SANY.java:220)
	at io.github.tlaplus.hardening.workflow.ParserWorkerMain.parse(ParserWorkerMain.java:94)
	at io.github.tlaplus.hardening.workflow.ParserWorkerMain.run(ParserWorkerMain.java:76)
	at io.github.tlaplus.hardening.workflow.ParserWorkerMain.main(ParserWorkerMain.java:32)
Caused by: java.util.UnknownFormatConversionException: Conversion = ':'
	at java.base/java.util.Formatter.parse(Formatter.java:2818)
	at java.base/java.util.Formatter.format(Formatter.java:2744)
	at java.base/java.util.Formatter.format(Formatter.java:2698)
	at java.base/java.lang.String.format(String.java:4455)
	at tla2sany.semantic.Errors$ErrorDetails.getMessage(Errors.java:70)
	at tla2sany.semantic.Errors$ErrorDetails.toString(Errors.java:79)
	at java.base/java.util.Formatter$FormatSpecifier.printString(Formatter.java:3292)
	at java.base/java.util.Formatter$FormatSpecifier.print(Formatter.java:3170)
	at java.base/java.util.Formatter.format(Formatter.java:2761)
	at java.base/java.util.Formatter.format(Formatter.java:2698)
	at java.base/java.lang.String.format(String.java:4455)
	at tla2sany.output.SimpleSanyOutput.log(SimpleSanyOutput.java:62)
	at tla2sany.drivers.SANY.frontEndSemanticAnalysis(SANY.java:491)
	at tla2sany.drivers.SANY.parse(SANY.java:196)
	... 3 more
```

## Expected behavior

`SANY.parse` should report
`SEMANTIC_ANALYSIS_OR_LEVEL_CHECKING_FAILURE` and preserve the level-error
diagnostic. User-controlled operator names and rendered diagnostics must be
treated as data, not as `String.format` templates.

## Impact

Nineteen independently generated inputs in the inspected corpus reached this
same exception. With an output implementation that does not format the rendered
message again, all nineteen produce ordinary level-checking failures. They are
one SANY diagnostic-formatting defect, not parser crashes caused by the inputs.

