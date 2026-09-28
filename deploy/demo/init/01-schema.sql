-- Demo-only schema for the Java authoritative backend.
-- The MySQL image executes this file in MYSQL_DATABASE on first initialization.
-- Definitions are copied from docs/tag/v3.0/mysql/sql/group_buy_market.sql plus
-- the existing agent-dev agent_refund_request table; no new business fields are introduced.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `crowd_tags` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `tag_id` varchar(32) NOT NULL COMMENT '人群ID',
  `tag_name` varchar(64) NOT NULL COMMENT '人群名称',
  `tag_desc` varchar(256) NOT NULL COMMENT '人群描述',
  `statistics` int NOT NULL COMMENT '人群标签统计量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人群标签';

CREATE TABLE IF NOT EXISTS `crowd_tags_detail` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `tag_id` varchar(32) NOT NULL COMMENT '人群ID',
  `user_id` varchar(16) NOT NULL COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tag_user` (`tag_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人群标签明细';

CREATE TABLE IF NOT EXISTS `crowd_tags_job` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `tag_id` varchar(32) NOT NULL COMMENT '标签ID',
  `batch_id` varchar(8) NOT NULL COMMENT '批次ID',
  `tag_type` tinyint(1) NOT NULL DEFAULT '1' COMMENT '标签类型（参与量、消费金额）',
  `tag_rule` varchar(8) NOT NULL COMMENT '标签规则（限定类型 N次）',
  `stat_start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '统计数据，开始时间',
  `stat_end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '统计数据，结束时间',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态；0初始、1计划、2重置、3完成',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人群标签任务';

CREATE TABLE IF NOT EXISTS `group_buy_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `discount_id` varchar(8) NOT NULL COMMENT '折扣ID',
  `group_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '拼团方式（0自动成团、1达成目标拼团）',
  `take_limit_count` int NOT NULL DEFAULT '1' COMMENT '拼团次数限制',
  `target` int NOT NULL DEFAULT '1' COMMENT '拼团目标',
  `valid_time` int NOT NULL DEFAULT '15' COMMENT '拼团时长（分钟）',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '活动状态（0创建、1生效、2过期、3废弃）',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动开始时间',
  `end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动结束时间',
  `tag_id` varchar(8) DEFAULT NULL COMMENT '人群标签规则标识',
  `tag_scope` varchar(4) DEFAULT NULL COMMENT '人群标签规则范围（1可见限制、2参与限制）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团活动';

CREATE TABLE IF NOT EXISTS `group_buy_discount` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `discount_id` varchar(8) NOT NULL COMMENT '折扣ID',
  `discount_name` varchar(64) NOT NULL COMMENT '折扣标题',
  `discount_desc` varchar(256) NOT NULL COMMENT '折扣描述',
  `discount_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '折扣类型（0:base、1:tag）',
  `market_plan` varchar(4) NOT NULL DEFAULT 'ZJ' COMMENT '营销优惠计划',
  `market_expr` varchar(32) NOT NULL COMMENT '营销优惠表达式',
  `tag_id` varchar(8) DEFAULT NULL COMMENT '人群标签',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_discount_id` (`discount_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `group_buy_order` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `original_price` decimal(8,2) NOT NULL COMMENT '原始价格',
  `deduction_price` decimal(8,2) NOT NULL COMMENT '折扣金额',
  `pay_price` decimal(8,2) NOT NULL COMMENT '支付价格',
  `target_count` int NOT NULL COMMENT '目标数量',
  `complete_count` int NOT NULL COMMENT '完成数量',
  `lock_count` int NOT NULL COMMENT '锁单数量',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态（0拼单中、1完成、2失败、3完成含退单）',
  `valid_start_time` datetime NOT NULL COMMENT '拼团开始时间',
  `valid_end_time` datetime NOT NULL COMMENT '拼团结束时间',
  `notify_type` varchar(8) NOT NULL DEFAULT 'HTTP' COMMENT '回调类型',
  `notify_url` varchar(512) DEFAULT NULL COMMENT '回调地址',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `group_buy_order_list` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `order_id` varchar(12) NOT NULL COMMENT '订单ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `goods_id` varchar(16) NOT NULL COMMENT '商品ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `original_price` decimal(8,2) NOT NULL COMMENT '原始价格',
  `deduction_price` decimal(8,2) NOT NULL COMMENT '折扣金额',
  `pay_price` decimal(8,2) NOT NULL COMMENT '支付金额',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态；0初始锁定、1消费完成、2用户退单',
  `out_trade_no` varchar(12) NOT NULL COMMENT '外部交易单号',
  `out_trade_time` datetime DEFAULT NULL COMMENT '外部交易时间',
  `biz_id` varchar(64) NOT NULL COMMENT '业务唯一ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_id` (`order_id`),
  KEY `idx_user_id_activity_id` (`user_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notify_task` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `notify_category` varchar(64) DEFAULT NULL COMMENT '回调种类',
  `notify_type` varchar(8) NOT NULL DEFAULT 'HTTP' COMMENT '回调类型',
  `notify_mq` varchar(32) DEFAULT NULL COMMENT '回调消息',
  `notify_url` varchar(128) DEFAULT NULL COMMENT '回调接口',
  `notify_count` int NOT NULL COMMENT '回调次数',
  `notify_status` tinyint(1) NOT NULL COMMENT '回调状态',
  `parameter_json` varchar(256) NOT NULL COMMENT '参数对象',
  `uuid` varchar(128) NOT NULL COMMENT '唯一标识',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `uq_uuid` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sc_sku_activity` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `goods_id` varchar(16) NOT NULL COMMENT '商品ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_sc_goodsid` (`source`,`channel`,`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道商品活动配置关联表';

CREATE TABLE IF NOT EXISTS `sku` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `goods_id` varchar(16) NOT NULL COMMENT '商品ID',
  `goods_name` varchar(128) NOT NULL COMMENT '商品名称',
  `original_price` decimal(10,2) NOT NULL COMMENT '商品价格',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品信息';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent refund execution idempotency record';
