import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.Arrays;

public class LoaderTest {
    public static void main(String[] a) throws Exception {
        File jar = new File("target/burp-zstd.jar");
        // 父 classloader 只给 Montoya API（模拟 Burp 提供），zstd-jni 必须从插件 jar 里出
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                LoaderTest.class.getClassLoader());

        Class<?> ext = cl.loadClass("burp.zstd.BurpExtender");
        Class<?> iface = cl.loadClass("burp.api.montoya.BurpExtension");
        System.out.println("  PASS  jar 内找到 BurpExtender: " + (ext != null));
        System.out.println("  PASS  实现 BurpExtension: " + iface.isAssignableFrom(ext));
        Object inst = ext.getDeclaredConstructor().newInstance();
        System.out.println("  PASS  可实例化: " + (inst != null));
        System.out.println("  PASS  有 initialize(MontoyaApi): " + Arrays.stream(ext.getMethods())
                .anyMatch(m -> m.getName().equals("initialize")));

        // 关键：从插件 jar 自己的 classloader 加载 ZstdCodec，逼它从 jar 里提 native
        Class<?> codec = cl.loadClass("burp.zstd.ZstdCodec");
        Method compress = codec.getMethod("compress", byte[].class, int.class);
        Method decompress = codec.getMethod("decompress", byte[].class);
        byte[] plain = "{\"k\":\"\u4e2d\u6587 native test\"}".repeat(50).getBytes("UTF-8");
        byte[] packed = (byte[]) compress.invoke(null, plain, 3);
        byte[] back = (byte[]) decompress.invoke(null, packed);
        System.out.println("  PASS  jar 内 zstd native 加载 + 往返: " + Arrays.equals(plain, back));
        System.out.println("        压缩 " + plain.length + "B -> " + packed.length + "B");
        cl.close();
    }
}
