package com.canLite.service.impl;

import com.canLite.entity.GoldSilverRatioUsd;
import com.canLite.mapper.GoldSilverRatioUsdMapper;
import com.canLite.service.RatioUsdApiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 美元计价金银比 API 业务实现。
 */
@Service
public class RatioUsdApiServiceImpl implements RatioUsdApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RECORD_DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GoldSilverRatioUsdMapper goldSilverRatioUsdMapper;

    public RatioUsdApiServiceImpl(GoldSilverRatioUsdMapper goldSilverRatioUsdMapper) {
        this.goldSilverRatioUsdMapper = goldSilverRatioUsdMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveByDate(BigDecimal gold, BigDecimal silver, String dateStr, String dataSource) {
        if (gold == null || silver == null || silver.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("gold/silver invalid");
        }
        if (dataSource == null || dataSource.isEmpty()) {
            dataSource = "goldapi";
        }
        BigDecimal ratio = gold.divide(silver, 4, RoundingMode.HALF_UP);
        LocalDate recordDate = LocalDate.parse(dateStr, YYYYMMDD);
        LocalDateTime recordTime = LocalDateTime.parse(
                dateStr + "000000",
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        goldSilverRatioUsdMapper.deleteByDate(dateStr);
        GoldSilverRatioUsd record = new GoldSilverRatioUsd();
        record.setGoldPrice(gold);
        record.setSilverPrice(silver);
        record.setRatio(ratio);
        record.setRecordDate(recordDate);
        record.setRecordTime(recordTime);
        record.setDataSource(dataSource);
        goldSilverRatioUsdMapper.insert(record);
    }

    @Override
    public boolean hasDataForDate(String dateStr) {
        return goldSilverRatioUsdMapper.countByDate(dateStr) > 0;
    }

    @Override
    public List<Map<String, Object>> listPage(int page, int size) {
        int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 100 : size);
        int limit = size <= 0 ? 100 : Math.min(size, 500);
        List<GoldSilverRatioUsd> list = goldSilverRatioUsdMapper.selectPage(offset, limit);
        return toMapList(list);
    }

    @Override
    public List<Map<String, Object>> listByMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        List<GoldSilverRatioUsd> list = goldSilverRatioUsdMapper.selectByMonth(start, end);
        return toMapList(list);
    }

    private List<Map<String, Object>> toMapList(List<GoldSilverRatioUsd> list) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (GoldSilverRatioUsd r : list) {
            Map<String, Object> row = new HashMap<>(4);
            row.put("recordDate",
                    r.getRecordDate() == null
                            ? (r.getRecordTime() == null ? "" : r.getRecordTime().format(RECORD_DATE_DISPLAY))
                            : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("goldPrice", r.getGoldPrice());
            row.put("silverPrice", r.getSilverPrice());
            row.put("ratio", r.getRatio());
            row.put("dataSource", r.getDataSource() != null ? r.getDataSource() : "goldapi");
            result.add(row);
        }
        return result;
    }
}
