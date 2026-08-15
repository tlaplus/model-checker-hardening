package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/** Releases an exclusive corpus lock and its backing file channel. */
public final class CorpusLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    CorpusLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
