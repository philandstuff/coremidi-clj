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

(defn connect-source [port source data]
  (native/connect-source* (:raw-port port) source data))

(defonce ^:private midi-client (atom nil))

(defn- create-client-if-nil [client]
  (if client
    client
    (create-client "CoreMIDI clj client" (fn [& more] (println "got notify msg")))))

(defn- get-midi-client []
  (when (nil? @midi-client)
    (swap! midi-client create-client-if-nil))
  @midi-client)

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
