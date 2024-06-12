(ns gensql.publish.asciidoc
  (:import [java.util HashMap]
           [org.asciidoctor Asciidoctor$Factory]
           [org.asciidoctor Options]
           [org.asciidoctor SafeMode]
           [org.asciidoctor.ast ContentModel]
           [org.asciidoctor.ast Document]
           [org.asciidoctor.extension BlockProcessor]
           [org.asciidoctor.extension Contexts]
           [org.asciidoctor.extension DocinfoProcessor]
           [org.asciidoctor.extension LocationType])
  (:require [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [hiccup.core :as hiccup]))

(defn docinfo-processor
  [f & {:keys [location]}]
  {:pre [(some? f)
         (some? location)
         (contains? #{LocationType/HEADER LocationType/FOOTER} location)]}
  (let [config (doto (HashMap.)
                 (.put "location" (.optionValue location)))]
    (proxy [DocinfoProcessor] [config]
      (process [^Document document]
        (f this document)))))

(defn add-stylesheet-processor
  [url]
  (docinfo-processor
   (fn [_ document]
     (if (.isBasebackend document "html")
       (hiccup/html [:link {:rel "stylesheet" :href url}])
       document))
   :location LocationType/HEADER))

(defn add-script-processor
  [url]
  (docinfo-processor
   (fn [_ document]
     (when (.isBasebackend document "html")
       (hiccup/html [:script {:type "text/javascript"
                              :crossorigin true
                              :src url}])))
   :location LocationType/HEADER))

(defn block-processor
  [& {:keys [fn name contexts content-model]}]
  {:pre [(some? fn) (some? name)]}
  (let [config (HashMap.)]
    (when contexts (.put config Contexts/KEY contexts))
    (when content-model (.put config ContentModel/KEY content-model))
    (proxy [BlockProcessor] [name config]
      (process [parent reader attributes]
        (fn this parent reader attributes)))))

(defn gensql-block-processor
  [name]
  (block-processor
    :name name
    :contexts [Contexts/PARAGRAPH Contexts/LISTING Contexts/EXAMPLE]
    :content-model ContentModel/SIMPLE
    :fn (fn [this parent reader _attributes]
          (let [id (str (gensym "publish"))
                query (.read reader)
                props (json/write-str {:initialQuery query})]
            (doto parent
                  (.append (.createBlock this parent "pass" (hiccup/html [:div {:id id} [:pre [:code query]]])))
                  (.append (.createBlock this parent "pass" (hiccup/html [:script {:type "text/javascript"}
                                                                          (str "gensql.publish.mount('Query', " props ", " (json/write-str id) ");")]))))))))

(def ^:private asciidoctor
  (let [asciidoctor (Asciidoctor$Factory/create)
        extension-registry (.javaExtensionRegistry asciidoctor)]
    (.docinfoProcessor extension-registry (add-script-processor "js/gensql.publish.js"))
    (.docinfoProcessor extension-registry (add-stylesheet-processor "styles/github.css"))
    (.block extension-registry (gensql-block-processor "gensql"))
    (.block extension-registry (gensql-block-processor "iql")) ; backwards-compatibility
    asciidoctor))

(defrecord Asciidoctor []
  component/Lifecycle

  (start [component]
    (let [asciidoctor (Asciidoctor$Factory/create)]
      (assoc component :asciidoctor asciidoctor)))

  (stop [{:keys [asciidoctor]}]
    (.shutdown asciidoctor)))

(defn title
  [f]
  (-> (.load asciidoctor (slurp f) {})
      (.doctitle)))

(defn ->html
  "Transforms an Asciidoc string into an HTML string."
  [s]
  (let [options (-> (Options/builder)
                    (.headerFooter true)
                    (.attributes {"linkcss" false})
                    (.safe SafeMode/SECURE)
                    (.build))]
    (.convert asciidoctor s options)))
