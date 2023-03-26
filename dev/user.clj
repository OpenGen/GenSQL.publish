(ns user
  (:import [java.io PushbackReader]
           [clojure.lang ExceptionInfo] 
           [java.io PushbackReader]
    )
  (:require [clojure.edn :as edn]
            [clojure.edn :as edn]
            [borkdude.dynaload :as dynaload]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [inferenceql.query.db :as db]
            [inferenceql.inference.gpm :as gpm]
            [inferenceql.query.io :as iql_io]
            [inferenceql.publish :as publish]
            [inferenceql.query.permissive :as permissive]
            #_[inferenceql.query.strict :as strict]))

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
    (let [model-path "examples/real_world_data.json"
      schema-path "examples/schema_real_world_data.edn"
      data-path "examples/real_world_data.csv"
      data (iql_io/slurp-csv data-path)
      model ('inferenceql.gpm.sppl/read-string model-path)
      db (-> (db/empty)
              (db/with-table 'data data)
              (db/with-model 'model model))
        handler (publish/app :db db
                             :path "examples/real_world_data.adoc"
                             :schema-path schema-path
                             :execute permissive/query #_strict/query)]
    (publish/jetty-server :handler handler :port 8080)))

;; (defn new-system
;;   []
;;   (let [db-path "examples/real_world_data_db.edn"
;;         schema-path "examples/schema_real_world_data.edn"
;;         db (atom (edn/read {:readers gpm/readers} (PushbackReader. (io/reader db-path))))
;;         handler (publish/app :db db
;;                               :path "examples/real_world_data.adoc"
;;                               :schema-path schema-path
;;                               :execute permissive/query #_strict/query)]
;;     (publish/jetty-server :handler handler :port 8080)))


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
  (stop)
  (start)
  (reset)

  ,)
