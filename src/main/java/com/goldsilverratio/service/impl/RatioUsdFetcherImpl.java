package com.goldsilverratio.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldsilverratio.service.RatioUsdApiService;
import com.goldsilverratio.service.RatioUsdFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 从 GoldAPI.io 拉取 XAU/USD、XAG/USD 并保存为美元计价金银比。
 * 支持按日期拉取（含历史），按月份则逐日请求。
 */
@Service
public class RatioUsdFetcherImpl implements RatioUsdFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RatioUsdFetcherImpl.class);
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String GOLDAPI_BASE = "https://www.goldapi.io/api";
    private static final String XAU_USD = "XAU/USD";
    private static final String XAG_USD = "XAG/USD";

    @Value("${app.goldapi.token:}")
    private String goldApiToken;

    @Value("${app.jisuapi.appkey:}")
    private String jisuApiAppkey;

    private static final String JISUAPI_GOLD_LONDON = "https://api.jisuapi.com/gold/london";
    private static final String JISUAPI_SILVER_LONDON = "https://api.jisuapi.com/silver/london";

    private final RatioUsdApiService ratioUsdApiService;

    public RatioUsdFetcherImpl(RatioUsdApiService ratioUsdApiService) {
        this.ratioUsdApiService = ratioUsdApiService;
    }

    @Override
    public String fetchAndSave(String dateStr) {
        LocalDate date;
        if (dateStr == null || dateStr.trim().isEmpty()) {
            date = LocalDate.now();
        } else {
            try {
                date = LocalDate.parse(dateStr.trim(), YYYYMMDD);
            } catch (Exception e) {
                return "日期格式错误，应为 yyyyMMdd";
            }
        }
        String dateParam = date.format(YYYYMMDD);

        if (goldApiToken == null || goldApiToken.isEmpty()) {
            return "未配置 app.goldapi.token，请在 application.yml 或环境变量 GOLDAPI_TOKEN 中设置";
        }

        if (ratioUsdApiService.hasDataForDate(dateParam)) {
            LOG.debug("美元计价金银比已存在，跳过: {}", dateParam);
            return "已跳过 " + dateParam;
        }

        try {
            BigDecimal gold = fetchPriceFromGoldApi(XAU_USD, dateParam);
            if (gold == null) {
                // 金价失败（如超限）不再请求银价，直接降级极速数据，避免多一次失败请求
                BigDecimal[] jisu = fetchGoldSilverFromJisuapi();
                if (jisu != null && jisu[0] != null && jisu[1] != null && jisu[1].compareTo(BigDecimal.ZERO) > 0) {
                    ratioUsdApiService.saveByDate(jisu[0], jisu[1], dateParam, "jisuapi");
                    LOG.info("美元计价金银比已保存(极速数据降级): {} 金={} 银={} USD/oz", dateParam, jisu[0], jisu[1]);
                    return "已保存 " + dateParam + " (极速数据)";
                }
                return "GoldAPI 无数据且极速数据不可用或未配置 app.jisuapi.appkey";
            }
            BigDecimal silver = fetchPriceFromGoldApi(XAG_USD, dateParam);
            if (silver != null && silver.compareTo(BigDecimal.ZERO) > 0) {
                ratioUsdApiService.saveByDate(gold, silver, dateParam, "goldapi");
                LOG.info("美元计价金银比已保存(GoldAPI): {} 金={} 银={} USD/oz", dateParam, gold, silver);
                return "已保存 " + dateParam;
            }
            BigDecimal[] jisu = fetchGoldSilverFromJisuapi();
            if (jisu != null && jisu[0] != null && jisu[1] != null && jisu[1].compareTo(BigDecimal.ZERO) > 0) {
                ratioUsdApiService.saveByDate(jisu[0], jisu[1], dateParam, "jisuapi");
                LOG.info("美元计价金银比已保存(极速数据降级): {} 金={} 银={} USD/oz", dateParam, jisu[0], jisu[1]);
                return "已保存 " + dateParam + " (极速数据)";
            }
            return "GoldAPI 无数据且极速数据不可用或未配置 app.jisuapi.appkey";
        } catch (Exception e) {
            LOG.warn("美元计价金银比拉取失败: {}", e.getMessage());
            return "拉取失败: " + e.getMessage();
        }
    }

    @Override
    public String fetchMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) {
            end = today;
        }
        int ok = 0;
        int skip = 0;
        int fail = 0;
        LocalDate day = start;
        while (!day.isAfter(end)) {
            String dateStr = day.format(YYYYMMDD);
            String msg = fetchAndSave(dateStr);
            if (msg != null && msg.startsWith("已保存")) {
                ok++;
            } else if (msg != null && msg.startsWith("已跳过")) {
                skip++;
            } else {
                fail++;
            }
            day = day.plusDays(1);
        }
        return String.format("已保存 %d 天，跳过 %d 天，%d 失败", ok, skip, fail);
    }

    /**
     * 仅通过 curl 请求 GoldAPI 单品种价格。
     */
    private BigDecimal fetchPriceFromGoldApi(String metalCurrency, String dateYyyyMmDd) {
        String urlStr = GOLDAPI_BASE + "/" + metalCurrency + "/" + dateYyyyMmDd;
        String json = getWithTokenViaCurl(urlStr);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            double price = root.has("price") && !root.path("price").isMissingNode()
                    ? root.get("price").asDouble()
                    : root.path("ask").asDouble(0);
            if (price <= 0) {
                return null;
            }
            return BigDecimal.valueOf(price);
        } catch (Exception e) {
            LOG.warn("解析 GoldAPI 响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 极速数据伦敦金、银价格（当前价，无历史日期）。GoldAPI 超限时降级用。
     * 返回 [金价, 银价] 或 null。金取自 gold/london 的「伦敦金」，银取自 silver/london 的「白银美元」。
     */
    private BigDecimal[] fetchGoldSilverFromJisuapi() {
        if (jisuApiAppkey == null || jisuApiAppkey.isEmpty()) {
            return null;
        }
        String goldJson = getJisuapiViaCurl(JISUAPI_GOLD_LONDON);
        String silverJson = getJisuapiViaCurl(JISUAPI_SILVER_LONDON);
        if (goldJson == null || silverJson == null) {
            return null;
        }
        ObjectMapper om = new ObjectMapper();
        try {
            JsonNode goldRoot = om.readTree(goldJson);
            if (goldRoot.path("status").asInt(-1) != 0) {
                LOG.warn("极速数据金价返回异常: {}", goldRoot.path("msg").asText(""));
                return null;
            }
            BigDecimal goldPrice = parseJisuapiPrice(goldRoot.path("result"), "伦敦金");
            JsonNode silverRoot = om.readTree(silverJson);
            if (silverRoot.path("status").asInt(-1) != 0) {
                LOG.warn("极速数据银价返回异常: {}", silverRoot.path("msg").asText(""));
                return null;
            }
            BigDecimal silverPrice = parseJisuapiPrice(silverRoot.path("result"), "白银美元");
            if (goldPrice == null || silverPrice == null) {
                return null;
            }
            return new BigDecimal[]{goldPrice, silverPrice};
        } catch (Exception e) {
            LOG.warn("解析极速数据响应失败: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal parseJisuapiPrice(JsonNode resultArray, String typeName) {
        if (!resultArray.isArray()) {
            return null;
        }
        for (JsonNode item : resultArray) {
            if (typeName.equals(item.path("type").asText(null))) {
                String priceStr = item.path("price").asText(null);
                if (priceStr == null || priceStr.isEmpty()) {
                    return null;
                }
                try {
                    return new BigDecimal(priceStr.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String getJisuapiViaCurl(String baseUrl) {
        String urlStr = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "appkey=" + jisuApiAppkey;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-m", "30",
                    "-H", "User-Agent: Mozilla/5.0 (compatible; GoldSilverRatio/1.0)",
                    urlStr
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            LOG.warn("极速数据请求失败 {}: {}", baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 通过系统 curl 请求 GoldAPI，需本机已安装 curl（Windows 10+ 通常自带）。
     */
    private String getWithTokenViaCurl(String urlStr) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-f", "-m", "30",
                    "-H", "x-access-token: " + goldApiToken,
                    "-H", "User-Agent: Mozilla/5.0 (compatible; GoldSilverRatio/1.0)",
                    urlStr
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("GoldAPI curl 超时: {}", urlStr);
                return null;
            }
            if (process.exitValue() != 0) {
                LOG.warn("GoldAPI curl 退出码 {}: {}", process.exitValue(), urlStr);
                return null;
            }
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                return null;
            }
            LOG.info("GoldAPI 通过 curl 获取成功: {}", urlStr);
            return result;
        } catch (Exception e) {
            LOG.warn("GoldAPI curl 执行失败 {}: {}", urlStr, e.getMessage());
            return null;
        }
    }
}
