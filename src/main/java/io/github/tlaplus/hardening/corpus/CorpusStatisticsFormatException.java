package io.github.tlaplus.hardening.corpus;

import java.io.IOException;

/** Reports a workflow-statistics file that does not follow the supported CBOR format. */
final class CorpusStatisticsFormatException extends IOException {
    CorpusStatisticsFormatException(String message) {
        super(message);
    }

    CorpusStatisticsFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
