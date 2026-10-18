package com.javalearning.modern;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;

/**
 * 知识点 6-2：Optional 与新日期时间 API（Java 8）。
 *
 * <p>重点：Optional 的正确用法（返回值而非字段/参数，链式 map/flatMap/filter/orElseGet，不要 get()/isPresent() 套路）；
 * java.time 的不可变与线程安全、LocalDate/LocalDateTime/Instant/ZonedDateTime 的区别、Period vs Duration、
 * DateTimeFormatter 替代线程不安全的 SimpleDateFormat、时区陷阱。
 */
public class OptionalDateTimeDemo {

    record Address(String city) { }
    record User(String name, Address address) { }

    static final Map<String, User> USERS = Map.of(
            "alice", new User("alice", new Address("Shanghai")),
            "bob", new User("bob", null));

    static Optional<User> findUser(String name) {
        return Optional.ofNullable(USERS.get(name));
    }

    public static void main(String[] args) {
        optional();
        dateTime();
    }

    static void optional() {
        System.out.println("== Optional ==");
        // 传统写法：三层 null 检查
        // if (u != null && u.address() != null && u.address().city() != null) ...
        String city1 = findUser("alice").map(User::address).map(Address::city).orElse("未知");
        String city2 = findUser("bob").map(User::address).map(Address::city).orElse("未知");
        String city3 = findUser("nobody").map(User::address).map(Address::city).orElse("未知");
        System.out.println("  链式取值: alice=" + city1 + " bob=" + city2 + " nobody=" + city3);

        System.out.println("  filter: " + findUser("alice").filter(u -> u.name().startsWith("a")).isPresent());
        findUser("alice").ifPresentOrElse(u -> System.out.println("  ifPresentOrElse 找到 " + u.name()), () -> System.out.println("  未找到"));
        System.out.println("  orElseGet 惰性求值（orElse 的参数总会被求值）: " + findUser("x").map(User::name).orElseGet(() -> "计算默认值"));
        try {
            findUser("x").orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        } catch (IllegalArgumentException e) {
            System.out.println("  orElseThrow: " + e.getMessage());
        }
        Optional<Optional<String>> nested = Optional.of(Optional.of("x"));
        System.out.println("  flatMap 展平嵌套: " + nested.flatMap(o -> o).orElse(""));
        System.out.println("""
                  规范：
                    - Optional 只用于方法返回值；不要作为字段、参数或集合元素（不可序列化、增加开销）
                    - 不要 Optional.get() 不检查；不要 if (opt.isPresent()) opt.get() —— 那只是换了写法的 null 检查
                    - 集合返回空集合而非 Optional<List>
                    - 基本类型用 OptionalInt / OptionalLong / OptionalDouble
                """);
    }

    static void dateTime() {
        System.out.println("== java.time ==");
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalDateTime dateTime = LocalDateTime.of(2024, 2, 29, 23, 30);
        Instant instant = Instant.parse("2024-02-29T15:30:00Z");
        ZonedDateTime shanghai = instant.atZone(ZoneId.of("Asia/Shanghai"));
        ZonedDateTime newYork = instant.atZone(ZoneId.of("America/New_York"));
        System.out.println("  LocalDate（无时区的日期）: " + date + "，闰年: " + date.isLeapYear());
        System.out.println("  LocalDateTime（无时区的日期时间，\"墙上时钟\"）: " + dateTime);
        System.out.println("  Instant（UTC 时间线上的一点，等价于 epoch 毫秒）: " + instant + " = " + instant.toEpochMilli());
        System.out.println("  同一 Instant 在上海 " + shanghai.toLocalTime() + "，在纽约 " + newYork.toLocalTime() + "（ZonedDateTime 带时区）");

        LocalDate next = date.plusYears(1);
        System.out.println("  2024-02-29 加一年 = " + next + "（自动调整到月末，不会抛异常）");
        System.out.println("  本月最后一天: " + date.with(TemporalAdjusters.lastDayOfMonth()) + "，下一个周一: " + date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));

        Period period = Period.between(LocalDate.of(1990, 5, 20), LocalDate.of(2024, 2, 29));
        Duration duration = Duration.between(dateTime, dateTime.plusHours(26).plusMinutes(5));
        System.out.println("  Period（日期间隔）: " + period.getYears() + " 年 " + period.getMonths() + " 月 " + period.getDays() + " 天");
        System.out.println("  Duration（时间间隔）: " + duration + " = " + duration.toMinutes() + " 分钟；ChronoUnit.DAYS.between = "
                + ChronoUnit.DAYS.between(LocalDate.of(2024, 1, 1), date));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
        System.out.println("  格式化: " + dateTime.format(fmt) + "，解析: " + LocalDateTime.parse("2024年03月01日 08:00", fmt));
        System.out.println("  DateTimeFormatter 不可变且线程安全，可以做静态常量；SimpleDateFormat 不行");
        System.out.println("""
                  陷阱：
                    - 模式里 YYYY 是"周年"，跨年那周会出错，要用 yyyy；hh 是 12 小时制，24 小时制用 HH
                    - 数据库 TIMESTAMP 存 UTC（Instant），展示时再按用户时区转换；LocalDateTime 不含时区，跨地区系统慎用
                    - 旧 API 互转：Date.from(instant) / date.toInstant()；java.sql.Timestamp.valueOf(localDateTime)
                """);
    }
}
