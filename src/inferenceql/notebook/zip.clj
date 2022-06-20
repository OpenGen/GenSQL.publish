(ns inferenceql.notebook.zip
  (:require [clojure.zip :as zip]))

(defn loc-tag
  "Returns the tag of the node at the current location."
  [loc]
  (-> loc
      (zip/node)
      (first)))

(defn iter-zip
  "Returns a depth-first sequence of nodes in a zipper."
  [loc]
  (->> loc
       (iterate zip/next)
       (take-while (complement zip/end?))))

(defn seek
  "Returns the location of the first node with a tag."
  [loc tag]
  (->> (iter-zip loc)
       (filter #(= tag (loc-tag %)))
       (first)))

(defn loc-text
  [loc]
  (->> loc
       (zip/children)
       (filter string?)
       (first)))

(defn top
  "Zips all the way up and returns the root location, reflecting any changes."
  [loc]
  (if (= :end (loc 1))
    loc
    (let [p (zip/up loc)]
      (if p
        (recur p)
        loc))))
