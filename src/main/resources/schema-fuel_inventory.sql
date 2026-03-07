-- 上期所燃油（燃料油）库存表
CREATE TABLE IF NOT EXISTS fuel_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL,
    inventory_kg DECIMAL(14, 2) NOT NULL COMMENT '库存千克',
    UNIQUE KEY uk_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上期所燃油库存每日数据';
