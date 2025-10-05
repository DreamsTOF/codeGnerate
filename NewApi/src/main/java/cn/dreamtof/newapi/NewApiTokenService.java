package cn.dreamtof.newapi;

import cn.dreamtof.newapi.common.ApiResponse;
import cn.dreamtof.newapi.model.request.CreateTokenRequest;
import cn.dreamtof.newapi.model.request.UpdateTokenRequest;
import cn.dreamtof.newapi.model.response.TokenInfo;
import cn.dreamtof.newapi.model.response.UpdateTokenResponseData;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

// 假设这个类是一个Spring Bean，例如用@Service注解
@Component
public class NewApiTokenService {

    // 从您的配置中注入或直接定义常量
    private static final String NEW_API_BASE_URL = "https://new-api.dreamtof.cn/"; // 替换为你的真实基地址
    private static final String NEW_API_TOKEN = "qYONM4+SiKGHgW2Volp0MU5E/cIjHuUJ"; // 替换为你的真实用户Token

    /**
     * 创建单个Key
     *
     * @param request 创建Key的请求参数
     * @return ApiResponse，data部分为null
     */
    public ApiResponse<Object> createToken(CreateTokenRequest request) {
        // 1. 拼接完整URL
        String url = NEW_API_BASE_URL + "/api/token/";

        // 2. 将请求对象转换为JSON字符串
        String requestBody = cn.hutool.json.JSONUtil.toJsonStr(request);

        // 3. 使用Hutool发起POST请求
        String responseBody = cn.hutool.http.HttpRequest.post(url)
                .header(cn.hutool.http.Header.CONTENT_TYPE, "application/json")
                .header(cn.hutool.http.Header.AUTHORIZATION, "Bearer " + NEW_API_TOKEN)
                .body(requestBody)
                .execute()
                .body();

        // 4. 将响应的JSON字符串转换为ApiResponse对象
        return JSONUtil.toBean(responseBody, new TypeReference<ApiResponse<Object>>() {}, false);
    }

    /**
     * 根据关键字搜索Key
     *
     * @param keyword 搜索关键字
     * @return ApiResponse，data部分为TokenInfo
     */
    public ApiResponse<TokenInfo> searchToken(String keyword) {
        // 1. 拼接带有查询参数的URL
        String url = NEW_API_BASE_URL + "/api/token/search";

        // 2. 使用Hutool发起GET请求
        String responseBody = cn.hutool.http.HttpRequest.get(url)
                .header(cn.hutool.http.Header.CONTENT_TYPE, "application/json")
                .header(cn.hutool.http.Header.AUTHORIZATION, "Bearer " + NEW_API_TOKEN)
                .form("keyword", keyword) // Hutool会自动将参数附加到URL后面
                .execute()
                .body();

        // 3. 将响应的JSON字符串转换为ApiResponse<TokenInfo>对象
        // 注意这里使用了TypeReference来处理泛型
        return JSONUtil.toBean(responseBody, new TypeReference<ApiResponse<TokenInfo>>() {}, false);
    }

    /**
     * 更新一个已有的Key
     *
     * @param request 更新Key的请求参数
     * @return ApiResponse，data部分为UpdateTokenResponseData
     */
    public ApiResponse<UpdateTokenResponseData> updateToken(UpdateTokenRequest request) {
        // 1. 拼接完整URL
        String url = NEW_API_BASE_URL + "/api/token/";

        // 2. 将请求对象转换为JSON字符串
        String requestBody = cn.hutool.json.JSONUtil.toJsonStr(request);

        // 3. 使用Hutool发起PUT请求
        String responseBody = cn.hutool.http.HttpRequest.put(url)
                .header(cn.hutool.http.Header.CONTENT_TYPE, "application/json")
                .header(cn.hutool.http.Header.AUTHORIZATION, "Bearer " + NEW_API_TOKEN)
                .body(requestBody)
                .execute()
                .body();

        // 4. 将响应的JSON字符串转换为ApiResponse<UpdateTokenResponseData>对象
        return cn.hutool.json.JSONUtil.toBean(responseBody, new cn.hutool.core.lang.TypeReference<ApiResponse<UpdateTokenResponseData>>() {}, false);
    }
}
