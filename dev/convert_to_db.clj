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

(defn read-model
    [file]
(let [slurp-spn (requiring-resolve 'inferenceql.gpm.sppl/slurp)]
    (slurp-spn file)))


(let [model-path "examples/synthetic_control_arm.json"
    schema-path "examples/schema_synthetic_control_arm.edn"
    data-path "examples/synthetic_control_arm_nullified.csv"
    poststrat-data-path "examples/synthetic_control_arm_poststrat.csv"
    data (iql_io/slurp-csv data-path)
    poststrat_data (iql_io/slurp-csv poststrat-data-path)
    model (read-model model-path)
    db (-> (db/empty)
            (db/with-table 'data data)
            (db/with-table 'new_data poststrat_data)
            (db/with-model 'model model))
            ]
(spit "synthetic_control_arm_db.edn" (prn-str db)) 
            )

(let [model-path "examples/real_world_data.json"
    schema-path "examples/schema_real_world_data.edn"
    data-path "examples/real_world_data_nullified.csv"
    meps-data-path "examples/real_world_poststrat.csv"
    data (iql_io/slurp-csv data-path)
    meps-data (iql_io/slurp-csv meps-data-path)
    model (read-model model-path)
    db (-> (db/empty)
            (db/with-table 'data data)
            (db/with-table 'meps_data meps-data)
            (db/with-model 'model model))
            ]
(spit "real_world_data_db.edn" (prn-str db)) 
            )
