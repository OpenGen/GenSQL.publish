(ns inferenceql.gpm.metaprob
  (:require [clojure.core :exclude [replicate]]
            [clojure.math :as math]
            [clojure.test :refer [deftest is]]
            [inferenceql.inference.gpm.conditioned :as conditioned]
            [inferenceql.inference.gpm.constrained :as constrained]
            [inferenceql.inference.gpm.proto :as gpm.proto]
            [inferenceql.query.db :as db]
            [inferenceql.query.permissive :as permissive]
            [metaprob.distributions :as distributions]
            [metaprob.generative-functions :as gen :refer [gen let-traced]]
            [metaprob.inference :as inference]
            [metaprob.prelude :as metaprob]))

(defn importance-sampling-score
  [& {:keys [model inputs observation-trace n-particles]
      :or {n-particles 1, inputs [], observation-trace {}}}]
  (let [scores (metaprob/replicate
                n-particles
                (fn []
                  (let [[_ _ s] (metaprob/infer-and-score :procedure model
                                                          :observation-trace observation-trace
                                                          :inputs inputs)]
                    s)))]
    (distributions/logmeanexp scores)))

(defn map->trace
  [m]
  (-> m
      (update-keys name)
      (update-vals #(hash-map :value %))))

(defrecord Metaprob [model variables n-particles]
  gpm.proto/Variables
  (variables [_]
    variables)

  gpm.proto/GPM
  (logpdf [_ targets constraints]
    (if (empty? constraints)
      (importance-sampling-score :model model :observation-trace (map->trace targets))
      (- (importance-sampling-score :model model
                                    :observation-trace (map->trace (merge targets constraints)))
         (importance-sampling-score :model model :observation-trace (map->trace targets)))))

  (simulate [_ targets constraints]
    (prn "targets" targets "constraints" constraints)
    (let [result
          (let [trace (map->trace constraints)]
            (-> (let [result (inference/importance-resampling :model model
                                                              :observation-trace trace
                                                              :n-particles n-particles)]
                  (prn "xxx" result)
                  result)
                (update-keys keyword)
                (select-keys targets)
                (update-vals :value)))]
      (prn "simulate-result" result)
      result))

  gpm.proto/Condition
  (condition [this conditions]
    (conditioned/condition this conditions))
  gpm.proto/Constrain
  (constrain [this event opts]
    (constrained/constrain this event opts)))

(def normal-normal
  (gen []
       (let-traced [x (distributions/gaussian 100 1)
                    y (distributions/gaussian x 1)]
         y)))

(deftest simulate-1
  (let [model (gen [] (let-traced [x (distributions/flip 0.5)]
                        x))
        gpm (map->Metaprob {:model model :n-particles 1})]
    (is (boolean? (:x (gpm.proto/simulate gpm [:x] {}))))))

(deftest logpdf-1
  (let [model (gen [] (let-traced [x (distributions/flip 0.75)]
                        x))
        gpm (map->Metaprob {:model model :n-particles 1})]
    (is (= 0.75 (math/exp (gpm.proto/logpdf gpm {:x true} {}))))
    (is (= 0.25 (math/exp (gpm.proto/logpdf gpm {:x false} {}))))))

(comment

  (-> (repeatedly 100 #(let [model (gen [] (let-traced [x (distributions/flip 0.5)
                                                        y (distributions/flip (if x 0.9 0.1))]
                                             y))
                             gpm (map->Metaprob {:model model :n-particles 1000})]
                         (gpm.proto/simulate gpm [:x] {:y true})))
      (frequencies))

  )

#_
(deftest logpdf-2
  (let [model (gen [] (let-traced [x (distributions/flip 0.75)]
                        x))
        gpm (map->Metaprob {:model model :n-particles 1})]
    (is (= 0.75 (math/exp (gpm.proto/logpdf gpm {:x true} {}))))
    (is (= 0.25 (math/exp (gpm.proto/logpdf gpm {:x false} {}))))))

(def schema
  {"x" :nominal
   "y" :nominal
   "z" :nominal})

(def variables (keys schema))

(def model
  (gen []
       (let-traced [x (distributions/categorical {"true" 0.5 "false" 0.5})
                    y (distributions/categorical (let [weight (if (= "true" x)
                                                                0.9
                                                                0.1)]
                                                   {"cats" weight "dogs" (- 1 weight)}))]
         y)))

(def gpm (map->Metaprob {:model model :variables variables :n-particles 1000}))

(def db
  (-> (db/empty)
      (db/with-model 'model gpm)))

(comment

  (permissive/query "SELECT * FROM (GENERATE * UNDER model) LIMIT 1" (atom db))

  (metaprob/infer-and-score :procedure model)


  (gpm.proto/simulate (map->Metaprob {:model model :n-particles 1})
                      [:x :y]
                      {})

  (inference/importance-sampling :model normal-normal
                                 :observation-trace {"y" {:value 100}}
                                 :n-particles 10)

  (importance-sampling-score :model normal-normal
                             :observation-trace {"x" {:value 10}}
                             :n-particles 10)

  ,)
