package io.github.tlaplus.hardening.workflow.worker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Binary framing shared by isolated parser and model-checker workers. */
public final class ToolWorkerProtocol {
    static final int MAGIC = 0x46545a57;
    static final int VERSION = 4;
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
        var length = input.readInt();
        if (length < 0) {
            throw new IOException("invalid worker request exploration length: " + length);
        }
        var bytes = input.readNBytes(byteCount);
        if (bytes.length != byteCount) {
            throw new IOException("truncated worker request");
        }
        return new ToolInput(new String(bytes, StandardCharsets.UTF_8), length);
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
        output.flush();
    }
}
