//import io.github.xxxyjade.lumirose.tool.Tool;
//import org.reflections.Reflections;
//import org.reflections.scanners.Scanners;
//import org.reflections.util.ClasspathHelper;
//import org.reflections.util.ConfigurationBuilder;
//
//import java.lang.reflect.Modifier;
//import java.util.HashMap;
//import java.util.Map;
//
//public class Test003 {
//    public static void main(String[] args) {
//        Map<String, Class<? extends Tool>> tools = new HashMap<>();
//
//        Reflections reflections = new Reflections(
//                new ConfigurationBuilder()
//                        .setUrls(ClasspathHelper.forJavaClassPath())
//        );
//
//        reflections.get(Scanners.SubTypes.of(Tool.class).asClass())
//                .stream()
//                .filter(c ->
//                        !c.isInterface() &&     // 排除接口
//                                    !Modifier.isAbstract(c.getModifiers()) &&   // 排除抽象类
//                                    !c.isAnonymousClass() &&    // 排除匿名类
//                                    !c.isLocalClass()  // 排除局部类
//                )
//                .forEach(c -> {
//                    System.out.println(c.getSimpleName());
//                    tools.put(c.getSimpleName(), (Class<? extends Tool>) c);
//                });
//
//        for (Map.Entry<String, Class<? extends Tool>> stringClassEntry : tools.entrySet()) {
//            System.out.println("key: " + stringClassEntry.getKey() + " value: " + stringClassEntry.getValue());
//        }
//    }
//}
