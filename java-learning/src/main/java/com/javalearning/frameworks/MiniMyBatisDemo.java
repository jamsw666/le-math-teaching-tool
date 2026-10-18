package com.javalearning.frameworks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识点 7-5：MyBatis / MyBatis-Plus 原理。
 *
 * <p>用 H2 内存库 + 约 100 行实现迷你 MyBatis：Mapper 接口没有实现类，由 JDK 动态代理在运行时生成；
 * 解析 @Select/@Insert 中的 #{param} 占位符为 PreparedStatement 的 ?（防注入）；
 * 结果集按列名映射为 record；一级缓存按 SQL+参数缓存查询结果。
 */
public class MiniMyBatisDemo {

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Select { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Insert { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Param { String value(); }

    record User(long id, String name, int age) { }

    /** Mapper 接口：和 MyBatis 用法一致。 */
    interface UserMapper {
        @Insert("INSERT INTO users(name, age) VALUES (#{name}, #{age})")
        int insert(@Param("name") String name, @Param("age") int age);

        @Select("SELECT id, name, age FROM users WHERE age > #{minAge} ORDER BY id")
        List<User> findOlderThan(@Param("minAge") int minAge);

        @Select("SELECT id, name, age FROM users WHERE id = #{id}")
        User findById(@Param("id") long id);
    }

    /** SqlSession：持有连接、一级缓存，并生产 Mapper 代理。 */
    static class MiniSqlSession implements AutoCloseable {
        private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(\\w+)}");
        private final Connection connection;
        private final Map<String, Object> firstLevelCache = new ConcurrentHashMap<>();

        MiniSqlSession(Connection connection) {
            this.connection = connection;
        }

        @SuppressWarnings("unchecked")
        <T> T getMapper(Class<T> mapperInterface) {
            return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class<?>[]{mapperInterface}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
                Select select = method.getAnnotation(Select.class);
                Insert insert = method.getAnnotation(Insert.class);
                String rawSql = select != null ? select.value() : insert.value();
                Map<String, Object> params = bindParams(method, args);

                // #{name} -> ? 并记录参数顺序（MyBatis 的 SqlSource -> BoundSql）
                List<Object> ordered = new ArrayList<>();
                Matcher m = PLACEHOLDER.matcher(rawSql);
                StringBuilder sql = new StringBuilder();
                while (m.find()) {
                    ordered.add(params.get(m.group(1)));
                    m.appendReplacement(sql, "?");
                }
                m.appendTail(sql);

                if (insert != null) {
                    firstLevelCache.clear(); // 写操作清空一级缓存
                    return executeUpdate(sql.toString(), ordered);
                }
                String cacheKey = sql.toString() + ordered;
                if (firstLevelCache.containsKey(cacheKey)) {
                    System.out.println("    [一级缓存命中] " + cacheKey);
                    return firstLevelCache.get(cacheKey);
                }
                Object result = executeQuery(sql.toString(), ordered, method);
                firstLevelCache.put(cacheKey, result);
                return result;
            });
        }

        private static Map<String, Object> bindParams(Method method, Object[] args) {
            Map<String, Object> params = new java.util.HashMap<>();
            Parameter[] ps = method.getParameters();
            for (int i = 0; i < ps.length; i++) {
                Param p = ps[i].getAnnotation(Param.class);
                params.put(p != null ? p.value() : ps[i].getName(), args[i]);
            }
            return params;
        }

        private int executeUpdate(String sql, List<Object> params) throws SQLException {
            System.out.println("    [SQL] " + sql + " 参数 " + params);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                return ps.executeUpdate();
            }
        }

        private Object executeQuery(String sql, List<Object> params, Method method) throws Exception {
            System.out.println("    [SQL] " + sql + " 参数 " + params);
            Class<?> rowType = method.getReturnType();
            boolean isList = List.class.isAssignableFrom(rowType);
            if (isList) {
                var generic = (java.lang.reflect.ParameterizedType) method.getGenericReturnType();
                rowType = (Class<?>) generic.getActualTypeArguments()[0];
            }
            List<Object> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(mapRow(rs, rowType));
                }
            }
            return isList ? rows : (rows.isEmpty() ? null : rows.get(0));
        }

        /** ResultSetHandler：按 record 组件名取列（MyBatis 支持 mapUnderscoreToCamelCase）。 */
        private static Object mapRow(ResultSet rs, Class<?> type) throws Exception {
            RecordComponent[] comps = type.getRecordComponents();
            Object[] values = new Object[comps.length];
            Class<?>[] types = new Class<?>[comps.length];
            for (int i = 0; i < comps.length; i++) {
                types[i] = comps[i].getType();
                Object v = rs.getObject(comps[i].getName());
                values[i] = switch (types[i].getName()) {
                    case "long" -> ((Number) v).longValue();
                    case "int" -> ((Number) v).intValue();
                    default -> v;
                };
            }
            return type.getDeclaredConstructor(types).newInstance(values);
        }

        @Override
        public void close() throws SQLException {
            connection.close();
        }
    }

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:mybatis;DB_CLOSE_DELAY=-1");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50), age INT)");
        }
        try (MiniSqlSession session = new MiniSqlSession(DriverManager.getConnection("jdbc:h2:mem:mybatis"))) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            System.out.println("== Mapper 代理类: " + mapper.getClass().getSimpleName() + " ==");

            System.out.println("\n== 插入 ==");
            mapper.insert("Alice", 30);
            mapper.insert("Bob", 17);
            mapper.insert("Robert'); DROP TABLE users; --", 25); // 注入尝试，因为是参数绑定所以只是个奇怪的名字

            System.out.println("\n== 查询 ==");
            System.out.println("  age > 18: " + mapper.findOlderThan(18));
            System.out.println("  findById(2): " + mapper.findById(2));
            System.out.println("  再次 findById(2):");
            mapper.findById(2);
        }
        System.out.println("""

                MyBatis 核心对象：
                  SqlSessionFactory（全局唯一，解析 XML/注解生成 Configuration + MappedStatement）
                  SqlSession（一次会话，非线程安全，持有 Executor 与一级缓存）
                  Mapper 代理 MapperProxy（本例的 Proxy）-> MapperMethod -> SqlSession.selectList/update
                  Executor（Simple/Reuse/Batch，被 CachingExecutor 装饰实现二级缓存）-> StatementHandler -> ParameterHandler / ResultSetHandler
                  插件 Interceptor 通过动态代理拦截上述四大对象（分页插件 PageHelper 就拦截 Executor）
                #{} vs ${}：#{} 预编译占位（安全），${} 字符串拼接（用于表名/排序字段，必须白名单校验）
                缓存：一级缓存 SqlSession 级默认开启（Spring 集成下每次请求新 Session，基本无效）；二级缓存 Mapper 级，分布式下易脏读，生产少用
                动态 SQL：<if> <where> <foreach> <choose> <trim>；MyBatis-Plus 在此之上提供 BaseMapper 通用 CRUD、LambdaQueryWrapper、分页、乐观锁、逻辑删除、代码生成
                """);
    }
}
