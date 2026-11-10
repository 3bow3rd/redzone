package com.redzone.club

/** يميّز اسم المنتج ويعطيه إيموجي مناسباً (شاي، قهوة، نودلز...). الكلمة التي تبدأ بـ = تُطابق كلمة كاملة. */
object Emo {
    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.trim().lowercase()) {
            if ((ch in '\u064B'..'\u065F') || ch == '\u0670' || ch == '\u0640') continue
            sb.append(when (ch) { 'أ', 'إ', 'آ', 'ٱ' -> 'ا'; 'ة' -> 'ه'; 'ى' -> 'ي'; else -> ch })
        }
        return sb.toString()
    }

    private val map: List<Pair<String, List<String>>> = listOf(
        "🍜" to listOf("نودلز", "نوديلز", "اندومي", "إندومي", "اندومى", "indomie", "noodle", "ramen", "رامن", "شعيرية", "شعيريه", "كاسة نودلز"),
        "🍝" to listOf("معكرونة", "باستا", "pasta", "سباغيتي", "spaghetti", "مكرونة"),
        "🍵" to listOf("ايس تي", "ايستي", "ice tea", "iced tea", "شاي", "شاى", "=tea", "زهورات", "ينسون", "يانسون", "بابونج", "ماتشا", "اعشاب", "أعشاب", "زنجبيل"),
        "🧉" to listOf("ماتيه", "=mate"),
        "☕" to listOf("نسكافيه", "نسكافي", "نسكافية", "nescafe", "nescafé", "قهوة", "قهوه", "coffee", "كابتشينو", "cappuccino", "لاتيه", "latte", "موكا", "mocha", "اسبريسو", "إسبريسو", "espresso", "امريكانو", "أمريكانو", "americano", "كوفي", "هوت شوكليت", "هوت شوكولا", "سحلب"),
        "⚡" to listOf("طاقة", "طاقه", "energy", "ريد بول", "ريدبول", "redbull", "red bull", "مونستر", "monster", "=بيغ", "بوم بوم"),
        "🥤" to listOf("ميلك شيك", "ميلكشيك", "milkshake", "=شيك", "=shake", "سموذي", "smoothie", "=كولا", "=cola", "كوكا", "coke", "كوكاكولا", "بيبسي", "pepsi", "=سفن", "7up", "سبرايت", "sprite", "فانتا", "fanta", "ميرندا", "mirinda", "غازي", "غازية", "سودا", "soda", "شويبس", "schweppes", "ماونتن", "=dew", "كابي", "=تانج", "=tang"),
        "🍹" to listOf("موهيتو", "mojito", "كوكتيل", "cocktail", "بينا كولادا"),
        "🍋" to listOf("ليمون", "lemon", "ليمونادا", "lemonade"),
        "🍊" to listOf("برتقال", "orange", "كلمنتين"),
        "🥭" to listOf("مانجا", "مانجو", "mango"),
        "🍓" to listOf("فراولة", "فراوله", "strawberry"),
        "🍍" to listOf("اناناس", "أناناس", "pineapple"),
        "🍎" to listOf("تفاح", "apple", "فواكه", "فاكهة", "فاكهه"),
        "🍌" to listOf("=موز", "banana"),
        "🍉" to listOf("بطيخ", "watermelon"),
        "🧃" to listOf("عصير", "عصائر", "juice", "ميلك"),
        "🥛" to listOf("حليب", "milk", "لبن", "عيران", "ayran", "نيدو", "زبادي"),
        "💧" to listOf("مياه", "ماء", "water", "ميه", "=مي", "=ماي"),
        "🧊" to listOf("ثلج", "=ice"),
        "🍦" to listOf("ايس كريم", "ايسكريم", "آيس كريم", "icecream", "ice cream", "بوظة", "بوظه", "كورنيتو", "cornetto", "مثلجات"),
        "🍟" to listOf("بطاطا", "بطاطس", "شيبس", "chips", "فرنش فرايز", "فرايز", "fries", "=ليز", "lays", "برينجلز", "pringles", "شيبسي"),
        "🍿" to listOf("فشار", "بوب كورن", "popcorn", "ذرة", "ذره"),
        "🥜" to listOf("مكسرات", "فستق", "لوز", "كاجو", "بندق", "سوداني", "nuts", "peanut", "تسالي", "بزر", "=لب"),
        "🍫" to listOf("شوكولا", "شيكولا", "شوكلا", "شوكولاتة", "شوكولاته", "chocolate", "كيت كات", "kitkat", "سنيكرز", "snickers", "=مارس", "=mars", "نوتيلا", "nutella", "بونتي", "bounty", "كندر", "kinder"),
        "🍪" to listOf("بسكويت", "بسكوت", "بيسكوت", "biscuit", "cookie", "كوكيز", "اوريو", "أوريو", "oreo", "ويفر", "wafer", "بيتيفور"),
        "🍩" to listOf("دونات", "donut", "دوناتس"),
        "🍰" to listOf("كيك", "cake", "براوني", "brownie", "تشيز كيك", "معمول", "كب كيك", "cupcake"),
        "🥐" to listOf("كرواسون", "croissant", "معجنات", "فطيرة", "فطيره", "منقوشة", "منقوشه"),
        "🍬" to listOf("سكاكر", "سكاكري", "حلوى", "حلويات", "علكة", "علكه", "لبان", "candy", "gum", "مصاصة", "مصاصه", "لوليبوب", "حلو"),
        "🍔" to listOf("برغر", "همبرغر", "بركر", "همبركر", "burger"),
        "🍕" to listOf("بيتزا", "بيتسا", "pizza"),
        "🌭" to listOf("هوت دوغ", "هوتدوغ", "نقانق", "hotdog", "hot dog"),
        "🌯" to listOf("شاورما", "shawarma", "لفة", "لفه", "wrap", "كباب"),
        "🥙" to listOf("فلافل", "falafel", "فتة", "حمص"),
        "🥪" to listOf("ساندويش", "ساندوتش", "ساندويتش", "سندويش", "سندويتش", "سندوتش", "sandwich", "توست", "toast"),
        "🍗" to listOf("دجاج", "فروج", "بروستد", "بروست", "broasted", "chicken", "كرسبي", "نغتس", "nuggets"),
        "🍳" to listOf("بيض", "egg", "عجة", "عجه"),
        "🍞" to listOf("خبز", "bread", "صمون", "كعك"),
        "🍚" to listOf("=رز", "=ارز", "=أرز", "=rice"),
        "🥗" to listOf("سلطة", "سلطه", "salad"),
        "💨" to listOf("ارجيلة", "ارجيله", "أرجيلة", "نرجيلة", "نرجيله", "shisha", "hookah", "معسل"),
        "🚬" to listOf("سجائر", "سيجارة", "سيجاره", "دخان", "cigarette"),
        "🔋" to listOf("بطارية", "بطاريه", "battery"),
        "🎮" to listOf("ساعة لعب", "ساعه لعب", "لعبة", "لعبه", "game"),
    )

    private val norms: List<Pair<String, List<String>>> = map.map { (e, ks) -> e to ks.map { norm(it) } }

    fun of(name: String): String {
        val n = norm(name)
        val toks = n.split(' ').filter { it.isNotEmpty() }
        for ((e, ks) in norms) for (k in ks) {
            if (k.length > 1 && k[0] == '=') { if (toks.contains(k.substring(1))) return e }
            else if (k.isNotEmpty() && n.contains(k)) return e
        }
        return "🍽️"
    }
}
