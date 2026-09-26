# 诗鲸 · 离线重写版（Android 客户端）

原「诗鲸」App 已停止维护、源码不可得（详见上级目录 [README.md](../README.md)）。
本工程用其开源诗词数据库与从原 APK 恢复的配色、图标、小组件参数重写为**原生 Kotlin 应用**，
支持纯离线阅读。

## 技术参数

| 项目 | 取值 |
|---|---|
| 语言 / 界面 | Kotlin + XML 布局（ViewBinding） |
| minSdk | **28（Android 9）** |
| targetSdk / compileSdk | **36（Android 16）** |
| 架构 | 纯 Kotlin 无 native 库，**天然支持 64 位**（arm64-v8a、armeabi-v7a、x86_64） |
| 依赖 | 仅 AndroidX + Material，无第三方 SDK |
| 构建 | Gradle 8.14.5 + AGP 8.13.2 + Kotlin 2.2.20 + JDK 21 |

## 构建（全程在 GitHub Actions，本地无需 Android 工具链）

1. 把本目录作为仓库根推送到 GitHub：
   ```bash
   cd poetry-app
   git init && git add -A && git commit -m "init"
   git remote add origin git@github.com:<你的账号>/<仓库名>.git
   git push -u origin main
   ```
2. 推送后 Actions 自动运行 `.github/workflows/build.yml`。
3. 在仓库 **Actions → 最新一次运行 → Artifacts** 下载 `shijing-release-apk`。

也可以在 Actions 页面手动触发 `workflow_dispatch`，并填入数据源地址（见下节）。

构建产物：`app/build/outputs/apk/release/app-release.apk`，使用 debug 签名，可直接安装。
正式分发前请替换为自建 keystore（见 `app/build.gradle.kts` 的 `signingConfig`）。

## 数据源配置

`assets/local/` 内置 2.6 MB 数据，覆盖：

- 全部 **72,417 首**诗词的索引（列表、搜索离线可用）
- **热门 Top 5000** 首的完整正文（离线可读）
- **5,764 条**名句、3,154 位诗人索引
- 桌面小组件内容取自内置数据，**断网也能显示**

其余冷门诗词的正文（含译文、赏析、创作背景）放在本仓库的 `data/` 目录下，
共 34.4 MB，经 **jsDelivr** 分发按需拉取：

```
https://cdn.jsdelivr.net/gh/wenyinos/poetry-app@main/data
```

该地址已写入 `gradle.properties` 的 `poetry.dataBaseUrl`，构建时自动带上，
**无需额外配置**。分片按 `poetryId` 区间切分，客户端用 `id / 1000` 直接定位，
一次下载（单片约 0.4 MB）覆盖 1000 首，之后永久缓存。

需要换托管地址时（例如迁到 Cloudflare R2 或国内 OSS），二选一：

```bash
# 方式一：改 gradle.properties
poetry.dataBaseUrl=https://your-cdn.example.com/data

# 方式二：命令行 / workflow_dispatch 输入（临时覆盖，不落盘）
./gradlew assembleRelease -Ppoetry.dataBaseUrl=https://your-cdn.example.com/data
```

留空则只使用内置数据，冷门诗词会提示「尚未包含在内置数据中」。

### jsDelivr 说明

**jsDelivr 不需要任何凭证** —— 无需注册、无需 API key、无需 token。
它只要求仓库是 **public**（本仓库已满足），之后直接代理仓库内的文件。

| 事项 | 说明 |
|---|---|
| URL 格式 | `https://cdn.jsdelivr.net/gh/<用户>/<仓库>@<版本>/<路径>` |
| 版本标识 | `@main` 分支（缓存 12 小时）；`@v1.0` 标签或 commit 哈希（永久缓存） |
| 单文件上限 | 20 MB（本项目最大分片约 1 MB） |
| 强制刷新缓存 | 请求 `https://purge.jsdelivr.net/gh/wenyinos/poetry-app@main/data/<路径>` |

诗词数据是静态的、基本不会变动，因此 `@main` 的 12 小时缓存不构成问题。

## 当前实现范围

已完成：

- 首页：随机名句卡片（点击换句）+ 热门诗词列表
- 诗词详情：正文 + 译文/赏析/创作背景 + 标签，冷门诗词按分片拉取并缓存
- 收藏：本地存储 + WebDAV 双向同步，多设备共享且**不需要账号**
- 桌面小组件：随机诗词，3 小时自动更换，支持锁屏与自由缩放，点击进入详情

尚未实现（原版有、可后续增量补充）：搜索页、探索筛选、诗人页、分享截图、
夜间模式、微信/QQ/微博分享、多套小组件样式（浅色/深色/多行/整点/每日）。

部分界面素材（如小组件浅色/深色预览图）已随工程就位，实现时可直接引用。

## 收藏与 WebDAV 同步

原版的账号体系（LeanCloud 登录）随作者注销已失效，其真正作用是会员付费与
小程序跨端同步，对本项目无意义，因此**不设账号体系**。收藏改为
「本地优先 + WebDAV 同步」：

- 收藏写入本机 `filesDir/favorites.json`，断网可正常增删查看
- 配置一个 WebDAV 网盘后即可多设备同步
- 数据只存在用户自己的网盘，不经过任何第三方服务器

**同步策略是合并而非覆盖**：两台设备各自新增的收藏都会保留，同一首诗保留较早的
收藏时间。代价是在一台设备取消收藏后，另一台同步时可能让它"复活" —— 收藏属于
低价值易重建的数据，少丢数据的收益大于删除不同步的代价。确需单向覆盖时，
设置页提供「用本机覆盖云端」与「用云端覆盖本机」。

坚果云配置示例：

| 字段 | 值 |
|---|---|
| 服务器地址 | `https://dav.jianguoyun.com/dav/诗鲸` |
| 用户名 | 坚果云登录邮箱 |
| 密码 | **应用密码**（坚果云「安全选项」中生成，不是登录密码） |

> 目录需先在网盘中创建。客户端会在 PUT 遇到 409 时尝试自动 MKCOL 创建一次目录，
> 但部分服务端要求父目录已存在。

实现为零依赖：WebDAV 只用到 MKCOL / GET / PUT 三个动作，直接基于
`HttpURLConnection` 完成，未引入第三方 WebDAV 库。

## 工程结构

```
app/src/main/
├── assets/local/                    内置数据（gzip + JSONL）
├── java/me/javayhu/poetry/
│   ├── PoetryApp.kt                 Application，提供全局 Context
│   ├── data/
│   │   ├── Models.kt                数据模型
│   │   ├── LocalDataSource.kt       内置数据读取（逐行解析，常驻内存）
│   │   ├── RemoteDataSource.kt      云端分片下载与缓存
│   │   ├── PoemRepository.kt        取诗统一入口（本地 → 云端 → 索引兜底）
│   │   ├── FavoriteStore.kt         收藏的本地持久化
│   │   ├── WebDavClient.kt          WebDAV 客户端（零依赖）
│   │   └── FavoriteRepository.kt    收藏入口 + 同步策略
│   ├── ui/
│   │   ├── home/                    首页与列表
│   │   ├── poetry/                  诗词详情（含收藏按钮）
│   │   ├── favorite/                收藏列表
│   │   └── setting/                 WebDAV 同步设置
│   └── widget/                      桌面小组件
└── res/                             布局、配色（沿用原版鲸鱼蓝色阶）、图标、文案
```

## 数据来源与许可

诗词数据来自 [libpoetry/poetry-1](https://github.com/libpoetry/poetry-1)
（`javayhu/poetry` 的 fork，**GPL-3.0**），原数据 2017 年爬取自古诗文网。
二次分发请保留 LICENSE 并注明来源。原 App 的名称与图标版权归原作者所有。
