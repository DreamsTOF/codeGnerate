//package com.dream.codegenerate.utils;
//
//
//import ch.qos.logback.core.joran.util.beans.BeanUtil;
//import cn.hutool.v7.core.annotation.AnnotationUtil;
//import cn.hutool.v7.json.JSONUtil;
//import feign.Response;
//import feign.Util;
//import feign.codec.Decoder;
//import java.io.IOException;
//import java.lang.reflect.InvocationHandler;
//import java.lang.reflect.Method;
//import java.lang.reflect.Proxy;
//import java.lang.reflect.Type;
//import java.nio.charset.StandardCharsets;
/**
 * 容错性极强（平滑处理未知字段）： 对方哪怕给 JSON 增加了 50 个新字段，你的代码一行都不用改，也不会因为 UnrecognizedPropertyException 挂掉。
 *
 * 摆脱 JAR 包依赖： 你不需要引入对方的 client.jar。你只需要定义一个只有 5 行代码的 interface，描述你关注的数据维度即可。
 *
 * 类型安全： 虽然底层是 Map 或者 JSONObject，但在业务代码里你用的是 summary.getAmount()，有 IDE 提示，有编译检查。
 *
 * 按需反序列化： 传统的 Jackson 反序列化会尝试解析整个 JSON 树。这个桥接器可以实现“懒加载”：只有当你调用 getAmount() 时，才去执行类型转换。
 */
////第一步，定义接口
//public interface RemoteUserBridge {
//
//    // 映射 JSON 顶层的字段
//    String getUserId();
//
//    // 映射 JSON 深层的字段：{ "data": { "base": { "nickname": "张三" } } }
//    @cn.hutool.core.annotation.Alias("data.base.nickname")
//    String getName();
//
//    // 映射类型不一致的情况：对方返回 "18"，这里自动转为 Integer
//    Integer getAge();
//
//    // --- 核心爽点：支持 default 业务方法 ---
//    default boolean isAdult() {
//        return getAge() != null && getAge() >= 18;
//    }
//
//    default String getDisplayName() {
//        return String.format("用户[%s](ID:%s)", getName(), getUserId());
//    }
//}
//第二步：定义 Feign Client
//
//@FeignClient(name = "user-service", url = "${remote.user-api.url}")
//public interface UserClient {
//
//    @GetMapping("/api/v1/user/detail/{id}")
//    RemoteUserBridge getUserDetail(@PathVariable("id") String id);
//}
//第三步：业务调用
//@Service
//@RequiredArgsConstructor
//public class UserService {
//    private final UserClient userClient;
//
//    public void checkUser(String id) {
//        // 这里的 user 是一个代理对象，不持有冗余数据，只有在调用时才去读取 JSON
//        RemoteUserBridge user = userClient.getUserDetail(id);
//
//        if (user.isAdult()) { // 调用接口定义的 default 方法
//            System.out.println("允许进入：" + user.getDisplayName());
//        } else {
//            System.out.println("未成年禁止进入");
//        }
//    }
//}

//public class FeignBridgeDecoder implements Decoder {
//
//    private final Decoder delegate;
//
//    public FeignBridgeDecoder(Decoder delegate) {
//        this.delegate = delegate;
//    }
//
//    @Override
//    public Object decode(Response response, Type type) throws IOException {
//        // 只有当 Feign 接口定义的返回类型是 接口 时，才启用桥接代理
//        if (!(type instanceof Class<?> clazz) || !clazz.isInterface()) {
//            return delegate.decode(response, type);
//        }
//
//        String jsonStr = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
//        if (JSONUtil.isTypeJSON(jsonStr)) {
//            return createProxy(JSONUtil.parseObj(jsonStr), clazz);
//        }
//        return delegate.decode(response, type);
//    }
//
//    @SuppressWarnings("unchecked")
//    private <T> T createProxy(JSONObject json, Class<T> interfaceClass) {
//        return (T) Proxy.newProxyInstance(
//            interfaceClass.getClassLoader(),
//            new Class<?>[]{interfaceClass},
//            (proxy, method, args) -> {
//                // 1. 支持接口的 default 方法 (JDK 16+ 核心特性)
//                if (method.isDefault()) {
//                    return InvocationHandler.invokeDefault(proxy, method, args);
//                }
//
//                // 2. 过滤 Object 基本方法
//                if (ReflectUtil.isHashCodeMethod(method)) return System.identityHashCode(proxy);
//                if (ReflectUtil.isToStringMethod(method)) return "BridgeProxy:" + interfaceClass.getName();
//                if (ReflectUtil.isEqualsMethod(method)) return proxy == args[0];
//
//                // 3. 属性映射逻辑
//                // 优先读取 Hutool 的 @Alias 注解，支持 json 路径提取（如 "user.name"）
//                String path = AnnotationUtil.getAnnotationValue(method, cn.hutool.core.annotation.Alias.class);
//                if (path == null) {
//                    path = BeanUtil.getPropertyName(method);
//                }
//
//                Object rawValue = json.getByPath(path);
//
//                // 4. 类型转换：利用 Hutool 强大的转换器处理类型不一致、日期格式等
//                return Convert.convert(method.getGenericReturnType(), rawValue);
//            }
//        );
//    }
//}
