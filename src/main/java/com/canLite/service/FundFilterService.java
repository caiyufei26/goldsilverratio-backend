package com.canLite.service;

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
import java.util.*;

/**
 * 基金持仓筛选服务：输入基金代码，查询持仓股票，
 * 过滤出单季度扣非净利润同比增长率超过阈值的股票。
 * 数据来源：东方财富天天基金（持仓）+ 东方财富 datacenter 业绩报表（每股收益 BASIC_EPS）。
 */
@Service
public class FundFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(FundFilterService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    /** 基金持仓接口：topline 为请求返回的持仓条数，尽量调大以获取全部持仓（东方财富页面通常展示全部需 topline 足够大）。 */
    private static final String FUND_HOLDINGS_URL =
            "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=%s&topline=1000&year=&month=&rt=%s";
    /** 业绩报表接口，返回每股收益(元)，报告期对应累计每股收益 */
    private static final String FINANCIAL_DATA_URL =
            "https://datacenter.eastmoney.com/api/data/v1/get";
    /** 业绩预告接口，PREDICT_FINANCE_CODE=004 为归属于上市公司股东的净利润，ADD_AMP_LOWER/UPPER 为同比增幅 */
    private static final String PROFIT_FORECAST_REPORT = "RPT_PUBLIC_OP_NEWPREDICT";
    /** 大股东减持计划接口是否请求东方财富（当前报表 RPT_PUBLIC_OP_REDUCE 不存在会返回 9501，设为 false 则使用公告接口作为数据源）。 */
    private static final boolean REDUCE_PLAN_API_ENABLED = false;
    private static final String REDUCE_PLAN_REPORT = "RPT_PUBLIC_OP_REDUCE";
    /** 东方财富个股公告接口，用于获取减持相关公告（标题或分类含“减持”） */
    private static final String ANNOTICE_API_URL =
            "https://np-anotice-stock.eastmoney.com/api/security/ann";
    /** 东方财富日 K 线接口，用于计算 60/125/250 日涨跌幅与 RPS。secid=市场.代码，klt=101 日 K，fqt=1 前复权，lmt 条数 */
    private static final String KLINE_URL =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=1&end=20500000&lmt=%d";
    /** 格隆汇 RPS/涨跌幅接口：market=sh|sz，code=6位代码，tradingDayCount=60|125|250。返回 result 为 0~1 的 RPS 或涨跌幅。 */
    private static final String GELONGHUI_RPS_URL =
            "https://hybrid.gelonghui.com/check/rps/stock-net-change?market=%s&code=%s&tradingDayCount=%d";
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

        Map<String, TreeMap<String, BigDecimal>> financialMap = new LinkedHashMap<>();
        Map<String, TreeMap<String, BigDecimal>> profitMap = new LinkedHashMap<>();
        fetchFinancialData(stockCodes, financialMap, profitMap, null);

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
     * CAN SLIM A - 年度每股收益：固定展示 n年、n-1年、n-2年（如 2026、2025、2024）。
     * n年：有年报展示年报，无年报展示预增数据，都没有展示 --。n-1年同理。n-2年仅展示年报。
     * 原版：n、n-1、n-2 均有年报且三年每股收益增长率均 &gt; 25%。
     * 放宽：n 无任何数据，n-1、n-2 有年报且两年增长率均 &gt; 25%。
     * 业绩预增：预增(净利润同比)&gt;80%、n-1年增长率&gt;0%、n年营收同比&gt;20%；可展示预告明细。
     *
     * @param fundCode 基金代码
     * @return 包含 matched / allStocks / matchType / forecastDetail(业绩预增时) 等
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

        int n = LocalDate.now().getYear() - 1;
        int n1 = n - 1;
        int n2 = n - 2;
        int n3 = n - 3;

        Map<String, TreeMap<String, BigDecimal>> financialMap = new LinkedHashMap<>();
        Map<String, TreeMap<String, BigDecimal>> profitMap = new LinkedHashMap<>();
        Map<String, TreeMap<String, BigDecimal>> revenueMap = new LinkedHashMap<>();
        fetchFinancialData(stockCodes, financialMap, profitMap, revenueMap);
        Map<String, Double> revenueYoyFromReport = computeRevenueYoyFromLatestQuarter(revenueMap);

        Map<String, Map<String, Object>> forecastN = fetchProfitForecastDetail(stockCodes, n);
        Map<String, Map<String, Object>> forecastN1 = fetchProfitForecastDetail(stockCodes, n1);
        Map<String, Map<String, Object>> deductN = fetchDeductForecastDetail(stockCodes, n);
        for (String code : deductN.keySet()) {
            forecastN.putIfAbsent(code, new LinkedHashMap<>());
            forecastN.get(code).putAll(deductN.get(code));
        }

        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>();

        for (Map<String, Object> holding : holdings) {
            String code = (String) holding.get("stockCode");
            if (!isAShareCode(code)) {
                continue;
            }
            Map<String, Object> row = buildAnnualRow(code, holding.get("stockName"), holding.get("weight"),
                    financialMap.get(code), profitMap.get(code), forecastN.get(code), forecastN1.get(code), n, n1, n2, n3,
                    revenueYoyFromReport != null ? revenueYoyFromReport.get(code) : null);
            all.add(row);
            if (row.get("matchType") != null) {
                matched.add(row);
            }
        }

        matched.sort((a, b) -> {
            String ma = (String) a.get("matchType");
            String mb = (String) b.get("matchType");
            int oa = "原版".equals(ma) ? 0 : "高速增长".equals(ma) ? 1 : "放宽".equals(ma) ? 2 : "业绩预增".equals(ma) ? 3 : 4;
            int ob = "原版".equals(mb) ? 0 : "高速增长".equals(mb) ? 1 : "放宽".equals(mb) ? 2 : "业绩预增".equals(mb) ? 3 : 4;
            return Integer.compare(oa, ob);
        });

        result.put("matched", matched);
        result.put("matchedCount", matched.size());
        result.put("allStocks", all);
        result.put("yearN", n);
        result.put("yearN1", n1);
        result.put("yearN2", n2);
        // 全部A股持仓表：若没有任何股票有当年(n)年报或业绩预报，则展示 n-1、n-2、n-3 年（如 2025、2024、2023）
        boolean anyStockHasYearNData = all.stream()
                .anyMatch(r -> !"none".equals(r.get("sourceN")));
        int allTableYearN = anyStockHasYearNData ? n : n1;
        int allTableYearN1 = anyStockHasYearNData ? n1 : n2;
        int allTableYearN2 = anyStockHasYearNData ? n2 : n3;
        result.put("allTableYearN", allTableYearN);
        result.put("allTableYearN1", allTableYearN1);
        result.put("allTableYearN2", allTableYearN2);
        return result;
    }

    /**
     * I - 机构认同度：查询基金持仓中正处于大股东减持计划执行期间的股票，独立列表展示。
     *
     * @param fundCode 基金代码
     * @return 包含 fundCode / holdingCount / inReductionPlanCount / list（处于减持计划执行期的持仓及计划摘要）
     */
    public Map<String, Object> queryAndFilterI(String fundCode) {
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

        Map<String, Object> reductionResult = fetchStocksInReductionPlanResult(stockCodes);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> reductionPlanMap = (Map<String, Map<String, Object>>) reductionResult.get("planMap");
        if (reductionPlanMap == null) {
            reductionPlanMap = new LinkedHashMap<>();
        }
        if (Boolean.FALSE.equals(reductionResult.get("sourceAvailable"))) {
            result.put("reductionPlanSourceUnavailable", true);
        }

        /* 仅保留正在减持（reductionType=CURRENT）的持仓，与“股票代码查询减持信息”同结构，含 reductionDetails 供详情 */
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, String> reductionTypeMap = new LinkedHashMap<>();
        for (Map<String, Object> holding : holdings) {
            String code = (String) holding.get("stockCode");
            if (!isAShareCode(code)) {
                continue;
            }
            Map<String, Object> plan = reductionPlanMap.get(code);
            if (plan != null && plan.get("reductionType") != null) {
                reductionTypeMap.put(code, plan.get("reductionType").toString());
            }
            if (plan == null || !"CURRENT".equals(plan.get("reductionType"))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockCode", code);
            row.put("stockName", holding.get("stockName"));
            row.put("weight", holding.get("weight"));
            row.put("noticeDate", plan.get("noticeDate"));
            row.put("planStartDate", plan.get("planStartDate"));
            row.put("planEndDate", plan.get("planEndDate"));
            row.put("planBrief", plan.get("planBrief"));
            row.put("reductionType", plan.get("reductionType"));
            row.put("futureReduction", plan.get("futureReduction"));
            if (plan.get("reductionDetails") != null) {
                row.put("reductionDetails", plan.get("reductionDetails"));
            }
            list.add(row);
        }

        result.put("inReductionPlanCount", list.size());
        result.put("list", list);
        result.put("reductionTypeMap", reductionTypeMap);
        return result;
    }

    /**
     * S - 回购：查询基金持仓中发生过回购且今日在回购期间内的股票，独立列表展示。
     *
     * @param fundCode 基金代码
     * @return 包含 fundCode / holdingCount / inRepurchasePeriodCount / list（今日在回购期间内的持仓）、repurchaseTypeMap
     */
    public Map<String, Object> queryAndFilterS(String fundCode) {
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

        Map<String, Object> repurchaseResult = fetchStocksInRepurchasePlanResult(stockCodes);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> repurchasePlanMap = (Map<String, Map<String, Object>>) repurchaseResult.get("planMap");
        if (repurchasePlanMap == null) {
            repurchasePlanMap = new LinkedHashMap<>();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, String> repurchaseTypeMap = new LinkedHashMap<>();
        for (Map<String, Object> holding : holdings) {
            String code = (String) holding.get("stockCode");
            if (!isAShareCode(code)) {
                continue;
            }
            Map<String, Object> plan = repurchasePlanMap.get(code);
            if (plan != null && plan.get("repurchaseType") != null) {
                repurchaseTypeMap.put(code, plan.get("repurchaseType").toString());
            }
            if (plan == null || !"CURRENT".equals(plan.get("repurchaseType"))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockCode", code);
            row.put("stockName", holding.get("stockName"));
            row.put("weight", holding.get("weight"));
            row.put("noticeDate", plan.get("noticeDate"));
            row.put("planStartDate", plan.get("planStartDate"));
            row.put("planEndDate", plan.get("planEndDate"));
            row.put("planBrief", plan.get("planBrief"));
            row.put("repurchaseType", plan.get("repurchaseType"));
            if (plan.get("repurchaseDetails") != null) {
                row.put("repurchaseDetails", plan.get("repurchaseDetails"));
            }
            list.add(row);
        }

        result.put("inRepurchasePeriodCount", list.size());
        result.put("list", list);
        result.put("repurchaseTypeMap", repurchaseTypeMap);
        return result;
    }

    /**
     * 查询单只股票减持相关状态：今日是否在减持执行期、未来是否将减持及类型。
     *
     * @param stockCode 股票代码，如 300059
     * @return inReductionPlan、reductionType(当前在减持执行期/未来将减持/已过减持期)、futureReduction、noticeDate 等
     */
    public Map<String, Object> checkStockReduction(String stockCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockCode", stockCode);
        result.put("inReductionPlan", false);
        result.put("futureReduction", false);
        if (stockCode == null || stockCode.trim().isEmpty()) {
            return result;
        }
        stockCode = stockCode.trim();
        if (!isAShareCode(stockCode)) {
            result.put("error", "仅支持A股股票代码");
            return result;
        }
        List<String> single = new ArrayList<>();
        single.add(stockCode);
        Map<String, Object> reductionResult = fetchStocksInReductionPlanResult(single);
        if (Boolean.FALSE.equals(reductionResult.get("sourceAvailable"))) {
            result.put("reductionPlanSourceUnavailable", true);
        }
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> planMap = (Map<String, Map<String, Object>>) reductionResult.get("planMap");
        Map<String, Object> plan = (planMap != null) ? planMap.get(stockCode) : null;
        if (plan != null) {
            result.put("inReductionPlan", true);
            result.put("noticeDate", plan.get("noticeDate"));
            result.put("planStartDate", plan.get("planStartDate"));
            result.put("planEndDate", plan.get("planEndDate"));
            result.put("planBrief", plan.get("planBrief"));
            result.put("reductionType", plan.get("reductionType"));
            result.put("futureReduction", Boolean.TRUE.equals(plan.get("futureReduction")));
            if (plan.get("reductionDetails") != null) {
                result.put("reductionDetails", plan.get("reductionDetails"));
            }
        }
        return result;
    }

    /**
     * 查询单只股票的 RPS60、RPS125、RPS250（个股相对涨幅强度）。
     * RPS 来自格隆汇接口；区间涨跌幅由东方财富 K 线计算；股票名称来自东方财富行情。
     *
     * @param stockCode 股票代码，如 002028
     * @return stockCode、stockName、return60/125/250（区间涨跌幅%）、rps60/125/250（0~100，格隆汇）
     */
    public Map<String, Object> getStockRps(String stockCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockCode", stockCode);
        result.put("stockName", null);
        result.put("return60", null);
        result.put("return125", null);
        result.put("return250", null);
        result.put("rps60", null);
        result.put("rps125", null);
        result.put("rps250", null);
        if (stockCode == null || stockCode.trim().isEmpty()) {
            result.put("error", "股票代码不能为空");
            return result;
        }
        stockCode = stockCode.trim();
        if (!isAShareCode(stockCode)) {
            result.put("error", "仅支持A股股票代码");
            return result;
        }
        String codeSix = normalizeStockCode(stockCode);
        String market = codeSix.startsWith("6") ? "sh" : "sz";
        for (int days : new int[]{60, 125, 250}) {
            Double rps = fetchGelonghuiRps(market, codeSix, days);
            if (rps != null) {
                if (days == 60) {
                    result.put("rps60", rps);
                } else if (days == 125) {
                    result.put("rps125", rps);
                } else {
                    result.put("rps250", rps);
                }
            }
        }
        String secid = toSecid(codeSix);
        List<double[]> kline = fetchStockKline(secid, 300);
        if (kline != null && kline.size() >= 2) {
            int n = kline.size();
            boolean newestFirst = kline.get(0)[0] > kline.get(n - 1)[0];
            double closeLatest = newestFirst ? kline.get(0)[1] : kline.get(n - 1)[1];
            if (n > 60) {
                double close60 = newestFirst ? kline.get(60)[1] : kline.get(n - 1 - 60)[1];
                if (close60 > 0) {
                    result.put("return60", (closeLatest - close60) / close60 * 100);
                }
            }
            if (n > 125) {
                double close125 = newestFirst ? kline.get(125)[1] : kline.get(n - 1 - 125)[1];
                if (close125 > 0) {
                    result.put("return125", (closeLatest - close125) / close125 * 100);
                }
            }
            if (n > 250) {
                double close250 = newestFirst ? kline.get(250)[1] : kline.get(n - 1 - 250)[1];
                if (close250 > 0) {
                    result.put("return250", (closeLatest - close250) / close250 * 100);
                }
            }
        }
        String stockName = parseStockNameFromKline(secid);
        if (stockName != null) {
            result.put("stockName", stockName);
        }
        if (result.get("rps60") == null && result.get("rps125") == null && result.get("rps250") == null) {
            result.put("error", "无法获取 RPS 数据，请检查股票代码或稍后重试");
        }
        return result;
    }

    /** 批量 RPS 最多返回只数，避免单次请求过久。 */
    private static final int RPS_BATCH_MAX = 20;

    /**
     * 批量查询多只股票的 RPS60、RPS125、RPS250。用于基金持仓列表展示。
     *
     * @param stockCodes 股票代码列表，仅 A 股；超过 {@link #RPS_BATCH_MAX} 只取前若干只
     * @return 与入参顺序一致的列表，每项含 stockCode、stockName、rps60、rps125、rps250
     */
    public List<Map<String, Object>> getStockRpsBatch(List<String> stockCodes) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (stockCodes == null || stockCodes.isEmpty()) {
            return out;
        }
        List<String> list = new ArrayList<>();
        for (String code : stockCodes) {
            if (code != null && isAShareCode(code.trim())) {
                list.add(code.trim());
            }
            if (list.size() >= RPS_BATCH_MAX) {
                break;
            }
        }
        for (String code : list) {
            Map<String, Object> one = getStockRps(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockCode", one.get("stockCode"));
            row.put("stockName", one.get("stockName"));
            row.put("rps60", one.get("rps60"));
            row.put("rps125", one.get("rps125"));
            row.put("rps250", one.get("rps250"));
            out.add(row);
        }
        return out;
    }

    /**
     * 调用格隆汇接口获取指定交易日的 RPS（或涨跌幅）。返回 0~100 的百分比。
     *
     * @param market  sh 或 sz
     * @param code    6 位股票代码
     * @param tradingDayCount 60、125 或 250
     * @return RPS 0~100，失败返回 null
     */
    private Double fetchGelonghuiRps(String market, String code, int tradingDayCount) {
        String url = String.format(GELONGHUI_RPS_URL, market, code, tradingDayCount);
        try {
            String json = fetchWithRelaxedSsl(url, "https://hybrid.gelonghui.com/", 10000);
            JsonNode root = OM.readTree(json);
            if (root.path("statusCode").asInt(0) != 200) {
                return null;
            }
            JsonNode resultNode = root.path("result");
            if (resultNode.isMissingNode() || resultNode.isNull()) {
                return null;
            }
            double v = resultNode.asDouble(Double.NaN);
            if (Double.isNaN(v)) {
                return null;
            }
            if (v <= 1 && v >= 0) {
                return v * 100;
            }
            return v;
        } catch (Exception e) {
            LOG.debug("格隆汇 RPS {} 天请求失败 {}: {}", tradingDayCount, code, e.getMessage());
            return null;
        }
    }

    /**
     * 统一为 6 位股票代码（不足左补零），便于与 clist 返回的 f12 匹配。
     */
    private String normalizeStockCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        String s = code.trim();
        try {
            int num = Integer.parseInt(s);
            return String.format("%06d", num);
        } catch (NumberFormatException e) {
            if (s.length() >= 6) {
                return s.substring(0, 6);
            }
            return String.format("%6s", s).replace(' ', '0');
        }
    }

    /**
     * 将 6 位 A 股代码转为东方财富 secid。6 开头沪市 1.xxxxxx，其余深市 0.xxxxxx。
     */
    private String toSecid(String code) {
        if (code == null || code.length() != 6) {
            return "0." + (code != null ? code : "");
        }
        return code.startsWith("6") ? "1." + code : "0." + code;
    }

    /**
     * 拉取个股日 K 线（前复权）。返回按日期升序的 [日期数值, 收盘价] 列表，日期为 yyyyMMdd 整数。
     */
    private List<double[]> fetchStockKline(String secid, int lmt) {
        String url = String.format(KLINE_URL, secid, lmt);
        try {
            String json = fetchWithRelaxedSsl(url, "https://quote.eastmoney.com/", 15000);
            JsonNode root = OM.readTree(json);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            JsonNode klines = data.path("klines");
            if (!klines.isArray() || klines.size() == 0) {
                return null;
            }
            List<double[]> out = new ArrayList<>(klines.size());
            for (JsonNode line : klines) {
                String s = line.asText("");
                if (s == null || s.isEmpty()) {
                    continue;
                }
                String[] parts = s.split(",");
                if (parts.length < 3) {
                    continue;
                }
                String dateStr = parts[0].replace("-", "");
                double close = parseDouble(parts[2]);
                try {
                    int dateNum = Integer.parseInt(dateStr);
                    out.add(new double[]{dateNum, close});
                } catch (NumberFormatException ignored) {
                }
            }
            return out;
        } catch (Exception e) {
            LOG.warn("拉取 K 线失败 {}: {}", secid, e.getMessage());
            return null;
        }
    }

    /**
     * 从行情接口获取股票名称（可选）。失败返回 null。
     */
    private String parseStockNameFromKline(String secid) {
        try {
            String url = "https://push2.eastmoney.com/api/qt/stock/get?secid=" + secid + "&fields=f58";
            String json = fetchWithRelaxedSsl(url, "https://quote.eastmoney.com/", 5000);
            JsonNode root = OM.readTree(json);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            if (data.has("f58")) {
                return data.get("f58").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 拉取正处于大股东减持计划执行期内的股票列表及计划摘要。
     * 当东方财富返回 success=false（如报表配置不存在 9501）时，返回空列表并标记 sourceAvailable=false。
     *
     * @param stockCodes 待查股票代码
     * @return Map: "planMap" -> 股票代码 -> { noticeDate, planStartDate, planEndDate, planBrief }, "sourceAvailable" -> Boolean
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchStocksInReductionPlanResult(List<String> stockCodes) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        out.put("planMap", result);
        if (stockCodes == null || stockCodes.isEmpty()) {
            out.put("sourceAvailable", Boolean.TRUE);
            return out;
        }
        if (REDUCE_PLAN_API_ENABLED) {
            out.put("sourceAvailable", Boolean.TRUE);
            try {
                StringBuilder filter = new StringBuilder("(SECURITY_CODE in (");
                for (int i = 0; i < stockCodes.size(); i++) {
                    if (i > 0) {
                        filter.append(",");
                    }
                    filter.append("\"").append(stockCodes.get(i)).append("\"");
                }
                filter.append("))");

                String url = FINANCIAL_DATA_URL
                        + "?sortColumns=NOTICE_DATE&sortTypes=-1"
                        + "&pageSize=2000&pageNumber=1"
                        + "&reportName=" + REDUCE_PLAN_REPORT
                        + "&columns=SECURITY_CODE,SECURITY_NAME,NOTICE_DATE,PLAN_START_DATE,PLAN_END_DATE,PLAN_BRIEF"
                        + "&filter=" + URLEncoder.encode(filter.toString(), "UTF-8")
                        + "&source=WEB&client=WEB";

                String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 15000);
                JsonNode root = OM.readTree(json);
                if (!root.path("success").asBoolean(false)) {
                    LOG.warn("减持计划接口返回失败: {}", root.path("message").asText(""));
                    out.put("sourceAvailable", Boolean.FALSE);
                    return out;
                }
                JsonNode resultNode = root.path("result");
                if (resultNode.isMissingNode() || resultNode.isNull()) {
                    out.put("sourceAvailable", Boolean.FALSE);
                    return out;
                }
                JsonNode dataArr = resultNode.path("data");
                if (dataArr.isMissingNode() || !dataArr.isArray()) {
                    return out;
                }

                LocalDate today = LocalDate.now();
                for (int i = 0; i < dataArr.size(); i++) {
                    JsonNode item = dataArr.get(i);
                    String code = item.path("SECURITY_CODE").asText("");
                    if (code.isEmpty()) {
                        continue;
                    }
                    String endStr = item.path("PLAN_END_DATE").asText(null);
                    if (endStr == null || endStr.length() < 10) {
                        continue;
                    }
                    LocalDate endDate = LocalDate.parse(endStr.substring(0, 10));
                    if (endDate.isBefore(today)) {
                        continue;
                    }
                    String startStr = item.path("PLAN_START_DATE").asText("");
                    LocalDate startDate = null;
                    if (startStr != null && startStr.length() >= 10) {
                        startDate = LocalDate.parse(startStr.substring(0, 10));
                    }
                    if (startDate != null && startDate.isAfter(today)) {
                        continue;
                    }
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("noticeDate", item.path("NOTICE_DATE").asText(null));
                    detail.put("planStartDate", startStr != null && startStr.length() >= 10 ? startStr.substring(0, 10) : null);
                    detail.put("planEndDate", endStr.substring(0, 10));
                    detail.put("planBrief", item.path("PLAN_BRIEF").asText(null));
                    result.put(code, detail);
                }
            } catch (Exception e) {
                LOG.warn("获取大股东减持计划失败: {}", e.getMessage());
                out.put("sourceAvailable", Boolean.FALSE);
            }
            return out;
        }

        /* 使用东方财富个股公告接口：筛选标题或分类含“减持”的公告，作为减持相关数据源。公告接口无计划开始/截止日，按惯例推算：开始日≈公告日+15交易日(约20自然日)，截止日=公告日+6个月。 */
        out.put("sourceAvailable", Boolean.TRUE);
        for (String code : stockCodes) {
            List<Map<String, Object>> details = fetchReductionNoticesFromAnn(code);
            if (!details.isEmpty()) {
                Map<String, Object> first = details.get(0);
                String noticeDateStr = (String) first.get("noticeDate");
                LocalDate noticeDate = null;
                if (noticeDateStr != null && noticeDateStr.length() >= 10) {
                    try {
                        noticeDate = LocalDate.parse(noticeDateStr.substring(0, 10));
                    } catch (Exception ignored) {
                    }
                }
                String planStartDate = null;
                String planEndDate = null;
                String reductionType = null;
                boolean futureReduction = false;
                if (noticeDate != null) {
                    planStartDate = noticeDate.plusDays(20).toString();
                    planEndDate = noticeDate.plusMonths(6).toString();
                    LocalDate today = LocalDate.now();
                    LocalDate start = LocalDate.parse(planStartDate);
                    LocalDate end = LocalDate.parse(planEndDate);
                    if (today.isBefore(start)) {
                        reductionType = "FUTURE";
                        futureReduction = true;
                    } else if (today.isAfter(end)) {
                        reductionType = "PASSED";
                    } else {
                        reductionType = "CURRENT";
                    }
                }
                Map<String, Object> plan = new LinkedHashMap<>();
                plan.put("noticeDate", first.get("noticeDate"));
                plan.put("planStartDate", planStartDate);
                plan.put("planEndDate", planEndDate);
                plan.put("planBrief", first.get("title"));
                plan.put("reductionType", reductionType);
                plan.put("futureReduction", futureReduction);
                plan.put("reductionDetails", details);
                result.put(code, plan);
            }
        }
        return out;
    }

    /**
     * 从东方财富个股公告接口拉取该股票的减持相关公告（标题或公告分类含“减持”）。
     *
     * @param stockCode 股票代码
     * @return 公告列表，每项含 noticeDate、title、url、columnName
     */
    private List<Map<String, Object>> fetchReductionNoticesFromAnn(String stockCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String url = ANNOTICE_API_URL
                    + "?ann_type=A&client_source=web&page_index=1&page_size=50&sr=-1&stock_list="
                    + stockCode;
            String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 20000);
            JsonNode root = OM.readTree(json);
            if (root.path("success").asInt(0) != 1) {
                return list;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return list;
            }
            JsonNode arr = data.path("list");
            if (arr.isMissingNode() || !arr.isArray()) {
                return list;
            }
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                String title = item.path("title_ch").asText("");
                if (title.isEmpty()) {
                    title = item.path("title").asText("");
                }
                boolean reductionByTitle = title != null && title.contains("减持");
                boolean reductionByColumn = false;
                JsonNode columns = item.path("columns");
                if (columns.isArray()) {
                    for (int j = 0; j < columns.size(); j++) {
                        String cn = columns.get(j).path("column_name").asText("");
                        if (cn != null && cn.contains("减持")) {
                            reductionByColumn = true;
                            break;
                        }
                    }
                }
                if (!reductionByTitle && !reductionByColumn) {
                    continue;
                }
                String noticeDateStr = item.path("notice_date").asText("");
                String noticeDate = noticeDateStr != null && noticeDateStr.length() >= 10
                        ? noticeDateStr.substring(0, 10) : noticeDateStr;
                String artCode = item.path("art_code").asText("");
                String link = "https://data.eastmoney.com/notices/detail/" + stockCode + "/" + artCode + ".html";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("noticeDate", noticeDate);
                row.put("title", title);
                row.put("url", link);
                row.put("artCode", artCode);
                list.add(row);
            }
        } catch (Exception e) {
            LOG.warn("获取减持相关公告失败 {}: {}", stockCode, e.getMessage());
        }
        return list;
    }

    private Map<String, Map<String, Object>> fetchStocksInReductionPlan(List<String> stockCodes) {
        Map<String, Object> out = fetchStocksInReductionPlanResult(stockCodes);
        Map<String, Map<String, Object>> planMap = (Map<String, Map<String, Object>>) out.get("planMap");
        return planMap != null ? planMap : new LinkedHashMap<>();
    }

    /**
     * 从东方财富个股公告接口拉取该股票的回购相关公告（标题或公告分类含「回购」）。
     *
     * @param stockCode 股票代码
     * @return 公告列表，每项含 noticeDate、title、url、artCode
     */
    private List<Map<String, Object>> fetchRepurchaseNoticesFromAnn(String stockCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String url = ANNOTICE_API_URL
                    + "?ann_type=A&client_source=web&page_index=1&page_size=50&sr=-1&stock_list="
                    + stockCode;
            String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 20000);
            JsonNode root = OM.readTree(json);
            if (root.path("success").asInt(0) != 1) {
                return list;
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return list;
            }
            JsonNode arr = data.path("list");
            if (arr.isMissingNode() || !arr.isArray()) {
                return list;
            }
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                String title = item.path("title_ch").asText("");
                if (title.isEmpty()) {
                    title = item.path("title").asText("");
                }
                boolean repurchaseByTitle = title != null && title.contains("回购");
                boolean repurchaseByColumn = false;
                JsonNode columns = item.path("columns");
                if (columns.isArray()) {
                    for (int j = 0; j < columns.size(); j++) {
                        String cn = columns.get(j).path("column_name").asText("");
                        if (cn != null && cn.contains("回购")) {
                            repurchaseByColumn = true;
                            break;
                        }
                    }
                }
                if (!repurchaseByTitle && !repurchaseByColumn) {
                    continue;
                }
                String noticeDateStr = item.path("notice_date").asText("");
                String noticeDate = noticeDateStr != null && noticeDateStr.length() >= 10
                        ? noticeDateStr.substring(0, 10) : noticeDateStr;
                String artCode = item.path("art_code").asText("");
                String link = "https://data.eastmoney.com/notices/detail/" + stockCode + "/" + artCode + ".html";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("noticeDate", noticeDate);
                row.put("title", title);
                row.put("url", link);
                row.put("artCode", artCode);
                list.add(row);
            }
        } catch (Exception e) {
            LOG.warn("获取回购相关公告失败 {}: {}", stockCode, e.getMessage());
        }
        return list;
    }

    /**
     * 拉取基金持仓股票的回购计划及今日是否在回购期间内。使用公告接口，回购期限按惯例为公告日起 12 个月。
     *
     * @param stockCodes 待查股票代码
     * @return Map: "planMap" -> 股票代码 -> { noticeDate, planStartDate, planEndDate, planBrief, repurchaseType }, "sourceAvailable" -> Boolean
     */
    private Map<String, Object> fetchStocksInRepurchasePlanResult(List<String> stockCodes) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        out.put("planMap", result);
        out.put("sourceAvailable", Boolean.TRUE);
        if (stockCodes == null || stockCodes.isEmpty()) {
            return out;
        }
        for (String code : stockCodes) {
            List<Map<String, Object>> details = fetchRepurchaseNoticesFromAnn(code);
            if (details.isEmpty()) {
                continue;
            }
            Map<String, Object> first = details.get(0);
            String noticeDateStr = (String) first.get("noticeDate");
            LocalDate noticeDate = null;
            if (noticeDateStr != null && noticeDateStr.length() >= 10) {
                try {
                    noticeDate = LocalDate.parse(noticeDateStr.substring(0, 10));
                } catch (Exception ignored) {
                }
            }
            String planStartDate = null;
            String planEndDate = null;
            String repurchaseType = null;
            if (noticeDate != null) {
                planStartDate = noticeDate.toString();
                planEndDate = noticeDate.plusMonths(12).toString();
                LocalDate today = LocalDate.now();
                LocalDate start = LocalDate.parse(planStartDate);
                LocalDate end = LocalDate.parse(planEndDate);
                if (today.isBefore(start)) {
                    repurchaseType = "FUTURE";
                } else if (today.isAfter(end)) {
                    repurchaseType = "PASSED";
                } else {
                    repurchaseType = "CURRENT";
                }
            }
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("noticeDate", first.get("noticeDate"));
            plan.put("planStartDate", planStartDate);
            plan.put("planEndDate", planEndDate);
            plan.put("planBrief", first.get("title"));
            plan.put("repurchaseType", repurchaseType);
            plan.put("repurchaseDetails", details);
            result.put(code, plan);
        }
        return out;
    }

    /**
     * 按业绩报表最新季度与去年同期营业收入计算同比（例：2025-09-30 与 2024-09-30），大于20%用于业绩预增判定。
     *
     * @param revenueMap stockCode -> reportDate -> 营业收入(万元)
     * @return stockCode -> 营收同比(百分比)，无同比或缺数据则为 null
     */
    private Map<String, Double> computeRevenueYoyFromLatestQuarter(
            Map<String, TreeMap<String, BigDecimal>> revenueMap) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (revenueMap == null) {
            return result;
        }
        for (Map.Entry<String, TreeMap<String, BigDecimal>> e : revenueMap.entrySet()) {
            String code = e.getKey();
            TreeMap<String, BigDecimal> dateToRevenue = e.getValue();
            if (dateToRevenue == null || dateToRevenue.isEmpty()) {
                continue;
            }
            String latestDate = dateToRevenue.lastKey();
            if (latestDate == null || latestDate.length() < 10) {
                continue;
            }
            int year = Integer.parseInt(latestDate.substring(0, 4));
            String lastYearDate = (year - 1) + latestDate.substring(4);
            BigDecimal revLatest = dateToRevenue.get(latestDate);
            BigDecimal revLastYear = dateToRevenue.get(lastYearDate);
            if (revLatest == null || revLastYear == null || revLastYear.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            double yoy = revLatest.subtract(revLastYear)
                    .divide(revLastYear.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            result.put(code, yoy);
        }
        return result;
    }

    /**
     * 按 n、n-1、n-2 规则构建单只股票的 A 行数据并判定 原版/高速增长/放宽/业绩预增。
     * @param revenueYoyFromReport 该股票按业绩报表最新季度与去年同期营业收入计算的同比(%)，用于业绩预增中「n年营收同比>20%」判定
     */
    private Map<String, Object> buildAnnualRow(String code, Object stockName, Object weight,
                                               TreeMap<String, BigDecimal> epsMap, TreeMap<String, BigDecimal> profitMap,
                                               Map<String, Object> forecastN, Map<String, Object> forecastN1,
                                               int n, int n1, int n2, int n3,
                                               Double revenueYoyFromReport) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stockCode", code);
        row.put("stockName", stockName);
        row.put("weight", weight);

        int n4 = n - 4;
        String dN = n + "-12-31";
        String dN1 = n1 + "-12-31";
        String dN2 = n2 + "-12-31";
        String dN3 = n3 + "-12-31";
        String dN4 = n4 + "-12-31";

        BigDecimal epsN = epsMap != null ? epsMap.get(dN) : null;
        BigDecimal epsN1 = epsMap != null ? epsMap.get(dN1) : null;
        BigDecimal epsN2 = epsMap != null ? epsMap.get(dN2) : null;
        BigDecimal epsN3 = epsMap != null ? epsMap.get(dN3) : null;
        BigDecimal epsN4 = epsMap != null ? epsMap.get(dN4) : null;
        BigDecimal profitN1 = profitMap != null ? profitMap.get(dN1) : null;
        BigDecimal profitN2 = profitMap != null ? profitMap.get(dN2) : null;
        BigDecimal profitN3 = profitMap != null ? profitMap.get(dN3) : null;

        boolean hasAnnualN = epsN != null && epsN1 != null && epsN1.compareTo(BigDecimal.ZERO) != 0;
        boolean hasAnnualN1 = epsN1 != null && epsN2 != null && epsN2.compareTo(BigDecimal.ZERO) != 0;
        boolean hasAnnualN2 = epsN2 != null && epsN3 != null && epsN3.compareTo(BigDecimal.ZERO) != 0;
        boolean hasAnnualN3 = epsN3 != null && epsN4 != null && epsN4.compareTo(BigDecimal.ZERO) != 0;

        BigDecimal growthN = null;
        BigDecimal growthN1 = null;
        BigDecimal growthN2 = null;
        BigDecimal growthN3 = null;
        if (hasAnnualN) {
            growthN = epsN.subtract(epsN1).divide(epsN1.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        if (hasAnnualN1) {
            growthN1 = epsN1.subtract(epsN2).divide(epsN2.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        if (hasAnnualN2) {
            growthN2 = epsN2.subtract(epsN3).divide(epsN3.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        if (hasAnnualN3) {
            growthN3 = epsN3.subtract(epsN4).divide(epsN4.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }

        Double n1NetProfitYoy = null;
        if (profitN1 != null && profitN2 != null && profitN2.compareTo(BigDecimal.ZERO) != 0) {
            n1NetProfitYoy = profitN1.subtract(profitN2).divide(profitN2.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        Double n2NetProfitYoy = null;
        if (profitN2 != null && profitN3 != null && profitN3.compareTo(BigDecimal.ZERO) != 0) {
            n2NetProfitYoy = profitN2.subtract(profitN3).divide(profitN3.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        String sourceN = hasAnnualN ? "annual" : (forecastN != null ? "forecast" : "none");
        String sourceN1 = hasAnnualN1 ? "annual" : (forecastN1 != null ? "forecast" : "none");

        row.put("yearN", n);
        row.put("yearN1", n1);
        row.put("yearN2", n2);
        row.put("sourceN", sourceN);
        row.put("sourceN1", sourceN1);
        row.put("sourceN2", hasAnnualN2 ? "annual" : "none");
        row.put("sourceN3", hasAnnualN3 ? "annual" : "none");

        row.put("growthN", hasAnnualN ? growthN : null);
        row.put("growthN1", hasAnnualN1 ? growthN1 : null);
        row.put("growthN2", growthN2);
        row.put("growthN3", growthN3);
        row.put("epsN", hasAnnualN ? epsN.setScale(2, RoundingMode.HALF_UP) : null);
        row.put("epsN1", hasAnnualN1 ? epsN1.setScale(2, RoundingMode.HALF_UP) : null);
        row.put("epsN2", epsN2 != null ? epsN2.setScale(2, RoundingMode.HALF_UP) : null);
        row.put("epsN3", epsN3 != null ? epsN3.setScale(2, RoundingMode.HALF_UP) : null);

        if (forecastN != null) {
            row.put("forecastProfitYoyN", forecastN.get("profitYoy"));
            row.put("forecastRevenueYoyN", forecastN.get("revenueYoy"));
        }
        if (forecastN1 != null) {
            row.put("forecastProfitYoyN1", forecastN1.get("profitYoy"));
            row.put("forecastRevenueYoyN1", forecastN1.get("revenueYoy"));
        }

        row.put("hasData", hasAnnualN || hasAnnualN1 || hasAnnualN2 || forecastN != null || forecastN1 != null);

        String matchType = null;
        Object forecastDetail = null;
        if (hasAnnualN && hasAnnualN1 && hasAnnualN2 && growthN != null && growthN1 != null && growthN2 != null
                && growthN.doubleValue() > 25 && growthN1.doubleValue() > 25 && growthN2.doubleValue() > 25) {
            matchType = "原版";
        } else if (hasAnnualN && hasAnnualN1 && hasAnnualN2 && growthN != null && growthN1 != null
                && growthN.doubleValue() > 80 && growthN1.doubleValue() > 0
                && revenueYoyFromReport != null && revenueYoyFromReport > 20) {
            matchType = "高速增长";
        } else if (!hasAnnualN && hasAnnualN1 && hasAnnualN2 && growthN1 != null && growthN2 != null
                && growthN1.doubleValue() > 25 && growthN2.doubleValue() > 25) {
            matchType = "放宽";
        } else {
            // 业绩预增：预增(净利润同比)>80%、n-1年增长率>0%、n年营收同比>20%（营收同比取业绩报表最新季度与去年同期营业收入计算）
            if (sourceN.equals("forecast") && forecastN != null) {
                Double py = forecastN.get("profitYoy") != null ? ((Number) forecastN.get("profitYoy")).doubleValue() : null;
                if (py != null && py > 80 && growthN1 != null && growthN1.doubleValue() > 0
                        && revenueYoyFromReport != null && revenueYoyFromReport > 20) {
                    matchType = "业绩预增";
                    forecastDetail = forecastN.get("content");
                }
            }
            if (matchType == null && sourceN.equals("none") && sourceN1.equals("forecast") && forecastN1 != null) {
                Double py = forecastN1.get("profitYoy") != null ? ((Number) forecastN1.get("profitYoy")).doubleValue() : null;
                if (py != null && py > 80 && growthN2 != null && growthN2.doubleValue() > 0
                        && revenueYoyFromReport != null && revenueYoyFromReport > 20) {
                    matchType = "业绩预增";
                    forecastDetail = forecastN1.get("content");
                }
            }
        }
        if (forecastDetail != null) {
            row.put("forecastDetail", forecastDetail);
        }
        if (forecastN != null) {
            row.put("deductForecastContent", forecastN.get("deductContent"));
            row.put("deductForecastLower", forecastN.get("deductLower"));
            row.put("deductForecastUpper", forecastN.get("deductUpper"));
            row.put("deductForecastPreYear", forecastN.get("deductPreYear"));
            row.put("deductForecastNoticeDate", forecastN.get("deductNoticeDate"));
            row.put("deductForecastAmpGt50", forecastN.get("deductAmpGt50"));
        }
        row.put("revenueYoyFromReport", revenueYoyFromReport);
        row.put("matchType", matchType);
        return row;
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

            Set<String> seenCodes = new LinkedHashSet<>();
            for (Element table : tables) {
                Elements rows = table.select("tbody tr");
                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() < 4) {
                        continue;
                    }
                    String stockCode = tds.get(1).text().trim();
                    String stockName = tds.get(2).text().trim();
                    if (stockCode.isEmpty() || stockName.isEmpty()) {
                        continue;
                    }
                    if (seenCodes.contains(stockCode)) {
                        continue;
                    }
                    seenCodes.add(stockCode);
                    int weightIdx = tds.size() >= 9 ? 6 : 4;
                    String weightStr = (weightIdx < tds.size() ? tds.get(weightIdx) : tds.get(3))
                            .text().trim().replace("%", "");

                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("stockCode", stockCode);
                    h.put("stockName", stockName);
                    h.put("weight", parseDouble(weightStr));
                    holdings.add(h);
                }
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
     * 批量查询股票的报告期每股收益（BASIC_EPS）、归属于母公司净利润（PARENT_NETPROFIT，万元）、营业收入（TOTAL_OPERATE_INCOME，万元）。
     *
     * @param epsResult     stockCode -> reportDate -> basicEps
     * @param profitResult  stockCode -> reportDate -> parentNetProfit(万元)
     * @param revenueResult stockCode -> reportDate -> totalOperateIncome(万元)
     */
    private void fetchFinancialData(List<String> stockCodes,
                                    Map<String, TreeMap<String, BigDecimal>> epsResult,
                                    Map<String, TreeMap<String, BigDecimal>> profitResult,
                                    Map<String, TreeMap<String, BigDecimal>> revenueResult) {
        int batchSize = 30;
        for (int i = 0; i < stockCodes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, stockCodes.size());
            List<String> batch = stockCodes.subList(i, end);
            fetchFinancialBatch(batch, epsResult, profitResult, revenueResult);
        }
    }

    private void fetchFinancialBatch(List<String> codes,
                                     Map<String, TreeMap<String, BigDecimal>> epsResult,
                                     Map<String, TreeMap<String, BigDecimal>> profitResult,
                                     Map<String, TreeMap<String, BigDecimal>> revenueResult) {
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
                    + "&columns=SECURITY_CODE,SECURITY_NAME_ABBR,REPORTDATE,BASIC_EPS,PARENT_NETPROFIT,TOTAL_OPERATE_INCOME"
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
                epsResult.computeIfAbsent(code, k -> new TreeMap<>())
                        .put(reportDate, BigDecimal.valueOf(eps));
                if (!item.path("PARENT_NETPROFIT").isNull()) {
                    double profit = item.path("PARENT_NETPROFIT").asDouble(Double.NaN);
                    if (!Double.isNaN(profit)) {
                        profitResult.computeIfAbsent(code, k -> new TreeMap<>())
                                .put(reportDate, BigDecimal.valueOf(profit));
                    }
                }
                if (revenueResult != null && !item.path("TOTAL_OPERATE_INCOME").isNull()) {
                    double revenue = item.path("TOTAL_OPERATE_INCOME").asDouble(Double.NaN);
                    if (!Double.isNaN(revenue)) {
                        revenueResult.computeIfAbsent(code, k -> new TreeMap<>())
                                .put(reportDate, BigDecimal.valueOf(revenue));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("获取财务数据失败: {}", e.getMessage());
        }
    }

    /**
     * 拉取指定报告年度的业绩预告明细（净利润 004、营业收入 006），用于 n/n-1 无年报时展示及业绩预增判定。
     *
     * @param codes      股票代码列表
     * @param reportYear 报告年度，如 2025
     * @return 股票代码 -> { profitYoy, revenueYoy, content }
     */
    private Map<String, Map<String, Object>> fetchProfitForecastDetail(List<String> codes, int reportYear) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        int batchSize = 30;
        for (int i = 0; i < codes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, codes.size());
            List<String> batch = codes.subList(i, end);
            fetchProfitForecastDetailBatch(batch, reportYear, result);
        }
        return result;
    }

    private void fetchProfitForecastDetailBatch(List<String> codes, int reportYear,
                                                Map<String, Map<String, Object>> result) {
        try {
            StringBuilder filter = new StringBuilder("(SECURITY_CODE in (");
            for (int j = 0; j < codes.size(); j++) {
                if (j > 0) {
                    filter.append(",");
                }
                filter.append("\"").append(codes.get(j)).append("\"");
            }
            filter.append("))");

            String url = FINANCIAL_DATA_URL
                    + "?sortColumns=REPORT_DATE&sortTypes=-1"
                    + "&pageSize=500&pageNumber=1"
                    + "&reportName=" + PROFIT_FORECAST_REPORT
                    + "&columns=SECURITY_CODE,REPORT_DATE,PREDICT_FINANCE_CODE,ADD_AMP_LOWER,ADD_AMP_UPPER,PREDICT_CONTENT"
                    + "&filter=" + URLEncoder.encode(filter.toString(), "UTF-8")
                    + "&source=WEB&client=WEB";

            String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 15000);

            JsonNode root = OM.readTree(json);
            JsonNode dataArr = root.path("result").path("data");
            if (dataArr.isMissingNode() || !dataArr.isArray()) {
                return;
            }

            String targetDatePrefix = reportYear + "-12-31";
            for (int j = 0; j < dataArr.size(); j++) {
                JsonNode item = dataArr.get(j);
                String code = item.path("SECURITY_CODE").asText("");
                if (code.isEmpty()) {
                    continue;
                }
                String reportDate = item.path("REPORT_DATE").asText("");
                if (reportDate.length() < 10 || !reportDate.startsWith(targetDatePrefix)) {
                    continue;
                }
                String financeCode = item.path("PREDICT_FINANCE_CODE").asText("");
                if (!"004".equals(financeCode) && !"006".equals(financeCode)) {
                    continue;
                }
                Double lower = item.path("ADD_AMP_LOWER").isNull() ? null : item.path("ADD_AMP_LOWER").asDouble(Double.NaN);
                Double upper = item.path("ADD_AMP_UPPER").isNull() ? null : item.path("ADD_AMP_UPPER").asDouble(Double.NaN);
                if (Double.isNaN(lower != null ? lower : 0)) {
                    lower = null;
                }
                if (upper != null && Double.isNaN(upper)) {
                    upper = null;
                }
                String content = item.path("PREDICT_CONTENT").asText("");

                result.putIfAbsent(code, new LinkedHashMap<>());
                Map<String, Object> detail = result.get(code);
                if ("004".equals(financeCode)) {
                    detail.put("profitYoy", lower != null ? lower : (upper != null ? upper : null));
                    if (content != null && !content.isEmpty()) {
                        detail.put("content", content);
                    }
                } else if ("006".equals(financeCode)) {
                    detail.put("revenueYoy", lower != null ? lower : (upper != null ? upper : null));
                }
            }
        } catch (Exception e) {
            LOG.warn("获取业绩预告明细失败: {}", e.getMessage());
        }
    }

    /**
     * 仅拉取最新一年（reportYear-12-31）扣非净利润（005）业绩预告，用于全部A股持仓的扣非列，减少请求数据量。
     */
    private Map<String, Map<String, Object>> fetchDeductForecastDetail(List<String> codes, int reportYear) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        int batchSize = 30;
        for (int i = 0; i < codes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, codes.size());
            List<String> batch = codes.subList(i, end);
            fetchDeductForecastDetailBatch(batch, reportYear, result);
        }
        return result;
    }

    private void fetchDeductForecastDetailBatch(List<String> codes, int reportYear,
                                                Map<String, Map<String, Object>> result) {
        try {
            StringBuilder filter = new StringBuilder("(SECURITY_CODE in (");
            for (int j = 0; j < codes.size(); j++) {
                if (j > 0) {
                    filter.append(",");
                }
                filter.append("\"").append(codes.get(j)).append("\"");
            }
            filter.append("))");

            String url = FINANCIAL_DATA_URL
                    + "?sortColumns=REPORT_DATE&sortTypes=-1"
                    + "&pageSize=500&pageNumber=1"
                    + "&reportName=" + PROFIT_FORECAST_REPORT
                    + "&columns=SECURITY_CODE,REPORT_DATE,PREDICT_FINANCE_CODE,ADD_AMP_LOWER,ADD_AMP_UPPER,PREDICT_CONTENT,PREYEAR_SAME_PERIOD,NOTICE_DATE"
                    + "&filter=" + URLEncoder.encode(filter.toString(), "UTF-8")
                    + "&source=WEB&client=WEB";

            String json = fetchWithRelaxedSsl(url, "https://data.eastmoney.com/", 15000);

            JsonNode root = OM.readTree(json);
            JsonNode dataArr = root.path("result").path("data");
            if (dataArr.isMissingNode() || !dataArr.isArray()) {
                return;
            }

            String targetDatePrefix = reportYear + "-12-31";
            for (int j = 0; j < dataArr.size(); j++) {
                JsonNode item = dataArr.get(j);
                String code = item.path("SECURITY_CODE").asText("");
                if (code.isEmpty()) {
                    continue;
                }
                if (!"005".equals(item.path("PREDICT_FINANCE_CODE").asText(""))) {
                    continue;
                }
                String reportDate = item.path("REPORT_DATE").asText("");
                if (reportDate.length() < 10 || !reportDate.startsWith(targetDatePrefix)) {
                    continue;
                }
                Double lower = item.path("ADD_AMP_LOWER").isNull() ? null : item.path("ADD_AMP_LOWER").asDouble(Double.NaN);
                Double upper = item.path("ADD_AMP_UPPER").isNull() ? null : item.path("ADD_AMP_UPPER").asDouble(Double.NaN);
                if (lower != null && Double.isNaN(lower)) {
                    lower = null;
                }
                if (upper != null && Double.isNaN(upper)) {
                    upper = null;
                }
                String content = item.path("PREDICT_CONTENT").asText("");
                Object preYearSame = item.path("PREYEAR_SAME_PERIOD").isNull() ? null : item.path("PREYEAR_SAME_PERIOD").isNumber() ? item.path("PREYEAR_SAME_PERIOD").numberValue() : null;
                String noticeDateRaw = item.path("NOTICE_DATE").asText(null);
                String noticeDate = (noticeDateRaw != null && noticeDateRaw.length() >= 10) ? noticeDateRaw.substring(0, 10) : noticeDateRaw;

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("deductContent", content != null && !content.isEmpty() ? content : null);
                detail.put("deductLower", lower);
                detail.put("deductUpper", upper);
                detail.put("deductPreYear", preYearSame);
                detail.put("deductNoticeDate", noticeDate);
                detail.put("deductAmpGt50", (lower != null && lower >= 50) || (upper != null && upper >= 50));
                result.put(code, detail);
            }
        } catch (Exception e) {
            LOG.warn("获取扣非业绩预告失败: {}", e.getMessage());
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
