MVN ?= mvn
ARGS ?=

.PHONY: compile test package verify run clean

compile:
	$(MVN) compile

test:
	$(MVN) test

package:
	$(MVN) package

verify:
	$(MVN) verify

run: package
	bin/fuzztla $(ARGS)

clean:
	$(MVN) clean
