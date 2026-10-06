(ns borba.tooling.stamp-test
  (:require
   [babashka.fs :as fs]
   [borba.tooling.stamp :as stamp]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(def ^:private variables
  {:repo            "borba-example-component"
   :lib             "io.github.af2b/borba-example-component"
   :description     "An example."
   :year            2026
   :tooling-sha     "0123456789abcdef0123456789abcdef01234567"
   :tooling-version "v0.1.0"
   :postgres        true
   :redis           false
   :kafka           false})

(deftest render-test
  (testing "fills every placeholder"
    (is (= "a borba b 2026"
           (stamp/render "a %%repo%% b %%year%%"
                         {:repo "borba" :year 2026}))))

  (testing "leaves GitHub expressions and other text alone"
    (is (= "${{ github.ref }} 100%"
           (stamp/render "${{ github.ref }} 100%" {}))))

  (testing "fails on a placeholder with no value"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no value"
                          (stamp/render "%%missing%%" {})))))

(deftest stamp-test
  (let [target (str (fs/create-temp-dir {:prefix "stamp-test"}))
        written (stamp/stamp {:target target :variables variables})]
    (testing "writes the standard files into the project"
      (is (some #{"Makefile"} written))
      (is (some #{".github/workflows/ci.yml"} written))
      (is (fs/exists? (fs/path target "LICENSE"))))

    (testing "fills in what is specific to the project"
      (let [ci (slurp (str (fs/path target ".github/workflows/ci.yml")))]
        (is (str/includes? ci "0123456789abcdef0123456789abcdef01234567"))
        (is (str/includes? ci "postgres: true"))
        (is (str/includes? ci "redis: false")))
      (is (str/includes? (slurp (str (fs/path target "CONTRIBUTING.md")))
                         "borba-example-component")))

    (testing "leaves no placeholder behind"
      (doseq [file (fs/glob target "**" {:hidden true})
              :when (fs/regular-file? file)]
        (is (not (re-find #"%%[a-z-]+%%" (slurp (str file))))
            (str "placeholder left in " file))))

    (testing "overwrites a file that exists, and is idempotent"
      (spit (str (fs/path target "Makefile")) "stale")
      (stamp/stamp {:target target :variables variables})
      (is (str/includes? (slurp (str (fs/path target "Makefile")))
                         ".DEFAULT_GOAL")))))
