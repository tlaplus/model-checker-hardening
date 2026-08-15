package io.github.tlaplus.hardening.workflow;

/** Constants shared by the parent and child sides of the parser protocol. */
final class ParserWorkerProtocol {
    static final int MAGIC = 0x46545a50;
    static final int VERSION = 1;
    static final int STOP = -1;
    static final int MAXIMUM_MESSAGE_BYTES = 16 * 1024 * 1024;
    static final int MAXIMUM_DIAGNOSTIC_BYTES = 1024 * 1024;

    private ParserWorkerProtocol() {}
}
