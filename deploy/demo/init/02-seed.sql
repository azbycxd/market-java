-- Repeatable demo seed. IDs and state combinations intentionally reuse scripts/agent-dev/c3-seed.sql.
-- Demo users are represented by group_buy_order_list.user_id; this project has no separate user table.

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `agent_refund_request`;
TRUNCATE TABLE `notify_task`;
TRUNCATE TABLE `group_buy_order_list`;
TRUNCATE TABLE `group_buy_order`;
TRUNCATE TABLE `sc_sku_activity`;
TRUNCATE TABLE `group_buy_activity`;
TRUNCATE TABLE `group_buy_discount`;
TRUNCATE TABLE `sku`;
TRUNCATE TABLE `crowd_tags_detail`;
TRUNCATE TABLE `crowd_tags_job`;
TRUNCATE TABLE `crowd_tags`;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `group_buy_discount`
(`discount_id`, `discount_name`, `discount_desc`, `discount_type`, `market_plan`, `market_expr`, `tag_id`, `create_time`, `update_time`)
VALUES
('900001', 'Demo refund discount', 'Isolated Docker demo refund seed', 0, 'ZJ', '20', NULL, '2026-01-01 00:00:00', '2026-01-01 00:00:00');

INSERT INTO `group_buy_activity`
(`activity_id`, `activity_name`, `discount_id`, `group_type`, `take_limit_count`, `target`, `valid_time`, `status`, `start_time`, `end_time`, `tag_id`, `tag_scope`, `create_time`, `update_time`)
VALUES
(900001, 'Docker demo refund activity', '900001', 0, 10, 3, 60, 1, '2026-01-01 00:00:00', '2030-01-01 00:00:00', NULL, NULL, '2026-01-01 00:00:00', '2026-01-01 00:00:00');

INSERT INTO `sku`
(`source`, `channel`, `goods_id`, `goods_name`, `original_price`, `create_time`, `update_time`)
VALUES
('s01', 'c01', 'C3_REFUND_01', 'Docker demo refund seed SKU', 100.00, '2026-01-01 00:00:00', '2026-01-01 00:00:00');

INSERT INTO `sc_sku_activity`
(`source`, `channel`, `activity_id`, `goods_id`, `create_time`, `update_time`)
VALUES
('s01', 'c01', 900001, 'C3_REFUND_01', '2026-01-01 00:00:00', '2026-01-01 00:00:00');

-- UNPAID, PAID_UNFORMED, PAID_FORMED and CLOSED teams.
INSERT INTO `group_buy_order`
(`team_id`, `activity_id`, `source`, `channel`, `original_price`, `deduction_price`, `pay_price`, `target_count`, `complete_count`, `lock_count`, `status`, `valid_start_time`, `valid_end_time`, `notify_type`, `notify_url`, `create_time`, `update_time`)
VALUES
('91000001', 900001, 's01', 'c01', 100.00, 20.00, 80.00, 3, 0, 1, 0, '2026-01-01 00:00:00', '2030-01-01 01:00:00', 'MQ', NULL, '2026-01-01 00:00:01', '2026-01-01 00:00:11'),
('91000002', 900001, 's01', 'c01', 100.00, 20.00, 80.00, 3, 1, 2, 0, '2026-01-01 00:00:00', '2030-01-01 01:00:00', 'MQ', NULL, '2026-01-01 00:00:02', '2026-01-01 00:00:12'),
('91000003', 900001, 's01', 'c01', 100.00, 20.00, 80.00, 3, 3, 3, 1, '2026-01-01 00:00:00', '2030-01-01 01:00:00', 'MQ', NULL, '2026-01-01 00:00:03', '2026-01-01 00:00:13'),
('91000004', 900001, 's01', 'c01', 100.00, 20.00, 80.00, 3, 0, 0, 0, '2026-01-01 00:00:00', '2030-01-01 01:00:00', 'MQ', NULL, '2026-01-01 00:00:04', '2026-01-01 00:00:14');

INSERT INTO `group_buy_order_list`
(`user_id`, `team_id`, `order_id`, `activity_id`, `start_time`, `end_time`, `goods_id`, `source`, `channel`, `original_price`, `deduction_price`, `pay_price`, `status`, `out_trade_no`, `out_trade_time`, `biz_id`, `create_time`, `update_time`)
VALUES
('agent_c3_unpaid', '91000001', '940000000001', 900001, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 'C3_REFUND_01', 's01', 'c01', 100.00, 20.00, 80.00, 0, '930000000001', NULL, '900001_agent_c3_unpaid_1', '2026-01-01 00:00:01', '2026-01-01 00:01:01'),
('agent_c3_paid_unformed', '91000002', '940000000002', 900001, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 'C3_REFUND_01', 's01', 'c01', 100.00, 20.00, 80.00, 1, '930000000002', '2026-01-01 00:02:02', '900001_agent_c3_paid_unformed_1', '2026-01-01 00:00:02', '2026-01-01 00:01:02'),
('agent_c3_paid_formed', '91000003', '940000000003', 900001, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 'C3_REFUND_01', 's01', 'c01', 100.00, 20.00, 80.00, 1, '930000000003', '2026-01-01 00:02:03', '900001_agent_c3_paid_formed_1', '2026-01-01 00:00:03', '2026-01-01 00:01:03'),
('agent_c3_closed', '91000004', '940000000004', 900001, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 'C3_REFUND_01', 's01', 'c01', 100.00, 20.00, 80.00, 2, '930000000004', '2026-01-01 00:02:04', '900001_agent_c3_closed_1', '2026-01-01 00:00:04', '2026-01-01 00:01:04');
