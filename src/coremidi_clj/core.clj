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
  ;; These are the notional structs, but they use the struct hack
  ;; and anyway are called by the callback and clj-native only
  ;; supports void* pointers for callbacks
  #_(:structs
   (MIDIPacket :timestamp i64, :length i16, :data byte[256])
   (MIDIPacketList :num-packets i32, :packets MIDIPacket))
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

;; struct MIDIPacket {
;;     MIDITimeStamp timeStamp; /* 64-bit */
;;     UInt16        length;    /* 16-bit */
;;     Byte          data[length]; /* variable-length */
;; };
;; Note that struct alignment is unaligned on Intel processors.
(defn read-packet
  "Reads a MIDIPacket structure from a JNA Pointer object. Assumes the struct is unaligned (correct on Intel arch)."
  [ptr]
  (let [timestamp  (.getLong  ptr 0)
        size       (.getShort ptr 8)
        data-array (.getByteArray ptr 10 size)]
    {:timestamp timestamp
     :size      size
     :data      (into [] (map #(bit-and 0xff %) (seq data-array)))}))

(defn read-packet-list [ptr]
  (let [num-packets (.getInt ptr 0)
        packet-base (.share ptr 4)]
    (loop [packets [] num-packets num-packets packet-base packet-base]
      (if (zero? num-packets)
        packets
        (let [packet (read-packet packet-base)]
          (recur (conj packets packet) (dec num-packets) (.share packet-base (+ 10 (:size packet)))))))))

(defn decode-controller-change [bytes]
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

(defonce ^:private midi-client (atom nil))

(defn- create-client-if-nil [client]
  (if client
    client
    (create-client "CoreMIDI clj client" (fn [& more] (println "got notify msg")))))

(defn- get-midi-client []
  (when (nil? @midi-client)
    (swap! midi-client create-client-if-nil))
  @midi-client)

;; TODO: devices with multiple entities? Entities with multiple sources?
(defn midi-in [in]
  (if-let [device (find-device-by-name in)]
    (let [entity (get-entity device 0)
          source (get-source entity 0)]
      source)
    (println "Did not find a matching midi input device for:" in)))

;; TODO: keep the callback object somewhere so that it won't get GC'd
(defn connect-to-source [source f]
  (let [port (create-input-port (get-midi-client) "port" f)
        _    (println "got port" (get-name (:raw-port port)))]
    (connect-source port source nil)
    port))

(defn -main []
  (init)
  (println "There are" (num-devices) "devices")
  (println "The first device has name" (get-name (get-device 0)))
  (println "found" (get-name (find-device-by-name "nanoKONTROL")))
  (let [source (midi-in "nanoKONTROL")
        port   (connect-to-source source
                                  (fn [packet-list & more]
                                    (println "got packet list:"
                                             (map decode-packet (read-packet-list packet-list)))))]
    (while true
      (Thread/sleep 1000)
      (println port (get-midi-client)))
    ))
