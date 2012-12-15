(ns coremidi-clj.core
  (:use [clj-native.direct :only [defclib loadlib]]
        [clj-native.dynamic :only [defcvar]])
  (:import [com.sun.jna Pointer Memory]))

;; type mappings:
;; MIDI*Ref    == void* (opaque) (where * = Object,Device,Entity etc)
;; OSStatus    == SInt32 == int
;; CFStringRef == void* (opaque)

(defclib coremidi-lib
  (:libname "coremidi")
  (:functions
   (num-devices MIDIGetNumberOfDevices [] int)
   (get-device MIDIGetDevice [int] void*)
   (create-client MIDIClientCreate [])
   (get-string-property MIDIObjectGetStringProperty [void* void* void*] int)))

(defclib foundation-lib
  (:libname "Foundation")
  (:functions
   (cfstring-create CFStringCreateWithCString [void* constchar* int] void*)
   (cfstring-length CFStringGetLength [void*] int)
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

(defn -main []
  (init)
  (println "There are" (num-devices) "devices")
  (println "The first device has name" (get-name (get-device 0)))
  (println "found" (get-name (find-device-by-name "nanoKONTROL"))))
