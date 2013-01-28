(ns coremidi-clj.core
  (:use coremidi-clj.coremidi))

;; Magic sysex messages (recorded from Korg Kontrol Editor output)
(def start-sysex [-16 126 127 6 1 -9])
(def second-sysex [-16 66 64 0 1 19 0 31 18 0 -9])
(def main-sysex [-16 66 64 0 1 19 0 127 127 2 3 5 64 0 0 0 1 16 1 0 0
                 0 0 127 0 1 0 16 0 0 127 0 1 0 32 0 127 0 0 1 0 48 0
                 127 0 0 1 0 64 0 127 0 16 0 1 0 1 0 127 0 1 0 0 17 0
                 127 0 1 0 0 33 0 127 0 1 0 49 0 0 127 0 1 0 65 0 0
                 127 0 16 1 0 2 0 0 127 0 1 0 18 0 127 0 0 1 0 34 0
                 127 0 0 1 0 50 0 127 0 1 0 0 66 0 127 0 16 1 0 0 3 0
                 127 0 1 0 0 19 0 127 0 1 0 35 0 0 127 0 1 0 51 0 0
                 127 0 1 0 67 0 127 0 0 16 1 0 4 0 127 0 0 1 0 20 0
                 127 0 0 1 0 36 0 127 0 1 0 0 52 0 127 0 1 0 0 68 0
                 127 0 16 1 0 0 5 0 127 0 1 0 21 0 0 127 0 1 0 37 0 0
                 127 0 1 0 53 0 127 0 0 1 0 69 0 127 0 0 16 1 0 6 0
                 127 0 0 1 0 22 0 127 0 1 0 0 38 0 127 0 1 0 0 54 0
                 127 0 1 0 70 0 0 127 0 16 1 0 7 0 0 127 0 1 0 23 0 0
                 127 0 1 0 39 0 127 0 0 1 0 55 0 127 0 0 1 0 71 0 127
                 0 16 0 1 0 58 0 127 0 1 0 0 59 0 127 0 1 0 0 46 0 127
                 0 1 0 60 0 0 127 0 1 0 61 0 0 127 0 1 0 62 0 127 0 0
                 1 0 43 0 127 0 0 1 0 44 0 127 0 1 0 0 42 0 127 0 1 0
                 0 41 0 127 0 1 0 45 0 0 127 0 127 127 127 127 0 127 0
                 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 -9])
(def end-sysex [-16 66 64 0 1 19 0 31 17 0 -9])

(defn set-external! [sink]
  (midi-sysex sink start-sysex)
  (midi-sysex sink second-sysex)
  (midi-sysex sink start-sysex)
  (midi-sysex sink main-sysex)
  (midi-sysex sink start-sysex)
  (midi-sysex sink end-sysex)
  )

(defn leds-on-test
  [nko]
  (dotimes [n 100] (midi-control nko n 127)))

(defn -main []
  (let [source  (midi-in "nanoKONTROL")
        in-port (midi-handle-events source
                                    (fn [packet timestamp]
                                      (println timestamp "got packet:" packet)))
        sink    (midi-out "nanoKONTROL")]
    (set-external! sink)
    (leds-on-test sink)
    (while true
      (Thread/sleep 1000)
      (println in-port))
    ))
