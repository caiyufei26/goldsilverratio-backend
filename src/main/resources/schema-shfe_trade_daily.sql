CREATE TABLE IF NOT EXISTS shfe_trade_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL,
    product_id VARCHAR(20) NOT NULL COMMENT '品种代码，如 cu, ag, au',
    product_name VARCHAR(50) NOT NULL COMMENT '品种名称，如 铜, 白银, 黄金',
    volume BIGINT NOT NULL DEFAULT 0 COMMENT '成交量（手）',
    open_interest BIGINT NOT NULL DEFAULT 0 COMMENT '持仓量（手）',
    oi_change BIGINT NOT NULL DEFAULT 0 COMMENT '日增仓（手）',
    UNIQUE KEY uk_date_product (record_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上期所每日交易数据（品种汇总）';
