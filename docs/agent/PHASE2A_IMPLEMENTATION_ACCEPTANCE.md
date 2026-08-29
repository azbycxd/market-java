# Phase 2A — Agent Order Facts Implementation Acceptance

## Verdict

PHASE2A_IMPLEMENTATION = PASS

REAL_MYSQL_INTEGRATION = NOT_TESTED

The implementation is not documentation-only. Source inspection confirms the complete HTTP to MyBatis read path. The focused tests compile the multi-module reactor and pass; they use MockMvc and mocked repositories, and no application or MySQL connection was started.

## A. Actual call chain

| Step | Class / method | File | Evidence |
| --- | --- | --- | --- |
| HTTP | AgentFactsController#getOrderFacts | group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentFactsController.java | PostMapping /facts under RequestMapping /api/v1/agent/order. |
| Authentication | AuthenticatedUserProvider#getAuthenticatedUserId | group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/AuthenticatedUserProvider.java | Controller obtains caller identity outside the request DTO. |
| Dev/test identity | DevHeaderAuthenticatedUserProvider#getAuthenticatedUserId | group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/DevHeaderAuthenticatedUserProvider.java | Reads X-Dev-Authenticated-User-Id only with dev/test profile and explicit opt-in. |
| Facts service | OrderFactsService#getOrderFacts | group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/facts/OrderFactsService.java | Reads order, then team, then activity; builds OrderFactsVO. |
| Repository contract | ITradeRepository queryMarketPayOrderEntityByOutTradeNo, queryGroupBuyTeamByTeamId, queryGroupBuyActivityEntityByActivityId | group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/repository/ITradeRepository.java | Existing contract used by the new service. |
| Repository implementation | Same methods on TradeRepository | group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java | Converts DAO results to domain entities. |
| DAO | IGroupBuyOrderListDao, IGroupBuyOrderDao, IGroupBuyActivityDao | group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/dao/ | MyBatis mapper interfaces. |
| Mapper | queryGroupBuyOrderRecordByOutTradeNo, queryGroupBuyTeamByTeamId, queryGroupBuyActivityByActivityId | group-buy-market-app/src/main/resources/mybatis/mapper/ | Actual SELECT statements below. |
| Tables | group_buy_order_list → group_buy_order → group_buy_activity | Same mapper XML files | IDs in later reads come only from the owned order chain. |

## B. Phase 2A Java files

### New

- group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentOrderFactsRequestDTO.java
- group-buy-market-api/src/main/java/cn/bugstack/api/dto/AgentOrderFactsResponseDTO.java
- group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/model/valobj/OrderFactsVO.java
- group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/IOrderFactsService.java
- group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/facts/OrderFactsService.java
- group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/AuthenticatedUserProvider.java
- group-buy-market-trigger/src/main/java/cn/bugstack/trigger/auth/DevHeaderAuthenticatedUserProvider.java
- group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/AgentFactsController.java
- group-buy-market-app/src/test/java/cn/bugstack/test/agent/OrderFactsServiceTest.java
- group-buy-market-app/src/test/java/cn/bugstack/test/agent/AgentFactsControllerTest.java
- group-buy-market-app/src/test/java/cn/bugstack/test/agent/DevHeaderAuthenticatedUserProviderTest.java

### Modified

- group-buy-market-types/src/main/java/cn/bugstack/types/enums/ResponseCode.java — adds AUTH_REQUIRED, INVALID_ARGUMENT, ORDER_NOT_FOUND_OR_NOT_AUTHORIZED, and INTERNAL_SERVICE_ERROR.
- group-buy-market-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderRequestDTO.java — existing nested callback DTO now has explicit equivalent accessors for Java 8/Maven toolchain compilation; this does not affect Facts behavior.

No Phase 2A repository implementation, DAO interface, or MyBatis mapper file was added or changed for the Facts query path. The working tree has a separate pre-existing modification to group_buy_order_list_mapper.xml for queryTimeoutUnpaidOrderList; this API never calls it.

## C. API and authentication

The endpoint exists exactly as required:

~~~~text
POST /api/v1/agent/order/facts
~~~~

AgentOrderFactsRequestDTO has exactly one business request field: outTradeNo. It has no userId.

DevHeaderAuthenticatedUserProvider reads the development Header only when both conditions hold:

- active Spring profile is dev or test;
- agent.facts.dev-header-auth.enabled is true.

For prod and every other profile it returns no identity even if the Header and property are supplied. The controller then returns AUTH_REQUIRED, rather than trusting the Header.

## D. Actual SQL / mapper reads

Phase 2A adds no new SELECT. It reuses the following existing statements.

### 1. Owned order lookup

Mapper: group-buy-market-app/src/main/resources/mybatis/mapper/group_buy_order_list_mapper.xml  
Statement: queryGroupBuyOrderRecordByOutTradeNo

~~~~sql
select user_id, team_id, order_id, activity_id, start_time,
       end_time, goods_id, source, channel, original_price, deduction_price, pay_price, status
from group_buy_order_list
where out_trade_no = #{outTradeNo} and user_id = #{userId}
~~~~

TradeRepository#queryMarketPayOrderEntityByOutTradeNo receives userId from OrderFactsService's authenticatedUserId parameter. It does not first search by out_trade_no alone. A trade number owned by another user therefore produces no order in this flow.

### 2. Team lookup

Mapper: group-buy-market-app/src/main/resources/mybatis/mapper/group_buy_order_mapper.xml  
Statement: queryGroupBuyTeamByTeamId

~~~~sql
select team_id, activity_id, target_count, complete_count, lock_count, status,
       valid_start_time, valid_end_time, notify_type, notify_url
from group_buy_order
where team_id = #{teamId}
~~~~

teamId is obtained only from the ownership-scoped order result. The mapper's legacy callback fields are not returned by the Facts service or response DTO.

### 3. Activity lookup

Mapper: group-buy-market-app/src/main/resources/mybatis/mapper/group_buy_activity_mapper.xml  
Statement: queryGroupBuyActivityByActivityId

~~~~sql
select activity_id, activity_name, discount_id, group_type, take_limit_count,
       target, valid_time, status, start_time, end_time, tag_id, tag_scope
from group_buy_activity
where activity_id = #{activityId}
~~~~

activityId is obtained only from the resolved team. All three statements are SELECT. The Phase 2A service/controller invoke no insert, update, delete, refund, settlement, lock-order, Redis, or notification methods.

## E. Returned Facts and actual sources

OrderFactsService creates OrderFactsVO from repository entities, then AgentFactsController#toResponse explicitly assigns each response field.

| Response field | Actual source |
| --- | --- |
| order.status | MarketPayOrderEntity.tradeOrderStatusEnumVO.name() |
| team.status | GroupBuyTeamEntity.status.name() |
| team.targetCount | GroupBuyTeamEntity.targetCount |
| team.lockCount | GroupBuyTeamEntity.lockCount |
| team.completeCount | GroupBuyTeamEntity.completeCount |
| team.validEndTime | GroupBuyTeamEntity.validEndTime |
| activity.status | GroupBuyActivityEntity.status.name() |
| references.teamId | GroupBuyTeamEntity.teamId |
| references.activityId | GroupBuyActivityEntity.activityId |

No reasonCode is constructed. The response DTO has no user identity, callback URL, notify parameter, Redis, MQ, DAO, SQL, or exception fields.

## F. Error paths

| Condition | Concrete implementation | External code |
| --- | --- | --- |
| Missing identity | provider returns empty Optional | AUTH_REQUIRED |
| Blank/null body or malformed JSON | null/blank validation and HttpMessageNotReadableException handler | INVALID_ARGUMENT |
| No matching ownership-scoped order | repository returns null; controller maps null facts | ORDER_NOT_FOUND_OR_NOT_AUTHORIZED |
| Other user's order | same user_id + out_trade_no query returns no row | ORDER_NOT_FOUND_OR_NOT_AUTHORIZED |
| Unexpected failure | controller catches Exception, logs internally, returns standard envelope | INTERNAL_SERVICE_ERROR |

Other-user and nonexistent-order cases are externally indistinguishable. The error response does not return an exception message or stack trace.

## G. Test result

Executed with JDK 8:

~~~~text
mvn -q -Dmaven.repo.local=.m2-phase2a-acceptance \
  -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false \
  -Dtest=OrderFactsServiceTest,AgentFactsControllerTest,DevHeaderAuthenticatedUserProviderTest test
~~~~

Result: 10 tests, 0 failures, 0 errors, 0 skipped.

- OrderFactsServiceTest verifies the three read calls, Fact population, and no additional repository interactions.
- AgentFactsControllerTest verifies safe output, invalid input, authentication, unified absent/unauthorized behavior, and hidden unexpected-exception details.
- DevHeaderAuthenticatedUserProviderTest verifies dev/test opt-in, production Header rejection, and no userId in the request DTO.

Only unit tests were completed; no real MySQL connection was made. No service was started and no live HTTP request was sent. The SQL above is therefore source-level MyBatis evidence, not executed database-integration evidence.

Existing non-Phase-2A tests were compiled in the Maven reactor but not executed because the focused test selector intentionally ran only the three isolated Phase 2A test classes. Existing relevant SpringBootTest tests may require MySQL, Redis, or RabbitMQ and were not claimed as passing.

## H. Still unverified

- Live MySQL schema/data compatibility and execution of the three SELECT statements.
- Spring application startup with MySQL, Redis, and RabbitMQ configuration.
- A live HTTP request through the running application.
- The future JWT/gateway AuthenticatedUserProvider and production claim mapping.

