(ns coremidi-clj.core
  (:use [clj-native.direct :only [defclib loadlib]]
        [clj-native.dynamic :only [defcvar]]
        [clj-native.callbacks :only [callback]])
  (:import [com.sun.jna Pointer Memory]))

;; type mappings:
;; MIDI*Ref    == void* (opaque) (where * = Object,Device,Entity etc)
;; OSStatus    == SInt32 == int
;; CFStringRef == void* (opaque)

(defclib coremidi-lib
  (:libname "coremidi")
  (:callbacks
   (notify-cb [void* void*])
   (packet-cb [void* void* void*]))
  (:functions
   (num-devices MIDIGetNumberOfDevices [] int)
   (get-device MIDIGetDevice [int] void*)
   (create-client* MIDIClientCreate [void* notify-cb void* void*] int)
   (create-input-port* MIDIInputPortCreate [void* void* packet-cb void* void*] int)
   (get-string-property MIDIObjectGetStringProperty [void* void* void*] int)
   (get-entity MIDIDeviceGetEntity [void* int] void*)
   (get-source MIDIEntityGetSource [void* int] void*)
   (connect-source* MIDIPortConnectSource [void* void* void*])
   ))

(defclib foundation-lib
  (:libname "Foundation")
  (:functions
   (cfstring-create CFStringCreateWithCString [void* constchar* int] void*)
   (cfstring-length CFStringGetLength [void*] int)
   ;; named cfstring-cheat because it's not guaranteed to work.
   ;; only cfstring-to-string is guaranteed, but it's painful to use
   (cfstring-cheat  CFStringGetCStringPtr [void* int] constchar*)
   (cfstring-to-string CFStringGetCString [void* byte* int int] byte)
   ))

(defn init []
  (loadlib coremidi-lib)
  (loadlib foundation-lib))

(def kCFStringEncodingMacRoman 0)

(defcvar coremidi/kMIDIPropertyName)

(defn cfstr [s]
  (cfstring-create nil s kCFStringEncodingMacRoman))

(defn get-name [obj]
  (let [namevar (Memory. Pointer/SIZE)
        status  (get-string-property obj (.getPointer kMIDIPropertyName 0) namevar)]
    (when status
      (cfstring-cheat (.getPointer namevar 0) kCFStringEncodingMacRoman))))

(defn devices []
  (for [i (range (num-devices))]
    (get-device i)))

(defn find-device-by-name [name]
  (let [re (re-pattern name)]
    (first (filter #(re-find re (get-name %)) (devices)))))

(defn create-client [name notify-fn]
  (let [cb        (callback notify-cb notify-fn)
        clientvar (Memory. Pointer/SIZE)
        status    (create-client* (cfstr name) cb nil clientvar)]
    (if-not (= 0 status)
      (throw (Exception. "Error creating client:" status))
      {:raw-client (.getPointer clientvar 0)
       :callback   cb})))

(defn create-input-port [client name read-fn]
  (let [cb      (callback packet-cb read-fn)
        portvar (Memory. Pointer/SIZE)
        status    (create-input-port* (:raw-client client) (cfstr name) cb nil portvar)]
    (if-not (= 0 status)
      (throw (Exception. "Error creating port:" status))
      {:raw-port (.getPointer portvar 0)
       :callback cb})))

(defn connect-source [port source data]
  (connect-source* (:raw-port port) source data))

(defn -main []
  (init)
  (println "There are" (num-devices) "devices")
  (println "The first device has name" (get-name (get-device 0)))
  (println "found" (get-name (find-device-by-name "nanoKONTROL")))
  (let [client (create-client "client" (fn [& more] (println "got notify")))
        _      (println "got client" (get-name (:raw-client client)))
        port   (create-input-port client "port" (fn [& more] (println "got packets")))
        _      (println "got port" (get-name (:raw-port port)))
        device (find-device-by-name "nanoKONTROL")
        _      (println "got device" (get-name device))
        source (get-source (get-entity device 0) 0)
        _      (println "got source (get-name source")]
    (connect-source port source nil)
    (while true
      (Thread/sleep 1000)
      (println port client))
    ))
