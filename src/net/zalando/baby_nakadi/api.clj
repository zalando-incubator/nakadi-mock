(ns net.zalando.baby-nakadi.api
  (:require [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [net.zalando.baby-nakadi.subscriptions :as subscriptions]
            [ring.util.response :as r]
            [clojure.set :as set]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clj-uuid :as uuid]
            [clojure.string :as s]
            [clj-time.local :as t-local]
            [clj-time.format :as t-fmt]))

(defrecord Controller [configuration]
  component/Lifecycle
  (start [this]
    (log/info "Starting API Controller")
    this)
  (stop [this]
    (log/info "Stopping API Controller")
    this))

(defn get-hello
  "Says hello"
  [{:as this :keys [configuration]} {:as params :keys [name]} request]
  (log/debug "API configuration: %s" configuration)
  (log/info "Hello called for %s" name)
  (r/response {:message (str "Hello " name)
               :details {:X-friboo (require-config configuration :example-param)}}))

(defn list-subscription
  "Returns every subscriptions"
  [this params request]
  (r/response @subscriptions/subscriptions))

(defn- request-json
  "Parses request body as JSON"
  [request]
  (let [body-reader (io/reader (:body request))
        body-str (slurp body-reader)]
    (json/decode body-str)))

(defn- error-response [e-i msg status]
  (r/status (r/response (merge e-i {:message msg})) status))
  
(defn save-subscription
  "Saves subscription"
  [this params request]
  (let [req-json (request-json request)]
    (try (let [[added? s] (subscriptions/append-to-subscriptions+ req-json) ; try to append.
               status-code (if added? 201 200)]
           ;; succeed! 201 -or- 200.
           (r/status (r/response s) status-code))
         (catch clojure.lang.ExceptionInfo e
           ;; invalid request.
           (let [i (ex-data e)]
             (case (:type i)
               :unknown-fields
               (error-response i "unknown-fields" 400)
               :missing-fields
               (error-response i "missing-fields" 422)
               (throw e)))))))
