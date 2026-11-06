package com.rsps1008.stockify.ui.text

/**
 * Traditional/Simplified Chinese conversion used by [localized] and [Text].
 *
 * The app stores Traditional Chinese in Room column names and in CSV values, so
 * conversion happens at render time only and never rewrites persisted data.
 */
internal object ChineseTextConverter {
    private const val REVERSE_IDENTITY_CHARS = "台里后面只干"

    private const val TRADITIONAL_CHARS =
        "一丟並亂佔併來係倉個們側備傳債僅價儘償優儲兌兒內兩別刪則劃動務勢匯區協參問啟單嗎嘗噸嚴囉囑國圍圓" +
        "圖執報場塊壓壞壟夠夾奐奧嬰嬸學實寧寫寬將專尋對導屆屍層屬島峽嶺巒巖帥師帳帶幣幫幹庫廁廂廄廈廚廠廣" +
        "廬廳張強彌彎彙後徑從復徵徹恆恥悅悵悶悼惡惱惻愛愜愴愷態慘慚慣慮慶慷憂憊憐憑懇應懷懸懺戰戲戶拋挾捨" +
        "捲掃掛採揀換揮損搖搶摑摟摯摳摺摻撈撐撓撥撫撿擁擄擇擊擋擔據擠擡擬擴擺擾攏攔攙攜攝攢攤敗敵數斂斬斷" +
        "於時晉暫曆曬書會東桿條楊業極構槍樁標樞樣樸樹橋機橫檔檢櫃欄權欽歐歡歲歷殘殺殼毀氣氫氬氳決沒沖況洩" +
        "洪浹淒淚淨淺減測渾湊湯準溝滄滅滌滬滯滲滷滿漁漣漬漲漳漸漿潑潔潛潤澀澆澤濁濃濕濟濤濫濱濾瀉瀋瀏瀘瀟" +
        "瀨瀰瀾灑灘灣灤災為烏無煉煙煩熱熾燈燉燒燙燜營燦燭燴燼爭爺牆牘牽犧狀狹獄獅獎獨獲獵獷獸獺獻玀現琺環" +
        "璽甕產畝畢畫異當疇疊痙痠痾瘂瘡瘧瘮療癆癇癉癘癢癤癥癧癩癬癮癱發白皁皚皺盃盜盞盡監盤盧睏睜睞瞇瞞矚" +
        "矯確碼磚礎礙礦祿禍禎禪禮禱稅稈種稱穀積穩窩窮竄竅竇竊競筆筍箏節範築篩簡簽籃籌籍籤糧糾紀紂約紅紆紇" +
        "紈紉紋納紐紓純紕紖紗紙級紛紜紝紡紮細紱紲紳絃組絆絎結絕絛絝絞絡絢給絨絰統絳絹絺絽綁綃綆綈綉綏綑經" +
        "綜綠綢綣綬維綰綱網綴綵綸綹綺綻綽綾綿緄緇緊緋緒緔緗緘緙線緝緞締緡緣緦編緩緬緯緱緲練緻縈縉縊縋縐縑" +
        "縛縝縞縟縣縫縭縮縱縲縳縹總績繃繅繆繒織繕繚繞繡繩繪繫繭繳繹繼繽續纏纖纜缽罈罌罰罵罷羅羆羈羋義習翹" +
        "翻耀耆耬聯聰聲聳聵聶職聹聽肅脅脈脛脣脩脫腎腖腡腦腫腳腸膃膚膠膩膽膾膿臉臍臏臘臚臟臠臨臺與興舉舊艙" +
        "艤艦艫艱芻莊華菸萇萊萬葉葒著葦葷蒐蒔蒙蒞蒼蓀蓋蓮蓯蓴蓽蔔蔞蔣蔥蔦蔭蕁蕆蕎蕒蕓蕕蕘蕢蕩蕪蕭蕷薈薊薌" +
        "薑薔薟薦薩薺藍藎藏藐藜藝藥藪藶藹藺蘄蘆蘇蘊蘋蘚蘞蘢蘭蘺蘿虆處虛虜號虯蝕蟲蟻蠅蠍蠐蠑蠔蠟蠣蠱蠶蠻衊" +
        "術衚衛衝袞裊裏補裝裟製複褸褻襖襝襪襯襲覆見規覓視覘覡覦親覬覲覺覽覿觀觴觶觸觺訂訃計訊訌討訐訑訓訕" +
        "訖託記訛訝訟訣訪設許訴訶診註証詁詆詎詐詒詔評詘詛詞詠詡詢詣試詩詫詬詭詮詰話該詳詵詼詿誄誅誆誇誌認" +
        "誑誒誕誘誚語誠誡誣誤誥誦誨說説誰課誶誹誼誾調諂諄談諉請諍諏諑諒論諗諛諜諝諞諢諤諦諧諫諭諮諳諶諷諸" +
        "諺諼諾謀謁謂謄謅謊謎謐謔謖謗謙謚講謝謠謨謫謬謳謹謾證譎譏譖識譙譚譜警譫譯議譴護譽讀變讎讒讓讕讖讚" +
        "豈豎豐豔豬貍貓貝貞負財貢貧貨販貪貫責貯貰貲貳貴貶買貸貺費貼貽貿賀賁賂賃賄賅資賈賉賊賑賒賓賕賙賚賜" +
        "賞賠賡賢賣賤賦賧質賫賬賭賴賵賺賻購賽贄贅贈贊贍贏贐贓贔贖贗贛赤赬趕趙趨趲跡踐踫蹤躂躉躊躋躍躑躓躕" +
        "躚躡躪軀車軋軌軍軒軔軟軫軸軻軼軾較輅載輊輒輓輔輕輛輜輝輟輥輦輩輪輯輸輻輾輿轄轅轆轉轍轎轟轡轢轤辦" +
        "辭辯農迴逕這連週進遊運過達違遙遜遞遠適遲遷選遺遼邁還邇邊邏郵鄒鄔鄖鄴鄶醬釀釁釋重野量釐金釘針釣釧" +
        "釩釵鈉鈍鈎鈔鈕鈣鈦鈧鈴鈷鉀鉅鉑鉗鉚鉛鉞鉤鉦鉬銀銅銑銓銖銜銥銬銲銳銷銹鋁鋃鋅鋇鋤鋦鋨鋪鋮鋯鋰鋸鋼錄" +
        "錐錘錚錛錠錡錢錦錫錮錯錳錶鍊鍋鍍鍔鍘鍥鍬鍰鍵鍾鎂鎊鎔鎖鎘鎢鎣鎦鎧鎬鎮鎰鎳鎵鏃鏈鏗鏘鏜鏝鏞鏟鏡鏢鏤" +
        "鏨鐐鐓鐔鐘鐙鐠鐫鐮鐵鐶鐸鐺鑄鑊鑌鑑鑒鑠鑣鑤鑥鑭鑰鑲鑷鑼鑽鑾鑿長門閂閃閆閈閉開閌閎閏閑間閔閘閡閣閥" +
        "閨閩閫閬閭閱閹閻閾閿闈闊闋闌闐闔闕闖關闞闡闢闤闥陘陝陣陰陳陸陽隄隉隊階際隨險隱隴隸隻雋雖雙雛雜雞" +
        "離難雲電霧霽靂靄靈靚靜靦靨鞏鞝鞦鞽韁韃韆韋韌韓韙韜韞韻響頁頂頃項順須頊頌頎頏預頑頒頓頗領頡頤頦頭" +
        "頰頷頸頹頻顆題額顎顏顓願顙顛類顢顥顧顫顯顰顱顳風颯颱颳颶颼飄飆飛飢飩飪飯飲飴飼飽飾餃餅餉養餌餐餒" +
        "餓餘餚餛餞餡館餵餿饃饅饈饉饋饌饑饒饗饜饞馬馭馮馱馳馴駁駐駑駒駕駘駛駝駟駭駱駿騁騅騍騎騏騖騙騫騮騰" +
        "騷驁驃驅驍驕驗驚驛驟驢驥驪骯髏髒髓體髕髖鬆鬍鬚鬢鬥鬧鬩鬮鬱魎魘魚魯魷鮑鮒鮫鮭鮮鯉鯊鯖鯛鯨鯽鰓鰭鰲" +
        "鰻鱈鱉鱒鱗鱷鱸鳥鳩鳳鳴鴉鴕鴛鴦鴨鴻鴿鵑鵝鵪鵬鵲鶉鶩鶯鶴鷗鷲鷹鷺鸚鸞鹵鹼鹽麗麥麩黃黌點黨黲黴黷黽黿" +
        "鼉鼴齊齋齎齏齒齔齙齜齟齠齡齣齦齪齬齲齷龍龔龕龜"

    private const val SIMPLIFIED_CHARS =
        "一丢并乱占并来系仓个们侧备传债仅价尽偿优储兑儿内两别删则划动务势汇区协参问启单吗尝吨严啰嘱国围圆" +
        "图执报场块压坏垄够夹奂奥婴婶学实宁写宽将专寻对导届尸层属岛峡岭峦岩帅师账带币帮干库厕厢厩厦厨厂广" +
        "庐厅张强弥弯汇后径从复征彻恒耻悦怅闷悼恶恼恻爱惬怆恺态惨惭惯虑庆慷忧惫怜凭恳应怀悬忏战戏户抛挟舍" +
        "卷扫挂采拣换挥损摇抢掴搂挚抠折掺捞撑挠拨抚捡拥掳择击挡担据挤抬拟扩摆扰拢拦搀携摄攒摊败敌数敛斩断" +
        "于时晋暂历晒书会东杆条杨业极构枪桩标枢样朴树桥机横档检柜栏权钦欧欢岁历残杀壳毁气氢氩氲决没冲况泄" +
        "洪浃凄泪净浅减测浑凑汤准沟沧灭涤沪滞渗卤满渔涟渍涨漳渐浆泼洁潜润涩浇泽浊浓湿济涛滥滨滤泻沈浏泸潇" +
        "濑弥澜洒滩湾滦灾为乌无炼烟烦热炽灯炖烧烫焖营灿烛烩烬争爷墙牍牵牺状狭狱狮奖独获猎犷兽獭献猡现珐环" +
        "玺瓮产亩毕画异当畴叠痉酸疴痖疮疟瘆疗痨痫瘅疠痒疖症疬癞癣瘾瘫发白皂皑皱杯盗盏尽监盘卢困睁睐眯瞒瞩" +
        "矫确码砖础碍矿禄祸祯禅礼祷税秆种称谷积稳窝穷窜窍窦窃竞笔笋筝节范筑筛简签篮筹籍签粮纠纪纣约红纡纥" +
        "纨纫纹纳纽纾纯纰纼纱纸级纷纭纴纺扎细绂绁绅弦组绊绗结绝绦绔绞络绚给绒绖统绛绢絺絽绑绡绠绨绣绥捆经" +
        "综绿绸绻绶维绾纲网缀彩纶绺绮绽绰绫绵绲缁紧绯绪绱缃缄缂线缉缎缔缗缘缌编缓缅纬缑缈练致萦缙缢缒绉缣" +
        "缚缜缟缛县缝缡缩纵缧缚缥总绩绷缫缪缯织缮缭绕绣绳绘系茧缴绎继缤续缠纤缆钵坛罂罚骂罢罗罴羁芈义习翘" +
        "翻耀耆耧联聪声耸聩聂职聍听肃胁脉胫唇修脱肾胨脶脑肿脚肠腽肤胶腻胆脍脓脸脐膑腊胪脏脔临台与兴举旧舱" +
        "舣舰舻艰刍庄华烟苌莱万叶荭着苇荤搜莳蒙莅苍荪盖莲苁莼荜卜蒌蒋葱茑荫荨蒇荞荬芸莸荛蒉荡芜萧蓣荟蓟芗" +
        "姜蔷莶荐萨荠蓝荩藏藐藜艺药薮苈蔼蔺蕲芦苏蕴苹藓蔹茏兰蓠萝蔂处虚虏号虬蚀虫蚁蝇蝎蛴蝾蚝蜡蛎蛊蚕蛮蔑" +
        "术胡卫冲衮袅里补装裟制复褛亵袄裣袜衬袭覆见规觅视觇觋觎亲觊觐觉览觌观觞觯触觺订讣计讯讧讨讦訑训讪" +
        "讫托记讹讶讼诀访设许诉诃诊注证诂诋讵诈诒诏评诎诅词咏诩询诣试诗诧诟诡诠诘话该详诜诙诖诔诛诓夸志认" +
        "诳诶诞诱诮语诚诫诬误诰诵诲说说谁课谇诽谊訚调谄谆谈诿请诤诹诼谅论谂谀谍谞谝诨谔谛谐谏谕咨谙谌讽诸" +
        "谚谖诺谋谒谓誊诌谎谜谧谑谡谤谦谥讲谢谣谟谪谬讴谨谩证谲讥谮识谯谭谱警谵译议谴护誉读变雠谗让谰谶赞" +
        "岂竖丰艳猪狸猫贝贞负财贡贫货贩贪贯责贮贳赀贰贵贬买贷贶费贴贻贸贺贲赂赁贿赅资贾恤贼赈赊宾赇赒赉赐" +
        "赏赔赓贤卖贱赋赕质赍账赌赖赗赚赙购赛贽赘赠赞赡赢赆赃赑赎赝赣赤赪赶赵趋趱迹践碰踪跶趸踌跻跃踯踬蹰" +
        "跹蹑躏躯车轧轨军轩轫软轸轴轲轶轼较辂载轾辄挽辅轻辆辎辉辍辊辇辈轮辑输辐辗舆辖辕辘转辙轿轰辔轹轳办" +
        "辞辩农回迳这连周进游运过达违遥逊递远适迟迁选遗辽迈还迩边逻邮邹邬郧邺郐酱酿衅释重野量厘金钉针钓钏" +
        "钒钗钠钝钩钞钮钙钛钪铃钴钾钜铂钳铆铅钺钩钲钼银铜铣铨铢衔铱铐焊锐销锈铝锒锌钡锄锔锇铺铖锆锂锯钢录" +
        "锥锤铮锛锭锜钱锦锡锢错锰表炼锅镀锷铡锲锹锾键钟镁镑熔锁镉钨蓥镏铠镐镇镒镍镓镞链铿锵镗镘镛铲镜镖镂" +
        "錾镣镦镡钟镫镨镌镰铁镮铎铛铸镬镔鉴鉴铄镳刨镥镧钥镶镊锣钻銮凿长门闩闪闫闬闭开闶闳闰闲间闵闸阂阁阀" +
        "闺闽阃阆闾阅阉阎阈阌闱阔阕阑阗阖阙闯关阚阐辟阛闼陉陕阵阴陈陆阳堤陧队阶际随险隐陇隶只隽虽双雏杂鸡" +
        "离难云电雾霁雳霭灵靓静腼靥巩绱秋鞒缰鞑千韦韧韩韪韬韫韵响页顶顷项顺须顼颂颀颃预顽颁顿颇领颉颐颏头" +
        "颊颔颈颓频颗题额颚颜颛愿颡颠类颟颢顾颤显颦颅颞风飒台刮飓飕飘飙飞饥饨饪饭饮饴饲饱饰饺饼饷养饵餐馁" +
        "饿余肴馄饯馅馆喂馊馍馒馐馑馈馔饥饶飨餍馋马驭冯驮驰驯驳驻驽驹驾骀驶驼驷骇骆骏骋骓骒骑骐骛骗骞骝腾" +
        "骚骜骠驱骁骄验惊驿骤驴骥骊肮髅脏髓体髌髋松胡须鬓斗闹阋阄郁魉魇鱼鲁鱿鲍鲋鲛鲑鲜鲤鲨鲭鲷鲸鲫鳃鳍鳌" +
        "鳗鳕鳖鳟鳞鳄鲈鸟鸠凤鸣鸦鸵鸳鸯鸭鸿鸽鹃鹅鹌鹏鹊鹑鹜莺鹤鸥鹫鹰鹭鹦鸾卤碱盐丽麦麸黄黉点党黪霉黩黾鼋" +
        "鼍鼹齐斋赍齑齿龀龅龇龃龆龄出龈龊龉龋龌龙龚龛龟"

    private val charMap: Map<Char, Char> by lazy {
        val map = HashMap<Char, Char>(TRADITIONAL_CHARS.length * 2)
        TRADITIONAL_CHARS.forEachIndexed { index, ch ->
            map[ch] = SIMPLIFIED_CHARS[index]
        }
        map
    }

    private val phrases: List<Pair<String, String>> = listOf(
        "資料夾" to "文件夹",
        "工作列" to "任务栏",
        "使用者" to "用户",
        "伺服器" to "服务器",
        "重新整理" to "刷新",
        "專案" to "项目",
        "裝置" to "设备",
        "存取" to "访问",
        "影像" to "图像",
        "字串" to "字符串",
        "雜湊" to "哈希",
        "點選" to "点击",
        "相簿" to "相册",
        "記憶體" to "内存",
        "預設值" to "默认值",
        "資訊" to "信息",
        "訊息" to "消息",
        "設定" to "设置",
        "預設" to "默认",
        "匯入" to "导入",
        "匯出" to "导出",
        "檔案" to "文件",
        "支援" to "支持",
        "登入" to "登录",
        "登出" to "退出登录",
        "登錄" to "登录",
        "儲存" to "保存",
        "載入" to "加载",
        "開啟" to "打开",
        "關閉" to "关闭",
        "搜尋" to "搜索",
        "連線" to "连接",
        "透過" to "通过",
        "檢視" to "查看",
        "資料" to "数据",
        "欄位" to "字段",
        "程式" to "程序",
        "選單" to "菜单",
        "覆寫" to "覆盖",
        "螢幕" to "屏幕",
        "網路" to "网络",
        "網際網路" to "互联网",
        "原始碼" to "源代码",
        "散佈" to "分发",
    )

    /** Longest phrase first so "資料夾" wins over "資料". */
    private val phrasesByLength: List<Pair<String, String>> =
        phrases.sortedByDescending { it.first.length }

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
            size > 2048
    }

    fun convert(text: String): String {
        if (text.isEmpty()) return text
        synchronized(cache) { cache[text]?.let { return it } }
        val converted = convertUncached(text)
        synchronized(cache) { cache[text] = converted }
        return converted
    }

    private fun convertUncached(text: String): String {
        var result = text
        for ((traditional, simplified) in phrasesByLength) {
            if (result.contains(traditional)) {
                result = result.replace(traditional, simplified)
            }
        }
        var changed = false
        val buffer = StringBuilder(result.length)
        for (ch in result) {
            val mapped = charMap[ch]
            if (mapped == null) {
                buffer.append(ch)
            } else {
                buffer.append(mapped)
                changed = true
            }
        }
        return if (changed) buffer.toString() else result
    }

    /** True when [text] contains at least one Traditional-only character. */
    fun isTraditional(text: String): Boolean = text.any { charMap.containsKey(it) }

    private val reverseCharMap: Map<Char, Char> by lazy {
        val skip = REVERSE_IDENTITY_CHARS.toSet()
        val map = HashMap<Char, Char>(TRADITIONAL_CHARS.length * 2)
        TRADITIONAL_CHARS.forEachIndexed { index, ch ->
            val simplified = SIMPLIFIED_CHARS[index]
            if (simplified !in skip) map.putIfAbsent(simplified, ch)
        }
        map
    }

    private val reversePhrases: List<Pair<String, String>> =
        phrases.sortedByDescending { it.second.length }.map { (traditional, simplified) ->
            simplified to traditional
        }

    /**
     * Best-effort Simplified -> Traditional conversion. Storage and searchable
     * data stay Traditional, so a Simplified search term has to be mapped back
     * before it can match. Ambiguous characters resolve to the first table entry.
     */
    fun toTraditional(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        for ((simplified, traditional) in reversePhrases) {
            if (result.contains(simplified)) {
                result = result.replace(simplified, traditional)
            }
        }
        val buffer = StringBuilder(result.length)
        for (ch in result) {
            buffer.append(reverseCharMap[ch] ?: ch)
        }
        return buffer.toString()
    }
}
