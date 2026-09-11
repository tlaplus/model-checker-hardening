package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** An exclusive corpus lock and its backing file channel, released together. */
public final class CorpusLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private CorpusLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Acquires the process-wide exclusive lock on {@code lockFile}, failing at once when another
     * process or this one holds it. {@code corpus} names the corpus in diagnostics.
     */
    static CorpusLock acquire(Path lockFile, Path corpus) throws IOException, CorpusException {
        var channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            var lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new CorpusException("corpus is already in use: " + corpus);
            }
            return new CorpusLock(channel, lock);
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new CorpusException(
                    "corpus is already locked by this process: " + corpus, exception);
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
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
