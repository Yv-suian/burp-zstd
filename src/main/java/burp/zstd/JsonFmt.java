package burp.zstd;

import java.nio.charset.StandardCharsets;

/**
 * 极简 JSON 美化。不引第三方依赖（Burp 的 classloader 不保证把 jackson 暴露给插件）。
 * 只处理「看起来是 JSON」的输入；不是 JSON 就原样返回。
 * 字符串内的逗号/括号/转义不会被破坏。
 */
public final class JsonFmt {

    private JsonFmt() {
    }

    /** 解出来的字节如果是 JSON，就美化；否则原样返回。 */
    public static byte[] prettyIfJson(byte[] raw) {
        if (raw == null || raw.length < 2) {
            return raw;
        }
        String s;
        try {
            s = new String(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
        // 必须完全可解码（没有替换字符），且首尾是 JSON 括号
        if (s.indexOf('\uFFFD') >= 0) {
            return raw;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return raw;
        }
        char c0 = t.charAt(0);
        char c1 = t.charAt(t.length() - 1);
        boolean obj = c0 == '{' && c1 == '}';
        boolean arr = c0 == '[' && c1 == ']';
        if (!obj && !arr) {
            return raw;
        }
        String out = format(t);
        if (out == null) {
            return raw;
        }
        // 已经美化过就别再动（避免反复加空白）
        if (out.equals(s)) {
            return raw;
        }
        return out.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 美化。返回 null 表示括号不平衡/结构异常，调用方应保留原样。
     */
    static String format(String json) {
        StringBuilder out = new StringBuilder(json.length() + 64);
        int indent = 0;
        boolean inStr = false;
        boolean esc = false;
        int depth = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                out.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inStr = true;
                    out.append(c);
                    break;
                case '{':
                case '[':
                    depth++;
                    out.append(c);
                    // 空的 {} / [] 不换行
                    int j = nextNonSpace(json, i + 1);
                    if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                        // 交给后面的 } / ] 处理，这里不换行
                    } else {
                        indent++;
                        out.append('\n').append(pad(indent));
                    }
                    break;
                case '}':
                case ']':
                    depth--;
                    if (depth < 0) {
                        return null;
                    }
                    // 上一个是 { 或 [（空对象/数组），不换行
                    int k = prevNonSpace(out);
                    if (k >= 0 && (out.charAt(k) == '{' || out.charAt(k) == '[')) {
                        out.append(c);
                    } else {
                        indent = Math.max(0, indent - 1);
                        out.append('\n').append(pad(indent)).append(c);
                    }
                    break;
                case ',':
                    out.append(c).append('\n').append(pad(indent));
                    break;
                case ':':
                    out.append(c).append(' ');
                    break;
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    // 字符串外的空白全部丢弃，由美化重新排版
                    break;
                default:
                    out.append(c);
            }
        }
        if (inStr || depth != 0) {
            return null;
        }
        return out.toString();
    }

    private static int nextNonSpace(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return i;
            }
        }
        return s.length();
    }

    private static int prevNonSpace(StringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            char c = sb.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String pad(int n) {
        return "  ".repeat(Math.max(0, n));
    }
}
