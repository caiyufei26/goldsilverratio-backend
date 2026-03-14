package com.canLite.service.impl;

import com.canLite.entity.ShfeTradeDaily;
import com.canLite.mapper.ShfeTradeDailyMapper;
import com.canLite.service.ShfeTradeDailyApiService;
import com.canLite.service.ShfeTradeDailyFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上期所每日交易数据 API 业务实现。
 */
@Service
public class ShfeTradeDailyApiServiceImpl implements ShfeTradeDailyApiService {

    private static final Logger LOG = LoggerFactory.getLogger(ShfeTradeDailyApiServiceImpl.class);
    private static final DateTimeFormatter RECORD_DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> PRODUCT_NAMES = new HashMap<>();

    static {
        PRODUCT_NAMES.put("cu", "铜");
        PRODUCT_NAMES.put("al", "铝");
        PRODUCT_NAMES.put("zn", "锌");
        PRODUCT_NAMES.put("pb", "铅");
        PRODUCT_NAMES.put("ni", "镍");
        PRODUCT_NAMES.put("sn", "锡");
        PRODUCT_NAMES.put("au", "黄金");
        PRODUCT_NAMES.put("ag", "白银");
        PRODUCT_NAMES.put("rb", "螺纹钢");
        PRODUCT_NAMES.put("wr", "线材");
        PRODUCT_NAMES.put("hc", "热轧卷板");
        PRODUCT_NAMES.put("ss", "不锈钢");
        PRODUCT_NAMES.put("sc", "原油");
        PRODUCT_NAMES.put("fu", "燃料油");
        PRODUCT_NAMES.put("bu", "石油沥青");
        PRODUCT_NAMES.put("ru", "天然橡胶");
        PRODUCT_NAMES.put("nr", "20号胶");
        PRODUCT_NAMES.put("sp", "纸浆");
        PRODUCT_NAMES.put("lu", "低硫燃料油");
        PRODUCT_NAMES.put("bc", "国际铜");
        PRODUCT_NAMES.put("br", "丁二烯橡胶");
        PRODUCT_NAMES.put("ao", "氧化铝");
        PRODUCT_NAMES.put("ec", "集运指数");
        PRODUCT_NAMES.put("ad", "铸造铝合金");
        PRODUCT_NAMES.put("op", "胶版印刷纸");
    }

    private final ShfeTradeDailyMapper shfeTradeDailyMapper;
    private final ShfeTradeDailyFetcher shfeTradeDailyFetcher;

    public ShfeTradeDailyApiServiceImpl(ShfeTradeDailyMapper shfeTradeDailyMapper,
                                         ShfeTradeDailyFetcher shfeTradeDailyFetcher) {
        this.shfeTradeDailyMapper = shfeTradeDailyMapper;
        this.shfeTradeDailyFetcher = shfeTradeDailyFetcher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String fetchMonthFromShfe(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate today = LocalDate.now();
        int lastDay = ym.lengthOfMonth();
        int savedDays = 0;
        int totalRecords = 0;

        for (int day = 1; day <= lastDay; day++) {
            LocalDate d = ym.atDay(day);
            if (d.isAfter(today)) {
                break;
            }
            if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                continue;
            }
            List<Map<String, Object>> dayData = shfeTradeDailyFetcher.fetchByDate(d);
            if (dayData.isEmpty()) {
                continue;
            }
            shfeTradeDailyMapper.deleteByDate(d);
            for (Map<String, Object> row : dayData) {
                ShfeTradeDaily record = new ShfeTradeDaily();
                record.setRecordDate(d);
                record.setProductId((String) row.get("productId"));
                record.setProductName((String) row.get("productName"));
                record.setVolume((Long) row.get("volume"));
                record.setOpenInterest((Long) row.get("openInterest"));
                record.setOiChange((Long) row.get("oiChange"));
                shfeTradeDailyMapper.insert(record);
                totalRecords++;
            }
            savedDays++;
            LOG.info("已保存 {} 交易数据，{} 个品种", d, dayData.size());
        }

        if (savedDays == 0) {
            return "该月无有效交易数据";
        }
        return "已获取 " + savedDays + " 个交易日，共 " + totalRecords + " 条记录";
    }

    @Override
    public List<Map<String, Object>> listByMonth(int year, int month, String productId) {
        List<ShfeTradeDaily> list;
        if (productId != null && !productId.trim().isEmpty()) {
            list = shfeTradeDailyMapper.selectByMonthAndProduct(year, month, productId.trim());
        } else {
            list = shfeTradeDailyMapper.selectByMonth(year, month);
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (ShfeTradeDaily r : list) {
            Map<String, Object> row = new HashMap<>(6);
            row.put("recordDate",
                    r.getRecordDate() == null ? "" : r.getRecordDate().format(RECORD_DATE_DISPLAY));
            row.put("productId", r.getProductId());
            row.put("productName", r.getProductName());
            row.put("volume", r.getVolume());
            row.put("openInterest", r.getOpenInterest());
            row.put("oiChange", r.getOiChange());
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listProducts() {
        List<String> productIds = shfeTradeDailyMapper.selectDistinctProducts();
        List<Map<String, Object>> result = new ArrayList<>(productIds.size());
        for (String pid : productIds) {
            Map<String, Object> row = new HashMap<>(2);
            row.put("productId", pid);
            row.put("productName", PRODUCT_NAMES.getOrDefault(pid, pid));
            result.add(row);
        }
        return result;
    }
}
