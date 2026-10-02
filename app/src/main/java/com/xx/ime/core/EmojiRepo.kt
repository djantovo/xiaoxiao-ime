package com.xx.ime.core

/** 内置 emoji（离线，无需联网）。分类数据用字符串拼接，运行时按码点聚合成簇。 */
object EmojiRepo {

    private const val FACES =
        "😀😃😄😁😆😅🤣😂🙂🙃😉😊😇🥰😍🤩😘😗😚😙🥲😋😛😜🤪😝🤑🤗🤭🤫🤔🤐🤨😐😑😶😏😒🙄😬🤥😌😔😪🤤😴😷🤒🤕🤢🤮🤧🥵🥶🥴😵🤯🤠🥳😎🤓🧐😕😟🙁😮😯😲😳🥺😦😧😨😰😥😢😭😱😖😣😞😓😩😫🥱😤😡😠🤬😈👿💀💩🤡👹👺👻👽🤖😺😸😹😻😼😽🙀😿😾"
    private const val HANDS =
        "👍👎👌✌️🤞🤟🤘🤙👈👉👆👇☝️✋🤚🖐🖖👋🤝🙏💪🦾✍️💅👏🙌👐🤲🤜🤛✊👊🫰🫵🫶"
    private const val NATURE =
        "🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵🙈🙉🙊🐔🐧🐦🐤🦆🦅🦉🦇🐺🐗🐴🦄🐝🐛🦋🐌🐞🐜🦗🕷🦂🐢🐍🦎🦖🦕🐙🦑🦐🦞🦀🐡🐠🐟🐬🐳🐋🦈🐊🐅🐆🦓🦍🐘🦏🐪🐫🦒🦘🐃🐂🐄🐎🐖🐏🐑🦙🐐🦌🐕🐩🦮🐈🐓🦃🦚🦜🦢🦩🕊🐇🦝🦨🦡🦦🦥🐁🐀🐿🦔"
    private const val FOOD =
        "🍏🍎🍐🍊🍋🍌🍉🍇🍓🫐🍈🍒🍑🥭🍍🥥🥝🍅🥑🥦🥬🥒🌶🫑🌽🥕🫒🧄🧅🥔🍠🥐🥯🍞🥖🥨🧀🥚🍳🧈🥞🧇🥓🥩🍗🍖🌭🍔🍟🍕🫓🥪🥙🧆🌮🌯🫔🥗🥘🫕🥫🍝🍜🍲🍛🍣🍱🥟🦪🍤🍙🍚🍘🍥🥠🥮🍢🍡🍧🍨🍦🥧🧁🍰🎂🍮🍭🍬🍫🍿🍩🍪🌰🥜🍯🥛🍼🫖☕🍵🧃🥤🧋🍶🍺🍻🥂🍷🥃🍸🍹🧉🍾"
    private const val OBJECTS =
        "⌚📱💻⌨️🖥🖨🖱🕹💽💾💿📀📷📸📹🎥📞☎️📟📠📺📻🎙🎚🎛🧭⏱⏲⏰🕰⌛⏳📡🔋🔌💡🔦🕯🪔🧯🛢💸💵💴💶💷💰💳💎⚖️🧰🔧🔨⚒️🛠⛏🔩⚙️🧱⛓🧲🔫💣🧨🪓🔪🗡⚔️🛡🚬⚰️🪦⚱️🏺🔮📿🧿💈⚗️🔭🔬🕳🩹🩺💊💉🩸🧬🦠🧫🧪🌡🧹🧺🧻🚽🚰🚿🛁🛀🧼🪒🧽🧴🛎🔑🗝🚪🪑🛋🛏🛌🧸🖼🛍🛒🎁🎈🎏🎀🪄🪅🎊🎉🎎🏮🎐🧧✉️📩📨📧💌📥📤📦🏷🪧📪📫📬📭📮📯📜📃📄📑🧾📊📈📉🗒🗓📆📅🗑📇🗃🗳🗄📋📁📂🗂🗞📰📓📔📒📕📗📘📙📚📖🔖🧷🔗📎🖇📐📏🧮📌📍✂️🖊🖋✒️🖌🖍📝✏️🔍🔎🔏🔐🔒🔓"
    private const val SYMBOLS =
        "❤️🧡💛💚💙💜🖤🤍🤎💔❣️💕💞💓💗💖💘💝💟☮️✝️☪️🕉☸️✡️🔯🕎☯️☦️🛐⛎♈♉♊♋♌♍♎♏♐♑♒♓🆔⚛️🉑☢️☣️📴📳🈶🈚🈸🈺🈷️✴️🆚💮🉐㊙️㊗️🈴🈵🈹🈲🅰️🅱️🆎🆑🅾️🆘❌⭕🛑⛔📛🚫💯💢♨️🚷🚯🚳🚱🔞📵🚭❗❕❓❔‼️⁉️🔅🔆〽️⚠️🚸🔱⚜️🔰♻️✅🈯💹❇️✳️❎🌐💠Ⓜ️🌀💤🏧🚾♿🅿️🈳🈂️🛂🛃🛄🛅🚹🚺🚼⚧🚻🚮🎦📶🈁🔣ℹ️🔤🔡🔠🆖🆗🆙🆒🆕🆓0️⃣1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣7️⃣8️⃣9️⃣🔟🔢#️⃣*️⃣⏏️▶️⏸⏯⏹⏺⏭⏮⏩⏪⏫⏬◀️🔼🔽➡️⬅️⬆️⬇️↗️↘️↙️↖️↕️↔️↪️↩️⤴️⤵️🔀🔁🔂🔄🔃🎵🎶➕➖➗✖️🟰♾️💲💱™️©️®️〰️➰➿🔚🔙🔛🔝🔜✔️☑️🔘🔴🟠🟡🟢🔵🟣⚫⚪🟤🔺🔻🔸🔹🔶🔷🔳🔲▪️▫️◾◽◼️◻️🟥🟧🟨🟩🟦🟪⬛⬜🟫🔈🔇🔉🔊🔔🔕📣📢💬💭🗯♠️♣️♥️♦️🃏🎴🀄🕐🕑🕒🕓🕔🕕🕖🕗🕘🕙🕚🕛"

    data class Category(val name: String, val emojis: List<String>)

    val categories: List<Category> by lazy {
        listOf(
            Category("表情", splitEmoji(FACES)),
            Category("手势", splitEmoji(HANDS)),
            Category("动物", splitEmoji(NATURE)),
            Category("食物", splitEmoji(FOOD)),
            Category("物品", splitEmoji(OBJECTS)),
            Category("符号", splitEmoji(SYMBOLS))
        )
    }

    /** 按“字素簇”切分，保证 ❤️ / 1️⃣ 这类组合 emoji 不被拆散 */
    private fun splitEmoji(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var prevWasZwj = false
        var riCount = 0
        var i = 0

        fun flush() {
            if (sb.isNotEmpty()) out.add(sb.toString())
            sb.setLength(0)
            prevWasZwj = false
            riCount = 0
        }

        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val ch = s.substring(i, i + n)
            val isRi = cp in 0x1F1E6..0x1F1FF
            val attach = sb.isNotEmpty() && (
                    prevWasZwj ||
                            cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 ||
                            cp in 0x1F3FB..0x1F3FF ||
                            (isRi && riCount == 1)
                    )
            if (!attach) flush()
            sb.append(ch)
            if (isRi) riCount = minOf(riCount + 1, 2)
            else if (cp != 0xFE0F && cp != 0x200D) riCount = 0
            prevWasZwj = cp == 0x200D
            i += n
        }
        flush()
        return out
    }
}
