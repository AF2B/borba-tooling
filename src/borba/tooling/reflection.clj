(ns borba.tooling.reflection
  "Fails when loading a project's own namespaces reports reflection, which is
   the slow path of Java interop and is fixed with a type hint."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.string :as str]))

(def ^:private source-root "src")
(def ^:private default-aliases [:test])
(def ^:private warning-pattern
  #"Reflection warning, (\S+?):(\d+):(\d+) - (.*)")

(defn path->namespace
  "Returns the namespace a source file defines, derived from its path.
   - root: the source root the path is relative to
   - path: the path of the source file"
  [root path]
  (-> (str (fs/relativize root path))
      (str/replace #"\.cljc?$" "")
      (str/replace "/" ".")
      (str/replace "_" "-")
      symbol))

(defn project-namespaces
  "Returns the namespaces defined under a source root, sorted.
   - root: the source root to scan"
  [root]
  (->> (fs/glob root "**.{clj,cljc}")
       (map #(path->namespace root %))
       sort
       vec))

(defn own-warnings
  "Returns the reflection warnings reported for files of the project itself,
   ignoring those of its dependencies.
   - output: the text the JVM wrote to its error stream
   - root: the project's source root"
  [output root]
  (->> (str/split-lines output)
       (keep #(re-find warning-pattern %))
       (filter (fn [[_ file]] (fs/exists? (fs/path root file))))
       (map first)))

(defn check
  "Loads every namespace of the project and fails when one reflects.
   - aliases: the aliases whose dependencies are on the classpath
     (default [:test])"
  [{:keys [aliases] :or {aliases default-aliases}}]
  (let [namespaces (project-namespaces source-root)
        program    (str "(set! *warn-on-reflection* true)"
                        "(doseq [n '" (pr-str namespaces) "] (require n))")
        result     (process/shell {:out :inherit :err :string :continue true}
                                  "clojure"
                                  (str "-A" (str/join aliases))
                                  "-M"
                                  "-e"
                                  program)
        warnings   (own-warnings (:err result) source-root)]
    (when-not (zero? (:exit result))
      (println (:err result))
      (System/exit (:exit result)))
    (if (seq warnings)
      (do (run! println warnings)
          (System/exit 1))
      (println "no reflection in" (count namespaces) "namespaces"))))
