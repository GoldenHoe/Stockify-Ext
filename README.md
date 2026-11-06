# 韭菜记账本[扩展]（Stockify-Ext）

> 本项目是 [rsps1008/Stockify](https://github.com/rsps1008/Stockify) 的衍生版本（fork），基于上游 **1.7.3（versionCode 1730）** 修改。

[上游项目](https://github.com/rsps1008/Stockify) · [本仓库](https://github.com/GoldenHoe/Stockify-Ext) · [许可证](./LICENSE)

## 一、与上游项目的关系

- 本仓库 fork 自 [rsps1008/Stockify](https://github.com/rsps1008/Stockify)，上游著作权归原作者 **rsps1008** 所有。
- 上游采用 [MIT License](./LICENSE)，本仓库**沿用同一许可证**，完整保留原始版权与许可声明（见「授权与隐私」）。
- 上游的 Google Play 与官网链接指的是**上游项目**，与本仓库的改动无关；本仓库目前只以源码形式提供，没有发布独立安装包。
- 本文档已本地化为简体中文；末尾附有**未经改动的上游 README 原文**（繁体，可折叠），便于对照。

## 二、⚠️ Vibe Coding 声明

本仓库相对上游的**所有改动均由 Vibe Coding（AI 辅助编程）完成**：

- 代码经过编译、单元测试与真机运行验证，但**没有经过完整的人工代码审查**；
- 可能存在**逻辑错误、边界条件遗漏、金额计算偏差**；
- 涉及资金记录时请**自行核对**，并在升级前**先备份数据**（本地备份或 Google Drive 备份均可）；
- 行情、分红等数据来自第三方公开接口，接口随时可能变更或失效，本仓库不保证数据准确；
- 本项目**不构成任何投资建议**，据此操作风险自负。

发现问题欢迎提交 Issue 或 Pull Request。

## 三、本分支做了什么

### 3.1 新增

**A 股（沪深京）完整支持**

| 模块 | 主要文件 | 说明 |
| --- | --- | --- |
| 行情抓取 | `data/CnStockInfoFetcher.kt` | 东方财富 `push2` / `push2his`，批量缺档时以单档查询补齐，并带回 `f51`／`f52` 涨跌停价 |
| 备用来源 | `data/CnTencentStockInfoFetcher.kt` | 东方财富整体失败时改用腾讯行情，只 fallback 一次 |
| 代码补全 | `data/CnStockSuggestService.kt` | 本机查不到时才请求东方财富 suggest，只收 `Classify=AStock` |
| 交易规则 | `data/CnTradingRules.kt` | 100 股为 1 手、佣金 0.025%（最低 ¥5）、过户费 0.001%、印花税 0.05%（仅卖出）、T+1、涨跌停 ±10%／±20%／±30%，ST ±5% |
| 交易日历 | `data/CnMarketCalendar.kt` | 按国务院节假日公告判断开市，周末补班日不开盘 |
| 指数摘要 | `data/CnMarketIndexService.kt`、`ui/screens/CnMarketIndexStrip.kt` | 首页显示上证指数、深证成指、创业板指 |
| 分红送股 | `data/dividend/CnDividendRepository.kt` | 东方财富 `RPT_SHAREBONUS_DET`，`每 10 股` 口径换算为每股 |
| A 股清单 | `assets/cn_stocks.json` | 内置 5,708 只（沪 2,358 / 深 2,992 / 北交所 358），支持一键更新 |
| 汇率换算 | `data/CurrencyConversionSupport.kt` | 合并模式按 CNY/TWD、USD/TWD 折算台币 |

**繁简双语文案（显示层转换）**

- `ui/text/ChineseTextConverter.kt`、`DisplayLanguage.kt`、`LocalizedText.kt`、`AppLanguage.kt`；
- 资料库仍以繁体存储，**简体只在显示时转换**，不写回数据库；
- 语言选项：跟随系统 / 简体中文 / 繁体中文（AppCompat per-app locale）；
- 品牌字标提供简体、繁体、浅色、深色四套资源。

**其他新增**

- `scripts/`：繁简映射表生成、A 股清单更新、品牌图生成、截图裁剪与转换预览脚本；
- 6 个 A 股相关单元测试（行情解析、交易规则、日历与指数、分红、代码建议、腾讯解析）。

### 3.2 修改

- **首页**：新增「台股 / 美股 / A 股」三颗 Chip 切换市场；A 股模式显示三大指数摘要；表头排序改为固定图标槽，切换排序列不再改变版式；未实现／已实现标题改为圆角；列表底部留出 FAB 空间。
- **当日损益**：当天买入的股数以实际买入成本计算，其余股数以昨收计算；数值保留正负号，0 值使用中性色。
- **新增交易**：按 Material 3 重做表单——账户选择改为 `ExposedDropdownMenuBox`、日期改为可点击的只读输入框、股数加减改为标准 `IconButton`、手续费与支出金额改为标准输入框、备注改为多行。
- **设置**：数据来源国内化（台股、美股保留下拉选项，A 股显示固定来源说明）、语言与文字大小、A 股费率可调。
- **数据管理**：PDF 导入教学由 4 张图片改为文字步骤；新增「A 股清单更新」入口。
- **全局 TopAppBar**：每个页面都以 `StockifyTopBar` 标明功能，次页带返回与页面专属动作，无障碍文案统一为中文。
- **底部导航**：改为标准 M3 `NavigationBar`（持股 / 交易 / 数据 / 设置），新增交易走 Scaffold 的浮动按钮。
- **配息配股页**：修正 `0.42000000000000004` 之类的浮点噪声、两列对齐与「最近一次」陈旧方案标示。
- **主题**：Typography、Shapes、Colors 按 Material 3 规范化。

### 3.3 重构

- `StockMarket` / `StockExchange`：加入 `CN` 与沪市／深市／北交所，并补上 A 股代码与市场的校验。
- `MarketAmountFormatting`：按币种决定小数位（人民币、美元 2 位，台币整数）。
- 行情抓取分层：每个市场「主来源 + 一次 fallback」，统一并发上限与重试策略，异常不再让协程崩溃。
- `HomeDisplayMode`：新增 `CN`；`COMBINED` 仅用于兼容旧设置。
- 主题层抽出 `StockifyShapes`、`StockColors`，所有圆角与涨跌色统一取自 token。
- 图片资源整理：删除 8 个自绘导航／资产图标与 4 张 PDF 教学图，改用 Material Icons 与文字步骤。

### 3.4 移除

- `ic_nav_*`、`ic_asset_*`、`ic_dividend_info` 等自绘矢量图（改用 Material Icons）；
- `pdf_import_tutorial_1..4.jpg`（改为文字步骤）。

## 四、功能（本地化说明）

### 集保 E 存折 PDF 导入

把「集保 E 存折」导出的库存 PDF 导入，输入密码、预览确认后即可建立持股清单，省去逐笔输入股票与股数的时间。导入时以当前股价建立买进快照、手续费为零，不会还原原始成本或历史交易。本分支只把导入教学从 4 张图片改成文字步骤，导入与解密逻辑未作改动。

### 台股、美股、A 股与交易记账

整合多个证券账户，切换台股、美股、A 股或合并查看。买进、卖出、配息、配股，以及分割、减资等公司行动都会保留完整纪录，支持交易备注。台股提供融资融券记账与利息估算；A 股以 100 股为 1 手，并按 T+1 规则计算可卖股数。

搭配实时报价，自动计算持股成本、市值与已实现／未实现损益。合并查看会把美股按 USD/TWD、A 股按 CNY/TWD 换算为台币，个股仍保持原始币别；持股列表可按字段排序，未套用字段排序时可长按拖动。

### 投资表现

通过历史损益曲线追踪长期变化，再用报酬日历回顾每日与每月成果。提供剩余部位成本、历来投入成本与年化报酬率（XIRR）三种计算方式，也可以选择把部分卖出列入已实现，或从损益与报酬中排除股息收入。

### 资产全景

用圆饼图查看台股、美股、A 股与个别持股的配置比例，点击切片或图例即可查看标的。加入银行存款与贷款后，股票以外的资产与负债一并纳入，掌握整体净资产。

### 数据保存与备份

支持 CSV 导入导出、本机与 Google Drive 备份还原。持股云端备份包含交易、证券账户与持股排序，还可启用离开 App 后的自动云端备份；银行存款与贷款有独立的备份／还原，不会混进持股交易 CSV。

### 个性化

可调整主题与字体大小，并设置数字密码锁与生物辨识解锁。界面默认简体中文，可在设置中切换为繁体中文或跟随系统。

## 五、数据来源

| 市场 | 实时报价 | 历史价格 | 股票清单 |
| --- | --- | --- | --- |
| 台股 | TWSE / Yahoo | TWSE、TPEx 月资料 | TWSE |
| 美股 | Nasdaq / Yahoo | Yahoo | Nasdaq Trader |
| A 股 | 东方财富（腾讯备用） | 东方财富 | 东方财富 `clist` |

- 交易日历：台股依 TWSE 休市日历；A 股依国务院节假日公告。
- 汇率：USD/TWD、CNY/TWD 启动时抓取一次，之后每 24 小时更新并保留最后可用快取。
- 分红：台股／美股走 Yahoo，A 股走东方财富 `RPT_SHAREBONUS_DET`。

## 六、技术架构

- **语言与 UI：** Kotlin、Jetpack Compose、Material 3
- **架构：** MVVM，按 `data/`、`ui/viewmodel/`、`ui/screens/` 分层
- **本地数据：** Room 保存股票、账户、交易与历史价格；DataStore 保存偏好、快取、排序、存款与贷款
- **网络：** Ktor HTTP Client、Jsoup
- **备份与排程：** Google Identity 授权、Google Drive API、WorkManager
- **PDF：** PDFBox Android（集保 E 存折 PDF 解密与文字抽取）
- **Android：** 最低 API 26，`compileSdk` / `targetSdk` 37
- **版本：** 1.7.3（versionCode 1730），以 `app/build.gradle.kts` 为准

## 七、编译与运行

需求：Android Studio、JDK 21、Android SDK API 37；依赖版本以 `gradle/libs.versions.toml` 与 Gradle wrapper 为准。

Windows PowerShell：

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat test
./gradlew.bat assembleDebug
./gradlew.bat installDebug
```

`installDebug` 需要已连接的 Android 装置或模拟器。编译与单元测试通过，不等于实机功能已验证。

## 八、已知限制

- 全部改动由 AI 辅助完成，详见第二节的声明。
- A 股交易日历目前只收录已公告年度（2026），缺失年份只排除周末。
- 涨跌停价优先采用行情来源返回的值，来源未提供时才按板别比例估算，可能与交易所实际价格有差异。
- 融资融券仅支持台股，且为本机估算，不是券商对账数据。
- 本仓库未发布独立安装包，上游的 Google Play 版本不包含上述改动。

## 九、授权与隐私

本仓库沿用上游的 [MIT License](./LICENSE)，原始版权声明为：

```
MIT License

Copyright (c) 2026 rsps1008
```

依据 MIT 条款，使用、复制、修改、合并、发布与散布本衍生作品时，**必须保留上述版权声明与许可声明**。本仓库的改动同样以 MIT 授权释出。

数据处理方式请参阅上游的[隐私权政策](https://stockify.rsps1008.ru/privacy-policy/)，使用规范请参阅[公开服务条款](https://stockify.rsps1008.ru/terms-of-service/)。

---

## 附录：未改动的上游 README 原文

<details>
<summary>点击展开 upstream README 原文（rsps1008/Stockify，繁体中文，未作任何修改）</summary>

# 韭菜記帳本（Stockify）

**持股、損益與資產，一眼掌握。**

韭菜記帳本是一款開源、免費的 Android 股票交易記帳與持股管理 App。集中管理台股、美股與多個證券帳戶，從日常買賣、股息收入到長期報酬，把分散的投資資料整理成清楚的成果。

[Google Play 下載](https://play.google.com/store/apps/details?id=com.rsps1008.stockify) · [官方網站](https://stockify.rsps1008.ru/) · [隱私權政策](https://stockify.rsps1008.ru/privacy-policy/)

## 前言

這是一個為了紀念與取代舊版 `com.bigmiracle.stockify` 而生的專案。

過去，`com.bigmiracle.stockify` 是一款出色的免費股票記帳軟體，許多人都曾依賴它來管理自己的投資。然而，最近它不僅開始收費，還導入了 Play Integrity 機制，使得在電腦模擬器上使用的用戶無法繼續順利記帳。

為了重現那段美好的免費使用時光，並確保投資管理者在任何裝置上都能自由操作，我借助 Vibe-Coding 的力量，從頭打造了這個全新的韭菜記帳本。它開源、免費，且永遠歡迎社群共同改進。

## 功能亮點

### 集保 E 存摺匯入，快速開始

將「集保 E 存摺」匯出的庫存 PDF 匯入，輸入密碼並預覽確認後，即可建立持股清單，省去逐檔輸入股票與股數的時間。App 內提供 PDF 匯出教學，並支援新增或替代匯入。

匯入時以目前取得的股價建立買進快照，手續費為零；這是建立持股的起點，不會還原原始買進成本或歷史交易。

### 台股、美股與交易，集中整理

整合多個證券帳戶，切換台股、美股或合併檢視。買進、賣出、配息、配股，以及分割、減資等公司行動，都能保留完整紀錄，並支援交易備註。台股另提供融資融券記帳與利息估算。

搭配即時報價，自動計算持股成本、市值與已實現、未實現損益。合併檢視將美股金額依 USD/TWD 匯率換算為台幣，個股仍維持原始幣別；持股清單可依欄位排序，或在未套用欄位排序時長按拖曳。

### 投資表現，不只看當下

透過歷史損益曲線追蹤長期變化，再用報酬日曆回顧每日與每月成果。提供剩餘部位成本、歷來投入成本與年化報酬率（XIRR）三種計算方式，也可選擇將部分賣出列入已實現，或從損益與報酬中排除股息收入。

配息記帳可帶入最近一次配息資料，方便整理股息收入；首頁亦提供台灣加權指數摘要，個股頁可快速查看 Yahoo Finance。

### 從持股配置，看到整體資產

以圓餅圖查看台股、美股與個別持股的配置比例，點擊切片或圖例即可查看標的。加入銀行存款與貸款後，將股票以外的資產及負債一起納入，掌握整體淨資產。

### 投資紀錄，長期安心保存

支援 CSV 匯入匯出、本機及 Google Drive 備份還原，方便保存資料或換機接續記帳。持股雲端備份包含交易、證券帳戶與持股排序，也可啟用離開 App 後的自動雲端備份。

銀行存款與貸款提供獨立的本機與 Google Drive 備份／還原，不混入持股交易 CSV 或持股雲端備份。

### 依自己的習慣使用

可調整主題與文字大小，並設定數字密碼鎖及生物辨識快速解鎖。主要資料保存在裝置本機，是否使用 App 內的 Google Drive 備份由您決定；Android 系統備份與裝置移轉則依系統設定運作。

## 功能截圖

以下使用 Google Play 截圖定稿，畫面中的投資數據為示範資料。

| 持股損益 | 台美股管理 | 報酬日曆 | 資產總覽 |
| :---: | :---: | :---: | :---: |
| ![持股損益與歷史走勢](./screenshots/google-play/01-持股損益.png) | ![台股與美股持股管理](./screenshots/google-play/02-台美股管理.png) | ![每日損益與每月報酬日曆](./screenshots/google-play/03-報酬日曆.png) | ![持股、存款與貸款資產配置](./screenshots/google-play/04-資產總覽.png) |

| 交易記帳 | 配息紀錄 | 報酬率設定 | 備份還原 |
| :---: | :---: | :---: | :---: |
| ![買賣與公司行動交易記帳](./screenshots/google-play/05-交易記帳.png) | ![帶入最近一次配息並記錄股息](./screenshots/google-play/06-配息紀錄.png) | ![損益模式與三種報酬率設定](./screenshots/google-play/07-報酬率設定.png) | ![本機、Google Drive 與自動備份](./screenshots/google-play/08-備份還原.png) |

## 資料與使用說明

- 台股報價使用 TWSE／Yahoo，美股使用 Nasdaq／Yahoo；依市場時區、交易時段與休市日曆更新，手動刷新可在休市時執行。
- 台股清單來自 TWSE，美股清單來自 Nasdaq Trader。啟動時依更新期限檢查，也可在設定中手動更新，無需額外提供 API key。
- 歷史圖表依交易時序處理分割、減資與股利，是否納入今天依市場當地收盤時間決定；報酬日曆沿用相同歷史計算結果。
- 融資融券屬本地記帳估算；行情與分析用於整理投資紀錄，並非券商對帳資料或投資建議。
- App 鎖不代表本機資料庫或匯出備份已加密，請妥善保管裝置與備份檔案。

## 技術架構

- **語言與 UI：** Kotlin、Jetpack Compose、Material 3
- **架構：** MVVM，依 `data/`、`ui/viewmodel/`、`ui/screens/` 分層
- **本地資料：** Room 儲存股票、證券帳戶、交易與歷史價格；DataStore 儲存偏好、快取、排序、存款與貸款
- **網路：** Ktor HTTP Client、Jsoup；行情與歷史資料使用 TWSE、TPEx、Yahoo Finance 與 Nasdaq，股票清單另使用 Nasdaq Trader
- **備份與排程：** Google Identity 授權、Google Drive API、WorkManager
- **PDF：** PDFBox Android，用於集保 E 存摺 PDF 解密與文字抽取
- **Android：** 最低 API 26，`compileSdk`／`targetSdk` 37
- **目前專案版本：** 1.7.2（versionCode 1721），以 `app/build.gradle.kts` 為準

## 編譯與執行

需求：Android Studio、JDK 21、Android SDK API 37。依賴版本以 `gradle/libs.versions.toml` 與 Gradle wrapper 為準。

Windows PowerShell：

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat test
./gradlew.bat assembleDebug
./gradlew.bat installDebug
```

`installDebug` 需要已連線的 Android 裝置或模擬器。編譯與單元測試不等同於實機功能驗證。

## 授權與隱私

本專案採用 [MIT License](./LICENSE)。使用、複製、修改、合併、發布與散布衍生作品時，請保留原始版權與授權聲明。

資料處理方式請參閱[隱私權政策](https://stockify.rsps1008.ru/privacy-policy/)，使用規範請參閱[公開服務條款](https://stockify.rsps1008.ru/terms-of-service/)。

本專案源自希望延續免費股票記帳工具的想法，歡迎提出 Issue 或 Pull Request 一起改進。

</details>
