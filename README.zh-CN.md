[English](README.md) | 中文

# CGSim（中文说明）


ChuangGaoSim 是一个面向**创高体育（cgapp）**的开源 Android 运动数据工作台。它以独立 App 的形式实现了创高的网络协议（登录、请求签名、运动数据上传），帮助你以**手动可控的方式**生成与提交运动记录,目前nk/zd/cd可用。

> ⚠️ **免责声明**
> 仅限授权使用。本项目仅供学习与研究用途（协议分析、移动安全学习）。请遵守所在学校的体育考核规定与相关法律法规，因使用本项目产生的任何后果由使用者自行承担!本项目不保证或滥用责任！

### 功能

- **统一认证登录**：WebView 内完成学校 CAS 统一身份认证，自动抓取 jwtToken 并解析出 `jwt` 与 `secret`
- **双开登录**（需 ROOT）：从官方cg App 的私有目录同步登录凭据
- **手动工作台**：
  - 获取 SportId → 等待运动时段 → 生成 `UploadJsonSports` → 提交 → 上传运动照片
  - **自定义公里数与路线**（内置多条校园标准 400m 跑道，也可自定义路线 ID）
  - **轨迹生成器**：两直道 + 两半圆的标准 400m 跑道几何；每圈周长精确归一化到 400m，逐圈独立内外偏移、真法向低频扰动、GPS 级噪声
  - 每一步均有弹窗确认与失败原因展示
- **运动记录**：历史记录列表、照片缩略图、详情查看
- **图片上传**：与官方一致的 `uploadpic` multipart 契约

### 技术要点

- **签名链**：请求签名通过 JNI 调用修补后的本地库（`cgapiEnrypt(secret, url)`），`sign` / `timestamp` 两个头由其输出解析；签名源为 `/api` 起的路径 + TreeMap 排序后的参数
- **请求头**：`cgAuthorization`(jwt)、`imei`(由 androidId 派生)、`v1`(安装期随机值的 MD5)、`client`(`{"root","heap","t"}` 用 secret 前 16 字节 AES-ECB 加密)、随机化 UA
- **上传体契约**：`UploadJsonSports` 与官方逐字段对齐——`sportImages` 提交时是 `"1"` 标记（服务端随后回填真实路径）、`randomPointStr` 必须是数组或缺省（字符串会导致服务端 500）、`jsonsports` 字段 AES-ECB 加密等
- **照片上传**：`uploadpic` multipart，文件 part 以文件名命名（`application/octet-stream`），先 `savesports` 建记录再传图，服务端自动重命名并绑定

### 构建
#### **目前只支持li ke体育场坐标数据** 
若想使用jinnan主体育场&balitai体育场的坐标数据，请发送email至**ny4tzbpe@anonaddy.me**付费获取。(十分便宜（6r）)
JDK 17 + Android SDK 35：

```bash
./gradlew assembleDebug
```

Release 签名：复制 `keystore.properties.example` 为 `keystore.properties` 并填入自己的签名配置；推送 `v*` tag 触发 GitHub Actions 自动构建。
### 构建好的apk软件
如果你不知道如何使用源代码，想要开箱即用的apk，给我发电子邮件 **ny4tzbpe@anonaddy.me** 。（你需要付费，但很便宜（6r））
### 学校配置

服务器与 CAS 地址默认，见：

- `app/src/main/java/com/hpophh/chuanggaosim/http.java`（`BASE_URL`）
- `app/src/main/java/com/hpophh/chuanggaosim/login.java`（`CALLBACK_PREFIX` / `LOGIN_URL`）
- `app/src/main/res/xml/network_security_config.xml`

### 项目结构

```
ChuangGaoSim/
├── app/src/main/java/com/hpophh/chuanggaosim/   # 主源码
│   ├── cg/                                # 与官方对齐的数据模型
│   └── net/crigh/...                      # 官方算法库的 JNI 桥接
├── app/src/main/assets/web/               # WebView 前端（工作台/记录/设置）
├── app/src/main/jniLibs/arm64-v8a/        # 修补后的签名 SO（arm64）
└── .github/workflows/                     # tag 触发的自动构建
```
### 来源致谢

基于 **某不愿提及姓名的开发者** 的思路与部分实现重构而来，感谢原作者。
同时感谢各类开源工具与 glm-5.3-flash 模型的帮助。

### Star History

<a href="https://www.star-history.com/?repos=hpophh%2FChuangGaoSim&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&legend=top-left" />
 </picture>
</a>

### 许可证

[MIT](LICENSE)
