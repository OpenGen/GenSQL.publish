(ns inferenceql.notebook
  (:import [com.sun.webkit.dom JSObject]
           [java.io File]
           [javafx.beans.value ChangeListener]
           [javafx.concurrent Worker$State]
           [javafx.scene.web WebEngine WebView])
  (:require [cljfx.api :as fx]
            [clojure.core.match :as match]
            [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.zip :as clojure.zip]
            [hiccup.core :as hiccup]
            [hickory.zip :as hickory.zip]
            [inferenceql.notebook.zip :as zip]
            [markdown-to-hiccup.core :as markdown]
            [nextjournal.beholder :as beholder]
            [org.httpkit.server :as server]))

(set! *warn-on-reflection* true)

(defn get-title
  [loc]
  (when-let [loc (or (zip/seek loc :title)
                     (zip/seek loc :h1))]
    (zip/loc-text loc)))

(defn markdown?
  [path]
  (string/ends-with? (str path) ".md"))

(defn file?
  "Returns true if its argument is a file."
  [^File file]
  (.isFile file))

(defn file-name
  "Returns the filename from a `java.io.File`."
  [^File file]
  (.getName file))

(defn markdown-files
  "Returns a set of all the files under a path."
  [path]
  (into #{}
        (comp (map io/file)
              (mapcat file-seq)
              (filter (every-pred markdown? file?)))
        (file-seq (io/file path))))

(defn cb
  "Returns a callback handler for an atom."
  [files]
  (fn [& {:keys [type path]}]
    (try
      (let [path (io/file (str path))]
        (case type
          :create
          (swap! files assoc (file-name path) path)

          :modify
          nil

          :delete
          (swap! files dissoc path)))
      (catch Exception e
        (repl/pst e)))))

(defn watcher
  [file-atom paths]
  (when-some [paths (seq paths)]
    (let [files (mapcat markdown-files paths)]
      (reset! file-atom (zipmap (map file-name files)
                                files))
      (apply beholder/watch (cb file-atom) paths))))

(defn set-class
  [node class]
  (update node 1 assoc :class class))

(defn transform
  [node]
  (letfn [(transform-node
            [node]
            (match/match [node]
              [[:pre {} [:code {:class "iql"} query]]]
              [:code {:class "iql"} query]

              :else node))]
    (walk/postwalk transform-node node)))

(defn render
  [md]
  (let [loc (-> (markdown/md->hiccup md)
                (transform)
                (hickory.zip/hiccup-zip))
        body (-> loc
                 (zip/seek :body)
                 (clojure.zip/children))
        title (get-title loc)
        hiccup [:html
                [:head [:title title]]
                `[~:body ~{:class "m-8 prose"} ~@body]]]
    (hiccup/html hiccup)))

(defn index
  [files]
  (hiccup/html
   [:html
    [:head [:title "Index"]]
    [:body {:class "m-8 prose"}
     [:button {:onClick "window.callback.invoke('https://www.google.com')"}
      "Submit"]
     [:h1 "Files"]
     `[~:ul ~{:class "list-disc"}
       ~@(for [[file-name _path] files]
           [:li [:a {:href (str "/" file-name)} file-name]])]]]))

(defn app
  [files]
  (fn [{:keys [uri]}]
    (let [files @files]
      (if (= "/" uri)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (index files)}
        (match/match [(re-matches #"^/([^.]+\.md)$" uri)]
          [[_ filename]]
          (if-let [file (get files filename)]
            {:status 200
             :headers {"Content-Type" "text/html"}
             :body (render (slurp file))}
            {:status 404
             :headers {"Content-Type" "text/plain"}
             :body "Markdown file not found."})

          [nil]
          (if (= "/tailwind.config.js" (str uri))
            {:status 200
             :headers {"Content-Type" "text/javascript"}
             :body (slurp (io/resource "tailwind.config.js"))}
            {:status 404
             :headers {"Content-Type" "text/plain"}
             :body "File not found."}))))))

(defn serve
  {:org.babashka/cli {:coerce {:path [] :port :long}}}
  [& {paths :path :keys [port] :or {port 8080}}]
  (let [files (atom #{})]
    (when-let [watcher (watcher files paths)]
      (let [server (server/run-server (app files) {:port port :legacy-return-value? false})]
        (.addShutdownHook (Runtime/getRuntime)
                          (Thread. (fn []
                                     (beholder/stop watcher)
                                     (server/server-stop! server))))))))

(defn on-created
  [files]
  (fn [^WebView webview]
    (let [^WebEngine engine (.getEngine webview)
          load (fn [url] (.load engine url))]
      (-> engine
          (.getLoadWorker)
          (.stateProperty)
          (.addListener (reify ChangeListener
                          (changed [_ _ _ state]
                            (when (= Worker$State/SUCCEEDED state)
                              ;; (.executeScript engine (slurp "https://cdn.tailwindcss.com?plugins=typography"))
                              ;; (.executeScript engine (slurp (io/resource "tailwind.config.js")))
                              (let [^JSObject window (.executeScript engine "window")]
                                (.setMember window "callback" load))
                              ;; (.executeScript engine (slurp "out/main.js"))
                              (prn "LOADED" state))))))
      ;; (.loadContent engine (render (slurp (val (first @files)))))
      ;; (.loadContent engine (render (slurp "index.html")))
      (.loadContent engine (render (slurp "examples/introduction.html")))
      ;; (load "http://localhost:8080")
      )))

(comment

  (spit "examples/introduction.html" (render (slurp "examples/introduction.md")))

  ,)

(defn webview
  {:org.babashka/cli {:coerce {:path []}}}
  [& {paths :path}]
  (let [files (atom #{})]
    (when-let [watcher (watcher files paths)]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn []
                                   (beholder/stop watcher))))
      (deref (fx/on-fx-thread
              (fx/create-component
               {:fx/type :stage
                :showing true
                :scene {:fx/type :scene
                        :root {:fx/type fx/ext-on-instance-lifecycle
                               :on-created (on-created files)
                               :desc {:fx/type :web-view}}}}))))))


(comment

  (webview :path ["examples"])

  (-> (markdown/md->hiccup "# Hello, world\n```iql\nselect * from data\n```")
      (markdown/hiccup-in :html :body)
      (transform))

  ,)
