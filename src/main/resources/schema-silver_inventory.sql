-- 上期所白银库存表（若不存在可执行此脚本）
CREATE TABLE IF NOT EXISTS silver_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL,
    inventory_kg DECIMAL(14, 2) NOT NULL COMMENT '库存千克',
    UNIQUE KEY uk_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上期所白银库存每日数据';
