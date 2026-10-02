package com.xx.ime.core

/** 普通话 410 音节表 + T9 映射，九键拼音与词库导入的基础 */
object Syllables {

    private const val RAW =
        "a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu " +
        "ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua " +
        "chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo da dai dan dang dao de dei deng " +
        "di dia dian diao die ding diu dong dou du duan dui dun duo e ei en eng er fa fan fang fei fen feng " +
        "fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo ha hai " +
        "han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo ji jia jian jiang jiao " +
        "jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao ke ken keng kong kou ku kua kuai kuan " +
        "kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long " +
        "lou lu luan lun luo lv lve ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo " +
        "mou mu na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan " +
        "nuo nv nve o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian " +
        "qiang qiao qie qin qing qiong qiu qu quan que qun ran rang rao re ren reng ri rong rou ru ruan rui " +
        "run ruo sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu " +
        "shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo ta tai tan tang tao te teng " +
        "ti tian tiao tie ting tong tou tu tuan tui tun tuo wa wai wan wang wei wen weng wo wu xi xia xian " +
        "xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu " +
        "yuan yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi " +
        "zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo"

    private val LETTER_DIGIT: Map<Char, Char> = buildMap {
        for (c in "abc") put(c, '2')
        for (c in "def") put(c, '3')
        for (c in "ghi") put(c, '4')
        for (c in "jkl") put(c, '5')
        for (c in "mno") put(c, '6')
        for (c in "pqrs") put(c, '7')
        for (c in "tuv") put(c, '8')
        for (c in "wxyz") put(c, '9')
    }

    val all: List<String> = RAW.split(' ').filter { it.isNotEmpty() }

    /** 合法音节集合 */
    val valid: Set<String> = all.toHashSet()

    fun digitOf(c: Char): Char = LETTER_DIGIT[c] ?: '0'

    /** 拼音字母 -> 九键数字，如 women -> 96636 */
    fun codeOf(letters: String): String = buildString {
        for (c in letters) append(digitOf(c))
    }

    /** 数字 -> 音节列表，如 "96" -> [wo, yo] */
    val byCode: Map<String, List<String>> = all.groupBy { codeOf(it) }

    /** 数字前缀 -> 可能的拼音字母前缀。例："9" -> {w,x,y,z}；"74" -> {sh} */
    fun letterPrefixes(digits: String): Set<String> {
        if (digits.isEmpty()) return emptySet()
        val n = digits.length
        val res = LinkedHashSet<String>()
        for (s in all) {
            if (s.length < n) continue
            if (codeOf(s).startsWith(digits)) res.add(s.substring(0, n))
        }
        return res
    }
}
