(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [inferenceql.notebook :as notebook]))

(def system nil)

(defn nilsafe
  "Returns a function that calls f on its argument if its argument is not nil."
  [f]
  (fn [x]
    (when x
      (f x))))

(defn new-system
  []
  (println "initializing new system")
  (let [handler (notebook/app :path "examples")]
    (notebook/jetty-server :handler handler :port 8080)))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system (fn [_] (new-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system (nilsafe component/stop)))

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


  (do (require '[inferenceql.query.db :as db])
      (require '[inferenceql.query.permissive :as permissive])

      (let [db (-> (db/empty)
                   (db/with-table 'satellites '[{x 0 y 1}])
                   (atom))]
        (permissive/query "select x from satellites limit 1" db)))

  ,)
