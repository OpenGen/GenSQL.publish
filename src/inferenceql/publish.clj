(ns inferenceql.publish
  (:gen-class)
  (:import [clojure.lang ExceptionInfo]
           [java.io File InputStream PushbackReader]
           [java.util Date]
           [org.jsoup Jsoup])
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [borkdude.dynaload :as dynaload]
            [clojure.edn :as edn]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as string]
            [cognitect.anomalies :as-alias anomalies]
            [com.stuartsierra.component :as component]
            [inferenceql.inference.gpm :as gpm]
            [inferenceql.publish.asciidoc :as asciidoc]
            [inferenceql.query.permissive :as permissive]
            [inferenceql.query.relation :as relation]
            [inferenceql.query.strict :as strict]
            [instaparse.failure :as failure]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.response :as response]))

(defn not-found-handler
  [_request]
  (-> (response/not-found "Not found")
      (response/header "Content-Type" "text/plain")))

(defn now-ms
  []
  (inst-ms (Date.)))

(defrecord JettyServer [handler port on-start]
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

(defn query-handler
  [db execute]
  (fn [request]
    (try (let [query (-> request
                         (get-in [:params "query"])
                         string/trim)
               relation (execute query db)
               columns (relation/attributes relation)]
           (response/response
            {:rows (into [] relation)
             :columns columns}))
         (catch ExceptionInfo ex
           (let [{::anomalies/keys [category] :as ex-data} (ex-data ex)]
             (case category
               ::anomalies/incorrect
               (let [message (if-let [failure (:instaparse/failure ex-data)]
                               (with-out-str (failure/pprint-failure failure))
                               (ex-message ex))]
                 (response/bad-request {:message message}))
               (throw ex)))))))

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {::exception/default (fn [exception request]
                           {:status  500
                            :exception (with-out-str (stacktrace/print-stack-trace exception))
                            :uri (:uri request)
                            :body "Internal server error"})
     ::exception/wrap (fn [handler e request]
                        (println "ERROR" (pr-str (:uri request)))
                        (stacktrace/print-stack-trace e)
                        (flush)
                        (handler e request))})))

(defn app
  [& {:keys [db path schema-path execute]}]
  (let [file-name (fs/file-name path)]
    (ring/ring-handler
     (ring/router
      [["/" (-> (fn [_]
                  (response/redirect (str "/" file-name))))]
       [(str "/" file-name) (-> (fn [_]
                                  (response/file-response path))
                                (wrap-content-type {:mime-types {"adoc" "text/plain"
                                                                 "md" "text/plain"}})
                                (wrap-not-modified)
                                (wrap-convert))]
       ["/api/query" (-> (#'query-handler db execute)
                         (wrap-restful-format :formats [:json])
                         (wrap-restful-response))]
       ["/schema.edn" (fn [_]
                        (-> (response/file-response schema-path)
                            (response/content-type "application/edn")))]
       ["/styles/*" (ring/create-resource-handler {:root "styles"})]
       ["/js/*" (ring/create-resource-handler {:root "js"})]])
     #'not-found-handler
     {:middleware [exception-middleware]})))

(defn jetty-server
  [& {:as opts}]
  (map->JettyServer opts))

(defn coerce-path
  [s]
  (-> s fs/expand-home fs/path str))

(def spec
  (let [file? (complement fs/directory?)]
    {:path {:desc "AsciiDoc file."
            :ref "<file>"
            :require true
            :coerce coerce-path
            :validate (every-pred fs/exists? file?)}
     :db {:desc "Database file."
          :ref "<file>"
          :require true
          :coerce coerce-path
          :validate (every-pred fs/exists? file?)}
     :schema {:desc "Schema file."
              :ref "<file>"
              :require true
              :coerce coerce-path
              :validate (every-pred fs/exists? file?)}
     :language {:desc "Query lanaguage. Can be strict or permissive."
                :ref "<lang>"
                :default "strict"
                :validate #{"strict" "permissive"}}
     :port {:desc "Server port."
            :ref "<int>"
            :default 8080
            :coerce :long}
     :help {:coerce :boolean}}))

(defn error-fn
  [{:keys [type cause msg option] :as data}]
  (if (= :org.babashka/cli type)
    (let [formatted-opt (cli/format-opts {:spec (select-keys spec [option])})]
      (println
       (case cause
         :require (format "Missing required argument:\n%s" formatted-opt)

         :coerce
         (case option
           :port (format "Argument is not a valid number:\n%s" formatted-opt)
           (format "Argument is not the required format:\n%s" formatted-opt))

         :validate
         (case option
           :path (format "Argument must be a path to a directory:\n%s" formatted-opt)
           :db (format "Argument must be a valid database file:\n%s" formatted-opt)
           :schema (format "Argument must be a valid schema file:\n%s" formatted-opt)
           :language (format "Argument must be one of (strict, permissive):\n%s" formatted-opt))
         (throw (ex-info msg data))))
      (flush))
    (throw (ex-info msg data)))
  (System/exit 1))

(def opts {:spec spec :error-fn error-fn})

(defn sppl-read-string
  [s]
  (try
    (let [read-string (dynaload/dynaload 'inferenceql.gpm.sppl/read-string)]
      (read-string s))
    (catch ExceptionInfo e
      (throw (ex-info "Could not resolve inferenceql.gpm.sppl/read-string. Is inferenceql.gpm.sppl on the classpath?"
                      {}
                      e)))))

(defn -main
  [& args]
  (if (or (empty? args)
          (contains? (set args) "--help"))
    (println (cli/format-opts opts))
    (let [{:keys [db path schema language port]}
          (cli/parse-opts args opts)

          execute (case (name language)
                    "permissive" permissive/query
                    "strict" strict/query)
          db (atom (edn/read {:readers (assoc gpm/readers 'inferenceql.gpm.spe/SPE sppl-read-string)}
                             (PushbackReader. (io/reader db))))
          handler (app :db db
                       :path path
                       :schema-path schema
                       :execute execute)
          system (jetty-server :handler handler :port port)]
      (component/start system)
      ;; Include a unique query string parameter to bust the browser's cache.
      (browse/browse-url (format "http://localhost:%s?rel=%s" port (now-ms))))))
