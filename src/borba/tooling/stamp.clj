(ns borba.tooling.stamp
  "Copies the shared repository standard, the files under templates/, into a
   project, filling in what is specific to it. Run from a clone of this
   repository at the commit the project should follow."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.string :as str])
  (:import
   (java.time LocalDate)))

(set! *warn-on-reflection* true)

(def ^:private template-root "templates")
(def ^:private placeholder-pattern #"%%([a-z-]+)%%")
(def ^:private library-group "io.github.af2b")
(def ^:private default-tooling-version "main")
(def ^:private known-services [:postgres :redis :kafka])

(defn render
  "Replaces every %%name%% in a template with its value, and fails when a
   placeholder has no value, so a typo cannot reach a repository.
   - text: the template
   - variables: a map from the placeholder name, as a keyword, to its value"
  [text variables]
  (str/replace text placeholder-pattern
               (fn [[_ placeholder]]
                 (if-some [value (get variables (keyword placeholder))]
                   (str value)
                   (throw (ex-info "a placeholder has no value"
                                   {:placeholder placeholder}))))))

(defn- template-files
  "Returns the files of the template tree, relative to its root."
  []
  (->> (fs/glob template-root "**" {:hidden true})
       (filter fs/regular-file?)
       (map #(fs/relativize template-root %))
       sort))

(defn stamp
  "Writes the standard files into a project, overwriting the ones that exist,
   and returns the paths written.
   - target: the project directory
   - variables: the values of the placeholders"
  [{:keys [target variables]}]
  (vec
   (for [relative (template-files)
         :let [destination (fs/path target relative)
               text        (slurp (str (fs/path template-root relative)))]]
     (do (fs/create-dirs (fs/parent destination))
         (spit (str destination) (render text variables))
         (str relative)))))

(defn- head-sha
  "Returns the commit this repository is at."
  []
  (str/trim (:out (process/shell {:out :string} "git" "rev-parse" "HEAD"))))

(defn- service-names
  "Returns the names of the services as keywords. From the command line they
   are a string, such as \"[postgres redis]\" or \"postgres,redis\", because
   `bb -x` does not read a vector; from a program they are a collection.
   - services: a string, or a collection of strings, symbols or keywords"
  [services]
  (if (string? services)
    (map keyword (remove str/blank? (str/split services #"[\s,\[\]]+")))
    (map keyword services)))

(defn- service-flags
  "Returns, for each service the pipelines can start, whether a project uses it.
   Fails naming a service that the pipelines cannot start, so that a typo is not
   a pipeline that quietly leaves its integration tests out.
   - services: the services the project's integration tests need"
  [services]
  (let [needed  (set (service-names services))
        unknown (sort (remove (set known-services) needed))]
    (when (seq unknown)
      (throw (ex-info (str "unknown services: "
                           (str/join ", " (map name unknown))
                           "; the pipelines can start "
                           (str/join ", " (map name known-services)))
                      {:unknown (vec unknown)
                       :known   known-services})))
    (into {} (map (fn [service] [service (contains? needed service)]))
          known-services)))

(defn run
  "Stamps the standard into a project directory and prints what was written.
   - target: the project directory
   - description: the one-line description of the project
   - tooling-version: the version tag of this repository the project follows
     (default main)
   - services: the services its integration tests need, from postgres, redis
     and kafka (default none)"
  [{:keys [target description tooling-version services]
    :or   {tooling-version default-tooling-version}}]
  (let [repository (str (fs/file-name target))
        variables  (merge
                    {:repo            repository
                     :lib             (str library-group "/" repository)
                     :description     description
                     :year            (.getYear (LocalDate/now))
                     :tooling-sha     (head-sha)
                     :tooling-version tooling-version}
                    (service-flags services))
        written    (stamp {:target (str target) :variables variables})]
    (println "stamped" (count written) "files into" (str target))
    (run! #(println " " %) written)))
