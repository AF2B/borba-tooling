(ns borba.tooling.conventions
  "Checks the conventions a compiler and a formatter cannot: every public var
   is documented, parameter by parameter; a definition with three or more
   parameters puts one on each line; and no line is longer than 80 columns."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [rewrite-clj.zip :as z]))

(set! *warn-on-reflection* true)

(def ^:private kondo-version "2026.08.04")
(def ^:private max-line-length 80)
(def ^:private minimum-vertical-parameters 3)
(def ^:private source-root "src")
(def ^:private test-root "test")
(def ^:private documented-definers
  #{"clojure.core/defn" "clojure.core/defmacro" "clojure.core/def"
    "clojure.core/defmulti" "clojure.core/defonce"})
(def ^:private function-definers '#{defn defn- defmacro})
(def ^:private binding-shorthand-names #{"keys" "strs" "syms"})
(def ^:private compiler-parameters '#{& &env &form})

;; ── Parameters ────────────────────────────────────────────────────────────

(declare binding-names)

(defn- shorthand-key?
  "Returns true when a destructuring map key lists names, such as :keys.
   - candidate: a key of a destructuring map"
  [candidate]
  (and (keyword? candidate) (binding-shorthand-names (name candidate))))

(defn- map-binding-names
  "Returns the names a map destructuring form binds.
   - form: the destructuring map, such as {:keys [a b] :as all}"
  [form]
  (let [listed   (->> form
                      (filter (comp shorthand-key? key))
                      (mapcat (comp #(map (comp symbol name) %) val)))
        explicit (->> (dissoc form :as :or)
                      (remove (comp shorthand-key? key))
                      keys
                      (mapcat binding-names))]
    (concat listed explicit (some-> (:as form) vector))))

(defn binding-names
  "Returns the names a binding form introduces, in order.
   - form: a symbol, a vector or a map destructuring form"
  [form]
  (cond
    (symbol? form) [form]
    (vector? form) (mapcat binding-names form)
    (map? form)    (map-binding-names form)
    :else          []))

(defn parameter-names
  "Returns the parameter names an arglist binds, without the ones that start
   with an underscore, which a function declares it ignores.
   - arglist: an arglist as data, such as [a {:keys [b]} & more]"
  [arglist]
  (->> (binding-names arglist)
       (remove compiler-parameters)
       (remove #(str/starts-with? (name %) "_"))
       distinct))

(defn- read-arglist
  "Reads an arglist from the text clj-kondo reports for it, ignoring the type
   hints it may carry.
   - text: the arglist as written, such as \"[^String s n]\""
  [text]
  (binding [*read-eval* false]
    (read-string text)))

;; ── Documentation ─────────────────────────────────────────────────────────

(defn undocumented-parameters
  "Returns the parameters a docstring does not describe on a line that starts
   with a dash, as in `- name: what it is`.
   - docstring: the docstring of the var
   - parameters: the parameter names to look for"
  [docstring parameters]
  (remove (fn [parameter]
            (re-find (re-pattern (str "(?m)^\\s*-\\s+"
                                      (java.util.regex.Pattern/quote
                                       (str parameter))
                                      ":"))
                     docstring))
          parameters))

(defn- definition-problem
  "Returns what is wrong with the documentation of one var, or nil.
   - definition: a var definition as reported by clj-kondo's analysis"
  [{:keys [doc arglist-strs]}]
  (let [parameters (->> arglist-strs
                        (mapcat (comp parameter-names read-arglist))
                        distinct)]
    (cond
      (str/blank? doc)
      "has no docstring"

      :else
      (when-let [missing (seq (undocumented-parameters doc parameters))]
        (str "does not describe " (str/join ", " missing)
             " (one line per parameter: - name: what it is)")))))

(defn documentation-problems
  "Returns a message for every public var that is not documented as required.
   - definitions: the var definitions of clj-kondo's analysis"
  [definitions]
  (for [definition definitions
        :when (and (not (:private definition))
                   (documented-definers (str (:defined-by definition))))
        :let  [problem (definition-problem definition)]
        :when problem]
    (str (:filename definition) ":" (:row definition) ": "
         (:ns definition) "/" (:name definition) " " problem)))

;; ── Layout ────────────────────────────────────────────────────────────────

(defn line-length-problems
  "Returns a message for every line longer than 80 columns.
   - filename: the file the text comes from
   - text: the contents of the file"
  [filename text]
  (for [[index line] (map-indexed vector (str/split-lines text))
        :when (> (count line) max-line-length)]
    (str filename ":" (inc index) ": line is " (count line)
         " columns, the limit is " max-line-length)))

(defn- arglist-vectors
  "Returns the arglist vector locations of a definition.
   - form: the zipper location of a defn, defn- or defmacro form"
  [form]
  (loop [sibling (z/right (z/down form)) found []]
    (cond
      (nil? sibling)
      found

      (z/vector? sibling)
      (conj found sibling)

      (and (z/list? sibling) (z/vector? (z/down sibling)))
      (recur (z/right sibling) (conj found (z/down sibling)))

      :else
      (recur (z/right sibling) found))))

(defn- vector-parameters
  "Returns the zipper locations of the parameters in an arglist vector,
   without the & marker.
   - arglist: the zipper location of an arglist vector"
  [arglist]
  (loop [child (z/down arglist) parameters []]
    (cond
      (nil? child)
      parameters

      (= '& (z/sexpr child))
      (recur (z/right child) parameters)

      :else
      (recur (z/right child) (conj parameters child)))))

(defn- layout-problem
  "Returns a message when an arglist of three or more parameters does not put
   each on its own line, or nil.
   - filename: the file the arglist is in
   - arglist: the zipper location of an arglist vector"
  [filename arglist]
  (let [parameters (vector-parameters arglist)
        rows       (map (comp first z/position) parameters)]
    (when (and (>= (count parameters) minimum-vertical-parameters)
               (not= (count rows) (count (distinct rows))))
      (str filename ":" (first (z/position arglist))
           ": " (count parameters)
           " parameters are not one per line"))))

(defn- top-level-forms
  "Returns the zipper locations of the top-level forms of a source text.
   - text: the source text"
  [text]
  (loop [location (z/of-string text {:track-position? true}) forms []]
    (if (nil? location)
      forms
      (recur (z/right location) (conj forms location)))))

(defn arglist-layout-problems
  "Returns a message for every definition whose arglist has three or more
   parameters that are not one per line.
   - filename: the file the text comes from
   - text: the contents of the file"
  [filename text]
  (for [form  (top-level-forms text)
        :when (and (z/list? form)
                   (function-definers (z/sexpr (z/down form))))
        arglist (arglist-vectors form)
        :let  [problem (layout-problem filename arglist)]
        :when problem]
    problem))

;; ── Running ───────────────────────────────────────────────────────────────

(defn- analyse
  "Returns clj-kondo's var definitions for the files under a directory.
   - directory: the source directory to analyse"
  [directory]
  (let [config (pr-str {:output   {:format :edn}
                        :analysis {:arglists true}})
        deps   (pr-str {:deps {'clj-kondo/clj-kondo
                               {:mvn/version kondo-version}}})
        result (process/shell {:out :string :err :string :continue true}
                              "clojure" "-Sdeps" deps "-M"
                              "-m" "clj-kondo.main"
                              "--lint" directory
                              "--config" config)]
    (get-in (edn/read-string (:out result)) [:analysis :var-definitions])))

(defn- clojure-files
  "Returns the Clojure source files under a directory.
   - directory: the directory to search"
  [directory]
  (if (fs/exists? directory)
    (map str (fs/glob directory "**.{clj,cljc,cljs}"))
    []))

(defn problems
  "Returns every convention problem of the project in the working directory.
   - source: the source directory (default src)
   - tests: the test directory (default test)"
  [{:keys [source tests] :or {source source-root tests test-root}}]
  (concat
   (documentation-problems (analyse source))
   (for [file (clojure-files source)
         problem (arglist-layout-problems file (slurp file))]
     problem)
   (for [file (concat (clojure-files source) (clojure-files tests))
         problem (line-length-problems file (slurp file))]
     problem)))

(defn check
  "Fails with every convention problem of the project.
   - options: a map with :source, the source directory (default src), and
     :tests, the test directory (default test)"
  [options]
  (let [found (sort (problems options))]
    (if (seq found)
      (do (run! println found)
          (println (count found) "convention problem(s)")
          (System/exit 1))
      (println "conventions respected"))))
