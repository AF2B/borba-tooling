# borba-tooling

[![CI](https://github.com/AF2B/borba-tooling/actions/workflows/ci.yml/badge.svg)](https://github.com/AF2B/borba-tooling/actions/workflows/ci.yml)

The shared engineering standard of the Borba framework: the pipelines, the checks and the repository files that every
`borba-*` library follows, kept in one place so that sixteen repositories cannot drift apart.

A Borba repository does not carry its own pipeline logic. Its workflows are a few lines that call the reusable workflows
here, its `Makefile` runs the same targets on a laptop and in CI, and its `bb.edn` pins the version of these tools it follows.

## What is here

| Part | What it gives a repository |
|---|---|
| [`.github/workflows/clojure-lib.yml`](.github/workflows/clojure-lib.yml) | The quality gate: lint, format, conventions, reflection, unit tests on two JDKs, coverage floor, integration tests against PostgreSQL, Redis or Kafka, the jar build, a vulnerability scan and a secret scan |
| [`.github/workflows/clojure-lib-release.yml`](.github/workflows/clojure-lib-release.yml) | A release from a version tag: changelog check, jar, bill of materials, provenance attestation, GitHub release |
| [`.github/workflows/clojure-lib-scheduled.yml`](.github/workflows/clojure-lib-scheduled.yml) | The weekly run: outdated dependencies, newly published vulnerabilities, new secret patterns |
| [`src/borba/tooling`](src/borba/tooling) | The Babashka tasks behind the make targets |
| [`templates`](templates) | The files a repository gets: license, community files, editor and formatter configuration, lint rules, `Makefile`, `build.clj`, workflows |
| [`docs/standard.md`](docs/standard.md) | The standard itself, and the decisions behind it in [`docs/adr`](docs/adr) |

## The tasks

| Task | What it checks or makes |
|---|---|
| `borba.tooling.conventions/check` | Every public var is documented, parameter by parameter; a definition with three or more parameters puts one on each line; no line is longer than 80 columns |
| `borba.tooling.reflection/check` | The project's own namespaces load without a reflection warning |
| `borba.tooling.sbom/generate` | A CycloneDX 1.5 bill of materials of the resolved dependencies |
| `borba.tooling.release/check` and `/notes` | A version tag has a changelog section, and its release notes |
| `borba.tooling.stamp/run` | Copies the standard files into a repository |

## Adopting the standard

From a clone of this repository, at the version to follow:

```bash
bb -x borba.tooling.stamp/run --target ../borba-example-component \
   --description '"One line that says what the library is"' \
   --tooling-version v0.1.0 \
   --services '[postgres]'      # only the services its integration tests need
```

Then give the repository a `deps.edn` with the aliases of the [standard](docs/standard.md#depsedn) and run `make check`.

To move a repository to a newer version of the tooling, stamp it again from the newer commit: the standard files are
overwritten, and the repository's own code, tests, README and changelog are never touched.

## Development

```bash
make check      # tests, lint, formatting, conventions, workflow lint
make ci         # everything the pipeline enforces
```

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md); the tooling is checked by its own standard.

## License

[MIT](LICENSE)
