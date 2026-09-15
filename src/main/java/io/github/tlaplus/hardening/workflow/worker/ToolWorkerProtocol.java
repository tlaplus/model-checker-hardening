package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Binary framing shared by isolated parser and model-checker workers. */
public final class ToolWorkerProtocol {
    static final int MAGIC = 0x46545a57;
    static final int VERSION = 6;
    static final int STOP = -1;
    static final int NO_FAILURE_CODE = -1;
    static final int MAXIMUM_MESSAGE_BYTES = 128 * 1024 * 1024;
    static final int MAXIMUM_DIAGNOSTIC_BYTES = 1024 * 1024;
    static final String PORT_PROPERTY = "fuzztla.worker.protocol.port";
    static final String TOKEN_PROPERTY = "fuzztla.worker.protocol.token";

    private ToolWorkerProtocol() {}

    /**
     * Maximum size in bytes of one request payload, the UTF-8 bytes of a rendered specification.
     * A larger rendering cannot be carried to a worker; the workflow rejects such an input at
     * generation and records it as a crash verdict if one is already stored.
     */
    public static int maximumMessageBytes() {
        return MAXIMUM_MESSAGE_BYTES;
    }

    public static void writeHandshake(DataOutputStream output) throws IOException {
        var token = System.getProperty(TOKEN_PROPERTY);
        if (token == null || token.isBlank()) {
            throw new IOException("worker protocol token is not configured");
        }
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeUTF(token);
        output.flush();
    }

    /** Returns {@code null} for the graceful-stop message. */
    public static ToolInput readRequest(DataInputStream input) throws IOException {
        var byteCount = input.readInt();
        if (byteCount == STOP) {
            return null;
        }
        if (byteCount < 0 || byteCount > MAXIMUM_MESSAGE_BYTES) {
            throw new IOException("invalid worker request length: " + byteCount);
        }
        var transitions = input.readInt();
        if (transitions < 0) {
            throw new IOException("invalid worker request exploration length: " + transitions);
        }
        var temporalProperty = input.readBoolean();
        var bytes = input.readNBytes(byteCount);
        if (bytes.length != byteCount) {
            throw new IOException("truncated worker request");
        }
        return new ToolInput(new String(bytes, StandardCharsets.UTF_8),
                new CheckRequest(transitions, temporalProperty));
    }

    public static void writeResult(DataOutputStream output, ToolResult result) throws IOException {
        var diagnostic = result.diagnostic().getBytes(StandardCharsets.UTF_8);
        if (diagnostic.length > MAXIMUM_DIAGNOSTIC_BYTES) {
            diagnostic = Arrays.copyOf(diagnostic, MAXIMUM_DIAGNOSTIC_BYTES);
        }
        output.writeInt(result.outcome().protocolCode());
        output.writeInt(result.failureCode()
                .map(code -> code.encodedCode())
                .orElse(NO_FAILURE_CODE));
        output.writeInt(diagnostic.length);
        output.write(diagnostic);
        writeMetrics(output, result.metrics());
        output.flush();
    }

    /**
     * Reads one result frame. The metrics section follows the diagnostic and names each metric, so
     * the declaration order of the metric enums is not part of the frame.
     */
    static ToolResult readResult(DataInputStream input) throws IOException, WorkflowException {
        var outcome = StageOutcome.fromProtocolCode(input.readInt());
        var encodedFailureCode = input.readInt();
        var failureCode = encodedFailureCode == NO_FAILURE_CODE
                ? Optional.<CheckerFailureCode>empty()
                : decodeFailureCode(encodedFailureCode);
        var diagnosticLength = input.readInt();
        if (diagnosticLength < 0 || diagnosticLength > MAXIMUM_DIAGNOSTIC_BYTES) {
            throw new WorkflowException(
                    "worker returned an invalid diagnostic length: " + diagnosticLength);
        }
        var diagnosticBytes = input.readNBytes(diagnosticLength);
        if (diagnosticBytes.length != diagnosticLength) {
            throw new EOFException("truncated worker diagnostic");
        }
        var metrics = readMetrics(input);
        try {
            return new ToolResult(
                    outcome,
                    failureCode,
                    new String(diagnosticBytes, StandardCharsets.UTF_8),
                    metrics);
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "worker returned an invalid failure classification", exception);
        }
    }

    private static void writeMetrics(DataOutputStream output, Optional<ExplorationMetrics> metrics)
            throws IOException {
        output.writeBoolean(metrics.isPresent());
        if (metrics.isEmpty()) {
            return;
        }
        var value = metrics.get();
        output.writeUTF(value.phase().map(ExplorationPhase::encodedName).orElse(""));
        output.writeBoolean(value.saturated());
        output.writeInt(value.counts().size());
        for (var count : value.counts().entrySet()) {
            output.writeUTF(count.getKey().fieldName());
            output.writeLong(count.getValue());
        }
    }

    private static Optional<ExplorationMetrics> readMetrics(DataInputStream input)
            throws IOException, WorkflowException {
        if (!input.readBoolean()) {
            return Optional.empty();
        }
        var builder = ExplorationMetrics.builder();
        var phase = input.readUTF();
        var saturated = input.readBoolean();
        var countNumber = input.readInt();
        if (countNumber < 0 || countNumber > ExplorationCount.values().length) {
            throw new WorkflowException("worker returned an invalid metric count: " + countNumber);
        }
        try {
            if (!phase.isEmpty()) {
                builder.phase(ExplorationPhase.fromEncodedName(phase));
            }
            builder.saturated(saturated);
            for (var index = 0; index < countNumber; index++) {
                var name = input.readUTF();
                var count = ExplorationCount.fromFieldName(name).orElseThrow(
                        () -> new IllegalArgumentException("unknown exploration metric: " + name));
                builder.count(count, input.readLong());
            }
            return Optional.of(builder.build());
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException("worker returned invalid exploration metrics", exception);
        }
    }

    private static Optional<CheckerFailureCode> decodeFailureCode(int encodedCode)
            throws WorkflowException {
        try {
            return Optional.of(CheckerFailureCode.fromEncodedCode(encodedCode));
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "worker returned an unknown failure code: " + encodedCode, exception);
        }
    }
}
