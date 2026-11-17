package edu.polyu.comp5511.hotelselection.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ChinaCities {
    private static final String PROVINCE_CITY_DATA = """
            北京市|北京市
            天津市|天津市
            河北省|石家庄市 辛集市 晋州市 新乐市 唐山市 遵化市 迁安市 滦州市 秦皇岛市 邯郸市 武安市 邢台市 南宫市 沙河市 保定市 涿州市 定州市 安国市 高碑店市 张家口市 承德市 平泉市 沧州市 泊头市 任丘市 黄骅市 河间市 廊坊市 霸州市 三河市 衡水市 深州市
            山西省|太原市 古交市 大同市 阳泉市 长治市 晋城市 高平市 朔州市 怀仁市 晋中市 介休市 运城市 永济市 河津市 忻州市 原平市 临汾市 侯马市 霍州市 吕梁市 孝义市 汾阳市
            内蒙古自治区|呼和浩特市 包头市 乌海市 赤峰市 通辽市 霍林郭勒市 鄂尔多斯市 呼伦贝尔市 满洲里市 牙克石市 扎兰屯市 额尔古纳市 根河市 巴彦淖尔市 乌兰察布市 丰镇市 乌兰浩特市 阿尔山市 二连浩特市 锡林浩特市
            辽宁省|沈阳市 新民市 大连市 瓦房店市 庄河市 鞍山市 海城市 抚顺市 本溪市 丹东市 东港市 凤城市 锦州市 凌海市 北镇市 营口市 盖州市 大石桥市 阜新市 辽阳市 灯塔市 盘锦市 铁岭市 调兵山市 开原市 朝阳市 北票市 凌源市 葫芦岛市 兴城市
            吉林省|长春市 榆树市 德惠市 公主岭市 吉林市 蛟河市 桦甸市 舒兰市 磐石市 四平市 双辽市 辽源市 通化市 梅河口市 集安市 白山市 临江市 松原市 扶余市 白城市 洮南市 大安市 延吉市 图们市 敦化市 珲春市 龙井市 和龙市
            黑龙江省|哈尔滨市 尚志市 五常市 齐齐哈尔市 讷河市 鸡西市 虎林市 密山市 鹤岗市 双鸭山市 大庆市 伊春市 铁力市 佳木斯市 同江市 富锦市 抚远市 七台河市 牡丹江市 绥芬河市 海林市 宁安市 穆棱市 东宁市 黑河市 北安市 五大连池市 嫩江市 绥化市 安达市 肇东市 海伦市 漠河市
            上海市|上海市
            江苏省|南京市 无锡市 江阴市 宜兴市 徐州市 新沂市 邳州市 常州市 溧阳市 苏州市 常熟市 张家港市 昆山市 太仓市 南通市 启东市 如皋市 海安市 连云港市 淮安市 盐城市 东台市 扬州市 仪征市 高邮市 镇江市 丹阳市 扬中市 句容市 泰州市 兴化市 靖江市 泰兴市 宿迁市
            浙江省|杭州市 建德市 宁波市 余姚市 慈溪市 温州市 瑞安市 乐清市 龙港市 嘉兴市 海宁市 平湖市 桐乡市 湖州市 绍兴市 诸暨市 嵊州市 金华市 兰溪市 义乌市 东阳市 永康市 衢州市 江山市 舟山市 台州市 温岭市 临海市 玉环市 丽水市 龙泉市
            安徽省|合肥市 巢湖市 芜湖市 无为市 蚌埠市 淮南市 马鞍山市 淮北市 铜陵市 安庆市 桐城市 潜山市 黄山市 滁州市 天长市 明光市 阜阳市 界首市 宿州市 六安市 亳州市 池州市 宣城市 宁国市 广德市
            福建省|福州市 福清市 厦门市 莆田市 三明市 永安市 泉州市 石狮市 晋江市 南安市 漳州市 南平市 邵武市 武夷山市 建瓯市 龙岩市 漳平市 宁德市 福安市 福鼎市
            江西省|南昌市 景德镇市 乐平市 萍乡市 九江市 瑞昌市 共青城市 庐山市 新余市 鹰潭市 贵溪市 赣州市 瑞金市 龙南市 吉安市 井冈山市 宜春市 丰城市 樟树市 高安市 抚州市 上饶市 德兴市
            山东省|济南市 青岛市 胶州市 平度市 莱西市 淄博市 枣庄市 滕州市 东营市 烟台市 龙口市 莱阳市 莱州市 招远市 栖霞市 海阳市 潍坊市 青州市 诸城市 寿光市 安丘市 高密市 昌邑市 济宁市 曲阜市 邹城市 泰安市 新泰市 肥城市 威海市 荣成市 乳山市 日照市 临沂市 德州市 乐陵市 禹城市 聊城市 临清市 滨州市 邹平市 菏泽市
            河南省|郑州市 巩义市 荥阳市 新密市 新郑市 登封市 开封市 洛阳市 平顶山市 舞钢市 汝州市 安阳市 林州市 鹤壁市 新乡市 卫辉市 辉县市 长垣市 焦作市 沁阳市 孟州市 濮阳市 许昌市 禹州市 长葛市 漯河市 三门峡市 义马市 灵宝市 南阳市 邓州市 商丘市 永城市 信阳市 周口市 项城市 驻马店市 济源市
            湖北省|武汉市 黄石市 大冶市 十堰市 丹江口市 宜昌市 宜都市 当阳市 枝江市 襄阳市 老河口市 枣阳市 宜城市 鄂州市 荆门市 钟祥市 京山市 孝感市 应城市 安陆市 汉川市 荆州市 石首市 洪湖市 松滋市 监利市 黄冈市 麻城市 武穴市 咸宁市 赤壁市 随州市 广水市 恩施市 利川市 仙桃市 潜江市 天门市
            湖南省|长沙市 浏阳市 宁乡市 株洲市 醴陵市 湘潭市 湘乡市 韶山市 衡阳市 耒阳市 常宁市 邵阳市 武冈市 邵东市 岳阳市 汨罗市 临湘市 常德市 津市市 张家界市 益阳市 沅江市 郴州市 资兴市 永州市 祁阳市 怀化市 洪江市 娄底市 冷水江市 涟源市 吉首市
            广东省|广州市 韶关市 乐昌市 南雄市 深圳市 珠海市 汕头市 佛山市 江门市 台山市 开平市 鹤山市 恩平市 湛江市 廉江市 雷州市 吴川市 茂名市 高州市 化州市 信宜市 肇庆市 四会市 惠州市 梅州市 兴宁市 汕尾市 陆丰市 河源市 阳江市 阳春市 清远市 英德市 连州市 东莞市 中山市 潮州市 揭阳市 普宁市 云浮市 罗定市
            广西壮族自治区|南宁市 横州市 柳州市 桂林市 荔浦市 梧州市 岑溪市 北海市 防城港市 东兴市 钦州市 贵港市 桂平市 玉林市 北流市 百色市 靖西市 平果市 贺州市 河池市 来宾市 合山市 崇左市 凭祥市
            海南省|海口市 三亚市 三沙市 儋州市 五指山市 琼海市 文昌市 万宁市 东方市
            重庆市|重庆市
            四川省|成都市 都江堰市 彭州市 邛崃市 崇州市 简阳市 自贡市 攀枝花市 泸州市 德阳市 广汉市 什邡市 绵竹市 绵阳市 江油市 广元市 遂宁市 射洪市 内江市 隆昌市 乐山市 峨眉山市 南充市 阆中市 眉山市 宜宾市 广安市 华蓥市 达州市 万源市 雅安市 巴中市 资阳市 马尔康市 康定市 西昌市 会理市
            贵州省|贵阳市 清镇市 六盘水市 盘州市 遵义市 赤水市 仁怀市 安顺市 毕节市 黔西市 铜仁市 兴义市 兴仁市 凯里市 都匀市 福泉市
            云南省|昆明市 安宁市 曲靖市 宣威市 玉溪市 澄江市 保山市 腾冲市 昭通市 水富市 丽江市 普洱市 临沧市 楚雄市 禄丰市 个旧市 开远市 蒙自市 弥勒市 文山市 景洪市 大理市 瑞丽市 芒市 泸水市 香格里拉市
            西藏自治区|拉萨市 日喀则市 昌都市 林芝市 米林市 山南市 错那市 那曲市
            陕西省|西安市 铜川市 宝鸡市 咸阳市 兴平市 彬州市 渭南市 韩城市 华阴市 延安市 子长市 汉中市 榆林市 神木市 安康市 旬阳市 商洛市
            甘肃省|兰州市 嘉峪关市 金昌市 白银市 天水市 武威市 张掖市 平凉市 华亭市 酒泉市 玉门市 敦煌市 庆阳市 定西市 陇南市 临夏市 合作市
            青海省|西宁市 海东市 同仁市 玉树市 格尔木市 德令哈市 茫崖市
            宁夏回族自治区|银川市 灵武市 石嘴山市 吴忠市 青铜峡市 固原市 中卫市
            新疆维吾尔自治区|乌鲁木齐市 克拉玛依市 吐鲁番市 哈密市 昌吉市 阜康市 博乐市 阿拉山口市 库尔勒市 阿克苏市 库车市 阿图什市 喀什市 和田市 伊宁市 奎屯市 霍尔果斯市 塔城市 乌苏市 沙湾市 阿勒泰市 石河子市 阿拉尔市 图木舒克市 五家渠市 北屯市 铁门关市 双河市 可克达拉市 昆玉市 胡杨河市 新星市 白杨市
            台湾省|台北市 台中市 基隆市 台南市 高雄市 新北市 宜兰市 桃园市 嘉义市 竹北市 苗栗市 头份市 南投市 彰化市 员林市 新竹市 斗六市 朴子市 太保市 屏东市 花莲市 台东市 马公市
            香港特别行政区|香港
            澳门特别行政区|澳门
            """;
    private static final Map<String, List<String>> CITIES_BY_PROVINCE = loadCities();
    private static final Set<String> CITIES = CITIES_BY_PROVINCE.values().stream()
            .flatMap(List::stream).collect(Collectors.toUnmodifiableSet());
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("香港特别行政区", "香港"),
            Map.entry("香港特別行政區", "香港"),
            Map.entry("Hong Kong", "香港"),
            Map.entry("澳门特别行政区", "澳门"),
            Map.entry("澳門", "澳门"),
            Map.entry("澳門特別行政區", "澳门"),
            Map.entry("Macau", "澳门"),
            Map.entry("Macao", "澳门"),
            Map.entry("臺北市", "台北市"), Map.entry("臺中市", "台中市"),
            Map.entry("臺南市", "台南市"), Map.entry("桃園市", "桃园市"),
            Map.entry("嘉義市", "嘉义市"), Map.entry("宜蘭市", "宜兰市"),
            Map.entry("頭份市", "头份市"), Map.entry("員林市", "员林市"),
            Map.entry("屏東市", "屏东市"), Map.entry("花蓮市", "花莲市"),
            Map.entry("臺東市", "台东市"), Map.entry("馬公市", "马公市"));

    private ChinaCities() {}

    static Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String city = value.strip();
        if (city.equalsIgnoreCase("Hong Kong")) city = "Hong Kong";
        if (city.equalsIgnoreCase("Macau")) city = "Macau";
        if (city.equalsIgnoreCase("Macao")) city = "Macao";
        String alias = ALIASES.get(city);
        if (alias != null) return Optional.of(alias);
        if (CITIES.contains(city)) return Optional.of(city);
        city += "市";
        city = ALIASES.getOrDefault(city, city);
        return CITIES.contains(city) ? Optional.of(city) : Optional.empty();
    }

    private static Map<String, List<String>> loadCities() {
        Map<String, List<String>> cities = new LinkedHashMap<>();
        for (String line : PROVINCE_CITY_DATA.strip().split("\\R")) {
            String[] parts = line.strip().split("\\|", 2);
            cities.put(parts[0], List.copyOf(Arrays.asList(parts[1].split(" "))));
        }
        return Collections.unmodifiableMap(cities);
    }
}
