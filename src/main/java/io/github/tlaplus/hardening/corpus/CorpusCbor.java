package io.github.tlaplus.hardening.corpus;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/** The Jackson wiring shared by every codec of the corpus CBOR formats. */
final class CorpusCbor {
    /** The one factory that reads and writes corpus documents. */
    static final CBORFactory FACTORY = new CBORFactory();

    private CorpusCbor() {}
}
