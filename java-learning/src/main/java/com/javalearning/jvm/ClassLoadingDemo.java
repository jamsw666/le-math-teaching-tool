package com.javalearning.jvm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 知识点 4-1：类加载机制与双亲委派。
 *
 * <p>重点：加载 → 验证 → 准备 → 解析 → 初始化 五个阶段、类初始化的触发时机（主动引用 vs 被动引用）、
 * 三层类加载器与双亲委派、自定义类加载器打破委派（热部署 / 隔离的原理）、同一份字节码由不同加载器加载得到不同 Class。
 */
public class ClassLoadingDemo {

    static class Parent {
        static int value = 1;
        static final String CONSTANT = "常量";

        static {
            System.out.println("  Parent 静态初始化块执行");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("  Child 静态初始化块执行");
        }
    }

    /** 从当前 classpath 读取字节码并自己 defineClass，不委派给父加载器——打破双亲委派。 */
    static class IsolatedClassLoader extends ClassLoader {
        private final String targetClass;

        IsolatedClassLoader(String targetClass) {
            super(ClassLoadingDemo.class.getClassLoader());
            this.targetClass = targetClass;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(targetClass)) {
                return super.loadClass(name, resolve); // 其他类仍走双亲委派
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                byte[] bytes = readBytes(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        }

        private byte[] readBytes(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(path)) {
                if (in == null) throw new ClassNotFoundException(name);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static class Plugin {
        public String hello() {
            return "来自 " + getClass().getClassLoader().getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        initializationTriggers();
        loaderHierarchy();
        breakDelegation();
    }

    static void initializationTriggers() {
        System.out.println("== 类初始化触发时机 ==");
        System.out.println("通过子类引用父类静态字段 Child.value：");
        System.out.println("  value = " + Child.value + "  <- 只初始化 Parent，不初始化 Child（被动引用）");
        System.out.println("引用编译期常量 Parent.CONSTANT：" + Parent.CONSTANT + "  <- 常量在编译期已内联，不触发初始化");
        System.out.println("new Child() 才会初始化 Child：");
        new Child();
        Object[] arr = new Child[10];
        System.out.println("  创建数组 new Child[10] 不触发初始化（数组类由 JVM 直接生成），长度=" + arr.length);
        System.out.println("主动引用：new / 静态字段读写 / 静态方法 / 反射 / 初始化子类 / main 所在类 / MethodHandle 调用");
    }

    static void loaderHierarchy() {
        System.out.println("\n== 类加载器层次 ==");
        ClassLoader app = ClassLoadingDemo.class.getClassLoader();
        System.out.println("  应用类加载器: " + app);
        System.out.println("  其父（平台类加载器）: " + app.getParent());
        System.out.println("  平台类加载器的父（启动类加载器，Java 中表现为 null）: " + app.getParent().getParent());
        System.out.println("  String.class 的加载器 = " + String.class.getClassLoader() + "（启动类加载器加载 java.base）");
        System.out.println("  双亲委派：先让父加载器尝试，父加载不到才自己加载 -> 保证 java.lang.String 不会被篡改，且同一个类只加载一次");
    }

    static void breakDelegation() throws Exception {
        System.out.println("\n== 打破双亲委派：同一字节码，不同加载器 ==");
        String name = Plugin.class.getName();
        Class<?> c1 = new IsolatedClassLoader(name).loadClass(name);
        Class<?> c2 = new IsolatedClassLoader(name).loadClass(name);
        System.out.println("  c1 == c2 ? " + (c1 == c2) + "（类的唯一性 = 类加载器 + 全限定名）");
        System.out.println("  c1 == Plugin.class ? " + (c1 == Plugin.class));
        Object plugin = c1.getDeclaredConstructor().newInstance();
        System.out.println("  调用: " + c1.getMethod("hello").invoke(plugin));
        System.out.println("  plugin instanceof Plugin ? " + (plugin instanceof Plugin) + "  <- 不同加载器加载的类互不兼容");
        System.out.println("  应用：Tomcat 每个 webapp 一个 WebappClassLoader 做隔离；SPI(JDBC) 用线程上下文类加载器反向委派；Arthas/热部署靠丢弃旧加载器实现类重载");
    }
}
