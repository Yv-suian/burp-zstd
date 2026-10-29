package burp.zstd;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * 响应方向：自动把 zstd 响应解成明文（Raw 直接可读）。
 * <p>
 * 不做任何额外标记：不改高亮色（避免和 HAE 冲突）、不写 notes、不注入响应头。
 * 唯一痕迹是编辑器里的 Zstd 选项卡。
 */
public class ZstdHttpHandler implements HttpHandler {

    private final MontoyaApi api;

    public ZstdHttpHandler(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        // 请求体不能改（会原样发给服务器），请求方向只在 Zstd 选项卡里展示明文。
        return RequestToBeSentAction.continueWith(request);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        try {
            byte[] body = response.body().getBytes();
            if (body == null || body.length < 4) {
                return ResponseReceivedAction.continueWith(response);
            }
            String ce = response.headerValue("Content-Encoding");
            boolean declared = ce != null && ce.toLowerCase().contains("zstd");
            boolean magic = ZstdCodec.hasMagic(body);
            if (!declared && !magic) {
                return ResponseReceivedAction.continueWith(response);
            }

            byte[] plain = ZstdCodec.decompress(body);
            HttpResponse base = response;
            if (declared) {
                base = base.withRemovedHeader("Content-Encoding");
            }
            HttpResponse decoded = base
                    .withBody(ByteArray.byteArray(plain))
                    .withUpdatedHeader("Content-Length", Integer.toString(plain.length));

            String url = "";
            try {
                url = response.initiatingRequest().url();
            } catch (Exception ignored) {
            }
            api.logging().logToOutput("[zstd] decoded " + body.length + "B -> " + plain.length + "B  " + url);
            return ResponseReceivedAction.continueWith(decoded);
        } catch (Exception e) {
            api.logging().logToError("[zstd] response decode failed: " + e.getMessage());
            return ResponseReceivedAction.continueWith(response);
        }
    }
}
