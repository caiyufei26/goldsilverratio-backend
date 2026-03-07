-- 美元计价金银比表（GoldAPI XAU/USD、XAG/USD，价格单位：美元/盎司）
CREATE TABLE IF NOT EXISTS gold_silver_ratio_usd (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gold_price DECIMAL(14, 4) NOT NULL COMMENT '金价 USD/oz',
    silver_price DECIMAL(14, 4) NOT NULL COMMENT '银价 USD/oz',
    ratio DECIMAL(10, 4) NOT NULL,
    record_date DATE NOT NULL,
    record_time DATETIME NULL,
    UNIQUE KEY uk_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美元计价金银比(GoldAPI)';
