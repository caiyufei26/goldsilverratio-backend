package com.canLite.service;

import com.canLite.entity.PriceSimulation;
import com.canLite.mapper.PriceSimulationMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 价格模拟：止损/止盈/动态止盈/日止盈计算与持久化。
 */
@Service
public class PriceSimulationService {

    private static final BigDecimal R_STOP = new BigDecimal("0.92");
    private static final BigDecimal R_TAKE = new BigDecimal("1.22");
    private static final BigDecimal R_DYNAMIC = new BigDecimal("0.95");
    private static final BigDecimal R_DAILY = new BigDecimal("1.08");

    private final FundFilterService fundFilterService;
    private final PriceSimulationMapper priceSimulationMapper;

    public PriceSimulationService(FundFilterService fundFilterService,
                                  PriceSimulationMapper priceSimulationMapper) {
        this.fundFilterService = fundFilterService;
        this.priceSimulationMapper = priceSimulationMapper;
    }

    public List<PriceSimulation> listAll() {
        return priceSimulationMapper.selectAll();
    }

    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        return priceSimulationMapper.deleteById(id) > 0;
    }

    /**
     * 计算各项价格并插入数据库。
     *
     * @param rawStockCode 股票代码
     * @param purchasePrice 购买价
     * @return 已持久化且带 id 的实体
     */
    public PriceSimulation calculateAndSave(String rawStockCode, BigDecimal purchasePrice) {
        if (rawStockCode == null || rawStockCode.trim().isEmpty()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("购买价格无效");
        }
        if (!fundFilterService.isValidAShareCode(rawStockCode)) {
            throw new IllegalArgumentException("仅支持A股股票代码");
        }
        String codeSix = fundFilterService.normalizeStockCodeForApi(rawStockCode);
        BigDecimal stopLoss = purchasePrice.multiply(R_STOP).setScale(4, RoundingMode.HALF_UP);
        BigDecimal takeProfit = purchasePrice.multiply(R_TAKE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal histHigh = fundFilterService.getStockHistHigh(codeSix);
        BigDecimal dynamicTakeProfit = null;
        if (histHigh != null) {
            dynamicTakeProfit = histHigh.multiply(R_DYNAMIC).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal lastClose = fundFilterService.getStockLastClose(codeSix);
        BigDecimal dailyTakeProfit = null;
        if (lastClose != null) {
            dailyTakeProfit = lastClose.multiply(R_DAILY).setScale(4, RoundingMode.HALF_UP);
        }
        String stockName = null;
        Map<String, Object> rps = fundFilterService.getStockRps(codeSix);
        if (rps != null) {
            Object sn = rps.get("stockName");
            if (sn != null && !String.valueOf(sn).isEmpty()) {
                stockName = String.valueOf(sn);
            }
        }
        PriceSimulation row = new PriceSimulation();
        row.setStockCode(codeSix);
        row.setStockName(stockName);
        row.setPurchasePrice(purchasePrice.setScale(4, RoundingMode.HALF_UP));
        row.setStopLoss(stopLoss);
        row.setTakeProfit(takeProfit);
        row.setHistHigh(histHigh);
        row.setDynamicTakeProfit(dynamicTakeProfit);
        row.setLastClose(lastClose);
        row.setDailyTakeProfit(dailyTakeProfit);
        priceSimulationMapper.insert(row);
        return row;
    }
}
