package com.canLite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从上期所官网抓取每日交易数据（品种汇总行：成交量、持仓量、日增仓）。
 * 数据源（新版）：https://www.shfe.com.cn/data/tradedata/future/dailydata/kx{yyyyMMdd}.dat
 * 备用（旧版）：http://tsite.shfe.com.cn/data/dailydata/kx/kx{yyyyMMdd}.dat
 */
@Component
public class ShfeTradeDailyFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(ShfeTradeDailyFetcher.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String[] DATA_URLS = {
        "https://www.shfe.com.cn/data/tradedata/future/dailydata/kx%s.dat",
        "http://tsite.shfe.com.cn/data/dailydata/kx/kx%s.dat"
    };

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

    /**
     * 获取指定日期的所有品种汇总数据。
     *
     * @param date 日期
     * @return [{ productId, productName, volume, openInterest, oiChange }]，无数据返回空列表
     */
    public List<Map<String, Object>> fetchByDate(LocalDate date) {
        String dateStr = date.format(YYYYMMDD);
        for (String urlTpl : DATA_URLS) {
            String url = String.format(urlTpl, dateStr);
            String json = fetchJson(url);
            if (json != null) {
                List<Map<String, Object>> result = parseTradeData(json);
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 获取指定日期上期所黄金(au)、白银(ag)主力合约结算价，用于计算金银比。
     * 黄金报价单位 元/克，白银 元/千克；返回 map 中 au 为 元/克，ag 已换算为 元/克。
     *
     * @param date 日期
     * @return map 含 "au"(金价元/克)、"ag"(银价元/克)，缺数据时对应 key 不存在或为 null
     */
    public Map<String, BigDecimal> fetchAuAgSettlementPrices(LocalDate date) {
        Map<String, BigDecimal> out = new HashMap<>(2);
        String dateStr = date.format(YYYYMMDD);
        for (String urlTpl : DATA_URLS) {
            String url = String.format(urlTpl, dateStr);
            String json = fetchJson(url);
            if (json == null) {
                continue;
            }
            BigDecimal auPrice = parseSettlementPriceByProduct(json, "au_f");
            BigDecimal agPriceKg = parseSettlementPriceByProduct(json, "ag_f");
            if (auPrice != null) {
                out.put("au", auPrice);
            }
            if (agPriceKg != null) {
                out.put("ag", agPriceKg.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    /**
     * 从 o_curinstrument 中取指定品种（如 au_f、ag_f）持仓量最大合约的结算价。
     * au 报价单位 元/克，ag 报价单位 元/千克。
     */
    private BigDecimal parseSettlementPriceByProduct(String json, String productId) {
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode dataArr = om.readTree(json).path("o_curinstrument");
            if (dataArr.isMissingNode() || !dataArr.isArray()) {
                return null;
            }
            long maxOi = 0;
            BigDecimal settlement = null;
            for (int i = 0; i < dataArr.size(); i++) {
                JsonNode item = dataArr.get(i);
                if (!productId.equals(item.path("PRODUCTID").asText("").trim())) {
                    continue;
                }
                String deliveryMonth = item.path("DELIVERYMONTH").asText("").trim();
                if (deliveryMonth.isEmpty() || "小计".equals(deliveryMonth)) {
                    continue;
                }
                long oi = parseLong(item.path("OPENINTEREST").asText("0"));
                if (oi <= 0) {
                    continue;
                }
                String sp = item.path("SETTLEMENTPRICE").asText("").trim();
                if (sp.isEmpty()) {
                    continue;
                }
                BigDecimal price = parseBigDecimal(sp);
                if (price == null) {
                    continue;
                }
                if (oi > maxOi) {
                    maxOi = oi;
                    settlement = price;
                }
            }
            return settlement;
        } catch (Exception e) {
            LOG.warn("解析上期所 au/ag 结算价失败: {}", e.getMessage());
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String s) {
        if (s == null) {
            return null;
        }
        s = s.replace(",", "").replace("，", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fetchJson(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            LOG.warn("获取上期所交易数据失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> parseTradeData(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            JsonNode dataArr = root.path("o_curinstrument");
            if (dataArr.isMissingNode() || !dataArr.isArray()) {
                LOG.warn("上期所数据格式异常：缺少 o_curinstrument");
                return result;
            }

            for (int i = 0; i < dataArr.size(); i++) {
                JsonNode item = dataArr.get(i);
                String productIdRaw = item.path("PRODUCTID").asText("").trim();
                String deliveryMonth = item.path("DELIVERYMONTH").asText("").trim();

                if (!productIdRaw.endsWith("_f")) {
                    continue;
                }
                if (!deliveryMonth.isEmpty() && !"小计".equals(deliveryMonth)) {
                    continue;
                }

                String productId = productIdRaw.replace("_f", "").trim();
                String productName = PRODUCT_NAMES.getOrDefault(productId, productId);

                long volume = parseLong(item.path("VOLUME").asText("0"));
                long openInterest = parseLong(item.path("OPENINTEREST").asText("0"));
                long oiChange = parseLong(item.path("OPENINTERESTCHG").asText("0"));

                if (volume == 0 && openInterest == 0) {
                    continue;
                }

                Map<String, Object> row = new HashMap<>(5);
                row.put("productId", productId);
                row.put("productName", productName);
                row.put("volume", volume);
                row.put("openInterest", openInterest);
                row.put("oiChange", oiChange);
                result.add(row);
            }
        } catch (Exception e) {
            LOG.warn("解析上期所交易数据失败: {}", e.getMessage());
        }
        return result;
    }

    private static long parseLong(String s) {
        if (s == null) {
            return 0;
        }
        s = s.replace(",", "").replace("，", "").trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }
}
