package com.javalearning.frameworks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识点 7-2：Spring MVC 请求处理流程。
 *
 * <p>用迷你实现复刻 DispatcherServlet 的九步：HandlerMapping 找处理器 → HandlerInterceptor 前置 →
 * HandlerAdapter 解析参数（@PathVariable / @RequestParam）并反射调用 → 返回值处理（@ResponseBody 序列化）→
 * 异常处理器（@ExceptionHandler）→ 拦截器后置。
 */
public class MiniMvcDemo {

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface GetMapping { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface PathVariable { }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface RequestParam { String value(); String defaultValue() default ""; }

    record Request(String method, String path, Map<String, String> params) { }
    record Response(int status, String body) { }

    /** 处理器 = 控制器实例 + 方法 + 路径模板的正则 + 变量名。 */
    record Handler(Object controller, Method method, Pattern pattern, java.util.List<String> variables) { }

    interface HandlerInterceptor {
        default boolean preHandle(Request req) { return true; }
        default void afterCompletion(Request req, Response resp) { }
    }

    static class MiniDispatcherServlet {
        private final Map<String, Handler> handlerMapping = new LinkedHashMap<>();
        private final java.util.List<HandlerInterceptor> interceptors = new java.util.ArrayList<>();

        void registerController(Object controller) {
            for (Method m : controller.getClass().getDeclaredMethods()) {
                GetMapping mapping = m.getAnnotation(GetMapping.class);
                if (mapping == null) continue;
                java.util.List<String> vars = new java.util.ArrayList<>();
                Matcher varMatcher = Pattern.compile("\\{(\\w+)}").matcher(mapping.value());
                while (varMatcher.find()) vars.add(varMatcher.group(1));
                Pattern pattern = Pattern.compile("^" + mapping.value().replaceAll("\\{\\w+}", "([^/]+)") + "$");
                handlerMapping.put("GET " + mapping.value(), new Handler(controller, m, pattern, vars));
            }
        }

        void addInterceptor(HandlerInterceptor i) {
            interceptors.add(i);
        }

        Response dispatch(Request req) {
            Response resp;
            try {
                // 1. HandlerMapping：根据 URL 找到处理器
                Handler handler = handlerMapping.values().stream()
                        .filter(h -> h.pattern().matcher(req.path()).matches())
                        .findFirst().orElse(null);
                if (handler == null) return new Response(404, "{\"error\":\"Not Found\"}");

                // 2. 拦截器 preHandle
                for (HandlerInterceptor i : interceptors) {
                    if (!i.preHandle(req)) return new Response(401, "{\"error\":\"Unauthorized\"}");
                }

                // 3. HandlerAdapter：参数解析（ArgumentResolver）
                Object[] args = resolveArguments(handler, req);
                // 4. 反射调用控制器方法
                Object result = handler.method().invoke(handler.controller(), args);
                // 5. 返回值处理（HttpMessageConverter）：这里用最简 JSON 序列化
                resp = new Response(200, toJson(result));
            } catch (java.lang.reflect.InvocationTargetException e) {
                // 6. 异常解析器 HandlerExceptionResolver（对应 @ControllerAdvice + @ExceptionHandler）
                Throwable cause = e.getCause();
                int status = cause instanceof IllegalArgumentException ? 400 : 500;
                resp = new Response(status, "{\"error\":\"" + cause.getMessage() + "\"}");
            } catch (Exception e) {
                resp = new Response(500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
            // 7. 拦截器 afterCompletion
            for (HandlerInterceptor i : interceptors) i.afterCompletion(req, resp);
            return resp;
        }

        private Object[] resolveArguments(Handler handler, Request req) {
            Matcher m = handler.pattern().matcher(req.path());
            m.matches();
            Map<String, String> pathVars = new HashMap<>();
            for (int i = 0; i < handler.variables().size(); i++) pathVars.put(handler.variables().get(i), m.group(i + 1));

            Parameter[] params = handler.method().getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter p = params[i];
                String raw;
                if (p.isAnnotationPresent(PathVariable.class)) {
                    raw = pathVars.get(p.getName()); // 依赖 -parameters 编译参数保留形参名（pom 已配置）
                } else if (p.isAnnotationPresent(RequestParam.class)) {
                    RequestParam rp = p.getAnnotation(RequestParam.class);
                    raw = req.params().getOrDefault(rp.value(), rp.defaultValue());
                } else {
                    raw = null;
                }
                args[i] = convert(raw, p.getType()); // 类型转换（ConversionService）
            }
            return args;
        }

        private static Object convert(String raw, Class<?> type) {
            if (raw == null) return null;
            if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
            if (type == long.class || type == Long.class) return Long.parseLong(raw);
            return raw;
        }

        private static String toJson(Object o) {
            if (o instanceof Record r) {
                StringBuilder sb = new StringBuilder("{");
                var comps = r.getClass().getRecordComponents();
                for (int i = 0; i < comps.length; i++) {
                    try {
                        Object v = comps[i].getAccessor().invoke(r);
                        sb.append('"').append(comps[i].getName()).append("\":")
                                .append(v instanceof Number ? v : "\"" + v + "\"");
                        if (i < comps.length - 1) sb.append(',');
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return sb.append('}').toString();
            }
            return o instanceof String s ? "\"" + s + "\"" : String.valueOf(o);
        }
    }

    // ---------- 控制器：写法与 Spring MVC 一致 ----------
    record UserDto(long id, String name, int page) { }

    static class UserController {
        @GetMapping("/users/{id}")
        UserDto getUser(@PathVariable long id, @RequestParam(value = "page", defaultValue = "1") int page) {
            if (id <= 0) throw new IllegalArgumentException("id 必须为正数");
            return new UserDto(id, "user-" + id, page);
        }

        @GetMapping("/health")
        String health() {
            return "UP";
        }
    }

    public static void main(String[] args) {
        MiniDispatcherServlet servlet = new MiniDispatcherServlet();
        servlet.registerController(new UserController());
        servlet.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(Request req) {
                boolean ok = !"/users/403".equals(req.path());
                System.out.println("  [拦截器] preHandle " + req.path() + " -> " + (ok ? "放行" : "拒绝"));
                return ok;
            }

            @Override
            public void afterCompletion(Request req, Response resp) {
                System.out.println("  [拦截器] afterCompletion 状态码 " + resp.status());
            }
        });

        System.out.println("== 请求分发 ==");
        for (Request req : new Request[]{
                new Request("GET", "/users/42", Map.of("page", "3")),
                new Request("GET", "/users/0", Map.of()),
                new Request("GET", "/users/403", Map.of()),
                new Request("GET", "/health", Map.of()),
                new Request("GET", "/nothing", Map.of())}) {
            Response resp = servlet.dispatch(req);
            System.out.println("  " + req.method() + " " + req.path() + " -> " + resp.status() + " " + resp.body() + "\n");
        }

        System.out.println("""
                Spring MVC 真实流程（DispatcherServlet.doDispatch）：
                  1. HandlerMapping（RequestMappingHandlerMapping）根据 URL/Method/Headers 找到 HandlerMethod + 拦截器链
                  2. HandlerAdapter（RequestMappingHandlerAdapter）：HandlerMethodArgumentResolver 解析参数
                     （@PathVariable / @RequestParam / @RequestBody 走 HttpMessageConverter / @Valid 触发校验）
                  3. 反射调用控制器方法
                  4. HandlerMethodReturnValueHandler 处理返回值：@ResponseBody -> MessageConverter 写 JSON；String -> ViewResolver 找视图
                  5. 异常由 HandlerExceptionResolver 链处理，@ControllerAdvice + @ExceptionHandler 是最常用的实现
                  6. 拦截器 preHandle -> postHandle -> afterCompletion；Filter 在 Servlet 容器层，比拦截器更早
                WebFlux 则是 DispatcherHandler + Reactor 非阻塞模型，编程模型相似但底层完全不同
                """);
    }
}
