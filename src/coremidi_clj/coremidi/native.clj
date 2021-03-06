(ns coremidi-clj.coremidi.native
  (:use [clj-native.direct :only [defclib loadlib]]
        [clj-native.dynamic :only [defcvar]])
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
   (create-output-port* MIDIOutputPortCreate [void* void* void*] int)
   (get-string-property MIDIObjectGetStringProperty [void* void* void*] int)
   (get-entity MIDIDeviceGetEntity [void* int] void*)
   (get-source MIDIEntityGetSource [void* int] void*)
   (get-destination MIDIEntityGetDestination [void* int] void*)
   (connect-source* MIDIPortConnectSource [void* void* void*])
   (midi-send* MIDISend [void* void* void*] int)
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
  (loadlib foundation-lib)
  (loadlib coremidi-lib)
  nil)

(def kCFStringEncodingMacRoman 0)

(defcvar coremidi/kMIDIPropertyName)

(defn cfstr [s]
  (cfstring-create nil s kCFStringEncodingMacRoman))

(defn get-name [obj]
  (let [namevar (Memory. Pointer/SIZE)
        status  (get-string-property obj (.getPointer kMIDIPropertyName 0) namevar)]
    (when status
      (cfstring-cheat (.getPointer namevar 0) kCFStringEncodingMacRoman))))


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

(defn- write-bytes-to-packet [^Pointer ptr bytes]
  (doto ptr
    (.setLong  0 0) ;; timeStamp; 0 means "now"
    (.setShort 8 (count bytes)) ;; size
    (.write    10 (into-array Byte/TYPE bytes) 0 (count bytes)))
  )

(defn write-bytes-to-packet-list [ptr bytes]
  (.setInt ptr 0 1) ;; numPackets
  (write-bytes-to-packet (.share ptr 4) bytes)
  )

;; ensure the CoreMIDI library is loaded
(defonce ^:private ensure-loaded
  (init))
