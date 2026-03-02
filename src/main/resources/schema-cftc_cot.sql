-- CFTC COT 持仓报告
CREATE TABLE IF NOT EXISTS cftc_cot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL COMMENT '报告日期(周)',
    commodity_code VARCHAR(20) NOT NULL DEFAULT 'GC' COMMENT '品种代码 GC=黄金 SI=白银',
    total_open_interest BIGINT COMMENT '总持仓量',
    fund_long BIGINT COMMENT '基金多头',
    fund_short BIGINT COMMENT '基金空头',
    fund_net BIGINT COMMENT '基金净持仓',
    commercial_long BIGINT COMMENT '商业多头',
    commercial_short BIGINT COMMENT '商业空头',
    reportable_traders INT COMMENT '交易商总数',
    record_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_commodity (record_date, commodity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CFTC COT 持仓报告';
