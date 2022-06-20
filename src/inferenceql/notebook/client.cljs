(ns inferenceql.notebook.client
  (:require ["@openiql/inferenceql.components" :as components]
            ["react" :as react]
            ["react-dom/client" :as react-dom]
            [clojure.string :as string]))

(enable-console-print!)

(defn execute
  [_s]
  (clj->js
   {:columns ["name" "age" "color"]
    :data [{:name "Disco" :age 16 :color "brown"}
           {:name "Henry" :age 14 :color "orange"}
           {:name "Zelda" :age 13 :color "black"}]}))

(doseq [element (js/window.document.querySelectorAll "code.iql")]
  (let [root (react-dom/createRoot element)
        props #js {:execute execute
                   :initialQuery (string/trim (.-innerHTML element))}]
    (.render root (react/createElement components/Query props))))
