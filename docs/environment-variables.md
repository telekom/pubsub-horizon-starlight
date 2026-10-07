<!--
Copyright 2024 Deutsche Telekom IT GmbH

SPDX-License-Identifier: Apache-2.0
-->

# Environment variables

Starlight is configured using environment variables. The following environment variables are supported:

| Name | Default | Description |
|---|---|---|
| LOG_LEVEL | INFO | Specifies the logging level for general application logs |
| JAEGER_COLLECTOR_URL | http://jaeger-collector.example.com:9411 | The URL endpoint for the Jaeger collector, which is used for distributed tracing |
| ZIPKIN_SAMPLER_PROBABILITY | 1.0 | Configures the probability of a trace being sampled for Zipkin. A value of 1.0 means all traces are sampled, while 0.0 means no traces are sampled |
| STARLIGHT_INFORMER_NAMESPACE | playground | The Kubernetes namespace from which the EventSubscription CRD is being polled |
| STARLIGHT_FEATURE_PUBLISHER_CHECK | true | Enable ownership verification for published events |
| STARLIGHT_HEADER_PROPAGATION_BLACKLIST | x-spacegate-token,authorization,content-length,host,accept.*,x-forwarded.*,cookie | A list of headers that will not be forwarded in the published event |
| STARLIGHT_ISSUER_URL | http://localhost:8080/auth/realms/default | The issuer(s) that are trusted by Starlight |
| STARLIGHT_DEFAULT_ENVIRONMENT | integration | The default environment that is used for multi-tenancy |
| STARLIGHT_PUBLISHING_TOPIC | published | The Kafka topic where events will be published |
| STARLIGHT_PUBLISHING_TIMEOUT_MS | 5000 | The timeout used when publishing events to Kafka |
| STARLIGHT_KAFKA_BROKERS | kafka:9092 | The Kafka broker that is used for publishing events |
| STARLIGHT_KAFKA_TRANSACTION_PREFIX | starlight | The transaction-prefix that is used for publishing events |
| STARLIGHT_KAFKA_GROUP_ID | starlight | The Kafka consumer group that is used for publishing events |
| STARLIGHT_KAFKA_LINGER_MS | 5 | How long the Kafka waits for other records before transmissing the batch ([Reference](https://docs.confluent.io/platform/current/installation/configuration/producer-configs.html#linger-ms)) |
| STARLIGHT_KAFKA_ACKS | 1 | How often the events needs to be acknowledge by Kafka |
| STARLIGHT_KAFKA_COMPRESSION_ENABLED | false | If events send to Kafka should be compressed |
| STARLIGHT_KAFKA_COMPRESSION_TYPE | none | The compression type used to compress events |
| STARLIGHT_FEATURE_SCHEMA_VALIDATION | false | Enable schema validation for published events |
| ENIAPI_BASEURL | localhost:8080 | Base URL of the SchemaStore endpoint (used for polling event schemas) |
| ENIAPI_REFRESHINTERVAL | 30000 | How often new schemas will be polled from the SchemaStore |
| IRIS_ISSUER_URL | https://iris.example.com/auth/realms/default/protocol/openid-connect/token | The issuer that is used to retrieve a token when calling SchemaStore endpoint |
| CLIENT_ID | foo | Client ID that is used to retrieve a token when calling SchemaStore endpoint |
| CLIENT_SECRET | bar | Client secret that is used to retrieve a token when calling SchemaStore endpoint |
| STARLIGHT_SPECTRE_DIRECT_PUBLISH_ENABLED | false | Master switch for Spectre direct-publish (rewrites `event.type` at publish time). See [docs/spectre-direct-publish.md](spectre-direct-publish.md) |
| STARLIGHT_SPECTRE_DIRECT_PUBLISH_PUBLISHER_ID | gateway | Only direct-publish events from this publisher (OAuth2 `clientId`), matched exactly. Must not be blank |
| STARLIGHT_SPECTRE_DIRECT_PUBLISH_APPLICABLE_TYPE | de.telekom.ei.listener | Event-type gate (exact equality); only events whose original type equals this are considered. Must not be blank |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ENABLED | true | Enables the pod-local subscription cache |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_FALLBACK_MODE | hazelcast-with-mongo-fallback | Read fallback when the local cache cannot serve reads (`hazelcast-with-mongo-fallback` or `none`). With `none`, stale local entries are served indefinitely if necessary |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_MONGO_HEAD_FALLBACK_ENABLED | true | Uses the MongoDB head when the ZooKeeper head cannot be determined (ZooKeeper mode only) |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_SNAPSHOT_COLLECTION | subscriptions.subscriber.horizon.telekom.de.v1-snapshots | MongoDB collection with the snapshot entries |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_HEAD_COLLECTION | subscriptions.subscriber.horizon.telekom.de.v1-head | MongoDB collection with the head of the active snapshot |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_STALE_LOCAL_CACHE_READ_GRACE_PERIOD | 120s | How long a stale local snapshot may serve reads before Hazelcast is used. Only applies to `FALLBACK_MODE=hazelcast-with-mongo-fallback` |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_REQUIRE_LOCAL_CACHE_AT_STARTUP | false | Whether startup waits for the first local snapshot. Only applies to `FALLBACK_MODE=hazelcast-with-mongo-fallback`; with `none`, startup always waits |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_INITIAL_SNAPSHOT_TIMEOUT | 15s | Maximum wait for the first local snapshot when startup waits for it; afterwards startup fails and the process terminates. `0s` waits indefinitely |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_RECONCILE_INTERVAL | 60s | Interval for re-checking the active head (ZooKeeper or MongoDB); `0s` disables it |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_MONGO_HEAD_POLL_JITTER | 10s | Maximum random offset of the first periodic head reconciliation |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_MONGO_SNAPSHOT_SYNC_JITTER | 10s | Maximum random delay before loading a snapshot for prepared preloads and reconnects (ZooKeeper mode only) |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_ENABLED | true | ZooKeeper as head source; `false` polls only the MongoDB head |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_ENSEMBLE_TRACKER_ENABLED | true | Lets Curator follow ZooKeeper-published ensemble addresses. Can be `false` for local operation, because the published addresses are not reachable from the host |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_CONNECT_STRING |  | ZooKeeper connect string; required when the local cache uses ZooKeeper (Helm derives `horizon-zookeeper.<namespace>.svc.cluster.local:2181`) |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_PREPARED_PATH | /horizon/subscriptions/prepared | ZNode path of the prepared head |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_ACTIVATE_PATH | /horizon/subscriptions/activated | ZNode path of the activated head |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_CONNECTION_TIMEOUT | 5s | Curator connection timeout; also bounds each ZooKeeper head read |
| STARLIGHT_CACHE_LOCAL_SUBSCRIPTION_CACHE_ZOO_KEEPER_SESSION_TIMEOUT | 30s | ZooKeeper session timeout |
