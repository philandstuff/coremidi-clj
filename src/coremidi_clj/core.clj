(ns coremidi-clj.core
  (:use coremidi-clj.coremidi)
  (:require [coremidi-clj.coremidi.decode :as decode]
            [coremidi-clj.coremidi.native :as native]))

(defn -main []
  (native/init)
  (let [source (midi-in "nanoKONTROL")
        port   (connect-to-source source
                                  (fn [packet-list & more]
                                    (println "got packet list:"
                                             (map decode/decode-packet (native/read-packet-list packet-list)))))]
    (while true
      (Thread/sleep 1000)
      (println port))
    ))
