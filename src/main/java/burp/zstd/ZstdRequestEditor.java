package burp.zstd;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;

import java.awt.Component;

/**
 * 请求编辑 tab：显示解压后的明文；用户改完，getRequest() 自动压回 zstd 并重算 Content-Length。
 * 这样在 Repeater 里改压缩请求体的流程就是：看明文 → 改 → 发送（自动压）。
 */
public class ZstdRequestEditor implements ExtensionProvidedHttpRequestEditor {

    private final MontoyaApi api;
    private final RawEditor editor;
    private HttpRequestResponse current;
    private volatile int srcLen = -1;
    private volatile int dstLen = -1;

    public ZstdRequestEditor(MontoyaApi api) {
        this.api = api;
        this.editor = api.userInterface().createRawEditor();
        this.editor.setEditable(true);
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse rr) {
        try {
            HttpRequest r = rr.request();
            if (r == null) {
                return false;
            }
            byte[] body = r.body().getBytes();
            if (body == null || body.length < 4) {
                return false;
            }
            String ce = r.headerValue("Content-Encoding");
            return ZstdCodec.findMagic(body) >= 0 || (ce != null && ce.toLowerCase().contains("zstd"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setRequestResponse(HttpRequestResponse rr) {
        this.current = rr;
        try {
            byte[] body = rr.request().body().getBytes();
            byte[] plain = ZstdCodec.decompress(body);
            // JSON 自动美化；非 JSON 原样
            editor.setContents(ByteArray.byteArray(JsonFmt.prettyIfJson(plain)));
            srcLen = body.length;
            dstLen = plain.length;
        } catch (Exception e) {
            srcLen = -1;
            dstLen = -1;
            api.logging().logToError("[zstd] request editor: " + e.getMessage());
            editor.setContents(rr.request().body());
        }
    }

    @Override
    public HttpRequest getRequest() {
        if (current == null) {
            return null;
        }
        HttpRequest original = current.request();
        if (!editor.isModified()) {
            return original;
        }
        try {
            byte[] plain = editor.getContents().getBytes();
            byte[] packed = ZstdCodec.compress(plain);
            HttpRequest out = original
                    .withBody(ByteArray.byteArray(packed))
                    .withUpdatedHeader("Content-Length", Integer.toString(packed.length));
            if (out.hasHeader("Content-Encoding")) {
                out = out.withUpdatedHeader("Content-Encoding", "zstd");
            } else {
                out = out.withAddedHeader("Content-Encoding", "zstd");
            }
            return out;
        } catch (Exception e) {
            api.logging().logToError("[zstd] request editor compress: " + e.getMessage());
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
