(ns borba.tooling.release
  "Checks that a version tag can be released and extracts its release notes
   from the changelog."
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def ^:private changelog-file "CHANGELOG.md")
(def ^:private tag-pattern #"v(\d+\.\d+\.\d+)")
(def ^:private link-definition-pattern #"^\[[^\]]+\]:\s+\S+\s*$")
(def ^:private default-notes-output "target/release-notes.md")

(defn tag->version
  "Returns the version a tag names, or nil when the tag is not a version tag.
   - tag: a tag such as v1.2.3"
  [tag]
  (some-> (re-matches tag-pattern (str tag)) second))

(defn- section-heading-pattern
  "Returns the pattern of a changelog heading for one version.
   - version: a version such as 1.2.3"
  [version]
  (re-pattern (str "(?m)^## \\[" (java.util.regex.Pattern/quote version)
                   "\\] - \\d{4}-\\d{2}-\\d{2}[ \\t]*$")))

(defn- without-link-definitions
  "Drops the link reference definitions that close a changelog.
   - lines: the lines of a changelog section"
  [lines]
  (->> lines
       reverse
       (drop-while #(or (str/blank? %) (re-matches link-definition-pattern %)))
       reverse))

(defn changelog-section
  "Returns the body of the changelog section of a version, trimmed, or nil
   when the changelog has no such section.
   - changelog: the text of the changelog
   - version: a version such as 1.2.3"
  [changelog version]
  (let [heading (re-matcher (section-heading-pattern version) changelog)]
    (when (.find heading)
      (let [body    (subs changelog (.end heading))
            next-up (re-matcher #"(?m)^## \[" body)
            end     (if (.find next-up) (.start next-up) (count body))]
        (->> (subs body 0 end)
             str/split-lines
             without-link-definitions
             (str/join "\n")
             str/trim)))))

(defn problems
  "Returns what stops a tag from being released, as a vector of messages that
   is empty when the tag is releasable.
   - tag: the tag being released
   - changelog: the text of the changelog"
  [{:keys [tag changelog]}]
  (if-let [version (tag->version tag)]
    (let [section (changelog-section changelog version)]
      (cond
        (nil? section)
        [(str changelog-file " has no section for " version
              ", written as: ## [" version "] - YYYY-MM-DD")]

        (str/blank? section)
        [(str "the " changelog-file " section for " version " is empty")]

        :else []))
    [(str "'" tag "' is not a version tag, which looks like v1.2.3")]))

(defn- requested-tag
  "Returns the tag the task was asked about, defaulting to the one the CI
   environment is building.
   - tag: the tag given on the command line, if any"
  [tag]
  (str (or tag (System/getenv "GITHUB_REF_NAME"))))

(defn check
  "Fails with the list of problems when a tag cannot be released.
   - tag: the tag to check (default: GITHUB_REF_NAME)"
  [{:keys [tag]}]
  (let [tag     (requested-tag tag)
        found   (problems {:tag       tag
                           :changelog (slurp changelog-file)})]
    (if (seq found)
      (do (doseq [problem found]
            (println "release check failed:" problem))
          (System/exit 1))
      (println "release check passed for" tag))))

(defn notes
  "Writes the release notes of a tag, taken from the changelog.
   - tag: the tag being released (default: GITHUB_REF_NAME)
   - output: the file to write (default target/release-notes.md)"
  [{:keys [tag output] :or {output default-notes-output}}]
  (let [tag     (requested-tag tag)
        section (changelog-section (slurp changelog-file) (tag->version tag))]
    (when-not section
      (throw (ex-info "the changelog has no section for the tag"
                      {:tag tag})))
    (fs/create-dirs (fs/parent output))
    (spit output (str section "\n"))
    (println "release notes written to" output)))
