package com.goldsilverratio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 基金持仓筛选服务：输入基金代码，查询持仓股票，
 * 过滤出单季度扣非净利润同比增长率超过阈值的股票。
 * 数据来源：东方财富天天基金（持仓）+ 东方财富 datacenter 业绩报表（每股收益 BASIC_EPS）。
 */
@Service
public class FundFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(FundFilterService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private static final String FUND_HOLDINGS_URL =
            "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=%s&topline=200&year=&month=&rt=%s";
    /** 业绩报表接口，返回每股收益(元)，报告期对应累计每股收益 */
    private static final String FINANCIAL_DATA_URL =
            "https://datacenter.eastmoney.com/api/data/v1/get";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * 查询基金持仓并按每股收益同比增长率过滤。
     *
     * @param fundCode  基金代码，如 "110011"
     * @param threshold 同比增长率阈值（%），如 25
     * @return 包含 filtered / allStocks / holdingCount / filteredCount 等字段
     */
    public Map<String, Object> queryAndFilter(String fundCode, double threshold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fundCode", fundCode);
        result.put("threshold", threshold);

        List<Map<String, Object>> holdings = fetchFundHoldings(fundCode);
        if (holdings.isEmpty()) {
            result.put("error", "未找到该基金的持仓数据，请检查基金代码是否正确");
            return result;
        }
        result.put("holdingCount", holdings.size());

        List<String> stockCodes = new ArrayList<>();
        for (Map<String, Object> h : holdings) {
            String code = (String) h.get("stockCode");
            if (isAShareCode(code)) {
                stockCodes.add(code);
            }
        }
        if (stockCodes.isEmpty()) {
            result.put("error", "该基金持仓中未找到A股股票");
            return result;
        }

        Map<String, TreeMap<String, BigDecimal>> financialMap = fetchFinancialData(stockCodes);

        List<Map<String, Object>> filtered = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>();

        for (Map<String, Object> holding : holdings) {
            String code = (String) holding.get("stockCode");
            if (!isAShareCode(code)) {
                continue;
            }

            TreeMap<String, BigDecimal> cumData = financialMap.get(code);
            Map<String, Object> growth = calculateSingleQuarterYoY(cumData);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockCode", code);
            row.put("stockName", holding.get("stockName"));
            row.put("weight", holding.get("weight"));

            if (growth != null) {
                row.put("reportDate", growth.get("latestReportDate"));
                row.put("singleQuarterProfit", growth.get("singleQuarterProfit"));
                row.put("lastYearProfit", growth.get("lastYearProfit"));
                row.put("yoyGrowth", growth.get("yoyGrowth"));
                row.put("hasData", true);
            } else {
                row.put("hasData", false);
            }

            all.add(row);

            if (growth != null) {
                BigDecimal yoy = (BigDecimal) growth.get("yoyGrowth");
                BigDecimal reportEps = (BigDecimal) growth.get("singleQuarterProfit");
                boolean yoyOk = yoy != null && yoy.doubleValue() > threshold;
                boolean epsPositive = reportEps != null && reportEps.compareTo(BigDecimal.ZERO) > 0;
                if (yoyOk && epsPositive) {
                    filtered.add(row);
                }
            }
        }

        filtered.sort((a, b) -> {
            BigDecimal ga = (BigDecimal) a.get("yoyGrowth");
            BigDecimal gb = (BigDecimal) b.get("yoyGrowth");
            if (ga == null) return 1;
            if (gb == null) return -1;
            return gb.compareTo(ga);
        });

        result.put("filtered", filtered);
        result.put("filteredCount", filtered.size());
        result.put("allStocks", all);
        return result;
    }

    /**
     * CAN SLIM A - 年度每股收益：按当前时间取最近三年目标年（如 2026 年取 2025、2024、2023），
     * 仅用这些年报数据判断，不取更早年份（如不取 2022）。若最新年（如 2025）财报未出，则用两年（2024、2023）判断，两年都满足即算满足。
     * 原版：有三年时两年增长率均 > 25%，仅两年时该年增长率 > 25%。
     * 放宽：有三年时去年 > 10% 且今年 > 50%；仅两年时该年增长率 > 50%。
     *
     * @param fundCode 基金代码
     * @return 包含 matched / allStocks / matchType(原版/放宽) 等
     */
    public Map<String, Object> queryAndFilterA(String fundCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fundCode", fundCode);

        List<Map<String, Object>> holdings = fetchFundHoldings(fundCode);
        if (holdings.isEmpty()) {
            result.put("error", "未找到该基金的持仓数据，请检查基金代码是否正确");
            return result;
        }
        result.put("holdingCount", holdings.size());

        List<String> stockCodes = new ArrayList<>();
        for (Map<String, Object> h : holdings) {
            String code = (String) h.get("stockCode");
            if (isAShareCode(code)) {
                stockCodes.add(code);
            }
        }
        if (stockCodes.isEmpty()) {
            result.put("error", "该基金持仓中未找到A股股票");
            return result;
        }

        Map<String, TreeMap<String, BigDecimal>> financialMap = fetchFinancialData(stockCodes);
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>();

        for (Map<String, Object> holding : holdings) {
            String code = (String) holding.get("stockCode");
            if (!isAShareCode(code)) {
                continue;
            }

            TreeMap<String, BigDecimal> fullData = financialMap.get(code);
            Map<String, Object> annual = calculateAnnualEpsGrowth(fullData);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockCode", code);
            row.put("stockName", holding.get("stockName"));
            row.put("weight", holding.get("weight"));
            if (annual != null) {
                row.put("growthThisYear", annual.get("growthThisYear"));
                row.put("growthLastYear", annual.get("growthLastYear"));
                row.put("growthTwoYearsAgo", annual.get("growthTwoYearsAgo"));
                row.put("yearThisYear", annual.get("yearThisYear"));
                row.put("yearLastYear", annual.get("yearLastYear"));
                row.put("yearTwoYearsAgo", annual.get("yearTwoYearsAgo"));
                row.put("epsThisYear", annual.get("epsThisYear"));
                row.put("epsLastYear", annual.get("epsLastYear"));
                row.put("matchType", annual.get("matchType"));
                row.put("hasData", true);
            } else {
                row.put("hasData", false);
            }
            all.add(row);

            if (annual != null && annual.get("matchType") != null) {
                matched.add(row);
            }
        }

        matched.sort((a, b) -> {
            String ma = (String) a.get("matchType");
            String mb = (String) b.get("matchType");
            if ("原版".equals(ma) && "放宽".equals(mb)) return -1;
            if ("放宽".equals(ma) && "原版".equals(mb)) return 1;
            return 0;
        });

        result.put("matched", matched);
        result.put("matchedCount", matched.size());
        result.put("allStocks", all);
        return result;
    }

    /**
     * 仅取年报（12-31）数据，按当前日期取「最近三年」目标年（2025、2024、2023）用于展示；
     * 为计算 2023年增长率（2023 vs 2022）会再取 2022 年数据，但 2022 年每股收益不返回、不展示。
     * 原版：有三年时两年增长率均 > 25%，仅两年时该年增长率 > 25%。
     * 放宽：有三年时去年 > 10% 且今年 > 50%；仅两年时该年增长率 > 50%。
     */
    private Map<String, Object> calculateAnnualEpsGrowth(TreeMap<String, BigDecimal> fullData) {
        if (fullData == null || fullData.isEmpty()) {
            return null;
        }
        int currentYear = LocalDate.now().getYear();
        int year0 = currentYear - 1;
        int year1 = currentYear - 2;
        int year2 = currentYear - 3;
        int year3 = currentYear - 4;

        TreeMap<String, BigDecimal> yearEnd = new TreeMap<>();
        for (Map.Entry<String, BigDecimal> e : fullData.entrySet()) {
            String d = e.getKey();
            if (d == null || d.length() < 10 || !d.endsWith("12-31")) {
                continue;
            }
            int y = Integer.parseInt(d.substring(0, 4));
            if (y != year0 && y != year1 && y != year2 && y != year3) {
                continue;
            }
            yearEnd.put(d, e.getValue());
        }
        if (yearEnd.size() < 2) {
            return null;
        }
        List<String> dates = new ArrayList<>(yearEnd.descendingKeySet());
        String d0 = dates.get(0);
        String d1 = dates.get(1);
        BigDecimal eps0 = yearEnd.get(d0);
        BigDecimal eps1 = yearEnd.get(d1);
        if (eps1 == null || eps1.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal growthThisYear = eps0.subtract(eps1)
                .divide(eps1.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        Integer outYear0 = Integer.parseInt(d0.substring(0, 4));
        Integer outYear1 = Integer.parseInt(d1.substring(0, 4));
        Integer outYear2 = null;
        BigDecimal growthLastYear = null;
        BigDecimal growthTwoYearsAgo = null;
        BigDecimal eps2Val = null;

        if (dates.size() >= 3) {
            String d2 = dates.get(2);
            BigDecimal eps2 = yearEnd.get(d2);
            if (eps2 != null && eps2.compareTo(BigDecimal.ZERO) != 0) {
                growthLastYear = eps1.subtract(eps2)
                        .divide(eps2.abs(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                outYear2 = Integer.parseInt(d2.substring(0, 4));
                eps2Val = eps2.setScale(2, RoundingMode.HALF_UP);
                if (dates.size() >= 4) {
                    String d3 = dates.get(3);
                    BigDecimal eps3 = yearEnd.get(d3);
                    if (eps3 != null && eps3.compareTo(BigDecimal.ZERO) != 0) {
                        growthTwoYearsAgo = eps2.subtract(eps3)
                                .divide(eps3.abs(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        boolean strict;
        boolean relaxed;
        if (growthTwoYearsAgo != null) {
            strict = growthThisYear.doubleValue() > 25 && growthLastYear.doubleValue() > 25
                    && growthTwoYearsAgo.doubleValue() > 25;
            relaxed = growthLastYear.doubleValue() > 10 && growthThisYear.doubleValue() > 50;
        } else if (growthLastYear != null) {
            strict = growthThisYear.doubleValue() > 25 && growthLastYear.doubleValue() > 25;
            relaxed = growthLastYear.doubleValue() > 10 && growthThisYear.doubleValue() > 50;
        } else {
            strict = growthThisYear.doubleValue() > 25;
            relaxed = growthThisYear.doubleValue() > 50;
        }

        String matchType = null;
        if (strict) {
            matchType = "原版";
        } else if (relaxed) {
            matchType = "放宽";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("growthThisYear", growthThisYear);
        out.put("growthLastYear", growthLastYear);
        out.put("growthTwoYearsAgo", growthTwoYearsAgo);
        out.put("yearThisYear", outYear0);
        out.put("yearLastYear", outYear1);
        out.put("yearTwoYearsAgo", outYear2);
        out.put("epsThisYear", eps0.setScale(2, RoundingMode.HALF_UP));
        out.put("epsLastYear", eps1.setScale(2, RoundingMode.HALF_UP));
        out.put("epsTwoYearsAgo", eps2Val);
        out.put("matchType", matchType);
        return out;
    }

    /* ===================== HTTP 请求（绕过东方财富 SSL 主机名校验） ===================== */

    /**
     * 使用放宽的 SSL 校验发起 HTTP 请求，解决 fundf10.eastmoney.com 等证书主机名不匹配问题。
     * Jsoup 1.12+ 已移除 validateTLSCertificates，故临时设置 HttpsURLConnection 默认值。
     */
    private String fetchWithRelaxedSsl(String url, String referer, int timeoutMs) {
        SSLSocketFactory origSf = null;
        HostnameVerifier origHv = null;
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());

            origSf = HttpsURLConnection.getDefaultSSLSocketFactory();
            origHv = HttpsURLConnection.getDefaultHostnameVerifier();
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            return Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Referer", referer)
                    .timeout(timeoutMs)
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        } finally {
            if (origSf != null) {
                HttpsURLConnection.setDefaultSSLSocketFactory(origSf);
            }
            if (origHv != null) {
                HttpsURLConnection.setDefaultHostnameVerifier(origHv);
            }
        }
    }

    /* ===================== 基金持仓 ===================== */

    private List<Map<String, Object>> fetchFundHoldings(String fundCode) {
        List<Map<String, Object>> holdings = new ArrayList<>();
        try {
            String url = String.format(FUND_HOLDINGS_URL, fundCode, Math.random());
            String body = fetchWithRelaxedSsl(url, "https://fundf10.eastmoney.com/", 15000);

            String html = extractContentHtml(body);
            if (html == null || html.isEmpty()) {
                LOG.warn("基金持仓HTML为空: {}", fundCode);
                return holdings;
            }

            Document doc = Jsoup.parse(html);
            Elements tables = doc.select("table");
            if (tables.isEmpty()) {
                return holdings;
            }

            Element firstTable = tables.first();
            Elements rows = firstTable.select("tbody tr");
            for (Element row : rows) {
                Elements tds = row.select("td");
                if (tds.size() < 4) {
                    continue;
                }
                String stockCode = tds.get(1).text().trim();
                String stockName = tds.get(2).text().trim();
                int weightIdx = tds.size() >= 9 ? 6 : 4;
                String weightStr = (weightIdx < tds.size() ? tds.get(weightIdx) : tds.get(3))
                        .text().trim().replace("%", "");

                if (stockCode.isEmpty() || stockName.isEmpty()) {
                    continue;
                }

                Map<String, Object> h = new LinkedHashMap<>();
                h.put("stockCode", stockCode);
                h.put("stockName", stockName);
                h.put("weight", parseDouble(weightStr));
                holdings.add(h);
            }
        } catch (Exception e) {
            LOG.error("获取基金持仓失败 {}: {}", fundCode, e.getMessage());
        }
        return holdings;
    }

    /**
     * 从 "var apidata={ content:\"...\", aryear:[...], curyear:... };" 等格式中提取 HTML。
     * 东方财富可能返回 content:"...",arryear 或 content:"...",binddata 等。
     */
    private String extractContentHtml(String body) {
        if (body == null) {
            return null;
        }
        int contentIdx = body.indexOf("content:\"");
        if (contentIdx < 0) {
            contentIdx = body.indexOf("content: \"");
        }
        if (contentIdx < 0) {
            return null;
        }
        int startIdx = body.indexOf("\"", contentIdx + 8) + 1;

        int endIdx = -1;
        String[] endPatterns = {"\",arryear", "\",binddata", "\",curyear", "\",aression", "\",cuession"};
        for (String pattern : endPatterns) {
            int pos = body.indexOf(pattern, startIdx);
            if (pos > 0 && (endIdx < 0 || pos < endIdx)) {
                endIdx = pos;
            }
        }
        if (endIdx < 0) {
            return null;
        }
        return body.substring(startIdx, endIdx)
                .replace("\\/", "/")
                .replace("\\\"", "\"");
    }

    /* ===================== 财务数据 ===================== */

    /**
     * 批量查询股票的报告期每股收益（BASIC_EPS）。
     *
     * @return Map: stockCode -> TreeMap(reportDate -> basicEps)
     */
    private Map<String, TreeMap<String, BigDecimal>> fetchFinancialData(List<String> stockCodes) {
        Map<String, TreeMap<String, BigDecimal>> result = new LinkedHashMap<>();
        int batchSize = 30;
        for (int i = 0; i < stockCodes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, stockCodes.size());
            List<String> batch = stockCodes.subList(i, end);
            fetchFinancialBatch(batch, result);
        }
        return result;
    }

    private void fetchFinancialBatch(List<String> codes,
                                     Map<String, TreeMap<String, BigDecimal>> result) {
        try {
            StringBuilder filter = new StringBuilder("(SECURITY_CODE in (");
            for (int i = 0; i < codes.size(); i++) {
                if (i > 0) {
                    filter.append(",");
                }
                filter.append("\"").append(codes.get(i)).append("\"");
            }
            filter.append("))");

            String url = FINANCIAL_DATA_URL
                    + "?sortColumns=REPORTDATE&sortTypes=-1"
                    + "&pageSize=2000&pageNumber=1"
                    + "&reportName=RPT_LICO_FN_CPD"
                    + "&columns=SECURITY_CODE,SECURITY_NAME_ABBR,REPORTDATE,BASIC_EPS"
                    + "&filter=" + URLEncoder.encode(filter.toString(), "UTF-8")
                    + "&source=WEB&client=WEB";

            String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 30000);

            JsonNode root = OM.readTree(json);
            JsonNode dataArr = root.path("result").path("data");
            if (dataArr.isMissingNode() || !dataArr.isArray()) {
                LOG.warn("财务数据API无结果: {}", root.path("message").asText());
                return;
            }

            for (int i = 0; i < dataArr.size(); i++) {
                JsonNode item = dataArr.get(i);
                String code = item.path("SECURITY_CODE").asText("");
                String dateStr = item.path("REPORTDATE").asText("");
                if (code.isEmpty() || dateStr.isEmpty()) {
                    continue;
                }
                if (item.path("BASIC_EPS").isNull()) {
                    continue;
                }
                double eps = item.path("BASIC_EPS").asDouble(Double.NaN);
                if (Double.isNaN(eps)) {
                    continue;
                }
                String reportDate = dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr;
                result.computeIfAbsent(code, k -> new TreeMap<>())
                        .put(reportDate, BigDecimal.valueOf(eps));
            }
        } catch (Exception e) {
            LOG.error("获取财务数据失败: {}", e.getMessage());
        }
    }

    /* ===================== 每股收益同比计算 ===================== */

    /**
     * 按报告期同比：最新报告期（如 2025-09-30）的值 vs 去年同期报告期（2024-09-30）的值。
     * 同比增长率 = (本期 - 去年同期) / 去年同期 * 100%。
     * 适用于每股收益等报告期指标。
     */
    private Map<String, Object> calculateSingleQuarterYoY(TreeMap<String, BigDecimal> cumulativeData) {
        if (cumulativeData == null || cumulativeData.size() < 2) {
            return null;
        }

        String latestDate = cumulativeData.lastKey();
        BigDecimal latestValue = cumulativeData.get(latestDate);

        int latestYear = Integer.parseInt(latestDate.substring(0, 4));
        String lastYearDate = (latestYear - 1) + latestDate.substring(4);
        BigDecimal lastYearValue = cumulativeData.get(lastYearDate);

        if (lastYearValue == null || lastYearValue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal yoy = latestValue.subtract(lastYearValue)
                .divide(lastYearValue.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("latestReportDate", latestDate);
        out.put("singleQuarterProfit", latestValue.setScale(2, RoundingMode.HALF_UP));
        out.put("lastYearProfit", lastYearValue.setScale(2, RoundingMode.HALF_UP));
        out.put("yoyGrowth", yoy);
        return out;
    }

    /* ===================== 工具方法 ===================== */

    private boolean isAShareCode(String code) {
        if (code == null || code.length() != 6) {
            return false;
        }
        char first = code.charAt(0);
        return first == '0' || first == '3' || first == '6';
    }

    private double parseDouble(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
