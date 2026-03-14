package com.canLite.service.impl;

import com.canLite.entity.GoldSilverRatio;
import com.canLite.mapper.GoldSilverRatioMapper;
import com.canLite.service.GoldSilverRatioService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 金银比业务实现：最新一条、分页列表。
 */
@Service
public class GoldSilverRatioServiceImpl implements GoldSilverRatioService {

    private final GoldSilverRatioMapper goldSilverRatioMapper;

    public GoldSilverRatioServiceImpl(GoldSilverRatioMapper goldSilverRatioMapper) {
        this.goldSilverRatioMapper = goldSilverRatioMapper;
    }

    @Override
    public GoldSilverRatio getLatest() {
        return goldSilverRatioMapper.selectLatest();
    }

    @Override
    public List<GoldSilverRatio> listPage(int page, int size) {
        int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 10 : size);
        int limit = size <= 0 ? 10 : Math.min(size, 500);
        return goldSilverRatioMapper.selectPage(offset, limit);
    }
}
