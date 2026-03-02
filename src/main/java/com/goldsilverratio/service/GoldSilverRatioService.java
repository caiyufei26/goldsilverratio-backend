package com.goldsilverratio.service;

import com.goldsilverratio.entity.GoldSilverRatio;

import java.util.List;

/**
 * 金银比业务：获取最新一条、分页列表（实体）。
 */
public interface GoldSilverRatioService {

    /**
     * 获取最新一条金银比记录，无数据时返回 null。
     *
     * @return 最新记录或 null
     */
    GoldSilverRatio getLatest();

    /**
     * 分页查询，按 record_time 倒序。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 实体列表，不会为 null
     */
    List<GoldSilverRatio> listPage(int page, int size);
}
