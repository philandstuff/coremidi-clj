(ns coremidi-clj.coremidi.decode)

(defn- decode-controller-change [bytes]
  (let [[status controller value] bytes]
    {:type       :controller-change
     :controller controller
     :value      value}))

(defn decode-packet [packet]
  (let [status-byte   (first (:data packet))
        status-nybble (unchecked-divide-int status-byte 16)]
    (case status-nybble
      0xb (decode-controller-change (:data packet))
      :unknown-midi-message)))
