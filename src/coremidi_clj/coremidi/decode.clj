(ns coremidi-clj.coremidi.decode)

(defn- decode-controller-change [bytes]
  (let [[status controller value] bytes
        channel (mod status 16)]
    {:cmd        :control-change
     :chan       channel
     :controller controller
     :value      value}))

(defn- decode-note-on [bytes]
  (let [[status pitch velocity] bytes
        channel (mod status 16)]
    {:cmd      (if (zero? velocity) :note-off :note-on)
     :chan     channel
     :pitch    pitch
     :velocity velocity}))

(defn- decode-note-off [bytes]
  (let [[status pitch velocity] bytes
        channel (mod status 16)]
    {:cmd      :note-off
     :chan     channel
     :pitch    pitch
     :velocity velocity}))

(defn decode-packet [packet]
  (let [status-byte   (first (:data packet))
        status-nybble (unchecked-divide-int status-byte 16)]
    (case status-nybble
      0x8 (decode-note-off (:data packet))
      0x9 (decode-note-on (:data packet))
      0xb (decode-controller-change (:data packet))
      {:type :unknown-midi-message
       :data (:data packet)})))

