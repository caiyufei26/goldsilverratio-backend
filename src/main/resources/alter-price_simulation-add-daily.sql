-- 已存在 price_simulation 表时执行：增加昨日收盘价、日止盈价（仅需执行一次）
ALTER TABLE price_simulation ADD COLUMN last_close DECIMAL(12,4) DEFAULT NULL COMMENT '昨日收盘价' AFTER dynamic_take_profit;
ALTER TABLE price_simulation ADD COLUMN daily_take_profit DECIMAL(12,4) DEFAULT NULL COMMENT '日止盈价=昨日收盘价*1.08' AFTER last_close;
