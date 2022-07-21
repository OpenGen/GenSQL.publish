(ns inferenceql.notebook
  (:import [clojure.lang ExceptionInfo]
           [java.io File InputStream]
           [org.jsoup Jsoup])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [cognitect.anomalies :as-alias anomalies]
            [com.stuartsierra.component :as component]
            [inferenceql.inference.gpm :as gpm]
            [inferenceql.notebook.asciidoc :as asciidoc]
            [inferenceql.query.permissive :as query]
            [inferenceql.query.relation :as relation]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.refresh :refer [wrap-refresh]]
            [ring.util.response :as response]))

(defn not-found-handler
  [_request]
  (-> (response/not-found "Not found")
      (response/header "Content-Type" "text/plain")))

(defrecord JettyServer [handler port]
  component/Lifecycle

  (start [component]
    (let [jetty-server (jetty/run-jetty handler {:port port :join? false})]
      (assoc component :server jetty-server)))

  (stop [{:keys [server]}]
    (.stop server)))

(defn wrap-debug [handler]
  (fn [request]
    (pprint/pprint request)
    (let [response (handler request)]
      (pprint/pprint response)
      response)))

(defn ->string
  [x]
  (condp = (type x)
    String x
    File (slurp x)
    InputStream (slurp x)
    (if (seq? x)
      (reduce str x)
      (throw (ex-info "Cannot convert to string: " (pr-str x)
                      {:value x})))))

(defn add-script
  [html selector url]
  (let [doc (Jsoup/parse html)]
    (-> doc
        (.select selector)
        (.first)
        (.appendElement "script")
        (.attr "type" "text/javascript")
        (.attr "crossorigin" true)
        (.attr "src" url))
    (str doc)))

(defn add-stylesheet
  [html url]
  (let [doc (Jsoup/parse html)]
    (-> doc
        (.select "head")
        (.first)
        (.appendElement "link")
        (.attr "rel" "stylesheet")
        (.attr "href" url))
    (str doc)))

(defn wrap-convert
  "Ring middleware that converts Asciidoc to HTML."
  [handler]
  (fn [request]
    (let [{:keys [status] :as response} (handler request)]
      (if (and (= 200 status)
               (= "text/plain" (response/get-header response "Content-Type")))
        (-> response
            (update :body #(-> % ->string asciidoc/->html))
            (update :headers assoc "Content-Type" "text/html")
            (update :headers dissoc "Content-Length"))
        response))))

(defn transform-html
  [html]
  (-> html
      (add-script "body" "js/main.js")
      (add-stylesheet "styles/github.css")))

(defn wrap-transform-html [handler]
  (fn [request]
    (let [{:keys [status] :as response} (handler request)]
      (if (and (= 200 status)
               (= "text/html" (response/get-header response "Content-Type")))
        (-> response
            (update :body #(-> % ->string transform-html))
            (update :headers dissoc "Content-Length"))
        response))))

(def db
  (atom (edn/read {:readers gpm/readers}
                  (java.io.PushbackReader. (io/reader "/Users/zane/Desktop/db.edn")))))

(defn query-handler
  [request]
  (try (let [query (-> request
                       (get-in [:params "query"])
                       string/trim)
             relation (query/query query db)
             columns (relation/attributes relation)]
         (response/response
          {:rows (into [] relation)
           :columns columns}))
       (catch ExceptionInfo e
         (let [{::anomalies/keys [category]} (ex-data e)]
           (case category
             ::anomalies/incorrect (response/bad-request (ex-message e))
             {:status 500})))))

(defn app
  [& {:keys [path]}]
  (ring/ring-handler
   (ring/router
    [["/api/query" (-> #'query-handler
                       (wrap-restful-format :formats [:json])
                       (wrap-restful-response))]
     ["/styles/*" (ring/create-file-handler {:root "node_modules/highlight.js/styles"})]
     ["/js/*" (ring/create-resource-handler {:root "js"})]])
   (-> #'not-found-handler
       (wrap-file path {:index-files? false})
       (wrap-content-type {:mime-types {"adoc" "text/plain"
                                        "md" "text/plain"}})
       (wrap-not-modified)
       (wrap-convert)
       (wrap-transform-html)
       ;; (wrap-refresh path)
       )))

(defn jetty-server
  [& {:as opts}]
  (map->JettyServer opts))
