# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.2] - 2026-10-06

### Fixed

- Every job resolves the dependencies in a step of its own, retried three times with a growing pause: Maven Central rate-limits
  shared runner addresses now and then, and the first release of a library failed on an HTTP 403 that a re-run did not repeat.
- `make deps` warms every alias on its own and the Babashka classpath; a single combined classpath did not.
- The release notes print the coordinate in the shape the README uses, ready to paste into `deps.edn`.

## [0.1.1] - 2026-10-06

### Fixed

- The reflection check ran the Clojure CLI with the aliases' `:main-opts`, so in a repository whose `:test` alias starts a
  test runner the program never ran. It now loads the namespaces from the classpath.
- The audit target passes the bill of materials to osv-scanner with `-L`, the replacement of the deprecated `--sbom`.

### Changed

- The formatter indents the body of a property test (`for-all`) like the body of a `let`.

## [0.1.0] - 2026-10-06

First release.

### Added

- Reusable GitHub workflows for a Clojure library: the quality gate, the release and the weekly scheduled checks.
- Babashka tasks: a CycloneDX bill of materials, a reflection check, a conventions check (documentation per parameter,
  arglist layout, line length), release checks and notes, and the stamping of the standard into a repository.
- Templates of the files every repository follows: license, security policy, contributing guide, code of conduct, issue and
  pull request templates, editor, formatter and lint configuration, `Makefile`, `build.clj` and the caller workflows.
- The standard and the decisions behind it.

[Unreleased]: https://github.com/AF2B/borba-tooling/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/AF2B/borba-tooling/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/AF2B/borba-tooling/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/AF2B/borba-tooling/releases/tag/v0.1.0
