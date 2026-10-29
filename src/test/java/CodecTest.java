import burp.zstd.ZstdCodec;
import java.util.Arrays;

public class CodecTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean cond) {
        System.out.println((cond ? "  PASS  " : "  FAIL  ") + name);
        if (cond) pass++; else fail++;
    }
    public static void main(String[] a) throws Exception {
        byte[] plain = ("{\"cmd\":\"login\",\"uid\":10001,\"nick\":\"\u5f20\u4e09\"}").repeat(30).getBytes("UTF-8");

        // 1. 往返
        byte[] packed = ZstdCodec.compress(plain, 3);
        ok("magic 正确", ZstdCodec.hasMagic(packed));
        ok("往返一致", Arrays.equals(plain, ZstdCodec.decompress(packed)));

        // 2. 带 4 字节长度前缀
        byte[] prefixed = new byte[4 + packed.length];
        prefixed[0]=(byte)(packed.length>>>24); prefixed[1]=(byte)(packed.length>>>16);
        prefixed[2]=(byte)(packed.length>>>8);  prefixed[3]=(byte)packed.length;
        System.arraycopy(packed,0,prefixed,4,packed.length);
        ok("前缀体 findMagic 定位", ZstdCodec.findMagic(prefixed) == 4);
        ok("前缀体解压", Arrays.equals(plain, ZstdCodec.decompress(prefixed)));

        // 3. 无 Content_Size 的流式帧（用 ZstdOutputStream 写出的默认就没有？造一个 chunked 流）
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        com.github.luben.zstd.ZstdOutputStream zos = new com.github.luben.zstd.ZstdOutputStream(bos);
        zos.setLevel(3);
        zos.write(plain); zos.close();
        byte[] streamingFrame = bos.toByteArray();
        ok("流式帧解压", Arrays.equals(plain, ZstdCodec.decompress(streamingFrame)));

        // 4. 非法数据要抛异常而不是静默
        boolean threw = false;
        try { ZstdCodec.decompress("not zstd at all".getBytes()); } catch (Exception e) { threw = true; }
        ok("非 zstd 抛异常", threw);

        // 5. 空/极小
        ok("hasMagic(null)=false", !ZstdCodec.hasMagic(null));
        ok("hasMagic(短)=false", !ZstdCodec.hasMagic(new byte[]{0x28, (byte)0xB5}));

        System.out.println("\n==== pass=" + pass + " fail=" + fail + " ====");
        if (fail == 0) System.out.println("ALL CODE TESTS PASSED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
