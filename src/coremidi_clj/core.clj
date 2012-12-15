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
  (:structs
   (CFRange :location long, :length long)) ;; CFIndex is a native long
  (:functions
   (cfstring-create CFStringCreateWithCString [void* constchar* int] void*)
   (cfstring-find   CFStringFind [void* void* int] CFRange)
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
        _       (println "created namevar")
        status  (get-string-property obj (.getPointer kMIDIPropertyName 0) namevar)]
    (println "called property get")
    (when status
      (cfstring-cheat (.getPointer namevar 0) kCFStringEncodingMacRoman))))

(defn -main []
  (init)
  (println "There are" (num-devices) "devices")
  (println "The first device has name" (get-name (get-device 0)))
  #_(println "found" (find-device-by-name "nanoKONTROL"))
  (println "find foo in barfoobaz:" (cfstring-find (cfstr "barfoobaz")
                                                   (cfstr "foo")
                                                   0))
  (println "find foo in barboobaz:" (cfstring-find (cfstr "barboobaz")
                                                   (cfstr "foo")
                                                   0))
  )
