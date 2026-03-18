-- 价格模拟记录：股票代码、购买价、止损、止盈、历史最高、动态止盈
CREATE TABLE IF NOT EXISTS price_simulation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(64) DEFAULT NULL COMMENT '股票名称',
    purchase_price DECIMAL(12,4) NOT NULL COMMENT '购买价格',
    stop_loss DECIMAL(12,4) NOT NULL COMMENT '止损=购买价*0.92',
    take_profit DECIMAL(12,4) NOT NULL COMMENT '止盈=购买价*1.22',
    hist_high DECIMAL(12,4) DEFAULT NULL COMMENT '历史最高价(用于动态止盈)',
    dynamic_take_profit DECIMAL(12,4) DEFAULT NULL COMMENT '动态止盈=历史最高*0.95',
    last_close DECIMAL(12,4) DEFAULT NULL COMMENT '昨日收盘价',
    daily_take_profit DECIMAL(12,4) DEFAULT NULL COMMENT '日止盈价=昨日收盘价*1.08',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格模拟记录';
