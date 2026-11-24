MVN ?= mvn
ARGS ?=
TLAPM ?= tlapm
COMMUNITY_MODULES_REPO ?= https://github.com/tlaplus/CommunityModules
COMMUNITY_MODULES_DIR ?= libraries/community-modules
ATLAS_OUTPUT ?= target/architecture
MKDOCS ?= mkdocs
HARDENING_REPO ?= https://github.com/tlaplus/model-checker-hardening
APALACHE_REPO ?= https://github.com/apalache-mc/apalache
MANYAN_REPO ?= https://github.com/soasme/manyan
# Earlier checker JARs for `fuzztla init --tool` (ADR 0032); `make clean` removes them.
CHECKERS_DIR ?= target/checkers
# A release tag of the tlc-build workflow, tlc-YYYYMMDD-<sha12>.
TLC_BUILD ?=
# An Apalache release version, such as 0.62.0.
APALACHE_VERSION ?=
# The Manyan revision that manyan-build builds (ADR 0033): a commit or a tag.
MANYAN_REV ?= a9ca4f38028f4e8b4cc3f016167d071b55e3abe0

.PHONY: compile test package verify run clean proofs community-modules tlc-build apalache-release \
	manyan-build architecture user-manual

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

# Builds the architecture atlas (ADR 0022) into $(ATLAS_OUTPUT); open its index.html in a browser.
# Source links name the commit of HEAD.
architecture:
	$(MVN) -q test-compile dependency:build-classpath -Dmdep.outputFile=target/atlas-classpath.txt
	java -cp "target/test-classes:target/classes:$$(cat target/atlas-classpath.txt)" \
		io.github.tlaplus.hardening.architecture.AtlasMain \
		--output $(ATLAS_OUTPUT) --commit "$$(git rev-parse HEAD)"

# Builds the user manual from docs/manual into target/user (mkdocs.yml). Needs
# python3 -m pip install -r script/requirements-docs.txt.
user-manual:
	$(MKDOCS) build --strict

# Proves every rewrite rule (ADR 0017 §7) and every temporal rewrite rule (ADR 0027 §1).
# Variants resolves from Apalache's standard modules.
proofs:
	@mkdir -p target/tlapm
	$(TLAPM) --cache-dir target/tlapm -I src/main/resources/tla2sany/StandardModules \
		libraries/rewrites/RewritesProofs.tla libraries/rewrites/TemporalRewritesProofs.tla

# Downloads the latest CommunityModules.jar release and records its tag and commit in VERSION.
# Phony, so every run fetches the latest release; a corpus keeps the copy made by
# `fuzztla init --library libraries/community-modules.toml`.
community-modules:
	@mkdir -p $(COMMUNITY_MODULES_DIR)
	@set -eu; \
	tag=$$(curl -fsSI $(COMMUNITY_MODULES_REPO)/releases/latest \
		| sed -n 's|^[Ll]ocation: .*/releases/tag/\([^[:space:]]*\).*|\1|p'); \
	test -n "$$tag" || { echo "cannot determine the latest CommunityModules release" >&2; exit 1; }; \
	commit=$$(git ls-remote $(COMMUNITY_MODULES_REPO) "refs/tags/$$tag" "refs/tags/$$tag^{}" \
		| awk '{ sha = $$1 } END { print sha }'); \
	curl -fsSL -o $(COMMUNITY_MODULES_DIR)/CommunityModules.jar.part \
		$(COMMUNITY_MODULES_REPO)/releases/download/$$tag/CommunityModules.jar; \
	mv $(COMMUNITY_MODULES_DIR)/CommunityModules.jar.part $(COMMUNITY_MODULES_DIR)/CommunityModules.jar; \
	printf 'tag %s\ncommit %s\n' "$$tag" "$$commit" > $(COMMUNITY_MODULES_DIR)/VERSION; \
	echo "CommunityModules $$tag ($$commit) in $(COMMUNITY_MODULES_DIR)"

# Downloads the TLC build TLC_BUILD, which the tlc-build workflow published (ADR 0032),
# into $(CHECKERS_DIR) and verifies its checksum. It is not the latest TLC.
tlc-build:
	@set -eu; \
	echo "$(TLC_BUILD)" | grep -Eqx 'tlc-[0-9]{8}-[0-9a-f]{12}' \
		|| { echo "set TLC_BUILD to a release tag tlc-YYYYMMDD-<sha12>" >&2; exit 1; }; \
	jar=$(CHECKERS_DIR)/tla2tools-$(TLC_BUILD:tlc-%=%).jar; \
	url=$(HARDENING_REPO)/releases/download/$(TLC_BUILD)/tla2tools-$(TLC_BUILD:tlc-%=%).jar; \
	mkdir -p $(CHECKERS_DIR); \
	curl -fsSL -o $$jar.part $$url; \
	curl -fsSL -o $$jar.sha256 $$url.sha256; \
	expected=$$(cut -d ' ' -f 1 $$jar.sha256); \
	actual=$$(sha256sum $$jar.part | cut -d ' ' -f 1); \
	test "$$expected" = "$$actual" \
		|| { echo "checksum mismatch for $$jar.part: expected $$expected, got $$actual" >&2; exit 1; }; \
	mv $$jar.part $$jar; \
	echo "TLC $(TLC_BUILD) in $$jar"

# Downloads the Apalache release APALACHE_VERSION and extracts its JAR as
# $(CHECKERS_DIR)/apalache-$(APALACHE_VERSION).jar. Needs GNU tar.
apalache-release:
	@set -eu; \
	test -n "$(APALACHE_VERSION)" || { echo "set APALACHE_VERSION, such as 0.62.0" >&2; exit 1; }; \
	jar=$(CHECKERS_DIR)/apalache-$(APALACHE_VERSION).jar; \
	mkdir -p $(CHECKERS_DIR); \
	curl -fsSL $(APALACHE_REPO)/releases/download/v$(APALACHE_VERSION)/apalache-$(APALACHE_VERSION).tgz \
		| tar -xzO --wildcards '*/lib/apalache.jar' > $$jar.part; \
	test -s $$jar.part || { echo "no lib/apalache.jar in Apalache $(APALACHE_VERSION)" >&2; exit 1; }; \
	mv $$jar.part $$jar; \
	echo "Apalache $(APALACHE_VERSION) in $$jar"

# Builds Manyan at MANYAN_REV from a clone in $(CHECKERS_DIR)/manyan-src, as
# $(CHECKERS_DIR)/manyan-<rev12>, for the generic checker (ADR 0033). A plain build: Manyan's
# profile-guided build would run its test suites. Needs git and a C11 compiler.
manyan-build:
	@set -eu; \
	src=$(CHECKERS_DIR)/manyan-src; \
	mkdir -p $(CHECKERS_DIR); \
	test -d $$src/.git || git clone -q $(MANYAN_REPO) $$src; \
	git -C $$src fetch -q --tags origin; \
	git -C $$src checkout -q --detach $(MANYAN_REV); \
	rev=$$(git -C $$src rev-parse HEAD); \
	$(MAKE) -B -C $$src manyan PGO_TOOLS=; \
	binary=$(CHECKERS_DIR)/manyan-$$(printf %.12s $$rev); \
	cp $$src/manyan $$binary; \
	echo "Manyan $$rev in $$binary"
