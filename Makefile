SHELL := /bin/bash

.DEFAULT_GOAL := build

.PHONY: clean
clean:
	./gradlew clean

.PHONY: build
build:
	./gradlew build publishToMavenLocal

.PHONY: build-ci
build-ci:
	./gradlew check --build-cache --no-daemon

.PHONY: tag-if-release
tag-if-release:
	$(eval COMMITTED_FILES:=$(shell git diff-tree --no-commit-id --name-only -r HEAD))
	@if [[ "$(COMMITTED_FILES)" = *version.txt* ]]; then\
		$(eval VERSION:=$(shell cat version.txt))\
		echo "New version $(VERSION) was committed - creating tag.";\
		git config user.name github-actions;\
		git config user.email github-actions@github.com;\
		git remote set-url origin https://x-access-token:$(GH_TOKEN)@github.com/${GITHUB_REPOSITORY}.git;\
		git tag -a "$(VERSION)" -m "Version $(VERSION)";\
		git push origin "$(VERSION)";\
	else\
		echo "New version was not committed - skipping tag creation.";\
	fi

.PHONY: create-release-note
create-release-note:
	@echo "Creating Release Note"
	@echo "Changelog:" > RN.md
	@sed -n "/^### v${GIT_TAG_NAME}[[:space:]]*.*$$/,/###/p" CHANGELOG.md | sed '1d' | sed '$$d' | sed '$$d' >> RN.md

.PHONY: publish-gradle-plugin
publish-gradle-plugin:
	./gradlew --build-cache --no-daemon publishPlugins \
		-PreleaseVersion="$(GIT_TAG_NAME)" \
		-Pgradle.publish.key="$${GRADLE_PUBLISH_KEY}" \
		-Pgradle.publish.secret="$${GRADLE_PUBLISH_SECRET}"

.PHONY: validate-gradle-plugin
validate-gradle-plugin:
	./gradlew --build-cache --no-daemon publishPlugins --validate-only \
		-PreleaseVersion="$(GIT_TAG_NAME)" \
   		-Pgradle.publish.key="$${GRADLE_PUBLISH_KEY}" \
   		-Pgradle.publish.secret="$${GRADLE_PUBLISH_SECRET}"
