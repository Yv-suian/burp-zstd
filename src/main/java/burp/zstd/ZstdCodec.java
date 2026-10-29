package burp.zstd;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * zstd 编解码。用 stream 版本，天然处理「流式帧（无 Content_Size）」——
 * 这是解 dctx.decompress() 会炸的最常见坑。
 */
public final class ZstdCodec {

    /** zstd frame magic: 0x28 B5 2F FD */
    private static final byte[] MAGIC = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};

    private ZstdCodec() {
    }

    public static boolean hasMagic(byte[] b, int off) {
        if (b == null || off < 0 || b.length < off + MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (b[off + i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasMagic(byte[] b) {
        return hasMagic(b, 0);
    }

    /** 找 body 里第一个 zstd magic 的偏移（兼容 4 字节长度前缀等），找不到 -1 */
    public static int findMagic(byte[] b) {
        if (b == null) {
            return -1;
        }
        for (int i = 0; i + MAGIC.length <= b.length; i++) {
            if (hasMagic(b, i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解压。会自动跳过 magic 之前的前缀（长度字段 / 填充），
     * 用 stream 读，无 Content_Size 的帧也能解。
     */
    public static byte[] decompress(byte[] src) throws IOException {
        if (src == null) {
            throw new IOException("null body");
        }
        int off = findMagic(src);
        if (off < 0) {
            throw new IOException("no zstd magic found");
        }
        if (off > 0) {
            byte[] trimmed = new byte[src.length - off];
            System.arraycopy(src, off, trimmed, 0, trimmed.length);
            src = trimmed;
        }
        try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(src))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(128, src.length * 4));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static byte[] compress(byte[] src, int level) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(128, src.length / 2));
        try (ZstdOutputStream out = new ZstdOutputStream(bos)) {
            out.setLevel(level);
            out.write(src);
        }
        return bos.toByteArray();
    }

    public static byte[] compress(byte[] src) throws IOException {
        return compress(src, 3);
    }
}
