(ns borba.tooling.sbom-test
  (:require
   [borba.tooling.sbom :as sbom]
   [clojure.test :refer [deftest is testing]]))

(def ^:private libs
  {'org.clojure/clojure {:version  "1.12.6"
                         :manifest :mvn
                         :license  {:name "EPL-1.0"
                                    :url  "https://example.test/epl"}}
   'aero/aero           {:version "1.1.6" :manifest :mvn}
   'io.github.af2b/borba-core-component
   {:manifest :deps
    :git-url  "https://github.com/AF2B/borba-core-component"
    :git-tag  "v1.2.0"
    :git-sha  "0123456789abcdef"}})

(deftest purl-test
  (testing "a Maven artifact"
    (is (= "pkg:maven/org.clojure/clojure@1.12.6"
           (sbom/maven-purl "org.clojure" "clojure" "1.12.6"))))

  (testing "a library fetched from GitHub"
    (is (= "pkg:github/af2b/borba-core-component@abc"
           (sbom/git-purl "x/y"
                          "https://github.com/AF2B/borba-core-component.git"
                          "abc"))))

  (testing "a library fetched from anywhere else"
    (is (= "pkg:generic/x/y@abc"
           (sbom/git-purl "x/y" "https://gitlab.example.test/x/y" "abc")))))

(deftest component-test
  (testing "a Maven dependency carries its purl and license"
    (let [component (sbom/component ['org.clojure/clojure
                                     (libs 'org.clojure/clojure)])]
      (is (= "pkg:maven/org.clojure/clojure@1.12.6" (get component "purl")))
      (is (= "org.clojure" (get component "group")))
      (is (= [{"license" {"name" "EPL-1.0" "url" "https://example.test/epl"}}]
             (get component "licenses")))))

  (testing "a dependency without a license has no licenses entry"
    (is (not (contains? (sbom/component ['aero/aero (libs 'aero/aero)])
                        "licenses"))))

  (testing "a git dependency is named by its tag and located by its purl"
    (let [component (sbom/component
                     ['io.github.af2b/borba-core-component
                      (libs 'io.github.af2b/borba-core-component)])]
      (is (= "v1.2.0" (get component "version")))
      (is (= "pkg:github/af2b/borba-core-component@0123456789abcdef"
             (get component "purl")))))

  (testing "a dependency with no coordinate is left out"
    (is (nil? (sbom/component ['x/y {:manifest :local}])))))

(deftest serial-number-test
  (testing "is a urn:uuid, and the same for the same seed"
    (is (re-matches
         #"urn:uuid:[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"
         (sbom/serial-number "seed")))
    (is (= (sbom/serial-number "seed") (sbom/serial-number "seed")))
    (is (not= (sbom/serial-number "seed") (sbom/serial-number "other")))))

(deftest bom-test
  (let [document (sbom/bom {:lib       'io.github.af2b/borba-example
                            :version   "1.0.0"
                            :libs      libs
                            :timestamp "2026-10-06T00:00:00Z"})]
    (testing "describes the project it was built for"
      (is (= "CycloneDX" (get document "bomFormat")))
      (is (= "1.5" (get document "specVersion")))
      (is (= "pkg:maven/io.github.af2b/borba-example@1.0.0"
             (get-in document ["metadata" "component" "purl"]))))

    (testing "lists every dependency, sorted by purl"
      (let [purls (map #(get % "purl") (get document "components"))]
        (is (= 3 (count purls)))
        (is (= (sort purls) purls))))

    (testing "is the same document for the same inputs"
      (is (= document
             (sbom/bom {:lib       'io.github.af2b/borba-example
                        :version   "1.0.0"
                        :libs      libs
                        :timestamp "2026-10-06T00:00:00Z"}))))))
