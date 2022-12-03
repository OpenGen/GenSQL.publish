(ns user
  (:import [clojure.lang ExceptionInfo]
           [java.io PushbackReader])
  (:require [borkdude.dynaload :as dynaload]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [inferenceql.inference.gpm :as gpm]
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

(defn sppl-read-string
  [s]
  (try
    (let [read-string (dynaload/dynaload 'inferenceql.gpm.sppl/read-string)]
      (read-string s))
    (catch ExceptionInfo e
      (throw (ex-info "Could not resolve inferenceql.gpm.sppl/read-string. Is inferenceql.gpm.sppl on the classpath?"
                      {}
                      e)))))

(defn new-system
  []
  (let [db-path "examples/db-stackoverflow-sppl.edn"
        schema-path "examples/schema-stackoverflow.edn"
        ;; db (atom (edn/read {:readers gpm/readers} (PushbackReader. (io/reader db-path))))
        db (atom (edn/read {:readers (assoc gpm/readers 'inferenceql.gpm.spe/SPE sppl-read-string)}
        (PushbackReader. (io/reader db-path))))
        handler (publish/app :db db
                             :path "examples/natural-language.adoc"
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
  (stop)
  (start)
  (reset)

  ,)
