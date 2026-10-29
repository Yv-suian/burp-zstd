# burp-zstd

Burp Suite extension：自动解压 `Content-Encoding: zstd` 的响应，并在请求/响应编辑器里提供 `Zstd` 选项卡查看、编辑明文。

> **一句话**：Burp 自己不认 zstd，这个插件补上。响应直接变明文，请求体压缩的包能在 tab 里改完自动压回去。

[English](#english) | 中文

---

## 为什么需要

Burp Suite 目前**不支持 `Content-Encoding: zstd`**。实测依据：

```bash
# 拆 burpsuite_pro.jar（2026 年版本，60106 个条目）
zstd-related entries: 0
```

也就是说，服务端返回 zstd 压缩的响应时，Burp 的 Proxy history / Repeater 里是一坨二进制乱码，搜索、扫描、看字段全部失效。

而 zstd 正在普及：Chrome 123+ 支持 `Content-Encoding: zstd`，nginx 有 zstd 模块，APP / 小程序 / 手游后端也常用 zstd 压 protobuf 或 JSON。

这个插件就是解决"看不懂"。

## 功能

| 功能 | 说明 |
|---|---|
| **响应自动解压** | `Content-Encoding: zstd` 或 body 带 zstd magic（`28 b5 2f fd`）的响应，自动解成明文，重算 Content-Length |
| **`Zstd` 选项卡（请求）** | 请求体是 zstd 的，编辑器顶部多一个 `Zstd 126→117` 标签，显示明文；**改完点发送，自动压回 zstd 再发** |
| **`Zstd` 选项卡（响应）** | 对称，用于原始压缩响应的查看/编辑 |
| **JSON 自动美化** | 解出来是 JSON 时自动缩进排版；非 JSON 原样显示；字符串内的逗号/括号/转义不会被破坏 |
| **不冲突** | 不改高亮色（不碰 HAE 等用颜色的插件）、不写 notes、不注入响应头 |

## 安装

1. 下载 `dist/burp-zstd.jar`（或用下面的方法自己构建）
2. Burp → **Extender → Extensions → Add**
3. Extension type 选 **Java**，选择 `burp-zstd.jar`
4. 加载成功后在 Output 里能看到 `[zstd] Zstd Decoder loaded (v1.3.0)`

要求：Burp 2023+（Montoya API），Java 17+。

## 用法

**响应**：不用做任何事。抓到一个 zstd 响应，Proxy history 里直接就是明文。

**请求**：抓到一个请求体是 zstd 的 POST，点开后在编辑器顶部找 `Zstd` 选项卡，里面是明文 JSON。改完直接发送，插件会压回 zstd 并重算 Content-Length。

## 已知限制

- **不支持 zstd 字典压缩**（`ZstdDecompressor(dict_data=...)`）。带字典的帧解压会报 `Data corruption detected`，这种目前解不了
- **请求方向不会自动解压**。因为请求体是发给服务器的，handler 里改了就变成明文发出去，服务器会 400。所以请求方向只能用选项卡查看/编辑
- **只处理 zstd**，不处理 gzip/brotli（Burp 自己支持这些，重复做没意义）
- 依赖 `zstd-jni`，jar 体积 6.2MB（含全平台 native：Windows/Linux/macOS，x86_64/arm64）
- 先压后加密 / 先加密后压的包，解出来是密文，需要另外找解密逻辑

## 与其它插件的关系

- [`antoinet/burp-decompressor`](https://github.com/antoinet/burp-decompressor)：支持 gzip/deflate/lz4/pkzip，**不支持 zstd**，2022 年后停止更新
- [`th3-bl1nd3r/burp-polyproto`](https://github.com/th3-bl1nd3r/burp-polyproto)：支持 zstd + protobuf + gRPC + 规则引擎，功能更全。如果你需要 protobuf/gRPC 解码，用它

本插件只做一件事：zstd。代码 6 个类，方便自己改。

## 构建

```bash
mvn clean package
# 产物：target/burp-zstd.jar
```

依赖：
- `net.portswigger.burp.extensions:montoya-api:2025.10`（provided，Burp 运行时提供）
- `com.github.luben:zstd-jni:1.5.7-25`（打包进 jar）

## 结构

```
src/main/java/burp/zstd/
├── BurpExtender.java        # 入口，注册 handler + editor provider
├── ZstdHttpHandler.java     # 响应自动解压
├── ZstdCodec.java           # zstd 编解码（stream 版，兼容无 Content_Size 的流式帧）
├── JsonFmt.java             # 极简 JSON 美化（不引第三方依赖）
├── ZstdRequestEditor.java   # 请求 Zstd 选项卡（改完自动压回）
├── ZstdResponseEditor.java  # 响应 Zstd 选项卡
└── ZstdEditorProviders.java # 两个 provider
```

## License

MIT

---

## English

Burp Suite does **not** support `Content-Encoding: zstd` (verified: zero zstd classes in `burpsuite_pro.jar`). This extension:

- Auto-decompresses zstd responses (by `Content-Encoding: zstd` header or `28 b5 2f fd` magic), so Proxy history / Repeater / Scanner see plaintext.
- Adds a **`Zstd` editor tab** for zstd-compressed request/response bodies. Edits in the request tab are re-compressed automatically on send.
- Pretty-prints JSON (no third-party dependency).
- Does not touch highlight colors or notes (no conflict with HAE).

**Install**: Extender → Extensions → Add → type `Java` → pick `dist/burp-zstd.jar`.

**Limitations**: no zstd dictionary support; request bodies are never modified by the handler (they'd be sent as plaintext); zstd only (gzip/brotli handled natively by Burp).

**Build**: `mvn clean package` → `target/burp-zstd.jar`.

MIT License.
