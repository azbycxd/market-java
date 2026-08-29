# Phase 2A — Agent Order Facts API Contract

## 1. Endpoint

`POST /api/v1/agent/order/facts`

This endpoint is read-only. It returns safe business facts for exactly one order in the authenticated caller's scope. It does not mutate orders, teams, activities, Redis, MQ, or notification tasks.

## 2. Authentication boundary

The Java facade obtains `authenticatedUserId` from `AuthenticatedUserProvider`; it is never accepted in the request body.

For local development and tests only, `DevHeaderAuthenticatedUserProvider` accepts the HTTP header `X-Dev-Authenticated-User-Id` when **both** conditions are true:

- the active Spring profile is `dev` or `test`; and
- `agent.facts.dev-header-auth.enabled=true` is explicitly configured.

The provider returns no identity in production profiles even if this property is mistakenly enabled. A later JWT/gateway implementation replaces this provider without changing the request DTO or facts contract.

## 3. Request schema

```json
{
  "outTradeNo": "202608250001"
}
```

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `outTradeNo` | string | yes | External trade number of the caller's own order. |

`userId` is intentionally forbidden in the request schema.

## 4. Successful response schema

All responses use the project's standard envelope: `code`, `info`, and `data`.

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "order": { "status": "COMPLETE" },
    "team": {
      "status": "PROGRESS",
      "targetCount": 3,
      "lockCount": 2,
      "completeCount": 2,
      "validEndTime": "2026-08-25T18:00:00+08:00"
    },
    "activity": { "status": "EFFECTIVE" },
    "references": { "teamId": "12345678", "activityId": 100123 }
  }
}
```

| Path | Type | Meaning |
| --- | --- | --- |
| `data.order.status` | string | Current order status: `CREATE`, `COMPLETE`, or `CLOSE`. |
| `data.team.status` | string | Current team status: `PROGRESS`, `COMPLETE`, `FAIL`, or `COMPLETE_FAIL`. |
| `data.team.targetCount` | integer | Required team size configured on the team. |
| `data.team.lockCount` | integer | Number of locked team orders. |
| `data.team.completeCount` | integer | Number of completed team orders. |
| `data.team.validEndTime` | RFC 3339 datetime | Team validity end time in `Asia/Shanghai` offset. |
| `data.activity.status` | string | Current activity status: `CREATE`, `EFFECTIVE`, `OVERDUE`, or `ABANDONED`. |
| `data.references.teamId` | string | Team reference associated with the owned order. |
| `data.references.activityId` | integer | Activity reference associated with that team. |

The values are facts, not diagnostic labels. No `reasonCode`, recommended action, or natural-language cause is returned.

## 5. Error contract

The HTTP status is currently `200` to match the existing project response convention. Clients must branch on `code`.

| Code | Meaning | `data` |
| --- | --- | --- |
| `0000` | Successful facts lookup. | Facts object. |
| `AUTH_REQUIRED` | No authenticated caller identity was available. | absent |
| `INVALID_ARGUMENT` | `outTradeNo` is missing, blank, or the JSON body is absent. | absent |
| `ORDER_NOT_FOUND_OR_NOT_AUTHORIZED` | No order was found using the exact pair `(authenticatedUserId, outTradeNo)`. This intentionally combines nonexistent and other-user orders. | absent |
| `INTERNAL_SERVICE_ERROR` | Unexpected server-side failure. Java exception details and stack traces are never returned. | absent |

## 6. Explicitly excluded data

The response must not return any other user's identity, request `userId`, payment prices, Redis keys, MQ routing keys, notification task fields (including `parameter_json`), callback URLs, SQL/DAO details, configuration secrets, Java exception messages, or stack traces.

## 7. Java / Agent responsibility boundary

Java authenticates the caller, applies ownership scope, reads the order → team → activity relationship, and returns the facts above. Java does not decide why a group did not succeed and does not perform settlement, refund, locking, state updates, or notify-task reads.

The Agent/Python client sends only `outTradeNo`, supplies the authenticated identity through the configured deployment authentication mechanism, checks `code`, and reasons over successful facts. It must not query Java databases, Redis, DAOs, or internal services directly. It must treat the combined not-found/unauthorized error as opaque and must not infer membership, completion, or a final support diagnosis from a single Java error code.
