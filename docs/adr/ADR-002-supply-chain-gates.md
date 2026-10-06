# ADR-002 — Supply chain gates

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

A library is only as safe as what it resolves to, and a Clojure library's dependencies are named in `deps.edn`, which the usual
tools do not read: Dependabot has no tools.deps support, and a vulnerability scanner that looks for a lockfile finds none. The
gate has to be real and still reproducible on a laptop.

## Decision

- **A bill of materials is the interface between the build and the scanner.** `make sbom` asks the Clojure CLI for the
  resolved dependency set, including transitive ones, and writes it as CycloneDX 1.5 with package URLs. It is deterministic: the
  same dependency set gives the same document.
- **OSV-Scanner reads that bill of materials** from a container pinned by version, as the Swift engine's pipeline does, so the
  repository does not maintain a vulnerability client. `make audit` fails on a known vulnerability; a vulnerability that cannot
  be fixed yet is accepted in `osv-scanner.toml`, with a reason and an expiry date.
- **Secrets are scanned across the whole history** by gitleaks, from a pinned image, so a secret that was committed and deleted
  is still found.
- **Outdated dependencies are reported weekly**, as one open issue that is updated while there is something to update and
  closed when there is not, because Dependabot cannot do it for `deps.edn`.
- **The same checks run weekly with no commit**, because a vulnerability is published on its own schedule.
- **Releases carry the bill of materials and an attestation** of the commit and the workflow that built them.

## Consequences

- The scan covers Maven artifacts, which is where known vulnerabilities in Java libraries live. A library fetched from git is
  named in the bill of materials by its commit and is first-party code, not scanned for advisories.
- The scanner and the secret scanner need Docker, which the pipelines and most laptops have.
- The bill of materials is useful by itself: a consumer can feed it to their own tooling.

## Alternatives considered

- **A custom client of the OSV API.** Less to run, more to maintain and test, and one more place to be wrong. The scanner exists.
- **`nvd-clojure`.** Needs an NVD API key, which is a secret to manage in sixteen repositories, and matches by CPE, which
  produces false positives for Clojure libraries.
- **Committing a generated `pom.xml` so that Dependabot can read it.** It updates the `pom.xml` and not the `deps.edn`, so the
  two drift.
