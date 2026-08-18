package io.github.tlaplus.hardening.workflow.worker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Child-side connection to the parent's private worker-protocol listener. */
public final class ToolWorkerConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    private ToolWorkerConnection(Socket socket) throws IOException {
        this.socket = socket;
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static ToolWorkerConnection connect() throws IOException {
        var encodedPort = System.getProperty(ToolWorkerProtocol.PORT_PROPERTY);
        if (encodedPort == null) {
            throw new IOException("worker protocol port is not configured");
        }
        final int port;
        try {
            port = Integer.parseInt(encodedPort);
        } catch (NumberFormatException exception) {
            throw new IOException("worker protocol port is invalid", exception);
        }
        if (port <= 0 || port > 65_535) {
            throw new IOException("worker protocol port is outside 1..65535");
        }

        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            socket.setTcpNoDelay(true);
            return new ToolWorkerConnection(socket);
        } catch (IOException exception) {
            try {
                socket.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public DataInputStream input() {
        return input;
    }

    public DataOutputStream output() {
        return output;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
