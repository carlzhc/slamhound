(ns slam.hound.stitch-test
  (:require [clojure.test :refer [deftest is testing]]
            [slam.hound.stitch :refer [imports-from-map
                                       keyword-list-from-map
                                       metadata-from-map
                                       ns-from-map
                                       refer-clojure-from-map
                                       requires-from-map
                                       stitch-up]]))

(deftest ^:unit test-keyword-list-from-map
  (is (= (keyword-list-from-map
           :gen-class '{:gen-class [:name Foo :extends Bar]})
         '(:gen-class :name Foo :extends Bar)))
  (is (nil? (keyword-list-from-map :foo '{:foo []}))))

(deftest ^:unit test-metadata-from-map
  (is (= (metadata-from-map {}) []))
  (is (= (metadata-from-map {:meta {:doc "foo"}}) ["foo"]))
  (is (= (metadata-from-map {:meta {:bar "bar"}}) [{:bar "bar"}]))
  (is (= (metadata-from-map {:meta {:doc "foo" :bar "bar"}})
         ["foo" {:bar "bar"}])))

(deftest ^:unit test-imports-from-map
  (is (= (imports-from-map '{:import #{java.util.BitSet
                                       java.util.Random
                                       java.io.File}})
         '(:import (java.io File)
                   (java.util BitSet Random))))
  (is (nil? (imports-from-map {:import #{}}))))

(deftest ^:unit test-requires-from-map
  (is (= (requires-from-map '{:import  #{}
                              :require #{clojure.xml}
                              :alias   {clojure.string string}
                              :refer   {clojure.string #{trim}
                                        clojure.set #{difference}
                                        clojure.java.shell :all}
                              :exclude {clojure.java.shell [with-sh-env]}
                              :rename  {clojure.java.shell {sh ssshhh}}
                              :verbose true
                              :reload  :all})
         '(:require [clojure.java.shell :refer :all :exclude [with-sh-env]
                     :rename {sh ssshhh}]
                    [clojure.set :refer [difference]]
                    [clojure.string :as string :refer [trim]]
                    [clojure.xml]
                    :reload-all :verbose)))
  (testing "sorting"
    (is (= (str (requires-from-map '{:rename {my.ns {c cc a aa b bb}}}))
           "(:require [my.ns :rename {a aa, b bb, c cc}])"))
    (is (= (requires-from-map '{:refer {my.ns [c b a]}})
           '(:require [my.ns :refer [a b c]]))))
  (testing "special handling of :refer {clojure.core :all}"
    (is (nil? (requires-from-map '{:refer {clojure.core :all}})))))

(deftest test-refer-clojure-from-map
  (is (nil? (refer-clojure-from-map '{:refer {clojure.core :all}})))
  (is (= (refer-clojure-from-map '{:refer {clojure.core :all}
                                   :rename {clojure.core {/ div}}
                                   :exclude {clojure.core [* + -]}})
         '(:refer-clojure :exclude [* + -] :rename {/ div})))
  (is (= (refer-clojure-from-map '{:refer {clojure.core [refer]}})
         '(:refer-clojure :only [refer])))
  (is (= (refer-clojure-from-map '{:refer {clojure.core []}})
         '(:refer-clojure :only []))) ; This is a common invocation
  (is (nil? (refer-clojure-from-map '{:refer {clojure.java.io [io]}}))))

(deftest ^:unit test-ns-from-map
  (is (= (ns-from-map '{:name      my.ns
                        :meta      {:doc "My example namespace."}
                        :import    #{java.util.BitSet java.util.Random}
                        :require   #{clojure.xml}
                        :alias     {clojure.string string}
                        :refer     {clojure.core [+ - * /]
                                    clojure.string #{trim}
                                    clojure.set #{difference}
                                    clojure.java.shell :all}
                        :exclude   {clojure.java.shell [with-sh-env]}
                        :rename    {clojure.java.shell {sh ssshhh}}
                        :verbose   true
                        :reload    :all
                        :gen-class [:name Foo]
                        :load      ["/foo" "/bar"]})
         '(ns my.ns
            "My example namespace."
            (:require [clojure.java.shell :refer :all :exclude [with-sh-env]
                       :rename {sh ssshhh}]
                      [clojure.set :refer [difference]]
                      [clojure.string :as string :refer [trim]]
                      [clojure.xml]
                      :reload-all :verbose)
            (:import (java.util BitSet Random))
            (:refer-clojure :only [* + - /])
            (:gen-class :name Foo)
            (:load "/foo" "/bar"))))
  (is (= (ns-from-map '{:name my.ns
                        :alias {clojure.string string
                                clojure.set set}
                        :refer {clojure.string [upper-case lower-case]}
                        :exclude {clojure.core [test compile]}})
         '(ns my.ns
            (:require [clojure.set :as set]
                      [clojure.string
                       :as string
                       :refer [lower-case upper-case]])
            (:refer-clojure :exclude [compile test])))))

(deftest ^:unit test-stitch-up
  (is (= "(ns slamhound.sample
  \"Testing some \\\"things\\\"\ngoing on here.\"
  {:slamhound-skip true, :zzz \"zzz\"}
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [slam.hound.stitch :refer [ns-from-map]])
  (:import (clojure.lang Compiler$BodyExpr)
           (java.io ByteArrayInputStream File)
           (java.util UUID))
  (:refer-clojure :exclude [compile test]))\n"
         (stitch-up '{:name slamhound.sample
                      :meta {:doc "Testing some \"things\"\ngoing on here."
                             :zzz "zzz"
                             :slamhound-skip true}
                      :import #{java.io.File
                                java.io.ByteArrayInputStream
                                clojure.lang.Compiler$BodyExpr
                                java.util.UUID}
                      :alias {clojure.java.io io
                              clojure.set set}
                      :refer {slam.hound.stitch #{ns-from-map}
                              clojure.test #{is deftest}}
                      :exclude {clojure.core #{compile test}}}))))
