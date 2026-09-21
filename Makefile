MVN ?= mvn
ARGS ?=
COMMUNITY_MODULES_REPO ?= https://github.com/tlaplus/CommunityModules
COMMUNITY_MODULES_DIR ?= libraries/community-modules

.PHONY: compile test package verify run clean community-modules

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
