package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Diagnostics;
import java.nio.file.Path;
import java.util.Objects;

/** Result of trying to preserve a payload that crashed its generator. */
public sealed interface GeneratorCrashRecording {
    /** Appends the persistence result to an operation-specific diagnostic. */
    String appendTo(String diagnostic);

    /** The candidate and stack trace were saved. */
    record Saved(Path candidate) implements GeneratorCrashRecording {
        public Saved {
            Objects.requireNonNull(candidate, "candidate");
        }

        @Override
        public String appendTo(String diagnostic) {
            return Objects.requireNonNull(diagnostic, "diagnostic")
                    + "; crash saved to '"
                    + candidate
                    + "'";
        }
    }

    /** Saving failed; the recording failure has also been suppressed on the generator failure. */
    record Failed(Throwable recordingFailure) implements GeneratorCrashRecording {
        public Failed {
            Objects.requireNonNull(recordingFailure, "recordingFailure");
        }

        @Override
        public String appendTo(String diagnostic) {
            return Objects.requireNonNull(diagnostic, "diagnostic")
                    + "; crash artifact could not be saved: "
                    + Diagnostics.message(recordingFailure);
        }
    }
}
