package app.goallive.live.data

/**
 * More Persian team names (FotMob's English full and short names), on top of the club themes and [faTeamName]'s list:
 * every team of the big European leagues, the Iranian league and the national teams.
 */
internal val TEAMS_FA: Map<String, String> = hashMapOf(
    // Premier League
    "Arsenal" to "آرسنال", "Chelsea" to "چلسی", "Liverpool" to "لیورپول", "Manchester City" to "منچسترسیتی", "Man City" to "منچسترسیتی",
    "Manchester United" to "منچستریونایتد", "Man United" to "منچستریونایتد", "Man Utd" to "منچستریونایتد", "Tottenham" to "تاتنهام",
    "Tottenham Hotspur" to "تاتنهام", "Newcastle" to "نیوکاسل", "West Ham" to "وستهم", "Brighton" to "برایتون", "Wolves" to "ولورهمپتون",
    "Nottm Forest" to "ناتینگهام فارست", "Forest" to "ناتینگهام فارست", "Bournemouth" to "بورنموث", "Leeds" to "لیدز",
    "Leicester" to "لسترسیتی", "Leicester City" to "لسترسیتی", "Ipswich" to "ایپسویچ", "Ipswich Town" to "ایپسویچ",
    "Southampton" to "ساوتهمپتون", "Sheffield United" to "شفیلد یونایتد", "Luton" to "لوتون", "Luton Town" to "لوتون", "Watford" to "واتفورد",
    "Norwich" to "نوریچ", "Norwich City" to "نوریچ", "Middlesbrough" to "میدلزبرو", "Stoke City" to "استوک‌سیتی", "West Brom" to "وست‌برومویچ",
    "Coventry" to "کاونتری", "Coventry City" to "کاونتری", "Hull City" to "هال‌سیتی", "Swansea" to "سوانزی", "Cardiff" to "کاردیف",
    // La Liga
    "Real Madrid" to "رئال مادرید", "Barcelona" to "بارسلونا", "Atletico Madrid" to "اتلتیکو مادرید", "Atlético Madrid" to "اتلتیکو مادرید",
    "Atl. Madrid" to "اتلتیکو مادرید", "Betis" to "رئال بتیس", "Celta" to "سلتاویگو", "Osasuna" to "اوساسونا", "CA Osasuna" to "اوساسونا",
    "Getafe" to "ختافه", "Mallorca" to "مایورکا", "Rayo Vallecano" to "رایو وایکانو", "Alaves" to "آلاوس", "Alavés" to "آلاوس", "Deportivo Alavés" to "آلاوس",
    "Espanyol" to "اسپانیول", "Las Palmas" to "لاس‌پالماس", "Leganes" to "لگانس", "Leganés" to "لگانس", "Valladolid" to "وایادولید",
    "Real Valladolid" to "وایادولید", "Levante" to "لوانته", "Elche" to "الچه", "Real Oviedo" to "رئال اویدو", "Oviedo" to "رئال اویدو",
    "Cadiz" to "کادیز", "Cádiz" to "کادیز", "Granada" to "گرانادا", "Almeria" to "آلمریا", "Almería" to "آلمریا",
    // Serie A
    "Inter" to "اینتر", "Internazionale" to "اینتر", "AC Milan" to "میلان", "Milan" to "میلان", "Juventus" to "یوونتوس", "Napoli" to "ناپولی",
    "Udinese" to "اودینزه", "Genoa" to "جنوا", "Cagliari" to "کالیاری", "Lecce" to "لچه", "Parma" to "پارما", "Verona" to "ورونا",
    "Hellas Verona" to "ورونا", "Empoli" to "امپولی", "Venezia" to "ونزیا", "Monza" to "مونزا", "Sassuolo" to "ساسولو", "Pisa" to "پیزا",
    "Cremonese" to "کرمونزه", "Salernitana" to "سالرنیتانا", "Frosinone" to "فروزینونه", "Sampdoria" to "سمپدوریا",
    // Bundesliga
    "Bayern München" to "بایرن مونیخ", "Bayern Munich" to "بایرن مونیخ", "Borussia Dortmund" to "دورتموند", "Dortmund" to "دورتموند",
    "Leverkusen" to "بایرلورکوزن", "Leipzig" to "لایپزیگ", "Stuttgart" to "اشتوتگارت", "Frankfurt" to "آینتراخت فرانکفورت",
    "Eintracht" to "آینتراخت فرانکفورت", "Gladbach" to "مونشن‌گلادباخ", "M'gladbach" to "مونشن‌گلادباخ", "Wolfsburg" to "ولفسبورگ",
    "Freiburg" to "فرایبورگ", "SC Freiburg" to "فرایبورگ", "Hoffenheim" to "هوفنهایم", "TSG Hoffenheim" to "هوفنهایم", "Mainz" to "ماینتس",
    "Mainz 05" to "ماینتس", "FSV Mainz 05" to "ماینتس", "Werder Bremen" to "وردربرمن", "Bremen" to "وردربرمن", "Augsburg" to "آگسبورگ",
    "FC Augsburg" to "آگسبورگ", "Union Berlin" to "اونیون برلین", "1. FC Union Berlin" to "اونیون برلین", "Heidenheim" to "هایدنهایم",
    "1. FC Heidenheim" to "هایدنهایم", "St. Pauli" to "سنت پائولی", "FC St. Pauli" to "سنت پائولی", "Holstein Kiel" to "هولشتاین کیل",
    "Bochum" to "بوخوم", "VfL Bochum" to "بوخوم", "Hamburger SV" to "هامبورگ", "Hamburg" to "هامبورگ", "1. FC Köln" to "کلن", "Köln" to "کلن",
    "FC Köln" to "کلن", "Schalke 04" to "شالکه", "Hertha BSC" to "هرتابرلین", "Darmstadt" to "دارمشتات",
    // Ligue 1
    "Paris Saint-Germain" to "پاری‌سن‌ژرمن", "PSG" to "پاری‌سن‌ژرمن", "Paris SG" to "پاری‌سن‌ژرمن", "Nice" to "نیس", "Lens" to "لانس",
    "Rennes" to "رن", "Stade Rennais" to "رن", "Strasbourg" to "استراسبورگ", "Nantes" to "نانت", "Toulouse" to "تولوز", "Brest" to "برست",
    "Stade Brestois" to "برست", "Reims" to "رنس", "Montpellier" to "مونپلیه", "Auxerre" to "اوسر", "Angers" to "آنژه", "Le Havre" to "لوهاور",
    "Saint-Etienne" to "سنت‌اتین", "St Etienne" to "سنت‌اتین", "Lorient" to "لوریان", "Metz" to "متز", "Paris FC" to "پاری اف‌سی",
    // Europe, others
    "Porto" to "پورتو", "Sporting" to "اسپورتینگ", "Sporting Lisbon" to "اسپورتینگ", "Braga" to "براگا", "SC Braga" to "براگا",
    "PSV" to "پی‌اس‌وی", "AZ Alkmaar" to "آلکمار", "Twente" to "توئنته", "Fenerbahce" to "فنرباغچه", "Beşiktaş" to "بشیکتاش",
    "Trabzonspor" to "ترابزون‌اسپور", "Rangers" to "رنجرز", "Anderlecht" to "آندرلخت", "Genk" to "گنک", "Basel" to "بازل",
    "Red Bull Salzburg" to "سالزبورگ", "Sparta Prague" to "اسپارتاپراگ", "Ferencvaros" to "فرنتسواروش", "Ferencváros" to "فرنتسواروش",
    "Midtjylland" to "میدتیلند", "Malmö FF" to "مالمو", "Molde" to "مولده", "Legia Warsaw" to "لگیا ورشو", "Lech Poznan" to "لخ پوزنان",
    "Al Sadd" to "السد", "Al-Sadd" to "السد", "Al Duhail" to "الدحیل", "Al-Duhail" to "الدحیل", "Al Gharafa" to "الغرافه",
    "Al Wahda" to "الوحده", "Al Ain" to "العین", "Shabab Al Ahli" to "شباب الاهلی", "Al Sharjah" to "الشارجه", "Sharjah" to "الشارجه",
    "Al Shabab" to "الشباب", "Al Taawoun" to "التعاون", "Al Qadsiah" to "القادسیه", "Al Ettifaq" to "الاتفاق", "Al Fateh" to "الفتح",
    "Pakhtakor" to "پاختاکور", "Nasaf" to "نساف", "Al Rayyan" to "الریان", "Al Wakrah" to "الوکره", "Al Zawraa" to "الزورا",
    "Al Quwa Al Jawiya" to "القوه الجویه", "Al Shorta" to "الشرطه", "Mohun Bagan" to "موهون باگان",
    // Iran
    "Persepolis FC" to "پرسپولیس", "Esteghlal FC" to "استقلال", "Sepahan SC" to "سپاهان", "Tractor Sazi" to "تراکتور", "Tractor SC" to "تراکتور",
    "Foolad FC" to "فولاد", "Foolad Khuzestan" to "فولاد", "Gol Gohar Sirjan" to "گل گهر", "Malavan FC" to "ملوان", "Zob Ahan FC" to "ذوب آهن",
    "Mes Rafsanjan FC" to "مس رفسنجان", "Havadar SC" to "هوادار", "Paykan FC" to "پیکان", "Esteghlal Khuzestan FC" to "استقلال خوزستان",
    "Kheybar" to "خیبر خرم‌آباد", "Chadormalou Ardakan" to "چادرملو", "Fajr Sepasi Shiraz" to "فجر سپاسی", "Sanat Naft Abadan" to "صنعت نفت",
    "Shams Azar Qazvin" to "شمس آذر", "Mes Kerman FC" to "مس کرمان", "Saipa" to "سایپا", "Pars Jonoubi Jam" to "پارس جنوبی جم",
    "Esteghlal Mollasani" to "استقلال ملاثانی", "Nassaji FC" to "نساجی",
    // national teams
    "USA" to "آمریکا", "United States" to "آمریکا", "Mexico" to "مکزیک", "Canada" to "کانادا", "Uruguay" to "اروگوئه", "Colombia" to "کلمبیا",
    "Chile" to "شیلی", "Ecuador" to "اکوادور", "Peru" to "پرو", "Paraguay" to "پاراگوئه", "Venezuela" to "ونزوئلا", "Bolivia" to "بولیوی",
    "Switzerland" to "سوئیس", "Austria" to "اتریش", "Denmark" to "دانمارک", "Sweden" to "سوئد", "Norway" to "نروژ", "Poland" to "لهستان",
    "Czechia" to "چک", "Czech Republic" to "چک", "Serbia" to "صربستان", "Turkey" to "ترکیه", "Türkiye" to "ترکیه", "Ukraine" to "اوکراین",
    "Scotland" to "اسکاتلند", "Wales" to "ولز", "Ireland" to "ایرلند", "Republic of Ireland" to "ایرلند", "Northern Ireland" to "ایرلند شمالی",
    "Hungary" to "مجارستان", "Romania" to "رومانی", "Greece" to "یونان", "Slovakia" to "اسلواکی", "Slovenia" to "اسلوونی", "Albania" to "آلبانی",
    "Georgia" to "گرجستان", "Russia" to "روسیه", "Finland" to "فنلاند", "Iceland" to "ایسلند", "Bosnia and Herzegovina" to "بوسنی",
    "Australia" to "استرالیا", "China" to "چین", "China PR" to "چین", "Jordan" to "اردن", "Oman" to "عمان", "Bahrain" to "بحرین",
    "Kuwait" to "کویت", "Syria" to "سوریه", "Lebanon" to "لبنان", "Palestine" to "فلسطین", "Kyrgyzstan" to "قرقیزستان",
    "Tajikistan" to "تاجیکستان", "Turkmenistan" to "ترکمنستان", "Afghanistan" to "افغانستان", "India" to "هند", "Thailand" to "تایلند",
    "Vietnam" to "ویتنام", "Indonesia" to "اندونزی", "Malaysia" to "مالزی", "North Korea" to "کره شمالی", "Hong Kong" to "هنگ‌کنگ",
    "Egypt" to "مصر", "Senegal" to "سنگال", "Nigeria" to "نیجریه", "Algeria" to "الجزایر", "Tunisia" to "تونس", "Ghana" to "غنا",
    "Cameroon" to "کامرون", "Ivory Coast" to "ساحل عاج", "Côte d'Ivoire" to "ساحل عاج", "Mali" to "مالی", "South Africa" to "آفریقای جنوبی",
    "New Zealand" to "نیوزیلند", "Costa Rica" to "کاستاریکا", "Panama" to "پاناما", "Jamaica" to "جامائیکا", "Honduras" to "هندوراس",
)
