# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-10-06

First release.

### Added

- Reusable GitHub workflows for a Clojure library: the quality gate, the release and the weekly scheduled checks.
- Babashka tasks: a CycloneDX bill of materials, a reflection check, a conventions check (documentation per parameter,
  arglist layout, line length), release checks and notes, and the stamping of the standard into a repository.
- Templates of the files every repository follows: license, security policy, contributing guide, code of conduct, issue and
  pull request templates, editor, formatter and lint configuration, `Makefile`, `build.clj` and the caller workflows.
- The standard and the decisions behind it.

[Unreleased]: https://github.com/AF2B/borba-tooling/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/AF2B/borba-tooling/releases/tag/v0.1.0
