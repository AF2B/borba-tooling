(ns borba.tooling.release-test
  (:require
   [borba.tooling.release :as release]
   [clojure.test :refer [deftest is testing]]))

(def ^:private changelog
  "# Changelog

## [Unreleased]

### Added

- Something not released yet.

## [1.2.0] - 2026-10-06

### Added

- A new thing.

## [1.1.0] - 2026-09-01

### Fixed

- An old thing.

[Unreleased]: https://example.test/compare/v1.2.0...HEAD
[1.2.0]: https://example.test/compare/v1.1.0...v1.2.0
[1.1.0]: https://example.test/releases/tag/v1.1.0
")

(deftest tag->version-test
  (testing "a version tag names its version"
    (is (= "1.2.3" (release/tag->version "v1.2.3")))
    (is (= "10.0.1" (release/tag->version 'v10.0.1))))

  (testing "anything else names none"
    (is (nil? (release/tag->version "1.2.3")))
    (is (nil? (release/tag->version "v1.2")))
    (is (nil? (release/tag->version "v1.2.3-rc1")))
    (is (nil? (release/tag->version "release-1")))))

(deftest changelog-section-test
  (testing "returns the body of a section, up to the next heading"
    (is (= "### Added\n\n- A new thing."
           (release/changelog-section changelog "1.2.0")))
    (is (= "### Fixed\n\n- An old thing."
           (release/changelog-section changelog "1.1.0"))))

  (testing "does not include the link definitions that close the file"
    (is (not (re-find #"example\.test"
                      (release/changelog-section changelog "1.1.0")))))

  (testing "returns nil for a version without a section"
    (is (nil? (release/changelog-section changelog "9.9.9")))
    (is (nil? (release/changelog-section changelog "1.2")))))

(deftest problems-test
  (testing "a tag with a filled section has no problems"
    (is (empty? (release/problems {:tag       "v1.2.0"
                                   :changelog changelog}))))

  (testing "a tag that is not a version is refused"
    (is (= 1 (count (release/problems {:tag       "latest"
                                       :changelog changelog})))))

  (testing "a version without a changelog section is refused"
    (let [[problem] (release/problems {:tag       "v2.0.0"
                                       :changelog changelog})]
      (is (re-find #"no section for 2\.0\.0" problem))))

  (testing "an empty section is refused"
    (let [empty-changelog "## [3.0.0] - 2026-10-06\n\n## [2.0.0] - 2026-01-01\n"
          [problem]       (release/problems {:tag       "v3.0.0"
                                             :changelog empty-changelog})]
      (is (re-find #"section for 3\.0\.0 is empty" problem)))))
