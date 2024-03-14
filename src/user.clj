(ns user
  (:require
   [repeatt.core :as repeatt]))

(defn start!
  "Load and switch to the 'dev' namespace."
  []
  (in-ns 'repeatt.core)
  (repeatt/-main)
  :loaded)
