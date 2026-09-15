/**
 * Exports a corpus to a SQLite database for analysis, as ADR 0009 defines it.
 *
 * <p>The database is derived data: {@link io.github.tlaplus.hardening.database.CorpusExport}
 * rebuilds it from the corpus on every export, and nothing reads it back. The package depends on
 * {@code corpus} and {@code checker}, and only {@code cli} depends on it.
 */
package io.github.tlaplus.hardening.database;
