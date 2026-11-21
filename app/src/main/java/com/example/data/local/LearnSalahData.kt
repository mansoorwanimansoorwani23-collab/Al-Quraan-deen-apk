package com.example.data.local

data class SalahStep(
    val stepNumber: Int,
    val title: String,
    val arabicTitle: String,
    val postureSummary: String,
    val instructions: String,
    val arabicRecitation: String,
    val transliteration: String,
    val englishMeaning: String,
    val pronunciationTip: String
)

data class WuduStep(
    val number: Int,
    val title: String,
    val detail: String
)

data class PrayerRakahInfo(
    val prayerName: String,
    val arabicName: String,
    val sunnahBefore: String,
    val fard: String,
    val sunnahAfter: String,
    val witrOrExtra: String,
    val recitationVoice: String
)

object LearnSalahData {

    val WUDU_STEPS = listOf(
        WuduStep(1, "Niyyah & Bismillah", "Make the intention in your heart to perform Wudu for Allah, and say \"Bismillah\" (In the name of Allah)."),
        WuduStep(2, "Wash Hands (3 times)", "Wash both hands thoroughly up to the wrists, cleaning between the fingers."),
        WuduStep(3, "Rinse Mouth (3 times)", "Take water into your mouth with your right hand, swirl it gently, and spit it out."),
        WuduStep(4, "Clean Nose (3 times)", "Sniff water gently into the nostrils with the right hand and blow it out using the left hand."),
        WuduStep(5, "Wash Face (3 times)", "Wash the entire face from the hairline to below the chin and from ear to ear."),
        WuduStep(6, "Wash Arms (3 times)", "Wash the right arm from fingertips up to and including the elbow, then the left arm."),
        WuduStep(7, "Wipe Head & Ears (1 time)", "Wipe wet hands over the head from front to back and back to front, then wipe inside and behind the ears."),
        WuduStep(8, "Wash Feet (3 times)", "Wash the right foot up to and including the ankle, cleaning between toes, then repeat for the left foot.")
    )

    val SALAH_STEPS = listOf(
        SalahStep(
            stepNumber = 1,
            title = "Facing Qibla & Intention (Niyyah)",
            arabicTitle = "النية واستقبال القبلة",
            postureSummary = "Stand upright facing the Qibla (Makkah) with calmness and focus.",
            instructions = "Stand cleanly on a pure place facing the direction of the Kaaba. Keep your feet shoulder-width apart, look down toward the place of prostration, and make a sincere intention in your heart for the specific prayer you are about to perform (e.g., 2 Rakah Fard of Fajr).",
            arabicRecitation = "اللَّهُمَّ إِنِّي أَصَلِّي لَكَ خَالِصًا لِوَجْهِكَ الْكَرِيمِ",
            transliteration = "Intention is made silently in the heart — no spoken words are required.",
            englishMeaning = "Intending sincerely in the heart to perform this prayer solely for Allah.",
            pronunciationTip = "Niyyah is an act of the heart. Take a calm breath to clear worldly distractions before beginning."
        ),
        SalahStep(
            stepNumber = 2,
            title = "Takbiratul Ihram (Opening Takbir)",
            arabicTitle = "تكبيرة الإحرام",
            postureSummary = "Raise both hands to earlobes or shoulders, palms facing Qibla.",
            instructions = "Raise your hands with fingers naturally open, say \"Allahu Akbar\", and then fold your right hand over your left hand/wrist on your chest (or below the navel per Hanafi school). From this moment, your prayer has officially begun.",
            arabicRecitation = "اللَّهُ أَكْبَرُ",
            transliteration = "Allāhu Akbar",
            englishMeaning = "Allah is the Greatest.",
            pronunciationTip = "Stretch the 'ā' in Allāhu smoothly (2 counts) and pronounce 'Akbar' crisply without adding an extra vowel at the end."
        ),
        SalahStep(
            stepNumber = 3,
            title = "Opening Supplication (Sana / Istiftah)",
            arabicTitle = "دعاء الاستفتاح (الثناء)",
            postureSummary = "Standing (Qiyam), eyes lowered toward the place of Sujood.",
            instructions = "In the very first Rakah only, recite the opening praise silently before seeking refuge and reciting Surah Al-Fatihah.",
            arabicRecitation = "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، وَتَبَارَكَ اسْمُكَ، وَتَعَالَى جَدُّكَ، وَلَا إِلَهَ غَيْرُكَ",
            transliteration = "Subḥānaka-llāhumma wa biḥamdika, wa tabārakasmuka, wa ta‘ālā jadduka, wa lā ilāha ghayruk",
            englishMeaning = "Glory be to You, O Allah, and praise be to You. Blessed is Your Name, exalted is Your Majesty, and there is none worthy of worship besides You.",
            pronunciationTip = "Pronounce the 'ḥ' in Subḥānaka and biḥamdika clearly from the middle throat, and the 'gh' in ghayruk softly from the top of the throat."
        ),
        SalahStep(
            stepNumber = 4,
            title = "Surah Al-Fatihah (Essential Recitation)",
            arabicTitle = "قراءة سورة الفاتحة",
            postureSummary = "Standing (Qiyam) — recited in every single Rakah.",
            instructions = "Say \"A‘ūdhu billāhi minash-shayṭānir-rajīm\" and \"Bismillāhir-Raḥmānir-Raḥīm\" softly, then recite Surah Al-Fatihah completely and finish with \"Ameen\". Then in the first two Rakahs, recite any additional short Surah (such as Surah Al-Ikhlas).",
            arabicRecitation = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
            transliteration = "Bismillāhir-Raḥmānir-Raḥīm. Al-ḥamdu lillāhi Rabbil-‘ālamīn. Ar-Raḥmānir-Raḥīm. Māliki yawmid-dīn. Iyyāka na‘budu wa iyyāka nasta‘īn. Ihdinaṣ-ṣirāṭal-mustaqīm. Ṣirāṭal-ladhīna an‘amta ‘alayhim, ghayril-maghḍūbi ‘alayhim wa laḍ-ḍāllīn. (Āmīn)",
            englishMeaning = "In the name of Allah, the Entirely Merciful, the Especially Merciful. All praise is due to Allah, Lord of the worlds... Guide us to the straight path.",
            pronunciationTip = "Give heavy emphasis to ص (ṣ), ط (ṭ), and ض (ḍ) in Ṣirāṭ and Ḍāllīn, and hold the long elongation (6 counts) on 'ḍāllīn'."
        ),
        SalahStep(
            stepNumber = 5,
            title = "Ruku (Bowing)",
            arabicTitle = "الركوع",
            postureSummary = "Bow with back flat and straight, hands gripping the knees.",
            instructions = "Say \"Allahu Akbar\" while bending into Ruku. Place both hands firmly on your knees with fingers spread, keeping your back level and head aligned with your spine. Pause calmly and recite the Tasbeeh at least 3 times.",
            arabicRecitation = "سُبْحَانَ رَبِّيَ الْعَظِيمِ",
            transliteration = "Subḥāna Rabbiyal-‘Aẓīm (3 times)",
            englishMeaning = "Glory be to my Lord, the Most Great.",
            pronunciationTip = "In 'Al-‘Aẓīm', articulate the deep throat '‘A' (ع) followed by the heavy interdental 'ẓ' (ظ) with tongue tip at the upper teeth."
        ),
        SalahStep(
            stepNumber = 6,
            title = "Qawmah (Rising from Ruku)",
            arabicTitle = "الاعتدال من الركوع",
            postureSummary = "Stand back up completely straight and relaxed.",
            instructions = "Rise upright while saying \"Sami‘allāhu liman ḥamidah\", then while standing still say \"Rabbanā wa lakal-ḥamd\". Do not rush into prostration until your spine has settled upright.",
            arabicRecitation = "سَمِعَ اللَّهُ لِمَنْ حَمِدَهُ ۝ رَبَّنَا وَلَكَ الْحَمْدُ",
            transliteration = "Sami‘allāhu liman ḥamidah. Rabbanā wa lakal-ḥamd.",
            englishMeaning = "Allah hears whoever praises Him. Our Lord, to You belongs all praise.",
            pronunciationTip = "Pronounce 'Sami‘a' with a clear throat 'ع' and keep 'ḥamd' crisp with a clean throat 'ح'."
        ),
        SalahStep(
            stepNumber = 7,
            title = "First Sujood (Prostration)",
            arabicTitle = "السجدة الأولى",
            postureSummary = "Seven points touch the ground: forehead + nose, both palms, both knees, toes of both feet.",
            instructions = "Say \"Allahu Akbar\" and descend gently into prostration. Keep your forehead and nose firmly on the ground, palms flat beside your shoulders/head, elbows raised off the floor, and toes pointing toward the Qibla. Recite the Tasbeeh at least 3 times.",
            arabicRecitation = "سُبْحَانَ رَبِّيَ الْأَعْلَى",
            transliteration = "Subḥāna Rabbiyal-A‘lā (3 times)",
            englishMeaning = "Glory be to my Lord, the Most High.",
            pronunciationTip = "Be sure to pronounce 'A‘lā' with a clear 'ع' sound in the middle rather than turning it into 'Allā'."
        ),
        SalahStep(
            stepNumber = 8,
            title = "Jalsa (Sitting Between Prostrations)",
            arabicTitle = "الجلسة بين السجدتين",
            postureSummary = "Sit calmly upright with hands resting on the thighs.",
            instructions = "Say \"Allahu Akbar\" and sit up from Sujood. Sit comfortably on your left foot with the right foot upright (toes pointing toward Qibla) if able. Pause calmly and ask Allah for forgiveness.",
            arabicRecitation = "رَبِّ اغْفِرْ لِي ، رَبِّ اغْفِرْ لِي",
            transliteration = "Rabbighfir lī, Rabbighfir lī",
            englishMeaning = "My Lord, forgive me. My Lord, forgive me.",
            pronunciationTip = "Connect 'Rabbi' smoothly into 'ghfir' without pausing awkwardly; keep the 'r' soft before 'lī'."
        ),
        SalahStep(
            stepNumber = 9,
            title = "Second Sujood & Standing for Next Rakah",
            arabicTitle = "السجدة الثانية",
            postureSummary = "Perform the second prostration identically to the first.",
            instructions = "Say \"Allahu Akbar\" and go into the second Sujood, reciting \"Subḥāna Rabbiyal-A‘lā\" 3 times. This completes 1 Rakah! Say \"Allahu Akbar\" to stand up for the 2nd Rakah (repeating from Surah Al-Fatihah). After the 2nd Rakah, sit for Tashahhud.",
            arabicRecitation = "سُبْحَانَ رَبِّيَ الْأَعْلَى",
            transliteration = "Subḥāna Rabbiyal-A‘lā (3 times)",
            englishMeaning = "Glory be to my Lord, the Most High.",
            pronunciationTip = "Move smoothly and unhurriedly; tranquility (Tuma’ninah) in every posture is a pillar of Salah."
        ),
        SalahStep(
            stepNumber = 10,
            title = "Tashahhud (At-Tahiyyat Sitting)",
            arabicTitle = "التشهد (التحيات)",
            postureSummary = "Seated posture after the 2nd Rakah and in the final Rakah.",
            instructions = "Sit with hands on your thighs/knees. Raise the right index finger gently during the testimony of faith (Ash-hadu an lā ilāha illallāh). If this is a 3- or 4-Rakah prayer's middle sitting, stand up after Tashahhud; in the final sitting, continue to Durood Ibrahim.",
            arabicRecitation = "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ",
            transliteration = "At-taḥiyyātu lillāhi waṣ-ṣalawātu waṭ-ṭayyibāt. As-salāmu ‘alayka ayyuhan-nabiyyu wa raḥmatullāhi wa barakātuh. As-salāmu ‘alaynā wa ‘alā ‘ibādillāhiṣ-ṣāliḥīn. Ash-hadu an lā ilāha illallāh, wa ash-hadu anna Muḥammadan ‘abduhu wa rasūluh.",
            englishMeaning = "All greetings, prayers, and pure words belong to Allah. Peace be upon you, O Prophet, and the mercy of Allah and His blessings. Peace be upon us and upon the righteous servants of Allah. I bear witness that there is none worthy of worship except Allah, and I bear witness that Muhammad is His servant and Messenger.",
            pronunciationTip = "Double the 'y' in 'At-taḥiyyātu' and 'ayyuhan-nabiyyu' (Shaddah), and keep 'ṣalawāt' and 'ṭayyibāt' full and emphatic."
        ),
        SalahStep(
            stepNumber = 11,
            title = "Durood Ibrahim & Final Dua",
            arabicTitle = "الصلاة الإبراهيمية والدعاء",
            postureSummary = "Final sitting (Qa‘dah Akhirah) before ending the prayer.",
            instructions = "In the final sitting of every prayer, send blessings upon Prophet Muhammad (ﷺ) and Prophet Ibrahim (عليه السلام), then make a short sincere Dua before saying Salam.",
            arabicRecitation = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ ۝ اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ",
            transliteration = "Allāhumma ṣalli ‘alā Muḥammadin wa ‘alā āli Muḥammad, kamā ṣallayta ‘alā Ibrāhīma wa ‘alā āli Ibrāhīm, innaka Ḥamīdun Majīd. Allāhumma bārik ‘alā Muḥammadin wa ‘alā āli Muḥammad, kamā bārakta ‘alā Ibrāhīma wa ‘alā āli Ibrāhīm, innaka Ḥamīdun Majīd.",
            englishMeaning = "O Allah, send prayers upon Muhammad and the family of Muhammad as You sent prayers upon Ibrahim and the family of Ibrahim; indeed You are Praiseworthy and Glorious. O Allah, bless Muhammad and the family of Muhammad as You blessed Ibrahim and the family of Ibrahim; indeed You are Praiseworthy and Glorious.",
            pronunciationTip = "Give 'ṣalli' a strong heavy ص (ṣ) and doubled 'l', and elongate 'āli' (family of) for 2 counts so it is distinct from '‘alā'."
        ),
        SalahStep(
            stepNumber = 12,
            title = "Taslim (Ending Salah with Salam)",
            arabicTitle = "التسليم",
            postureSummary = "Turn your head gently to the right, then to the left.",
            instructions = "Turn your head toward your right shoulder and say the Salam, then turn your head toward your left shoulder and repeat the Salam. Your Salah is now complete! Follow with Astaghfirullah (3x) and post-Salah Dhikr.",
            arabicRecitation = "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ",
            transliteration = "As-salāmu ‘alaykum wa raḥmatullāh (Right, then Left)",
            englishMeaning = "May the peace and mercy of Allah be upon you.",
            pronunciationTip = "Keep the 'lā' in As-salāmu smooth (2 counts) and pronounce 'raḥmatullāh' clearly without dropping the final 'h'."
        )
    )

    val FIVE_PRAYERS_RAKAH_TABLE = listOf(
        PrayerRakahInfo("Fajr", "الفجر", "2 Sunnah", "2 Fard", "—", "—", "Loud (2 Rakahs)"),
        PrayerRakahInfo("Dhuhr", "الظهر", "4 Sunnah", "4 Fard", "2 Sunnah", "2 Nafl", "Silent"),
        PrayerRakahInfo("Asr", "العصر", "4 Ghayr Mu'akkadah", "4 Fard", "—", "—", "Silent"),
        PrayerRakahInfo("Maghrib", "المغرب", "—", "3 Fard", "2 Sunnah", "2 Nafl", "Loud (first 2), Silent (3rd)"),
        PrayerRakahInfo("Isha", "العشاء", "4 Ghayr Mu'akkadah", "4 Fard", "2 Sunnah", "3 Witr", "Loud (first 2), Silent (3rd & 4th)")
    )
}
