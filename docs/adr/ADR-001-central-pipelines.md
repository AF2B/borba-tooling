# ADR-001 — Central pipelines and stamped standard files

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

The Borba framework is sixteen small libraries. Each needs the same pipeline, the same lint and format rules, the same
community files and the same release procedure. Copying them into every repository means that the first improvement has to
be made sixteen times, and the second is made in fifteen. The repositories drift, and the one that drifted furthest is the one
whose pipeline nobody trusts.

## Decision

The standard lives in one repository, `borba-tooling`, and reaches the others in two ways.

- **Pipelines are reusable workflows.** A repository's `ci.yml`, `release.yml` and `scheduled.yml` are a few lines that call
  the workflows here, pinned by commit. The logic is written once; a repository upgrades by moving a pin.
- **Files the tools read from the repository root are stamped.** `.cljfmt.edn`, `.clj-kondo/config.edn`, `.editorconfig`, the
  `Makefile` and the community files must exist in each repository for the tools and for GitHub to find them. They are rendered
  from `templates/`, overwritten on upgrade, and never edited in place.
- **The tasks are Babashka namespaces**, consumed as a git dependency pinned in each repository's `bb.edn`. The `Makefile` calls
  them, so the pipeline and a laptop run the same commands.

The repository's own code, tests, README, changelog and `deps.edn` are never stamped.

## Consequences

- A change to the standard is one pull request here and one small pull request per repository to adopt it, instead of sixteen
  edits.
- A repository can lag behind and say exactly how far: its pins name the version it follows.
- The workflows call each other's inputs, so a repository that needs a service (PostgreSQL, Redis, Kafka) declares it when it
  is stamped, and the stamped workflow passes it on.
- Reusable workflows cannot reference themselves by commit, so the release workflow does not call the CI workflow; the
  repository's `release.yml` calls both, in order.
- A bug in a shared workflow can break every repository at once, which is the price of one place to fix it. Pinning by commit
  contains it: only a repository that moved its pin is affected.

## Alternatives considered

- **Copy the files into every repository.** Simple, and drifts. This is the situation this decision removes.
- **Organization-level workflows and a `.github` repository.** The account is a user, not an organization, so the features that
  would share these files do not exist, and a `.github` repository of defaults would silently change repositories that are not
  part of the framework.
- **A GitHub template repository.** It only helps at creation: it cannot upgrade a repository that already exists.
- **A Clojure library for the tasks instead of Babashka.** The tasks would start a JVM for a one-second job. They run in
  Babashka, which the pipeline installs anyway.
