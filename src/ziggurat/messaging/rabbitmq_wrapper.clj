(ns ziggurat.messaging.rabbitmq-wrapper
  (:require [ziggurat.config :refer [get-in-config]]
            [mount.core :refer [defstate]]
            [ziggurat.config :refer [ziggurat-config]]
            [ziggurat.sentry :refer [sentry-reporter]]
            [ziggurat.retry :refer [with-retry]]
            [ziggurat.messaging.util :refer [is-connection-required?]]
            [ziggurat.tracer :refer [tracer]]
            [ziggurat.messaging.rabbitmq.connection :as rmq-connection]
            [ziggurat.messaging.rabbitmq.producer :as rmq-producer]
            [ziggurat.messaging.rabbitmq.consumer :as rmq-consumer]
            [mount.core :as mount])
  (:import (ziggurat.messaging.messaging_interface MessagingProtocol)))

(defn start-connection [config stream-routes]
  (when (is-connection-required? (:ziggurat config) stream-routes)
    (rmq-connection/start-connection config)))

(defn stop-connection [connection config stream-routes]
  (when (is-connection-required? (:ziggurat config) stream-routes)
    (rmq-connection/stop-connection connection config)))

(defstate connection
          :start (start-connection ziggurat.config/config (:stream-routes (mount/args)))
          :stop (stop-connection connection ziggurat.config/config (:stream-routes (mount/args))))

(defn publish
  ([exchange message-payload]
   (publish exchange message-payload nil))
  ([exchange message-payload expiration]
   (rmq-producer/publish connection exchange message-payload expiration)))

(defn create-and-bind-queue
  ([queue-name exchange-name]
   (create-and-bind-queue queue-name exchange-name nil))
  ([queue-name exchange-name dead-letter-exchange]
   (rmq-producer/create-and-bind-queue connection queue-name exchange-name dead-letter-exchange)))

(defn get-messages-from-queue
  ([queue-name ack?]
   (get-messages-from-queue queue-name ack? 1))
  ([queue-name ack? count]
   (rmq-consumer/get-messages-from-queue connection queue-name ack? count)))

(defn process-messages-from-queue [queue-name count processing-fn]
  (rmq-consumer/process-messages-from-queue connection queue-name count processing-fn))

(defn start-subscriber [prefetch-count wrapped-mapper-fn queue-name]
  (rmq-consumer/start-subscriber connection prefetch-count wrapped-mapper-fn queue-name))

(defn consume-message [ch meta payload ack?]
  (rmq-consumer/consume-message ch meta payload ack?))

(deftype RabbitMQMessaging []
  MessagingProtocol
  (defn start-connection [impl config stream-routes]
    (start-connection config stream-routes))
  (defn stop-connection [impl connection config stream-routes]
    (stop-connection connection config stream-routes))
  (defn publish
    ([impl exchange message-payload] (publish exchange message-payload))
    ([impl exchange message-payload expiration] (publish exchange message-payload expiration)))
  (defn get-messages-from-queue
    ([impl queue-name ack?] (get-messages-from-queue queue-name ack?))
    ([impl queue-name ack? count] (get-messages-from-queue queue-name ack? count)))
  (defn process-messages-from-queue [impl queue-name count processing-fn]
    (process-messages-from-queue queue-name count processing-fn))
  (defn start-subscriber [impl prefetch-count wrapped-mapper-fn queue-name]
    (start-subscriber prefetch-count wrapped-mapper-fn queue-name))
  (defn consume-message [impl ch meta payload ack?]
    (consume-message ch meta payload ack?)))

