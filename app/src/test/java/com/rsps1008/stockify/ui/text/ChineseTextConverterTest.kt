package com.rsps1008.stockify.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChineseTextConverterTest {
    @Test
    fun `converts traditional characters`() {
        assertEquals("损益", ChineseTextConverter.convert("損益"))
        assertEquals("账户", ChineseTextConverter.convert("帳戶"))
        assertEquals("涨跌", ChineseTextConverter.convert("漲跌"))
    }

    @Test
    fun `rewrites taiwan vocabulary rather than only characters`() {
        assertEquals("设置", ChineseTextConverter.convert("設定"))
        assertEquals("导入", ChineseTextConverter.convert("匯入"))
        assertEquals("文件", ChineseTextConverter.convert("檔案"))
        assertEquals("默认值", ChineseTextConverter.convert("預設值"))
    }

    @Test
    fun `matches the longest phrase first`() {
        assertEquals("文件夹", ChineseTextConverter.convert("資料夾"))
        assertEquals("数据", ChineseTextConverter.convert("資料"))
    }

    @Test
    fun `rewrites mainland specific wording`() {
        assertEquals("项目", ChineseTextConverter.convert("專案"))
        assertEquals("设备", ChineseTextConverter.convert("裝置"))
        assertEquals("访问", ChineseTextConverter.convert("存取"))
        assertEquals("图像", ChineseTextConverter.convert("影像"))
        assertEquals("字符串", ChineseTextConverter.convert("字串"))
        assertEquals("哈希", ChineseTextConverter.convert("雜湊"))
        assertEquals("点击", ChineseTextConverter.convert("點選"))
        assertEquals("相册", ChineseTextConverter.convert("相簿"))
        assertEquals("刷新", ChineseTextConverter.convert("重新整理"))
        assertEquals("互联网", ChineseTextConverter.convert("網際網路"))
        assertEquals("源代码", ChineseTextConverter.convert("原始碼"))
        assertEquals("分发", ChineseTextConverter.convert("散佈"))
    }

    @Test
    fun `rewrites whole sentences instead of transliterating`() {
        assertEquals(
            "本项目 fork 自 rsps1008/Stockify，并沿用其 MIT 授权。",
            ChineseTextConverter.convert("本專案 fork 自 rsps1008/Stockify，並沿用其 MIT 授權。")
        )
        assertEquals(
            "隐私政策：数据保存在设备上，可通过云端备份导出。",
            ChineseTextConverter.convert("隱私政策：資料儲存在裝置上，可透過雲端備份匯出。")
        )
    }

    @Test
    fun `maps mainland wording back to traditional`() {
        assertEquals("專案", ChineseTextConverter.toTraditional("项目"))
        assertEquals("裝置", ChineseTextConverter.toTraditional("设备"))
    }

    @Test
    fun `keeps ascii and numbers untouched`() {
        assertEquals("台股 2330 上涨 5.2%", ChineseTextConverter.convert("台股 2330 上漲 5.2%"))
        assertEquals("Google Drive 备份", ChineseTextConverter.convert("Google Drive 備份"))
    }

    @Test
    fun `is idempotent for simplified input`() {
        val once = ChineseTextConverter.convert("融資融券與損益")
        assertEquals(once, ChineseTextConverter.convert(once))
    }

    @Test
    fun `maps simplified search terms back to traditional`() {
        assertEquals("台積電", ChineseTextConverter.toTraditional("台积电"))
        assertEquals("貝特利", ChineseTextConverter.toTraditional("贝特利"))
        assertEquals("買進", ChineseTextConverter.toTraditional("买进"))
        assertEquals("設定", ChineseTextConverter.toTraditional("设置"))
    }

    @Test
    fun `keeps traditional search terms usable`() {
        assertEquals("台積電", ChineseTextConverter.toTraditional("台積電"))
        assertEquals("2330", ChineseTextConverter.toTraditional("2330"))
    }
}

class DisplayLanguageTest {
    @Test
    fun `traditional regions keep traditional chinese`() {
        assertFalse(DisplayLanguage.isSimplified(Locale.forLanguageTag("zh-TW")))
        assertFalse(DisplayLanguage.isSimplified(Locale.forLanguageTag("zh-HK")))
        assertFalse(DisplayLanguage.isSimplified(Locale.forLanguageTag("zh-Hant-TW")))
    }

    @Test
    fun `mainland chinese and other languages use simplified chinese`() {
        assertTrue(DisplayLanguage.isSimplified(Locale.forLanguageTag("zh-CN")))
        assertTrue(DisplayLanguage.isSimplified(Locale.forLanguageTag("zh-Hans")))
        assertTrue(DisplayLanguage.isSimplified(Locale.forLanguageTag("en-US")))
    }
}
