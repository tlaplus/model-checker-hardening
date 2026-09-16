package io.github.tlaplus.hardening.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.utils.InfoCmp.Capability;

/** A virtual terminal with real terminfo capabilities and captured output. */
record RunTestTerminal(Terminal terminal, ByteArrayOutputStream output) implements AutoCloseable {
    static RunTestTerminal open(String type) throws IOException {
        return open(type, false);
    }

    static RunTestTerminal open(String type, boolean monochrome) throws IOException {
        var output = new ByteArrayOutputStream();
        // Use a stream-backed terminal with the requested terminfo, never a native PTY.
        var terminal = new DumbTerminal("test", type,
                new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8) {
            @Override
            public Integer getNumericCapability(Capability capability) {
                if (monochrome && capability == Capability.max_colors) {
                    return 0;
                }
                return super.getNumericCapability(capability);
            }
        };
        terminal.setSize(new Size(80, 24));
        return new RunTestTerminal(terminal, output);
    }

    String drain() {
        terminal.flush();
        var result = output.toString(StandardCharsets.UTF_8);
        output.reset();
        return result;
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }
}
