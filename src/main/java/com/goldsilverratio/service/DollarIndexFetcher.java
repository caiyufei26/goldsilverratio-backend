package com.goldsilverratio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 美元指数 DXY 数据获取。
 * 通过 frankfurter 免费汇率 API 获取历史汇率，按 DXY 官方公式计算。
 * Java 8 TLS 不兼容时自动降级为系统 curl 命令。
 */
@Component
public class DollarIndexFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(DollarIndexFetcher.class);

    private static final String FRANKFURTER_API = "https://api.frankfurter.dev/v1";
    private static final String CURRENCIES = "EUR,JPY,GBP,CAD,SEK,CHF";

    private static final double DXY_COEFFICIENT = 50.14348112;
    private static final double W_EUR = 0.576;
    private static final double W_JPY = 0.136;
    private static final double W_GBP = 0.119;
    private static final double W_CAD = 0.091;
    private static final double W_SEK = 0.042;
    private static final double W_CHF = 0.036;

    /**
     * 获取指定年月的 DXY 数据（通过汇率计算）。
     *
     * @param year  年
     * @param month 月
     * @return [{ date: "yyyyMMdd", closePrice: BigDecimal }]
     */
    public List<Map<String, Object>> fetchMonth(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        LocalDate today = LocalDate.now();
        if (startDate.isAfter(today)) {
            LOG.warn("请求的月份在未来: {}-{}", year, month);
            return new ArrayList<>();
        }
        if (endDate.isAfter(today)) {
            endDate = today;
        }

        String url = FRANKFURTER_API + "/" + startDate + ".." + endDate
                + "?base=USD&symbols=" + CURRENCIES;

        String json = fetchUrl(url);
        if (json == null) {
            return new ArrayList<>();
        }
        return parseRatesResponse(json);
    }

    /**
     * 获取 URL 内容。先尝试 Java HttpURLConnection，失败后降级为系统 curl。
     */
    private String fetchUrl(String url) {
        String result = fetchUrlViaJava(url);
        if (result != null) {
            return result;
        }
        LOG.info("Java HTTP 失败，降级为 curl: {}", url);
        return fetchUrlViaCurl(url);
    }

    private String fetchUrlViaJava(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            if (conn instanceof HttpsURLConnection) {
                trustAllCerts((HttpsURLConnection) conn);
            }
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != 200) {
                LOG.warn("Java HTTP 返回 {}: {}", code, url);
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                StringBuilder sb = new StringBuilder();
                while ((n = is.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            LOG.warn("Java HTTP 失败 {}: {}", url, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 通过系统 curl 命令获取 URL 内容，绕过 Java 8 TLS 限制。
     */
    private String fetchUrlViaCurl(String url) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-f", "-m", "30",
                    "-H", "User-Agent: Mozilla/5.0",
                    url
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

            boolean finished = process.waitFor(35, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.warn("curl 超时: {}", url);
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                LOG.warn("curl 退出码 {}: {}", exitCode, url);
                return null;
            }

            String result = sb.toString().trim();
            if (result.isEmpty()) {
                LOG.warn("curl 返回空内容: {}", url);
                return null;
            }
            LOG.info("curl 获取成功: {}", url);
            return result;
        } catch (Exception e) {
            LOG.warn("curl 执行失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> parseRatesResponse(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            JsonNode rates = root.path("rates");
            if (rates.isMissingNode() || !rates.isObject()) {
                LOG.warn("frankfurter: 缺少 rates 字段");
                return result;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = rates.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String dateKey = entry.getKey();
                JsonNode dayRates = entry.getValue();

                Double dxy = calculateDxy(dayRates);
                if (dxy == null) {
                    continue;
                }

                String dateFormatted = dateKey.replace("-", "");
                BigDecimal closePrice = BigDecimal.valueOf(dxy)
                        .setScale(4, RoundingMode.HALF_UP);

                Map<String, Object> row = new HashMap<>(2);
                row.put("date", dateFormatted);
                row.put("closePrice", closePrice);
                result.add(row);
            }
            result.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
        } catch (Exception e) {
            LOG.warn("解析 frankfurter 汇率数据失败: {}", e.getMessage());
        }
        return result;
    }

    private Double calculateDxy(JsonNode dayRates) {
        double eur = dayRates.path("EUR").asDouble(0);
        double jpy = dayRates.path("JPY").asDouble(0);
        double gbp = dayRates.path("GBP").asDouble(0);
        double cad = dayRates.path("CAD").asDouble(0);
        double sek = dayRates.path("SEK").asDouble(0);
        double chf = dayRates.path("CHF").asDouble(0);
        if (eur <= 0 || jpy <= 0 || gbp <= 0 || cad <= 0 || sek <= 0 || chf <= 0) {
            return null;
        }
        double eurusd = 1.0 / eur;
        double gbpusd = 1.0 / gbp;
        return DXY_COEFFICIENT
                * Math.pow(eurusd, -W_EUR)
                * Math.pow(jpy, W_JPY)
                * Math.pow(gbpusd, -W_GBP)
                * Math.pow(cad, W_CAD)
                * Math.pow(sek, W_SEK)
                * Math.pow(chf, W_CHF);
    }

    private static void trustAllCerts(HttpsURLConnection conn) {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            LOG.debug("trustAllCerts 设置失败，使用默认");
        }
    }
}
