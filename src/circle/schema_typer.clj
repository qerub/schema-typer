(ns circle.schema-typer
  "Creates core.typed types from prismatic/schema definitions. Inspired by https://gist.github.com/c-spencer/6569571"
  (:import (clojure.lang Keyword))
  (:require [clojure.core.typed :as t :refer (ann def-alias)]))

;; refine these later
(def-alias Schema Any)
(def-alias CoreType (U Symbol (t/Seq Any)))

(defn convert-dispatch [schema]
  (cond
   (class? schema) schema
   :else (class schema)))

(defmulti convert "" #'convert-dispatch )

(defmethod convert Number [s]
  'Number)

(defmethod convert String [s]
  'String)

(defmethod convert Keyword [s]
  'Keyword)

(defmethod convert clojure.lang.Symbol [s]
  s)

(t/ann hmap-grouper [Any -> (U (Value :mandatory)
                               (Value :optional))])
(defn hmap-grouper
  [kv]
  (if (= schema.core.OptionalKey (class (key kv)))
    :optional
    :mandatory))

(defn convert-hmap
  "Returns a core.typed HMap. All keys of the map must be keywords"
  [s]
  (assert (map? s))
  (let [{:keys [mandatory optional]} (group-by hmap-grouper s)
        ;; strip the schema.core.OptionalKey off optionals
        optional (map (fn [[k v]]
                        [(:k k) v]) optional)
        convert-kvs (fn [kvs]
                      (->>
                       (for [[k v] kvs]
                         (do
                           [k (convert v)]))
                       (into {})))]
    (list 'HMap
          :mandatory (convert-kvs mandatory)
          :optional (convert-kvs optional))))

(defn class->name [c]
  (-> c .getName symbol))

(defn convert-map [s]
  (assert (map? s))
  (assert (= 1 (count s)) "convert-map only supports one kv")
  (let [kt (-> s first key)
        vt (-> s first val)]
    (assert (class kt))
    (assert (class vt))
    (list 't/Map (class->name kt) (class->name vt))))

(defn non-hmap? [s]
  (and (map? s)
       (class? (first (keys s)))))

(defmethod convert clojure.lang.IPersistentMap [s]
  (if (non-hmap? s)
    (convert-map s)
    (convert-hmap s)))

(t/ann schema->type [Schema -> CoreType])
(defn schema->type
  "Takes a prismatic schema. Returns a list of symbols that can be understood as a core.typed type."
  [s]
  ;; {:post [(do (println "schema->type" s %) true) (or (symbol? %) (list? %))]}
  (convert s))

(defmacro def-schema-type
  "creates a def-alias named type-name, from schema type"
  [type-name s]
  `(def-alias ~type-name ~(schema->type s)))