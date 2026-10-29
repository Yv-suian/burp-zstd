package burp.zstd;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;

import java.awt.Component;

/**
 * 响应编辑 tab：和请求方向对称。用于「就是想改一下再看/再压」的场景。
 * 注意：响应方向 ZstdHttpHandler 默认已经把响应解成明文了，此时本 tab 不激活；
 * 只有当响应未被 handler 解（例如在 Repeater 里手动重放过的原始压缩体）时才用得上。
 */
public class ZstdResponseEditor implements ExtensionProvidedHttpResponseEditor {

    private final MontoyaApi api;
    private final RawEditor editor;
    private HttpRequestResponse current;
    private volatile int srcLen = -1;
    private volatile int dstLen = -1;

    public ZstdResponseEditor(MontoyaApi api) {
        this.api = api;
        this.editor = api.userInterface().createRawEditor();
        this.editor.setEditable(true);
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse rr) {
        try {
            HttpResponse resp = rr.response();
            if (resp == null) {
                return false;
            }
            byte[] body = resp.body().getBytes();
            if (body == null || body.length < 4) {
                return false;
            }
            String ce = resp.headerValue("Content-Encoding");
            return ZstdCodec.findMagic(body) >= 0 || (ce != null && ce.toLowerCase().contains("zstd"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setRequestResponse(HttpRequestResponse rr) {
        this.current = rr;
        try {
            byte[] body = rr.response().body().getBytes();
            byte[] plain = ZstdCodec.decompress(body);
            // JSON 自动美化；非 JSON 原样
            editor.setContents(ByteArray.byteArray(JsonFmt.prettyIfJson(plain)));
            srcLen = body.length;
            dstLen = plain.length;
        } catch (Exception e) {
            srcLen = -1;
            dstLen = -1;
            api.logging().logToError("[zstd] response editor: " + e.getMessage());
            editor.setContents(rr.response().body());
        }
    }

    @Override
    public HttpResponse getResponse() {
        if (current == null) {
            return null;
        }
        HttpResponse original = current.response();
        if (!editor.isModified()) {
            return original;
        }
        try {
            byte[] plain = editor.getContents().getBytes();
            byte[] packed = ZstdCodec.compress(plain);
            HttpResponse out = original
                    .withBody(ByteArray.byteArray(packed))
                    .withUpdatedHeader("Content-Length", Integer.toString(packed.length));
            if (out.hasHeader("Content-Encoding")) {
                out = out.withUpdatedHeader("Content-Encoding", "zstd");
            } else {
                out = out.withAddedHeader("Content-Encoding", "zstd");
            }
            return out;
        } catch (Exception e) {
            api.logging().logToError("[zstd] response editor compress: " + e.getMessage());
            return original;
        }
    }

    @Override
    public boolean isModified() {
        return editor.isModified();
    }

    @Override
    public String caption() {
        return (srcLen > 0 && dstLen >= 0) ? ("Zstd " + srcLen + "→" + dstLen) : "Zstd";
    }

    @Override
    public Component uiComponent() {
        return editor.uiComponent();
    }

    @Override
    public Selection selectedData() {
        return editor.selection().orElse(null);
    }
}
