(ns coremidi-clj.core
  (:use coremidi-clj.coremidi))

(defn -main []
  (native/init)
  (let [source (midi-in "nanoKONTROL")
        port   (midi-handle-events source
                                  (fn [packet timestamp]
                                    (println timestamp "got packet:" packet)))]
    (while true
      (Thread/sleep 1000)
      (println port))
    ))
