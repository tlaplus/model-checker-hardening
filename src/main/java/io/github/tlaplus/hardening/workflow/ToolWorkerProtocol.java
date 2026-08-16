package io.github.tlaplus.hardening.workflow;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Binary framing shared by isolated parser and model-checker workers. */
final class ToolWorkerProtocol {
    static final int MAGIC = 0x46545a57;
    static final int VERSION = 1;
    static final int STOP = -1;
    static final int MAXIMUM_MESSAGE_BYTES = 16 * 1024 * 1024;
    static final int MAXIMUM_DIAGNOSTIC_BYTES = 1024 * 1024;

    private ToolWorkerProtocol() {}

    static void writeHandshake(DataOutputStream output) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.flush();
    }

    /** Returns {@code null} for the graceful-stop message. */
    static String readRequest(DataInputStream input) throws IOException {
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

    static void writeResult(DataOutputStream output, ToolResult result) throws IOException {
        var diagnostic = result.diagnostic().getBytes(StandardCharsets.UTF_8);
        if (diagnostic.length > MAXIMUM_DIAGNOSTIC_BYTES) {
            diagnostic = Arrays.copyOf(diagnostic, MAXIMUM_DIAGNOSTIC_BYTES);
        }
        output.writeInt(result.outcome().protocolCode());
        output.writeInt(diagnostic.length);
        output.write(diagnostic);
        output.flush();
    }
}
