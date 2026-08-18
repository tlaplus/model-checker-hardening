package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.FileTrees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything a child process says outside the control channel, and the scratch tree it says it in.
 *
 * <p>Native tool output can bypass JVM stream redirection, so stdout and stderr are drained
 * continuously into bounded buffers: an unread pipe would otherwise block the child. Closing stops
 * draining, collects any JVM fatal-error report the child left behind, and removes the tree.
 */
final class WorkerOutput implements AutoCloseable {
    private static final int MAXIMUM_STANDARD_OUTPUT_BYTES = 64 * 1024;
    private static final int MAXIMUM_STANDARD_ERROR_BYTES = 64 * 1024;
    private static final String FATAL_ERROR_REPORT_PREFIX = "hs_err_pid";

    private final String description;
    private final Path temporaryDirectory;
    private final BoundedOutputCapture standardOutput;
    private final BoundedOutputCapture standardError;
    private String fatalErrorReports = "";

    WorkerOutput(String description, Process process, Path temporaryDirectory) {
        this.description = description;
        this.temporaryDirectory = temporaryDirectory;
        standardOutput = new BoundedOutputCapture(
                process.getInputStream(), MAXIMUM_STANDARD_OUTPUT_BYTES, "stdout");
        standardError = new BoundedOutputCapture(
                process.getErrorStream(), MAXIMUM_STANDARD_ERROR_BYTES, "stderr");
    }

    /** Returns the file name pattern of a JVM fatal-error report inside a worker directory. */
    static String fatalErrorReportPattern(Path directory) {
        return directory.resolve(FATAL_ERROR_REPORT_PREFIX + "%p.log").toString();
    }

    /** Appends whatever the child printed, and any fatal-error report, to a diagnostic. */
    String describe(String diagnostic) {
        var result = diagnostic;
        result = appendCapture(result, standardOutput, "stdout");
        result = appendCapture(result, standardError, "stderr");
        if (!fatalErrorReports.isBlank()) {
            result = WorkerDiagnostics.append(result, fatalErrorReports);
        }
        return result;
    }

    @Override
    public void close() {
        standardOutput.close();
        standardError.close();
        fatalErrorReports = readFatalErrorReports();
        try {
            FileTrees.deleteRecursively(temporaryDirectory);
        } catch (IOException ignored) {
            // The run-scoped corpus scratch owner retries after all workers stop.
        }
    }

    private String appendCapture(String diagnostic, BoundedOutputCapture capture, String stream) {
        var captured = capture.text();
        if (captured.isBlank()) {
            return diagnostic;
        }
        return WorkerDiagnostics.append(
                diagnostic,
                description
                        + " "
                        + stream
                        + ":"
                        + System.lineSeparator()
                        + captured.stripTrailing());
    }

    private String readFatalErrorReports() {
        try (var paths = Files.list(temporaryDirectory)) {
            var reports = paths.filter(path -> path.getFileName()
                            .toString()
                            .startsWith(FATAL_ERROR_REPORT_PREFIX))
                    .sorted()
                    .toList();
            var result = "";
            for (var report : reports) {
                result = WorkerDiagnostics.append(
                        result,
                        description
                                + " fatal error report:"
                                + System.lineSeparator()
                                + Files.readString(report));
            }
            return result;
        } catch (IOException exception) {
            return "Could not read "
                    + description
                    + " fatal error report: "
                    + exception.getMessage();
        }
    }
}
