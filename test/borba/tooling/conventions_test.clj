(ns borba.tooling.conventions-test
  (:require
   [borba.tooling.conventions :as conventions]
   [clojure.test :refer [deftest is testing]]))

(deftest parameter-names-test
  (testing "plain symbols"
    (is (= '(a b) (conventions/parameter-names '[a b]))))

  (testing "the rest marker is not a parameter, its binding is"
    (is (= '(a more) (conventions/parameter-names '[a & more]))))

  (testing "map destructuring names its keys and its alias"
    (is (= '(b c opts)
           (conventions/parameter-names '[{:keys [b c] :as opts}]))))

  (testing "namespaced keys are named without the namespace"
    (is (= '(b) (conventions/parameter-names '[{:keys [a/b]}]))))

  (testing "a map entry names its local binding"
    (is (= '(local) (conventions/parameter-names '[{local :key}]))))

  (testing "vector destructuring names every element"
    (is (= '(a b c) (conventions/parameter-names '[[a b] c]))))

  (testing "a parameter the function declares unused is not documented"
    (is (= '(x) (conventions/parameter-names '[_ x _ignored])))))

(deftest undocumented-parameters-test
  (let [docstring "Does a thing.\n   - a: the first\n   - b: the second"]
    (is (empty? (conventions/undocumented-parameters docstring '[a b])))
    (is (= '(c) (conventions/undocumented-parameters docstring '[a c])))
    (is (= '(a) (conventions/undocumented-parameters "Mentions a, no list."
                                                     '[a])))))

(defn- definition
  [overrides]
  (merge {:filename     "src/a.clj"
          :row          3
          :ns           'a
          :name         'f
          :defined-by   'clojure.core/defn
          :private      false
          :doc          "Does a thing.\n   - x: the x"
          :arglist-strs ["[x]"]}
         overrides))

(deftest documentation-problems-test
  (testing "a documented public function has no problems"
    (is (empty? (conventions/documentation-problems [(definition {})]))))

  (testing "a missing docstring is reported with its location"
    (is (= ["src/a.clj:3: a/f has no docstring"]
           (conventions/documentation-problems [(definition {:doc nil})]))))

  (testing "an undescribed parameter is reported by name"
    (let [[problem] (conventions/documentation-problems
                     [(definition {:arglist-strs ["[x y]"]})])]
      (is (re-find #"does not describe y" problem))))

  (testing "a private var needs no documentation"
    (is (empty? (conventions/documentation-problems
                 [(definition {:doc nil :private true})]))))

  (testing "a var a record defines is not checked"
    (is (empty? (conventions/documentation-problems
                 [(definition {:doc        nil
                               :defined-by 'clojure.core/defrecord})]))))

  (testing "a type hint on a parameter does not hide it"
    (let [[problem] (conventions/documentation-problems
                     [(definition {:arglist-strs ["[^String x ^long n]"]})])]
      (is (re-find #"does not describe n" problem)))))

(deftest line-length-problems-test
  (is (empty? (conventions/line-length-problems
               "a.clj"
               (apply str (repeat 80 "x")))))
  (is (= ["a.clj:2: line is 81 columns, the limit is 80"]
         (conventions/line-length-problems
          "a.clj"
          (str "short\n" (apply str (repeat 81 "x")) "\n")))))

(deftest arglist-layout-problems-test
  (testing "three parameters on one line are reported"
    (is (= ["a.clj:1: 3 parameters are not one per line"]
           (conventions/arglist-layout-problems
            "a.clj" "(defn f [a b c] nil)"))))

  (testing "three parameters, one per line, are fine"
    (is (empty? (conventions/arglist-layout-problems
                 "a.clj" "(defn f\n  [a\n   b\n   c]\n  nil)"))))

  (testing "two parameters may share a line"
    (is (empty? (conventions/arglist-layout-problems
                 "a.clj" "(defn f [a b] nil)"))))

  (testing "the rest marker is not counted"
    (is (empty? (conventions/arglist-layout-problems
                 "a.clj" "(defn f [a & more] nil)"))))

  (testing "every arity of a multi-arity function is checked"
    (is (= ["a.clj:3: 3 parameters are not one per line"]
           (conventions/arglist-layout-problems
            "a.clj" "(defn g\n  ([a] a)\n  ([a b c] a))"))))

  (testing "a vector in a body is not an arglist"
    (is (empty? (conventions/arglist-layout-problems
                 "a.clj" "(defn f [a]\n  (let [x 1 y 2 z 3] x))")))))
