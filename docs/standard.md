# The Borba standard for Clojure libraries

What every `borba-*` repository looks like, what its pipelines enforce, and how to keep sixteen of them in step. The reasoning
behind the larger choices is in the [decision records](adr).

## The contract of a repository

A repository follows the standard when it has these, and `make check` passes.

| File | Where it comes from |
|---|---|
| `deps.edn` with the [standard aliases](#depsedn) | The repository |
| `src/`, `test/`, `README.md`, `CHANGELOG.md` | The repository |
| `Makefile`, `bb.edn`, `build.clj`, `tests.edn` | Stamped from [`templates`](../templates) |
| `.editorconfig`, `.gitattributes`, `.gitignore`, `.cljfmt.edn`, `.clj-kondo/config.edn` | Stamped |
| `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` | Stamped |
| `.github/workflows/{ci,release,scheduled}.yml`, `dependabot.yml`, `CODEOWNERS`, issue and pull request templates | Stamped |

Stamped files are the standard's: they are overwritten when the repository adopts a newer version of the tooling, so change
them here, not there. The repository's own code, tests, README, changelog and `deps.edn` are never touched.

## deps.edn

Every repository declares these aliases, because the make targets call them.

```clojure
{:paths ["src"]

 :deps
 {org.clojure/clojure {:mvn/version "1.12.6"}}

 :aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps  {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
   :main-opts   ["-m" "kaocha.runner"]}

  :coverage
  {:extra-paths ["test"]
   :extra-deps  {lambdaisland/kaocha           {:mvn/version "1.91.1392"}
                 lambdaisland/kaocha-cloverage {:mvn/version "1.1.89"}}
   :main-opts   ["-m" "kaocha.runner" "--plugin" "cloverage"
                 "--cov-fail-threshold" "90"
                 "--cov-output" "target/coverage"
                 "--focus" ":unit"]}

  :lint
  {:replace-deps {clj-kondo/clj-kondo {:mvn/version "2026.08.04"}}
   :main-opts    ["-m" "clj-kondo.main" "--lint" "src" "test"
                  "--fail-level" "warning"]}

  :fmt
  {:replace-deps {dev.weavejester/cljfmt {:mvn/version "0.16.6"}}
   :main-opts    ["-m" "cljfmt.main"]}

  :build
  {:deps       {io.github.clojure/tools.build {:mvn/version "0.10.14"}}
   :ns-default build
   :exec-args  {:lib         io.github.af2b/borba-example-component
                :description "One line that says what the library is"}}

  :outdated
  {:replace-deps {com.github.liquidz/antq {:mvn/version "2.11.1276"}}
   :main-opts    ["-m" "antq.core" "--error-format"
                  "{{name}} {{version}} -> {{latest-version}}"]}}}
```

The coverage floor is a number a little below what the tests reach, so that a regression fails and noise does not. It is set
per repository and only goes up.

## The targets

`make help` lists them. They are the same on a laptop and in the pipeline.

| Target | What it does |
|---|---|
| `check` | `lint`, `fmt-check`, `conventions`, `reflection`, `test`, `coverage`: what a change must pass before a push |
| `ci` | `check`, then `audit`, `secret-scan` and `build`: everything the pipelines enforce |
| `lint` | clj-kondo, failing on any warning |
| `fmt-check`, `fmt` | cljfmt |
| `conventions` | Documentation per parameter, arglist layout, 80 columns |
| `reflection` | No reflection warning in the project's own namespaces |
| `test`, `test-integration` | The unit suite; the suite that needs the real service |
| `coverage` | The unit suite with cloverage and its floor |
| `sbom`, `audit` | A CycloneDX bill of materials, scanned by OSV-Scanner |
| `secret-scan` | gitleaks over the whole history |
| `build` | The library jar and its `pom.xml` |
| `outdated` | Dependencies with a newer version |
| `release-check` | A tag can be released: it is a version, and the changelog has its section |

## The pipelines

| Workflow | Runs on | Answers |
|---|---|---|
| **CI** | Pull requests, pushes to `main` | May this change be merged? Gate: **Quality gate** |
| **Release** | A `v*.*.*` tag | The gate again, then: is this release correct, and where was it built? |
| **Scheduled** | Weekly | Did something change without a commit? |

The jobs of the gate: lint and conventions; unit tests on JDK 21 and 25; coverage; integration tests against PostgreSQL,
Redis or Kafka when the repository declares them; the jar; a scan of the dependencies and of the history. The last job,
**Quality gate**, requires all of them, so branch protection names one check and survives the jobs changing.

Supply chain: every action is pinned by commit, with the version in a comment, and Dependabot proposes updates weekly. Nothing
runs on `pull_request_target`. Permissions are read-only unless a job needs more. A release carries a CycloneDX bill of
materials and an attestation of where it was built.

## Conventions the compiler and the formatter cannot check

- **Every public var is documented**, with a first line that stands alone and one line per parameter: `- name: what it is`.
  A destructured parameter lists the names it binds. A parameter that starts with an underscore needs none.
- **A definition with three or more parameters puts one on each line.**
- **No line is longer than 80 columns.**
- **No reflection.** Interop is type-hinted, and a namespace that uses it sets `*warn-on-reflection*`.
- **Named constants** instead of unexplained numbers and strings.
- **Failure is data.** An expected failure is a value with a stable `:error` keyword and context. Exceptions are for broken
  invariants and for the edge: I/O, network, database.
- **Imports are grouped and sorted** by `cljfmt` and clj-kondo; there is no `:use` and no `:refer :all`.

## Versions and the changelog

Versions follow [Semantic Versioning](https://semver.org/), and the changelog follows
[Keep a Changelog](https://keepachangelog.com/): a change a consumer can notice adds a line under `[Unreleased]`, and a
release moves those lines under a dated heading. A breaking change is marked `!` in its commit and says in the changelog what a
consumer has to do.

A library is consumed from git, with the tag and the commit, because that needs no registry and no credentials:

```clojure
io.github.af2b/borba-example-component
{:git/url "https://github.com/AF2B/borba-example-component"
 :git/tag "v1.0.0"
 :git/sha "<the commit of the tag>"}
```

The release notes print that coordinate, with the commit filled in.

## Settings that are not code

A pipeline cannot enforce these, so they are set on each repository and checked by hand when one is added:

- **Private vulnerability reporting** enabled, so `SECURITY.md` is true.
- A ruleset on the default branch that requires the **Quality gate** check, and one that protects `v*` tags.
- Secret scanning and push protection enabled.

## Adopting and upgrading

```bash
bb -x borba.tooling.stamp/run --target ../borba-example-component \
   --description '"One line that says what the library is"' \
   --tooling-version v0.1.0 --services '[postgres]'
```

Run it from a clone of this repository checked out at the version to follow; the stamped `bb.edn` and workflows pin that
commit. Review the diff, run `make check`, and commit it as `chore: adopt borba-tooling v0.1.0`.
