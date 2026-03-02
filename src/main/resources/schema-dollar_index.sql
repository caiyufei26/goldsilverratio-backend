-- 美元指数每日收盘价表（若不存在可执行此脚本）
CREATE TABLE IF NOT EXISTS dollar_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL,
    close_price DECIMAL(12, 4) NOT NULL,
    UNIQUE KEY uk_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美元指数每日收盘价';
