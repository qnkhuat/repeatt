(ns build
  (:require
   [clojure.tools.build.api :as b]))

(defmacro ignore-exceptions
  "Simple macro which wraps the given expression in a try/catch block and ignores the exception if caught."
  {:style/indent 0}
  [& body]
  `(try ~@body (catch Throwable ~'_)))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file "target/repeatt.jar")

(defn clean [_]
  (b/delete {:path "target/"}))

(defn- build-backend
  []
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      'repeatt.core})
  (println (format "Jar built: %s" uber-file)))

(defn uberjar [_]
  (println "Start to build")
  (clean nil)
  (build-backend)
  (println "Built successfully!"))
