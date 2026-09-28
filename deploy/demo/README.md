# Java Demo Docker environment

This directory provides an isolated demonstration environment for the Java authoritative backend. It does not publish the Java, MySQL, Redis, or RabbitMQ ports to the host. An Agent container should join the Compose network and call `http://java:8091`.

## Build

The repository-root `Dockerfile` is the production/demo image definition. It builds the complete Maven reactor with Java 8 in the builder stage and copies only the executable jar into the Java 8 JRE runtime stage.

```bash
docker build -t group-buy-market-java:demo .
```

No host Maven, JDK, or prebuilt `target` directory is used.

## Start the isolated demo stack

Copy `.env.example` to a local `.env`, replace all placeholder credentials and JWT values, then run:

```bash
docker compose --env-file deploy/demo/.env -f deploy/demo/docker-compose.yml up -d --build
```

The Java container uses profile `demo`. Its dependencies are:

- MySQL database `group_buy_market_demo`, initialized from `deploy/demo/init/` on the first empty-volume startup.
- A dedicated Redis container and logical database.
- A dedicated RabbitMQ container, vhost, exchange, routing keys, and queues.
- `TimeoutRefundJob` and `GroupBuyNotifyJob` disabled by `application-demo.yml`.

The image default is `JAVA_TOOL_OPTIONS=-Xms256m -Xmx768m`. Override that environment variable at deployment time when a different container memory budget is required.

## Health

The image healthcheck calls:

```text
GET http://127.0.0.1:8091/actuator/health/liveness
```

This reuses Spring Boot Actuator's liveness group and checks only that the Java process is responsive. It does not perform model or RAG checks.

From the host, inspect health without publishing the Java port:

```bash
docker compose --env-file deploy/demo/.env -f deploy/demo/docker-compose.yml exec -T java \
  curl -fsS http://127.0.0.1:8091/actuator/health/liveness
```

## Seed data

| Refund scenario | userId / JWT sub | outTradeNo | teamId | activityId |
| --- | --- | --- | --- | ---: |
| UNPAID | `agent_c3_unpaid` | `930000000001` | `91000001` | 900001 |
| PAID_UNFORMED | `agent_c3_paid_unformed` | `930000000002` | `91000002` | 900001 |
| PAID_FORMED | `agent_c3_paid_formed` | `930000000003` | `91000003` | 900001 |
| CLOSED | `agent_c3_closed` | `930000000004` | `91000004` | 900001 |

These identities and state combinations reuse the existing agent-dev C3 seed. There is no separate user table in this project; the demo identities are the `user_id` values in `group_buy_order_list`.
