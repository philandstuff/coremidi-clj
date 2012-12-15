(ns coremidi-clj.core
  (:use [clj-native.direct :only [defclib loadlib]]))

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
   #_(get-string-property MIDIObjectGetStringProperty [void* void* void**] int)))

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

(defn cfstr [s]
  (cfstring-create nil s kCFStringEncodingMacRoman))

(defn device-has-name [name]
  (fn [device]
    
    ))

(defn find-device-by-name [name]
  (some (device-has-name name) (map get-device (range (num-devices)))))

(defn -main []
  (init)
  (println "There are" (num-devices) "devices")
  #_(println "found" (find-device-by-name "nanoKONTROL"))
  (println "find foo in barfoobaz:" (cfstring-find (cfstr "barfoobaz")
                                                   (cfstr "foo")
                                                   0))
  (println "find foo in barboobaz:" (cfstring-find (cfstr "barboobaz")
                                                   (cfstr "foo")
                                                   0))
  )
