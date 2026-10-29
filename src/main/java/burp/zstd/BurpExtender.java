package burp.zstd;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/**
 * Burp Zstd Decoder
 * <p>
 * 1. 响应自动解压：zstd 响应自动解成明文，Proxy history 里该条打青色高亮 + notes。
 * 2. "Zstd" 编辑 tab：请求体/响应体是 zstd 的，编辑器里多一个 tab 显示明文，
 *    请求 tab 里改完自动压回 zstd 再发。
 */
public class BurpExtender implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Zstd Decoder");

        api.http().registerHttpHandler(new ZstdHttpHandler(api));

        api.userInterface().registerHttpRequestEditorProvider(
                new ZstdEditorProviders.RequestProvider(api));
        api.userInterface().registerHttpResponseEditorProvider(
                new ZstdEditorProviders.ResponseProvider(api));

        api.logging().logToOutput("[zstd] Zstd Decoder loaded (v1.3.0)");
    }
}
