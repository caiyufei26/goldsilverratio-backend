package com.goldsilverratio.service.impl;

import com.goldsilverratio.entity.GoldSilverRatio;
import com.goldsilverratio.mapper.GoldSilverRatioMapper;
import com.goldsilverratio.service.RatioApiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 金银比 API 业务实现。
 */
@Service
public class RatioApiServiceImpl implements RatioApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 列表展示用：数据日期 yyyy-MM-dd */
    private static final DateTimeFormatter RECORD_DATE_DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GoldSilverRatioMapper goldSilverRatioMapper;

    public RatioApiServiceImpl(GoldSilverRatioMapper goldSilverRatioMapper) {
        this.goldSilverRatioMapper = goldSilverRatioMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveByDate(BigDecimal gold, BigDecimal silver, String dateStr) {
        if (gold == null || silver == null || silver.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("gold/silver invalid");
        }
        BigDecimal ratio = gold.divide(silver, 4, RoundingMode.HALF_UP);
        LocalDate recordDate = LocalDate.parse(dateStr, YYYYMMDD);
        LocalDateTime recordTime = LocalDateTime.parse(
                dateStr + "000000",
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        goldSilverRatioMapper.deleteByDate(dateStr);
        GoldSilverRatio record = new GoldSilverRatio();
        record.setGoldPrice(gold);
        record.setSilverPrice(silver);
        record.setRatio(ratio);
        record.setRecordDate(recordDate);
        record.setRecordTime(recordTime);
        if (record.getRecordTime() == null) {
            record.setRecordTime(LocalDateTime.now());
        }
        goldSilverRatioMapper.insert(record);
    }

    @Override
    public List<Map<String, Object>> listPage(int page, int size) {
        int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 100 : size);
        int limit = size <= 0 ? 100 : Math.min(size, 500);
        List<GoldSilverRatio> list = goldSilverRatioMapper.selectPage(offset, limit);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (GoldSilverRatio r : list) {
            Map<String, Object> row = new HashMap<>(4);
            row.put("recordDate",
                    r.getRecordDate() == null
                            ? (r.getRecordTime() == null ? "" : r.getRecordTime().format(RECORD_DATE_DISPLAY))
                            : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("goldPrice", r.getGoldPrice());
            row.put("silverPrice", r.getSilverPrice());
            row.put("ratio", r.getRatio());
            result.add(row);
        }
        return result;
    }
}
