(ns inferenceql.notebook.client
  (:require ["@openiql/inferenceql.components" :as components]
            ["react" :as react]
            ["react-dom/client" :as react-dom]))

(defn execute
  [_s]
  (clj->js
   {:columns ["name" "age" "color"]
    :data [{:name "Disco" :age 16 :color "brown"}
           {:name "Henry" :age 14 :color "orange"}
           {:name "Zelda" :age 13 :color "black"}]}))

#_
(doseq [element (js/window.document.querySelectorAll "code.iql")]
  (let [root (react-dom/createRoot element)
        props #js {:execute execute
                   :value (.-innerText element)}]
    (.render root (react/createElement components/Query props))))
