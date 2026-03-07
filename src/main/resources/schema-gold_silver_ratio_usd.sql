-- 美元计价金银比表（多数据源：goldapi / jisuapi，价格单位：美元/盎司）
CREATE TABLE IF NOT EXISTS gold_silver_ratio_usd (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gold_price DECIMAL(14, 4) NOT NULL COMMENT '金价 USD/oz',
    silver_price DECIMAL(14, 4) NOT NULL COMMENT '银价 USD/oz',
    ratio DECIMAL(10, 4) NOT NULL,
    record_date DATE NOT NULL,
    record_time DATETIME NULL,
    data_source VARCHAR(20) NOT NULL DEFAULT 'goldapi' COMMENT '数据来源: goldapi, jisuapi, manual',
    UNIQUE KEY uk_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美元计价金银比';

-- 已有表增加 data_source 列（若表已存在可单独执行）：
-- ALTER TABLE gold_silver_ratio_usd ADD COLUMN data_source VARCHAR(20) NOT NULL DEFAULT 'goldapi' COMMENT '数据来源: goldapi, jisuapi, manual' AFTER record_time;
