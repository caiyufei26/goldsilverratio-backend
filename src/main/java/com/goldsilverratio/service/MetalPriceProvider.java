package com.goldsilverratio.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 贵金属实时价格提供者（金、银等）。
 */
public interface MetalPriceProvider {

    /**
     * 获取当前金价（美元/盎司）
     *
     * @return 金价，获取失败为空
     */
    Optional<BigDecimal> getGoldPriceUsd();

    /**
     * 获取当前银价（美元/盎司）
     *
     * @return 银价，获取失败为空
     */
    Optional<BigDecimal> getSilverPriceUsd();
}
