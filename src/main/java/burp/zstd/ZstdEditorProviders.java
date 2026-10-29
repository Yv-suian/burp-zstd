package burp.zstd;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;

/** 两个 provider 放一起，省文件。 */
public final class ZstdEditorProviders {

    private ZstdEditorProviders() {
    }

    public static class RequestProvider implements HttpRequestEditorProvider {
        private final MontoyaApi api;

        public RequestProvider(MontoyaApi api) {
            this.api = api;
        }

        @Override
        public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext ctx) {
            return new ZstdRequestEditor(api);
        }
    }

    public static class ResponseProvider implements HttpResponseEditorProvider {
        private final MontoyaApi api;

        public ResponseProvider(MontoyaApi api) {
            this.api = api;
        }

        @Override
        public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext ctx) {
            return new ZstdResponseEditor(api);
        }
    }
}
