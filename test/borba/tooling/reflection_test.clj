(ns borba.tooling.reflection-test
  (:require
   [babashka.fs :as fs]
   [borba.tooling.reflection :as reflection]
   [clojure.test :refer [deftest is testing]]))

(deftest path->namespace-test
  (testing "derives the namespace from the path under the source root"
    (is (= 'borba.core.main
           (reflection/path->namespace "src" "src/borba/core/main.clj")))
    (is (= 'borba.sql-client
           (reflection/path->namespace "src" "src/borba/sql_client.clj")))
    (is (= 'borba.util
           (reflection/path->namespace "src" "src/borba/util.cljc")))))

(deftest own-warnings-test
  (let [root   (str (fs/create-temp-dir {:prefix "reflection-test"}))
        _      (fs/create-dirs (fs/path root "borba"))
        _      (spit (str (fs/path root "borba/core.clj")) "(ns borba.core)")
        output (str "Reflection warning, borba/core.clj:12:3 - call to"
                    " method foo can't be resolved.\n"
                    "Reflection warning, io/pedestal/http.clj:99:1 - call to"
                    " method bar can't be resolved.\n"
                    "some other line\n")]
    (testing "keeps the warnings of the project's own files"
      (is (= 1 (count (reflection/own-warnings output root))))
      (is (re-find #"borba/core\.clj:12:3"
                   (first (reflection/own-warnings output root)))))

    (testing "ignores the warnings of the dependencies"
      (is (empty? (reflection/own-warnings
                   "Reflection warning, io/pedestal/http.clj:99:1 - call"
                   root))))))
