# Contributing

## Set up

```bash
git clone https://github.com/AF2B/borba-tooling.git && cd borba-tooling
make check      # tests, lint, formatting, conventions and workflow lint
make ci         # everything the pipeline enforces, with the secret scan
```

`make help` lists every target. You need the Clojure CLI, Babashka and Docker (the workflow lint and the secret scan run in
pinned containers); the versions the pipeline uses are in `.github/workflows/ci.yml`.

## Branches and commits

- **Branches** are `<type>/<short-description>`: `feat/`, `fix/`, `refactor/`, `perf/`, `test/`, `docs/`, `build/`, `ci/` or
  `chore/`, for example `fix/body-limit`. One branch carries one intent.
- **Commits** follow [Conventional Commits](https://www.conventionalcommits.org/): a type, an optional scope, and an imperative
  title in lowercase without a final period, then a body of bullets that say what changed in the library and what that
  does. They are written in English, and they describe the code, never the process that produced it: no ticket numbers, no
  names, no "as discussed". A breaking change is marked with `!` after the type or a `BREAKING CHANGE:` footer.
- **Commit what is finished**, in pieces that each build and pass: a fix with its test, a refactor on its own.

## What a change needs

- **Tests.** Behaviour has tests; a bug fix has a test that failed before the fix.
- **Failure as data.** Expected failures are values with a stable `:error` keyword and context, not strings and not exceptions
  thrown from deep inside the logic. Exceptions are for broken invariants and for the edge (I/O, network, database).
- **Named constants** instead of unexplained numbers and strings; calls and definitions with three or more parameters, one per
  line.
- **Documentation** on every public var, parameter by parameter (`- name: what it is`), with a first line that stands alone.
  `make conventions` enforces it, together with the 80-column limit.
- **No reflection**: namespaces that use interop set `*warn-on-reflection*`.
- **No dead code**, no commented-out code, no `TODO` left behind.
- **The changelog.** A change a consumer can notice adds a line under `[Unreleased]` in `CHANGELOG.md`.

## Releasing

1. Move the `[Unreleased]` entries under a new `## [x.y.z] - YYYY-MM-DD` heading, add the link at the bottom of the file and
   commit it as `docs(changelog): release x.y.z`.
2. Tag and push: `git tag -a vx.y.z -m "Release x.y.z" && git push origin vx.y.z`.
3. Create the release from the changelog section: `gh release create vx.y.z --title vx.y.z --notes-file <the section>`.

Repositories adopt a release by stamping the standard from its tag; see the [standard](docs/standard.md#adopting-and-upgrading).

## Security

Report vulnerabilities privately, as [`SECURITY.md`](SECURITY.md) describes.
