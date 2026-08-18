package io.github.tlaplus.hardening.workflow.worker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Binary framing shared by isolated parser and model-checker workers. */
public final class ToolWorkerProtocol {
    static final int MAGIC = 0x46545a57;
    static final int VERSION = 3;
    static final int STOP = -1;
    static final int NO_FAILURE_CODE = -1;
    static final int MAXIMUM_MESSAGE_BYTES = 16 * 1024 * 1024;
    static final int MAXIMUM_DIAGNOSTIC_BYTES = 1024 * 1024;
    static final String PORT_PROPERTY = "fuzztla.worker.protocol.port";
    static final String TOKEN_PROPERTY = "fuzztla.worker.protocol.token";

    private ToolWorkerProtocol() {}

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
    public static String readRequest(DataInputStream input) throws IOException {
        var length = input.readInt();
        if (length == STOP) {
            return null;
        }
        if (length < 0 || length > MAXIMUM_MESSAGE_BYTES) {
            throw new IOException("invalid worker request length: " + length);
        }
        var bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("truncated worker request");
        }
        return new String(bytes, StandardCharsets.UTF_8);
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
