package io.github.tlaplus.hardening;

import io.github.tlaplus.hardening.cli.FuzzTlaCommand;

class Main {
    void main(String[] args) {
        System.exit(FuzzTlaCommand.execute(args));
    }
}
