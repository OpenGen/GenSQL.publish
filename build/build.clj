(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as string]
            [clojure.tools.build.api :as build]))

(defmacro with-reporting
  [s & body]
  `(do (println (str ~s "..."))
       (flush)
       ~@body))

(defn current-sha
  [& {:keys [short] :or {short false}}]
  (build/git-process
   {:git-args (if short
                "rev-parse --short HEAD"
                "rev-parse HEAD")}))

(defn worktree-dirty?
  []
  (-> (build/git-process {:git-args "status --short"})
      (string/blank?)
      (not)))

(def default-opts
  {:lib 'inferenceql/inferenceql.publish
   :main 'inferenceql.publish
   :target "target"
   :clojure-src-dirs ["src"]
   :java-src-dirs ["java"]
   :version (current-sha :short true)
   :basis (build/create-basis)})

(defn class-dir
  [{:keys [class-dir target]}]
  (or class-dir (str target "/classes")))

(defn uber-file
  [{:keys [target lib version]}]
  (let [filename (->> (cond-> [(name lib) version]
                        (worktree-dirty?)
                        (conj "DIRTY"))
                      (string/join "-"))]
    (format "%s/%s.jar" target filename)))

(defn compile-java
  [opts]
  (with-reporting "Compiling Java files"
    (let [{:keys [basis java-src-dirs] :as opts} (merge default-opts opts)]
      (build/javac {:src-dirs java-src-dirs
                    :class-dir (class-dir opts)
                    :basis basis})))
  opts)

(defn compile-clj
  [opts]
  (with-reporting "Compiling Clojure files"
    (let [{:keys [basis clojure-src-dirs] :as opts} (merge default-opts opts)]
      (build/compile-clj {:src-dirs clojure-src-dirs
                          :class-dir (class-dir opts)
                          :basis basis})
      opts)))

(defn compile-cljs
  [opts]
  (let [{:keys [target] :as opts} (merge default-opts opts)
        working-dir (str target "/js")
        cljsbuild-out-file (str working-dir "/index.js")]
    (with-reporting "Compiling ClojureScript files"
      (let [process-params (build/java-command (assoc opts
                                                      :main 'clojure.main
                                                      :main-args ["-m" "cljs.main"
                                                                  "-co" "build.edn"
                                                                  "-d" working-dir
                                                                  "-o" cljsbuild-out-file
                                                                  "-O" "advanced"
                                                                  "-c"]))
            {:keys [exit]} (build/process process-params)]
        (when-not (zero? exit)
          (throw (ex-info (str "ClojureScript compilation failed, working-dir preserved: "
                               (.toString working-dir))
                          {})))))
    (assoc opts :bundler-input cljsbuild-out-file)))

(defn bundle-js
  [opts]
  (let [{:keys [bundler-input bundler-outdir]} (merge default-opts opts)
        bundler-outdir (or bundler-outdir
                           (str (class-dir opts) "/js"))]
    (with-reporting "Bundling JavaScript files"
      (let [{:keys [exit]} (build/process {:command-args ["pnpm" "esbuild" bundler-input
                                                          "--bundle"
                                                          "--format=iife"
                                                          (str "--outdir=" bundler-outdir)]})]
        (when-not (zero? exit)
          (throw (ex-info (str "JavaScript bundling failed")
                          {})))))))

(defn clean
  [opts]
  (with-reporting "Deleting target directory"
    (let [{:keys [target] :as opts} (merge default-opts opts)]
      (build/delete {:path target})
      opts)))

(defn copy-css
  [opts]
  (with-reporting "Copying stylesheets"
    (let [opts (merge default-opts opts)
          class-dir (class-dir opts)]

      (build/copy-dir {:src-dirs ["node_modules/highlight.js/styles"]
                       :target-dir (str class-dir "/styles")})
      opts)))

(defn copy-clj
  [opts]
  (with-reporting "Copying Clojure source"
    (let [{:keys [clojure-src-dirs] :as opts} (merge default-opts opts)
          class-dir (class-dir opts)]
      (build/copy-dir {:target-dir class-dir
                       :src-dirs clojure-src-dirs})
      opts)))

(defn build
  [opts]
  (-> opts
      (copy-css)
      (compile-java)
      (compile-cljs)
      (bundle-js))
  opts)

(defn uberjar
  [opts]
  (let [{:keys [basis main] :as opts} (merge default-opts opts)]
    (-> opts
        (clean)
        (build)
        (compile-clj)
        (copy-clj))
    (with-reporting "Building uberjar"
      (build/uber {:class-dir (class-dir opts)
                   :uber-file (uber-file opts)
                   :basis basis
                   :main main})))
  opts)
