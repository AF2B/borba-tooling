# The tasks of borba-tooling itself: the same targets the pipeline runs.

SHELL := /bin/bash
.DEFAULT_GOAL := help

CLOJURE           ?= clojure
BB                ?= bb
DOCKER            ?= docker
ACTIONLINT_IMAGE  ?= rhysd/actionlint:1.7.12
GITLEAKS_IMAGE    ?= ghcr.io/gitleaks/gitleaks:v8.30.1

.PHONY: help
help: ## List the targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: test
test: ## Run the tests
	$(BB) test

.PHONY: lint
lint: ## Lint with clj-kondo, failing on any warning
	$(CLOJURE) -M:lint

.PHONY: fmt-check
fmt-check: ## Check the formatting with cljfmt
	$(CLOJURE) -M:fmt check

.PHONY: fmt
fmt: ## Format the sources with cljfmt
	$(CLOJURE) -M:fmt fix

.PHONY: conventions
conventions: ## Check docstrings, arglist layout and line length
	$(BB) -x borba.tooling.conventions/check

.PHONY: workflows
workflows: ## Lint the workflows, including the shell inside them
	$(DOCKER) run --rm --volume "$(CURDIR):/repo:ro" --workdir /repo $(ACTIONLINT_IMAGE) -color

.PHONY: secret-scan
secret-scan: ## Scan the whole git history for committed secrets
	$(DOCKER) run --rm --volume "$(CURDIR):/repo:ro" $(GITLEAKS_IMAGE) \
		git /repo --redact --no-banner

.PHONY: check
check: test lint fmt-check conventions workflows ## What a change must pass before a push

.PHONY: ci
ci: check secret-scan ## Everything the pipeline enforces
