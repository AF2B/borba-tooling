(ns borba.tooling.sbom
  "Builds a CycloneDX software bill of materials for a Clojure project, from
   the dependency set its deps.edn resolves to."
  (:require
   [babashka.fs :as fs]
   [borba.tooling.basis :as basis]
   [cheshire.core :as json]
   [clojure.string :as str])
  (:import
   (java.security MessageDigest)
   (java.time Instant)))

(set! *warn-on-reflection* true)

(def ^:private spec-version "1.5")
(def ^:private default-output "target/sbom.cdx.json")
(def ^:private deps-file "deps.edn")
(def ^:private tool-name "borba-tooling")
(def ^:private project-license-id "MIT")
(def ^:private uuid-groups [8 4 4 4 12])
(def ^:private byte-mask 0xff)
(def ^:private github-url-pattern
  #"^https://github\.com/([^/]+)/([^/.]+?)(?:\.git)?/?$")

(defn maven-purl
  "Returns the package URL of a Maven artifact.
   - group: the Maven group
   - artifact: the Maven artifact
   - version: the version"
  [group
   artifact
   version]
  (str "pkg:maven/" group "/" artifact "@" version))

(defn git-purl
  "Returns the package URL of a library fetched from a git repository, or a
   generic one when the repository is not on GitHub.
   - library: the library name
   - url: the repository URL
   - revision: the commit SHA or tag"
  [library
   url
   revision]
  (if-let [[_ owner repository] (some->> url (re-matches github-url-pattern))]
    (str "pkg:github/" (str/lower-case owner) "/" repository "@" revision)
    (str "pkg:generic/" library "@" revision)))

(defn- license-entries
  "Returns the CycloneDX license entries of a resolved dependency.
   - license: the license map of the dependency, with :name and :url"
  [license]
  (when-let [license-name (:name license)]
    [{"license" (cond-> {"name" license-name}
                  (:url license) (assoc "url" (:url license)))}]))

(defn component
  "Returns the CycloneDX component of one resolved dependency, or nil when the
   dependency has no coordinate a bill of materials can name.
   - entry: a [lib coordinate] pair as produced by basis/libs"
  [entry]
  (let [[lib coordinate] entry
        {:keys [version git-sha git-tag git-url license]} coordinate
        group    (basis/group-id lib)
        artifact (basis/artifact-id lib)
        licenses (license-entries license)
        purl     (cond
                   version (maven-purl group artifact version)
                   git-sha (git-purl (str lib) git-url git-sha))]
    (when purl
      (cond-> {"type"    "library"
               "bom-ref" purl
               "group"   group
               "name"    artifact
               "version" (or version git-tag git-sha)
               "purl"    purl}
        licenses (assoc "licenses" licenses)))))

(defn- hex
  "Formats bytes as lowercase hexadecimal.
   - digest: the byte array to format"
  [digest]
  (apply str (map #(format "%02x" (bit-and byte-mask %)) digest)))

(defn- uuid-parts
  "Splits a hexadecimal string into the groups of a UUID.
   - digits: at least 32 hexadecimal characters"
  [digits]
  (->> (reductions + 0 uuid-groups)
       (partition 2 1)
       (map (fn [[start end]] (subs digits start end)))))

(defn serial-number
  "Returns a deterministic urn:uuid derived from what the bill describes, so
   that building the same dependency set twice gives the same document.
   - seed: the string the number is derived from"
  [seed]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String seed "UTF-8"))]
    (str "urn:uuid:" (str/join "-" (uuid-parts (hex digest))))))

(defn bom
  "Returns the CycloneDX document, as data, of a project.
   - lib: the library symbol the project publishes
   - version: the version being built
   - libs: the resolved dependencies, as produced by basis/libs
   - timestamp: the ISO-8601 instant to record"
  [{:keys [lib version libs timestamp]}]
  (let [components (->> libs (keep component) (sort-by #(get % "purl")) vec)
        group      (basis/group-id lib)
        artifact   (basis/artifact-id lib)
        purl       (maven-purl group artifact version)
        purls      (cons purl (map #(get % "purl") components))
        license    {"license" {"id" project-license-id}}]
    {"bomFormat"    "CycloneDX"
     "specVersion"  spec-version
     "serialNumber" (serial-number (str/join "\n" purls))
     "version"      1
     "metadata"     {"timestamp" timestamp
                     "tools"     {"components" [{"type" "application"
                                                 "name" tool-name}]}
                     "component" {"type"     "library"
                                  "bom-ref"  purl
                                  "group"    group
                                  "name"     artifact
                                  "version"  version
                                  "purl"     purl
                                  "licenses" [license]}}
     "components"   components}))

(defn- timestamp
  "Returns the instant to record: SOURCE_DATE_EPOCH when set, so a build can be
   reproduced, otherwise now."
  []
  (str (if-let [epoch (System/getenv "SOURCE_DATE_EPOCH")]
         (Instant/ofEpochSecond (parse-long epoch))
         (Instant/now))))

(defn generate
  "Writes the bill of materials of the project in the working directory and
   prints where it went.
   - output: the file to write (default target/sbom.cdx.json)
   - aliases: the aliases whose dependencies are included (default none)"
  [{:keys [output aliases] :or {output default-output}}]
  (let [lib (basis/project-lib deps-file)]
    (when-not lib
      (throw (ex-info "deps.edn declares no :lib in the :build alias"
                      {:file deps-file})))
    (fs/create-dirs (fs/parent output))
    (spit output
          (json/generate-string
           (bom {:lib       lib
                 :version   (basis/describe-version)
                 :libs      (basis/libs aliases)
                 :timestamp (timestamp)})
           {:pretty true}))
    (println "SBOM written to" output)))
