(ns coremidi-clj.coremidi
  (:require [coremidi-clj.coremidi.native :as native]
            [coremidi-clj.coremidi.decode :as decode])
  (:use     [clj-native.callbacks :only [callback]])
  (:import  [com.sun.jna Pointer Memory]))

(defn devices []
  (for [i (range (native/num-devices))]
    (native/get-device i)))

(defn create-client [name notify-fn]
  (let [cb        (callback native/notify-cb notify-fn)
        clientvar (Memory. Pointer/SIZE)
        status    (native/create-client* (native/cfstr name) cb nil clientvar)]
    (if-not (= 0 status)
      (throw (Exception. "Error creating client:" status))
      {:raw-client (.getPointer clientvar 0)
       :callback   cb})))

(defn create-input-port [client name read-fn]
  (let [cb      (callback native/packet-cb read-fn)
        portvar (Memory. Pointer/SIZE)
        status    (native/create-input-port* (:raw-client client) (native/cfstr name) cb nil portvar)]
    (if-not (= 0 status)
      (throw (Exception. "Error creating port:" status))
      {:raw-port (.getPointer portvar 0)
       :callback cb})))

(defn create-output-port [client name]
  (let [portvar (Memory. Pointer/SIZE)
        status    (native/create-output-port* (:raw-client client) (native/cfstr name) portvar)]
    (if-not (= 0 status)
      (throw (Exception. "Error creating port:" status))
      (.getPointer portvar 0))))

(defn connect-source [port source data]
  (native/connect-source* (:raw-port port) source data))

(defonce ^:private midi-client (atom nil))

(defn- create-client-if-nil [client]
  (if client
    client
    (let [client (create-client "CoreMIDI clj client" (fn [& more] (println "got notify msg")))
          port   (create-output-port client "output port")]
      {:client      client
       :output-port port})))

(defn- get-midi-state []
  (when (nil? @midi-client)
    (swap! midi-client create-client-if-nil))
  @midi-client)

(defn- get-midi-client []
  (:client (get-midi-state)))

(defn- get-midi-output-port []
  (:output-port (get-midi-state)))

(defn- find-device-by-name [name]
  (let [re (re-pattern name)]
    (first (filter #(re-find re (native/get-name %)) (devices)))))

;; TODO: devices with multiple entities? Entities with multiple sources?
(defn midi-in [in]
  (if-let [device (find-device-by-name in)]
    (let [entity (native/get-entity device 0)
          source (native/get-source entity 0)]
      source)
    (println "Did not find a matching midi input device for:" in)))

(defn midi-out [out]
  (if-let [device (find-device-by-name out)]
    (let [entity (native/get-entity device 0)
          dest   (native/get-destination entity 0)]
      dest)
    (println "Did not find a matching midi output device for:" out)))

;; TODO: keep the callback object somewhere so that it won't get GC'd
(defn connect-to-source [source f]
  (let [port (create-input-port (get-midi-client) "port" f)
        _    (println "got port" (native/get-name (:raw-port port)))]
    (connect-source port source nil)
    port))

(defn midi-handle-events [source handler]
  (let [callback (fn [packet-list & more]
                   (doseq [packet (native/read-packet-list packet-list)]
                     (handler (decode/decode-packet packet) (:timestamp packet))))]
    (connect-to-source source callback)))

;; FIXME: Assumes no alignment padding :/
(defn- midi-send [sink byte-seq]
  (let [num-midi-bytes  (count byte-seq)
        num-total-bytes (+
                         4 ;; UInt32 numPackets
                         8 ;; MIDITimestamp timestamp
                         2 ;; UInt16 length
                         num-midi-bytes ;; Byte data[length]
                         )
        list-ptr (Memory. num-total-bytes)]
    (native/write-bytes-to-packet-list list-ptr byte-seq)
    (native/midi-send* (get-midi-output-port) sink list-ptr)))

(defn midi-sysex [sink byte-seq]
  (midi-send sink (seq byte-seq)))

(defn midi-control [sink ctl-num val]
  (midi-send sink [(- 0xb0 256) ctl-num val]))

(defn midi-note-on
  ([sink note] (midi-note-on sink note 64))
  ([sink note vel] (midi-send sink [(- 0x90 256) note vel])))
