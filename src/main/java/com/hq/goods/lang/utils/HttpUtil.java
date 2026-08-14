package com.hq.goods.lang.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description
 * @Author weiwenhan
 * @Date 2026/4/27 10:00
 */
@Slf4j
@Component
public class HttpUtil {
    // aihubmix域名
    public static String aihubmixUrl;
    // aihubmix密钥
    public static String aihubmixKey;
    // 是否需要代理
    public static boolean aiProxy;
    // deepseek密钥
    public static String deepseekKey;

    /**
     * 阿波罗配置修改同步到属性
     * @param aihubmixUrl 阿波罗配置的aihubmixUrl
     */
    @Value("${hq.nextpcb.ai.aihubmixUrl:https://aihubmix.com}")
    public void setAihubmixUrl(String aihubmixUrl) {
        log.info("setAihubmixUrl: {}", aihubmixUrl);
        HttpUtil.aihubmixUrl = aihubmixUrl;
    }

    @Value("${hq.nextpcb.ai.aihubmixKey}")
    public void setAihubmixKey(String aihubmixKey) {
        log.info("setAihubmixKey: {}", aihubmixKey);
        HttpUtil.aihubmixKey = aihubmixKey;
    }

    @Value("${hq.nextpcb.ai.proxy:false}")
    public void setAiProxy(boolean aiProxy) {
        log.info("setAiProxy: {}", aiProxy);
        HttpUtil.aiProxy = aiProxy;
    }

    @Value("${hq.nextpcb.ai.deepseekKey}")
    public void setDeepseekKey(String deepseekKey) {
        log.info("setDeepseekKey: {}", deepseekKey);
        HttpUtil.deepseekKey = deepseekKey;
    }

    /**
     * get请求，3分钟超时时间
     * @param url 请求地址
     * @return 请求结果
     */
    public static String sendGet(String url) {
        long start = System.currentTimeMillis();
        try {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(3*60*1000)
                    .setConnectionRequestTimeout(3*60*1000)
                    .setSocketTimeout(3*60*1000)
                    .build();
            HttpClientBuilder client = HttpClientBuilder.create();
            HttpGet request = new HttpGet(url);
            request.setConfig(config);
            CloseableHttpClient build = client.build();
            HttpResponse response = build.execute(request);
            String str = EntityUtils.toString(response.getEntity());
            return str;
        }catch (IOException e){
            log.warn("sendGet error,{}ms:"+url+e.getMessage(), System.currentTimeMillis()-start);
            log.info("详细信息：", e);
        }
        return null;
    }


    public static String sendGetByCookie(String url, Map<String, String> cookieMap) {
        try {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(3*60*1000)
                    .setConnectionRequestTimeout(3*60*1000)
                    .setSocketTimeout(3*60*1000)
                    .build();
            String cookieStr = "";
            if(cookieMap!=null){
                for(String key: cookieMap.keySet()){
                    cookieStr+=key+":"+cookieMap.get(key)+";";
                }
            }
            HttpClientBuilder client = HttpClientBuilder.create();
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(config);
            httpGet.setHeader("Cookie", cookieStr);
            CloseableHttpClient build = client.build();
            HttpResponse response = build.execute(httpGet);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String str = EntityUtils.toString(response.getEntity());
                return str;
            }
        }catch (Exception e){
            log.error("sendGetByCookie error:"+url, e);
        }
        return  null;
    }

    /**
     * post json请求 3分钟超时时间
     * @param url
     * @param param
     * @return
     */
    public static JSONObject postJson(String url, String param) {
        try {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(3*60*1000)
                    .setConnectionRequestTimeout(3*60*1000)
                    .setSocketTimeout(3*60*1000)
                    .build();
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(config);
            CloseableHttpClient client = HttpClients.createDefault();
            StringEntity entity = new StringEntity(param, "UTF-8");
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);
            HttpResponse resp = client.execute(httpPost);
            InputStream respIs = resp.getEntity().getContent();
            byte[] respBytes = IOUtils.toByteArray(respIs);
            String result = new String(respBytes, StandardCharsets.UTF_8);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            log.warn("接口请求异常！url:" + url+"，原因："+e.getMessage());
            log.info("接口请求信息" + url, e);
        }
        return null;
    }

    /**
     * 请求post json
     * @return
     */
    public static JSONObject postBodyJson(String url, String param) throws IOException {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(3*60*1000)
                .setConnectionRequestTimeout(3*60*1000)
                .setSocketTimeout(3*60*1000)
                .build();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(config);
        CloseableHttpClient client = HttpClients.createDefault();
        StringEntity entity = new StringEntity(param, "UTF-8");
        entity.setContentEncoding("UTF-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        HttpResponse resp = client.execute(httpPost);
        StatusLine statusLine = resp.getStatusLine();
        int code = statusLine.getStatusCode();
        if(HttpStatus.SC_OK == code){
            InputStream respIs = resp.getEntity().getContent();
            byte[] respBytes = IOUtils.toByteArray(respIs);
            String result = new String(respBytes, StandardCharsets.UTF_8);
            return JSONObject.parseObject(result);
        }else{
            log.warn("接口请求异常，原因：{} {}", code, statusLine.getReasonPhrase());
            throw new IOException(code+code+" "+statusLine.getReasonPhrase());
        }
    }


    /**
     * 请求AiHubMix
     * @param url 接口地址
     * @param bodyParam post json参数
     * @return ai返回的content内容
     */
    public static String postAihubmixContent(String url, String bodyParam){
        //请求
        long start = System.currentTimeMillis();
        try {
            String result = postAihubmixStr(url, bodyParam);
            log.info("AiHubMix返回，AI请求结果花费：{}ms，数据：{}：", System.currentTimeMillis()-start, result);
            JSONObject obj = JSONObject.parseObject(result);
            JSONArray choices = obj.getJSONArray("choices");
            if(choices.size()>0){
                JSONObject choice0 = choices.getJSONObject(0);
                JSONObject choiceMsg = choice0.getJSONObject("message");
                String content = choiceMsg.getString("content");
                if(StringUtils.isNotBlank(content)){
                    return content.trim();
                }
            }
        }catch (SocketTimeoutException e){
            log.warn("AiHubMix请求超时异常！" + e.getMessage());
            log.info("详细信息：", e);
        }catch (Exception e){
            log.error("AiHubMix请求报错！" + e.getMessage());
            log.info("详细信息：", e);
        }
        return "";
    }


    /**
     * 请求AiHubMix，返回对象
     * @param url 接口地址
     * @param bodyParam post json参数
     * @return 接口响应完整json字符串
     */
    public static String postAihubmixStr(String url, String bodyParam) throws IOException{
        //请求客户端
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .proxy(getProxy(HttpUtil.aiProxy))               //代理配置
                .connectTimeout(1000, TimeUnit.SECONDS)  // 连接超时（TCP握手+建立连接）
                .readTimeout(600, TimeUnit.SECONDS)      // 读取超时（等待服务器响应）
                .writeTimeout(600, TimeUnit.SECONDS)     // 写入超时（发送请求体）
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, bodyParam);
        Request request = new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer "+ HttpUtil.aihubmixKey)
                .build();
        Response response = client.newCall(request).execute();
        if(response.body() != null) {
            return response.body().string();
        }
        return null;
    }

    /**
     * 获取代理
     * @return 代理对象
     */
    public static Proxy getProxy(boolean flag){
        if(flag){
            String proxyHost = "proxy.elecfans.net";
            int proxyPort = 3128;
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        }
        return null;
    }

}
