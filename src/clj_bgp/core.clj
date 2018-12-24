(ns clj-bgp.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [link.core :refer :all]
            [link.tcp :as tcp]
            [clojure.tools.logging :as logging])
  (:gen-class))

(defonce router-id (atom "1.2.3.4"))

(defn parseInt [x]
  (Integer/parseInt x))

(defn ip2byte-array [ip]
  (byte-array
   (map parseInt (str/split ip #"\."))))

(defn bgp-handler []
  (create-handler
   (on-message [ch msg]
               (let [remote-addr (remote-addr ch)
                     packet (.copy msg)
                     message-type (.getByte packet 18)
                     readable-bytes (.readableBytes packet)]
                 (logging/infof "BGP: from %s length: %d type: %d"
                                remote-addr readable-bytes  message-type)
                 ;; Rewrite OPEN message's router-id with my own router-id.
                 (if (= message-type 1)
                   (.setBytes packet 24 (ip2byte-array @router-id)))
                 (send! ch packet)))))

(defn neighbor-factory [addr]
  (tcp/tcp-client
   (tcp/tcp-client-factory (bgp-handler)) addr 179))

(defn client-start [config]
  (let [neighbors (config :neighbors)]
    (doall (map neighbor-factory
                (map :address neighbors)))))

(defn load-config [path]
  (load-string (slurp path)))

(defn bgp-start []
  (let [config (load-config (io/resource "bgp.edn"))]
    (reset! router-id (config :router-id))
    (client-start config)
    (while true)))

(defn -main
  "Just establish BGP peering."
  [& args]
  (bgp-start))
