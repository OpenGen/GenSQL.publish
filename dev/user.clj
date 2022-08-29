(ns user
  (:import [java.io PushbackReader])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [inferenceql.inference.gpm :as gpm]
            [inferenceql.publish :as publish]
            [inferenceql.query.permissive :as permissive]
            #_[inferenceql.query.strict :as strict]))

(def db-path "/Users/zane/Downloads/repro/db.edn")
(def schema-path "examples/schema.edn")

(def system nil)

(defmacro with-reporting
  [s & body]
  `(do (print (str ~s "..."))
       (flush)
       (let [result# (do ~@body)]
         (println "done!")
         (flush)
         result#)))

(defn nilsafe
  "Returns a function that calls f on its argument if its argument is not nil."
  [f]
  (fn [x]
    (when x
      (f x))))

(defn new-system
  []
  (let [db (atom (edn/read {:readers gpm/readers} (PushbackReader. (io/reader db-path))))
        handler (publish/app :db db
                             :path "examples"
                             :schema-path schema-path
                             :execute permissive/query #_strict/query)]
    (publish/jetty-server :handler handler :port 8080)))

(defn init
  "Constructs the current development system."
  []
  (with-reporting "Initializing system"
    (alter-var-root #'system (fn [_] (new-system)))))

(defn start
  "Starts the current development system."
  []
  (with-reporting "Starting system"
    (alter-var-root #'system component/start)))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (with-reporting "Stopping system"
    (alter-var-root #'system (nilsafe component/stop))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/go))

(comment

  (go)
  (reset)
  (stop)
  (start)

  ,)
