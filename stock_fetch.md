# Stockify 股价数据抓取节点

本文件整理 Stockify 目前实际使用的台股、美股即时报价、历史价格与台湾加权指数数据来源。内容以程序码中的实作为准；若外部服务改变回应格式，需同步检查对应 parser。

## 1. 抓取流程总览

```text
即时报价
├─ 台股上市／上柜
│  ├─ 主要来源：TWSE MIS 批次报价
│  └─ 备援来源：Yahoo Finance 台股页面
├─ 台股兴柜
│  └─ Yahoo Finance 台股页面
└─ 美股
   ├─ 主要来源：Nasdaq quote API
   └─ 备援来源：Yahoo Finance chart API

历史价格
├─ 台股上市／上柜：TWSE STOCK_DAY，逐月抓取
├─ 台股兴柜：TPEx emerging historical，逐月抓取
└─ 美股：Nasdaq historical，单次抓完整区间
```

即时报价的来源顺序由设置控制：

- 台股默认 `TWSE`，可切换为 `Yahoo`。
- 美股默认 `Nasdaq`，可切换为 `Yahoo`。
- 主要来源失败时最多切换一次备援，不会在两个来源间无限重试。
- 共用的暂时性网络错误与 TLS 凭证错误处理由 `HttpRetry.kt` 负责。

主要入口是 [`RealtimeStockDataService.kt`](app/src/main/java/com/rsps1008/stockify/data/RealtimeStockDataService.kt)。

## 2. 台股即时报价：TWSE MIS

实作：[`TwseStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockInfoFetcher.kt)

### Endpoint

```text
GET https://mis.twse.com.tw/stock/api/getStockInfo.jsp
```

查询参数：

| 参数 | 说明 | 范例 |
|---|---|---|
| `ex_ch` | 一个或多个查询频道，以 `%7C` 分隔 | `tse_2330.tw%7Cotc_6547.tw` |
| `json` | 要求 JSON | `1` |
| `delay` | 报价延迟设置 | `0` |

实际 URL 范例：

```text
https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=tse_2330.tw%7Cotc_6547.tw&json=1&delay=0
```

### 频道规则

- 上市：`tse_<代号>.tw`
- 上柜：`otc_<代号>.tw`
- 不确定交易所时，同时查询 `tse_` 与 `otc_`。
- 每个 HTTP request 最多组 5 档股票。
- 同时最多 3 个 request。

### 主要解析字段

TWSE 回应的 `msgArray` 每一列使用以下字段：

| 字段 | 意义 | App 用途 |
|---|---|---|
| `c` | 股票代号 | 对应股票 |
| `z` | 成交价 | 优先使用的现价 |
| `a` | 卖价五档 | `z` 无效时取第一个有效价格 |
| `b` | 买价五档 | 前述价格仍无效时取第一个有效价格 |
| `y` | 昨收 | 计算涨跌与涨跌幅 |
| `u` | 涨停价 | 判断涨停 |
| `w` | 跌停价 | 判断跌停 |

若 `z` 为 `-` 或无法解析，会依序从 `a`、`b` 找第一个大于 0 的价格。

PowerShell 测试范例：

```powershell
$url = 'https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=tse_2330.tw&json=1&delay=0'
Invoke-RestMethod -Uri $url -Headers @{ 'User-Agent' = 'Mozilla/5.0' } |
    Select-Object -ExpandProperty msgArray
```

## 3. 台股即时报价：Yahoo Finance 备援

实作：[`YahooStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/YahooStockInfoFetcher.kt)

### Endpoint

```text
GET https://tw.stock.yahoo.com/quote/<股票代号>
```

范例：

```text
https://tw.stock.yahoo.com/quote/2330
```

这个 endpoint 回传 HTML，不是 JSON。App 使用 Jsoup 找到：

```text
section#qsp-overview-realtime-info ul
```

再从每个 `li` 的两个 `span` 建立字段，读取下列标签（Yahoo 台股页面以繁体显示，程序直接比对原文）：

- 「成交」或「收盘」：目前价格。
- 「昨收」或「前收」：前一交易日收盘价。

台股涨跌幅使用 `(现价 - 昨收) / 昨收 * 100` 计算。Yahoo 显示的千分位逗号会先移除，例如 `1,575` 会转成 `1575`。

### 特殊路由

台股兴柜不走 TWSE `tse_`／`otc_` endpoint，会直接使用这个 Yahoo 来源。这个规则在 `RealtimeStockDataService.fetchStockInfoForMarket()` 中处理。

PowerShell 测试范例：

```powershell
$url = 'https://tw.stock.yahoo.com/quote/2330'
(Invoke-WebRequest -Uri $url -Headers @{ 'User-Agent' = 'Mozilla/5.0' }).Content |
    Select-String -Pattern '成交|昨收' -AllMatches
```

## 4. 美股即时报价：Nasdaq quote API

实作：[`NasdaqStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/NasdaqStockInfoFetcher.kt)

### Endpoint

```text
GET https://api.nasdaq.com/api/quote/<代号>/info?assetclass=<类别>
```

`assetclass` 规则：

- 一般股票：`stocks`
- ETF：`etf`

范例：

```text
https://api.nasdaq.com/api/quote/AAPL/info?assetclass=stocks
https://api.nasdaq.com/api/quote/SPY/info?assetclass=etf
```

App 会带浏览器相关 headers，包括 `User-Agent`、`Accept`、`Referer` 与 `Origin`。

### 主要解析字段

数据路径：

```text
data.primaryData.lastSalePrice
data.primaryData.netChange
data.primaryData.percentageChange
```

解析时会移除 `$`、逗号与 `%`。美股没有台股式涨跌停判断，因此 `limitState` 固定为 `NONE`。

PowerShell 测试范例：

```powershell
$url = 'https://api.nasdaq.com/api/quote/AAPL/info?assetclass=stocks'
$headers = @{
    'User-Agent' = 'Mozilla/5.0'
    'Accept' = 'application/json, text/plain, */*'
    'Referer' = 'https://www.nasdaq.com/'
    'Origin' = 'https://www.nasdaq.com'
}
$body = Invoke-RestMethod -Uri $url -Headers $headers
$body.data.primaryData
```

## 5. 美股即时报价：Yahoo Finance chart 备援

实作：[`UsYahooStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/UsYahooStockInfoFetcher.kt)

### Endpoint

```text
GET https://query1.finance.yahoo.com/v8/finance/chart/<代号>?interval=1d&range=1d&includePrePost=false&events=div%2Csplits
```

范例：

```text
https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d&includePrePost=false&events=div%2Csplits
```

### 主要解析字段

数据路径：

```text
chart.result[0].meta.regularMarketPrice
chart.result[0].meta.chartPreviousClose
chart.result[0].meta.previousClose
chart.result[0].meta.regularMarketChange
chart.result[0].meta.regularMarketChangePercent
```

`chartPreviousClose` 优先于 `previousClose`。如果 API 没有直接提供涨跌或涨跌幅，App 会用现价与前收自行计算。

PowerShell 测试范例：

```powershell
$url = 'https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d&includePrePost=false&events=div%2Csplits'
Invoke-RestMethod -Uri $url -Headers @{ 'User-Agent' = 'Mozilla/5.0' } |
    Select-Object -ExpandProperty chart |
    Select-Object -ExpandProperty result
```

## 6. 台股历史价格：TWSE STOCK_DAY

实作：[`TwseStockHistoryService.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockHistoryService.kt)

上市与上柜股票每个月份各发出一次 request：

```text
GET https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=<YYYYMM01>&stockNo=<股票代号>
```

范例：

```text
https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=20260801&stockNo=2330
```

解析条件：

- `stat` 必须是 `OK`。
- 从 `data` 阵列逐列解析。
- `row[0]` 是民国日期，例如 `115/08/07`，转成 `2026-08-07`。
- `row[6]` 是收盘价，移除逗号后转成数字。

为避免被 TWSE 阻挡，月份 request 之间会延迟 500 ms。

PowerShell 测试范例：

```powershell
$url = 'https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=20260801&stockNo=2330'
Invoke-RestMethod -Uri $url -Headers @{ 'User-Agent' = 'Mozilla/5.0' } |
    Select-Object stat, data
```

## 7. 兴柜台股历史价格：TPEx

实作同为 [`TwseStockHistoryService.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockHistoryService.kt)，但兴柜股票会改用 TPEx API：

```text
GET https://www.tpex.org.tw/www/zh-tw/emerging/historical?type=Monthly&date=<YYYY%2FMM%2F01>&code=<股票代号>&response=json
```

范例：

```text
https://www.tpex.org.tw/www/zh-tw/emerging/historical?type=Monthly&date=2026%2F08%2F01&code=7777&response=json
```

解析条件：

- `stat` 必须是 `ok`，不分大小写。
- 从 `tables[0].data` 逐列解析。
- `row[0]` 是民国日期，转为西元日期。
- `row[5]` 是「成交均价」，作为历史图表价格。

## 8. 美股历史价格：Nasdaq historical API

实作同为 [`TwseStockHistoryService.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockHistoryService.kt)。美股缺少的图表月份会用一个 request 抓完整区间：

```text
GET https://api.nasdaq.com/api/quote/<代号>/historical?assetclass=<类别>&fromdate=<YYYY-MM-DD>&todate=<YYYY-MM-DD>&limit=400
```

范例：

```text
https://api.nasdaq.com/api/quote/AAPL/historical?assetclass=stocks&fromdate=2025-08-08&todate=2026-08-08&limit=400
```

ETF 使用 `assetclass=etf`。如果依股票主档的类型查不到数据，App 会再用另一个 asset class 尝试一次，成功后会自动修正数据库中的 `stockType`。

解析数据路径：

```text
data.tradesTable.rows[].date
data.tradesTable.rows[].close
```

日期格式由 `MM/DD/YYYY` 转成 `YYYY-MM-DD`；收盘价会移除 `$` 与千分位逗号。

PowerShell 测试范例：

```powershell
$url = 'https://api.nasdaq.com/api/quote/AAPL/historical?assetclass=stocks&fromdate=2025-08-08&todate=2026-08-08&limit=400'
$headers = @{
    'User-Agent' = 'Mozilla/5.0'
    'Accept' = 'application/json, text/plain, */*'
    'Referer' = 'https://www.nasdaq.com/'
    'Origin' = 'https://www.nasdaq.com'
}
$body = Invoke-RestMethod -Uri $url -Headers $headers
$body.data.tradesTable.rows
```

## 9. 台湾加权指数

这不是个股报价，但首页会抓取并显示，因此一并列在股价数据节点文件中。

实作：[`TaiwanWeightedIndexService.kt`](app/src/main/java/com/rsps1008/stockify/data/TaiwanWeightedIndexService.kt)

### TWSE 来源

```text
GET https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=tse_t00.tw&json=1&delay=0
```

解析 `msgArray[0]` 的 `n`、`z`、`y`、`o`、`h`、`l`、`tlong`，分别代表名称、现值、昨收、开盘、最高、最低与更新时间。

### Yahoo 备援

```text
GET https://query1.finance.yahoo.com/v8/finance/chart/%5ETWII?interval=1d&range=1d&includePrePost=false&events=div%2Csplits
```

解析 Yahoo `chart.result[0].meta` 的 `regularMarketPrice`、`chartPreviousClose`、`regularMarketOpen`、`regularMarketDayHigh`、`regularMarketDayLow`、`regularMarketTime`。

## 10. A 股（沪深京）：东方财富 + 腾讯备援

实作：[`CnStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/CnStockInfoFetcher.kt)、[`CnTencentStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/CnTencentStockInfoFetcher.kt)、[`CnMarketIndexService.kt`](app/src/main/java/com/rsps1008/stockify/data/CnMarketIndexService.kt)、[`StockDataFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/StockDataFetcher.kt)

### 即时报价（主来源，东方财富）

```text
GET https://push2.eastmoney.com/api/qt/ulist.np/get?secids=1.600000,0.000001&fields=f12,f13,f14,f2,f3,f4,f18,f15,f16,f17
```

- `secid` 前缀：沪市 `1.`，深市与北交所 `0.`。
- `f2` 最新价、`f3` 涨跌幅、`f4` 涨跌额、`f17` 昨收，皆为百分之一，需除以 100。
- 批次缺少某档时改用单档查询：`https://push2.eastmoney.com/api/qt/stock/get?secid=<secid>&fields=f43,f51,f52,f57,f58,f60,f169,f170`，其中 `f51`／`f52` 为涨停价／跌停价。

### 即时报价（腾讯备援，只尝试一次）

```text
GET https://qt.gtimg.cn/q=sh600000,sz000001
```

回传 `v_sh600000="..."` 文本行，字段以 `~` 分隔（编码 GBK）：字段 2 为代码、3 最新价、4 昨收、31 涨跌额、32 涨跌幅（%）、47 涨停价、48 跌停价。

### 历史日线（东方财富）

```text
GET https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.600000&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58&klt=101&fqt=1&beg=20240101&end=20500101
```

`klines` 每行为 `日期,开盘,收盘,最高,最低,成交量,成交额,振幅`，收盘价取第 3 栏；`fqt=1` 为前复权。

### 指数（东方财富）

```text
GET https://push2.eastmoney.com/api/qt/ulist.np/get?secids=1.000001,0.399001,0.399006&fields=f12,f14,f2,f3,f4
```

上证指数 `1.000001`、深证成指 `0.399001`、创业板指 `0.399006`，同样除以 100。

### 股票清单（东方财富）

```text
GET https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=100&po=1&np=1&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048&fields=f12,f14
```

以 `data.total` 为终止条件分页抓取，输出 `app/src/main/assets/cn_stocks.json`（名称存繁体，显示层再转简体）；可执行 `python scripts/update_cn_stock_list.py` 重新生成。

### 分红送配（东方财富）

```text
GET https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_SHAREBONUS_DET&columns=ALL&filter=(SECURITY_CODE%3D%22600000%22)&pageNumber=1&pageSize=10&sortColumns=REPORT_DATE&sortTypes=-1
```

`PRETAX_BONUS_RMB` 与 `BONUS_IT_RATIO` 是「每 10 股」数值，须除以 10 才是每股股利。

### 名称搜索（本机查无结果时才调用）

```text
GET https://searchapi.eastmoney.com/api/suggest/get?input=600000&type=14&token=D43BF722C8E33BDC906FB84D85E326E8&count=10
```

只接受 `Classify=AStock` 的结果，名称写入时存繁体。

## 11. 共用行为与限制

- 每个 fetcher 的 Ktor CIO request timeout 目前为 5 秒。
- 台股 TWSE 批次查询每批最多 5 档；TWSE、Nasdaq、Yahoo fetcher 各自有最多 3 个并行 request 的限制。
- 即时数据在 App 启动时会强制抓取一次；背景轮询依台湾／纽约交易时间分流。
- 手动刷新会绕过市场开盘限制。
- 历史价格会先查内存快取与 Room；成功抓取后写入 `stock_history_prices`，并依市场收盘时间限制图表最新日期。
- 美股历史价格目前固定使用 Nasdaq historical API，不会切换到 Yahoo 历史 API。
- `scripts/update_us_stock_list.py` 的股票清单下载不是即时报价来源。它只产生股票主档，不能取代上述报价 API。

## 12. 股票清单来源（非股价 API）

这些 endpoint 只用于建立股票主档，与即时价格抓取分开：

- 台股清单：`https://isin.twse.com.tw/isin/C_public.jsp?strMode=<mode>`，实作于 [`StockDataFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/StockDataFetcher.kt)。
- 美股 Nasdaq Trader 清单：`https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt` 与 `https://www.nasdaqtrader.com/dynamic/SymDir/otherlisted.txt`，App 设置页与 [`scripts/update_us_stock_list.py`](scripts/update_us_stock_list.py) 都使用这两个来源；会过滤测试商品与档尾 `File Creation Time` 记录。
- 旧版脚本仍保留 Finnhub 产生函式供比较，但 App 更新流程已不再使用 Finnhub，也不再要求 API key。

## 13. 相关程序文件

- 即时路由：[`RealtimeStockDataService.kt`](app/src/main/java/com/rsps1008/stockify/data/RealtimeStockDataService.kt)
- 台股即时：[`TwseStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockInfoFetcher.kt)
- 台股 Yahoo 备援：[`YahooStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/YahooStockInfoFetcher.kt)
- 美股 Nasdaq 即时：[`NasdaqStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/NasdaqStockInfoFetcher.kt)
- 美股 Yahoo 备援：[`UsYahooStockInfoFetcher.kt`](app/src/main/java/com/rsps1008/stockify/data/UsYahooStockInfoFetcher.kt)
- 历史价格：[`TwseStockHistoryService.kt`](app/src/main/java/com/rsps1008/stockify/data/TwseStockHistoryService.kt)
- 台湾加权：[`TaiwanWeightedIndexService.kt`](app/src/main/java/com/rsps1008/stockify/data/TaiwanWeightedIndexService.kt)
- 共用网络错误处理：[`HttpRetry.kt`](app/src/main/java/com/rsps1008/stockify/data/HttpRetry.kt)
