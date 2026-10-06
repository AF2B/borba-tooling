(ns borba.tooling.basis
  "Reads the dependency set that a project resolves to."
  (:require
   [babashka.process :as process]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def ^:private list-command
  ["clojure" "-X:deps" "list" ":format" ":edn"])

(defn- list-output
  "Runs the dependency listing of the project in the working directory.
   - aliases: the aliases whose dependencies are included"
  [aliases]
  (let [command (cond-> list-command
                  (seq aliases) (into [":aliases" (pr-str (vec aliases))]))]
    (:out (apply process/shell {:out :string :err :inherit} command))))

(defn- normalize
  "Keeps what a bill of materials needs from one resolved dependency.
   - entry: a [lib coordinate] pair of the resolved dependency map"
  [[lib coordinate]]
  [lib {:version  (:mvn/version coordinate)
        :manifest (:deps/manifest coordinate)
        :license  (:license coordinate)
        :git-url  (:git/url coordinate)
        :git-tag  (:git/tag coordinate)
        :git-sha  (:git/sha coordinate)}])

(defn libs
  "Returns the libraries the project resolves to, as a map from the library
   symbol to its version, manifest type and license.
   - aliases: the aliases whose dependencies are included (default none)"
  ([]
   (libs nil))
  ([aliases]
   (into (sorted-map-by #(compare (str %1) (str %2)))
         (map normalize)
         (edn/read-string (list-output aliases)))))

(defn maven-libs
  "Returns only the libraries that come from a Maven repository.
   - resolved: a map as returned by `libs`"
  [resolved]
  (into (empty resolved)
        (filter (comp #{:mvn} :manifest val))
        resolved))

(defn group-id
  "Returns the Maven group of a library symbol, which is its name when the
   symbol has no namespace.
   - lib: a library symbol such as org.clojure/clojure"
  [lib]
  (or (namespace lib) (name lib)))

(defn artifact-id
  "Returns the Maven artifact of a library symbol.
   - lib: a library symbol such as org.clojure/clojure"
  [lib]
  (name lib))

(defn project-lib
  "Returns the library symbol the project publishes, read from the :lib of its
   build alias in deps.edn, or nil when it is not declared.
   - deps-file: the path of the project's deps.edn"
  [deps-file]
  (let [deps (edn/read-string (slurp deps-file))]
    (get-in deps [:aliases :build :exec-args :lib])))

(defn describe-version
  "Returns the version to stamp on a build: APP_VERSION when set, otherwise the
   output of git describe, otherwise dev."
  []
  (or (System/getenv "APP_VERSION")
      (let [result (process/shell {:out :string :err :string :continue true}
                                  "git" "describe" "--tags" "--always")]
        (when (zero? (:exit result))
          (str/replace (str/trim (:out result)) #"^v" "")))
      "dev"))
