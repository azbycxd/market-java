USE `group_buy_market_agent_test`;

CREATE TABLE IF NOT EXISTS `agent_refund_request` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `idempotency_key` varchar(128) NOT NULL,
    `user_id` varchar(64) NOT NULL,
    `out_trade_no` varchar(64) DEFAULT NULL,
    `expected_version` varchar(96) DEFAULT NULL,
    `expected_refund_type` varchar(32) DEFAULT NULL,
    `status` varchar(16) NOT NULL,
    `result_code` varchar(64) DEFAULT NULL,
    `result_json` text,
    `created_at` datetime NOT NULL,
    `updated_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_refund_request_idempotency_key` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent refund execution idempotency record';

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
