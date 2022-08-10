(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as build]))

(defn clean [_]
  (build/delete {:path "target"}))

(defn build-cljs [_]
  (let [java-command (-> (build/java-command {:main-args ["-e" (pr-str '(+ 1 2 3))]
                                              :basis (build/create-basis {})
                                              :main 'clojure.main})
                         (merge {:out :capture
                                 :err :capture}))]
    (build/process java-command)))

(defn compile [_]
  (build/javac {:src-dirs ["java"]
                :class-dir "target/classes"
                :basis (build/create-basis {:project "deps.edn"})}))

(comment

  (build-cljs nil)

  (clean nil)
  (compile nil)

  ,)
