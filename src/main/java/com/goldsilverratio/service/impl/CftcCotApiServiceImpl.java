package com.goldsilverratio.service.impl;

import com.goldsilverratio.entity.CftcCot;
import com.goldsilverratio.mapper.CftcCotMapper;
import com.goldsilverratio.service.CftcCotApiService;
import com.goldsilverratio.service.CftcCotFetcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CFTC COT 持仓报告 API 业务实现。
 */
@Service
public class CftcCotApiServiceImpl implements CftcCotApiService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_COMMODITY = "GC";

    private final CftcCotMapper cftcCotMapper;
    private final CftcCotFetcher cftcCotFetcher;

    public CftcCotApiServiceImpl(CftcCotMapper cftcCotMapper, CftcCotFetcher cftcCotFetcher) {
        this.cftcCotMapper = cftcCotMapper;
        this.cftcCotFetcher = cftcCotFetcher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int fetchFromCftc() {
        List<Map<String, Object>> rows = cftcCotFetcher.fetchLatest();
        int saved = 0;
        for (Map<String, Object> row : rows) {
            try {
                save(row);
                saved++;
            } catch (Exception e) {
                // 单条失败不中断，继续保存其他
            }
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Map<String, Object> body) {
        String dateStr = getString(body, "date");
        if (dateStr == null || dateStr.length() != 8) {
            throw new IllegalArgumentException("date 格式须为 yyyyMMdd");
        }
        String commodity = getString(body, "commodityCode");
        if (commodity == null || commodity.isEmpty()) {
            commodity = DEFAULT_COMMODITY;
        }
        LocalDate recordDate = LocalDate.parse(dateStr, YYYYMMDD);
        cftcCotMapper.deleteByDateAndCommodity(recordDate, commodity);

        CftcCot record = new CftcCot();
        record.setRecordDate(recordDate);
        record.setCommodityCode(commodity);
        record.setTotalOpenInterest(toLong(body.get("totalOpenInterest")));
        record.setFundLong(toLong(body.get("fundLong")));
        record.setFundShort(toLong(body.get("fundShort")));
        record.setFundNet(toLong(body.get("fundNet")));
        record.setCommercialLong(toLong(body.get("commercialLong")));
        record.setCommercialShort(toLong(body.get("commercialShort")));
        record.setReportableTraders(toInt(body.get("reportableTraders")));
        record.setRecordTime(LocalDateTime.now());

        if (record.getFundNet() == null && record.getFundLong() != null && record.getFundShort() != null) {
            record.setFundNet(record.getFundLong() - record.getFundShort());
        }
        cftcCotMapper.insert(record);
    }

    @Override
    public List<Map<String, Object>> list(String commodityCode, Integer year, Integer month,
                                          int page, int size) {
        String commodity = (commodityCode != null && !commodityCode.isEmpty())
                ? commodityCode : DEFAULT_COMMODITY;
        List<CftcCot> list;
        if (year != null && month != null) {
            list = cftcCotMapper.selectByMonth(commodity, year, month);
        } else {
            int offset = (page <= 0 ? 0 : page - 1) * (size <= 0 ? 100 : size);
            int limit = size <= 0 ? 100 : Math.min(size, 500);
            list = cftcCotMapper.selectPage(commodity, offset, limit);
        }
        return buildListWithChange(list, commodity);
    }

    private List<Map<String, Object>> buildListWithChange(List<CftcCot> list, String commodity) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (CftcCot r : list) {
            Map<String, Object> row = new HashMap<>(16);
            row.put("recordDate", r.getRecordDate() == null ? "" : r.getRecordDate().format(DISPLAY));
            row.put("commodityCode", r.getCommodityCode());
            row.put("totalOpenInterest", r.getTotalOpenInterest());
            row.put("fundLong", r.getFundLong());
            row.put("fundShort", r.getFundShort());
            row.put("fundNet", r.getFundNet());
            row.put("commercialLong", r.getCommercialLong());
            row.put("commercialShort", r.getCommercialShort());
            row.put("reportableTraders", r.getReportableTraders());

            CftcCot prev = cftcCotMapper.selectPrevByDate(r.getRecordDate(), commodity);
            if (prev != null) {
                row.put("changeTotalOpenInterest", diff(r.getTotalOpenInterest(), prev.getTotalOpenInterest()));
                row.put("changeFundLong", diff(r.getFundLong(), prev.getFundLong()));
                row.put("changeFundShort", diff(r.getFundShort(), prev.getFundShort()));
                row.put("changeFundNet", diff(r.getFundNet(), prev.getFundNet()));
                row.put("changeCommercialLong", diff(r.getCommercialLong(), prev.getCommercialLong()));
                row.put("changeCommercialShort", diff(r.getCommercialShort(), prev.getCommercialShort()));
                row.put("changeReportableTraders", diffInt(r.getReportableTraders(), prev.getReportableTraders()));
            } else {
                row.put("changeTotalOpenInterest", null);
                row.put("changeFundLong", null);
                row.put("changeFundShort", null);
                row.put("changeFundNet", null);
                row.put("changeCommercialLong", null);
                row.put("changeCommercialShort", null);
                row.put("changeReportableTraders", null);
            }
            result.add(row);
        }
        return result;
    }

    private Long diff(Long curr, Long prev) {
        if (curr == null || prev == null) {
            return null;
        }
        return curr - prev;
    }

    private Integer diffInt(Integer curr, Integer prev) {
        if (curr == null || prev == null) {
            return null;
        }
        return curr - prev;
    }

    private static String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
