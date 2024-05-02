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
  {:lib 'OpenGen/gensql.publish
   :main 'gensql.publish
   :target "target"
   :clojure-src-dirs ["src"]
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

(defn compile-clj
  [opts]
  (with-reporting "Compiling Clojure files"
    (let [{:keys [basis clojure-src-dirs] :as opts} (merge default-opts opts)]
      (build/compile-clj {:src-dirs clojure-src-dirs
                          :class-dir (class-dir opts)
                          :basis basis})
      opts)))

(defn bundle-js
  [opts]
  (let [{:keys [bundler-input bundler-outfile]} (merge default-opts opts)
        bundler-input (or bundler-input
                          "js/main.js")
        bundler-outfile (or bundler-outfile
                            (str (class-dir opts) "/js/gensql.publish.js"))]
    (with-reporting "Bundling JavaScript files"
      (let [{:keys [exit]} (build/process {:command-args ["pnpm" "esbuild" bundler-input
                                                          "--bundle"
                                                          "--format=iife"
                                                          "--global-name=gensql.publish"
                                                          "--sourcemap"
                                                          (str "--outfile=" bundler-outfile)]})]
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
