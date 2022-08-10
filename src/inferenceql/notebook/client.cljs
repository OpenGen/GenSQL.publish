(ns inferenceql.notebook.client
  (:require-macros [inferenceql.query.io :as io])
  (:require ["@inferenceql/components" :as components]
            ["react" :as react]
            ["react-dom" :as react-dom]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [lambdaisland.fetch :as fetch]))

(def schema
  (edn/read-string (io/inline-file "repro.schema.edn")))

(enable-console-print!)

(defn execute
  [s]
  (-> (fetch/post "/api/query"
                  {:content-type :json
                   :accept :json
                   :body {:query s}
                   :mode :same-origin
                   :cache :no-cache})
      (.then (fn [{:keys [body status] :as response}]
               (case status
                 200 (clj->js body)
                 400 (js/Promise.reject (js/Error. body))
                 500 (do (js/alert "Internal query execution error")
                         (throw response))
                 (do (js/alert "Unhandled HTTP status code from server")
                     (throw response)))))))

(def stattype->type
  {:numerical "quantitative"
   :nominal "nominal"})

(defn stattype
  [col]
  (cond (string/starts-with? col "prob")
        :numerical

        (string/starts-with? col "count")
        :numerical

        :else
        (get schema col :ignored)))

(doseq [code (js/window.document.querySelectorAll "pre code")]
  (let [query (.-innerText code)
        pre (.closest code "pre")
        parent (.-parentElement pre)
        div (.createElement js/window.document "div")]
    (.insertBefore parent div pre)
    (.remove pre)
    (let [props #js {:execute execute
                     :initialQuery (string/trim query)
                     ;; TODO: Rename `:statType` prop
                     :statType (comp stattype->type stattype)}
          element (react/createElement components/Query props)]
      (react-dom/render element div))))
