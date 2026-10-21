// English FotMob titles -> Persian. Unknown titles fall back to the English text.
const T = {
  // match stat groups
  'Top stats': 'آمار کلیدی', 'Shots': 'شوت‌ها', 'Expected goals (xG)': 'گل مورد انتظار (xG)', 'Passes': 'پاس‌ها', 'Defence': 'دفاع', 'Defense': 'دفاع',
  'Duels': 'نبردها', 'Discipline': 'انضباطی', 'Attack': 'حمله', 'Goalkeeping': 'دروازه‌بانی',
  // match stats
  'Ball possession': 'مالکیت توپ', 'Total shots': 'کل شوت‌ها', 'Shots on target': 'شوت در چارچوب', 'Shots off target': 'شوت خارج از چارچوب',
  'Touches in opposition box': 'لمس در محوطه حریف', 'Big chances': 'موقعیت‌های عالی', 'Big chances missed': 'موقعیت عالی از دست رفته',
  'Accurate passes': 'پاس‌های دقیق', 'Yellow cards': 'کارت زرد', 'Red cards': 'کارت قرمز', 'Corners': 'کرنر', 'Blocked shots': 'شوت‌های بلاک‌شده',
  'Hit woodwork': 'برخورد به تیرک', 'Shots inside box': 'شوت از داخل محوطه', 'Shots outside box': 'شوت از بیرون محوطه',
  'xG open play': 'xG جریان بازی', 'xG set play': 'xG ضربات ایستگاهی', 'xG Non-penalty': 'xG بدون پنالتی', 'xG on target (xGOT)': 'xG در چارچوب (xGOT)',
  'Own half': 'در زمین خودی', 'Opposition half': 'در زمین حریف', 'Accurate long balls': 'پاس بلند دقیق', 'Accurate crosses': 'سانتر دقیق',
  'Throws': 'پرتاب اوت', 'Offsides': 'آفساید', 'Tackles': 'تکل', 'Interceptions': 'قطع توپ', 'Blocks': 'بلاک', 'Clearances': 'دفع توپ',
  'Keeper saves': 'سیو دروازه‌بان', 'Duels won': 'نبردهای برده', 'Ground duels won': 'نبرد زمینی برده', 'Aerial duels won': 'نبرد هوایی برده',
  'Successful dribbles': 'دریبل موفق', 'Fouls committed': 'خطا', 'Expected goals': 'گل مورد انتظار', 'Free kicks': 'ضربه آزاد',
  'Passes into final third': 'پاس به یک‌سوم پایانی', 'Expected assists (xA)': 'پاس گل مورد انتظار (xA)', 'Big chances created': 'خلق موقعیت عالی',
  // player stats
  'FotMob rating': 'نمره', 'Minutes played': 'دقایق بازی', 'Goals': 'گل', 'Assists': 'پاس گل', 'Expected goals on target (xGOT)': 'xG در چارچوب (xGOT)',
  'xG + xA': 'xG + xA', 'Chances created': 'موقعیت‌سازی', 'Shot accuracy': 'دقت شوت', 'Defensive actions': 'اقدامات دفاعی', 'Touches': 'لمس توپ',
  'Dispossessed': 'توپ از دست داده', 'Headed clearance': 'دفع با سر', 'Recoveries': 'بازپس‌گیری توپ', 'Dribbled past': 'دریبل‌خورده',
  'Was fouled': 'خطا روی او', 'Duels lost': 'نبرد باخته', 'Line breaking passes': 'پاس خط‌شکن', 'Saves': 'سیو', 'Goals conceded': 'گل خورده',
  'xGOT faced': 'xGOT دریافتی', 'Goals prevented': 'گل جلوگیری‌شده', 'Diving save': 'سیو شیرجه‌ای', 'Saves inside box': 'سیو داخل محوطه',
  'Acted as sweeper': 'خروج به‌عنوان لیبرو', 'Punches': 'مشت‌زدن', 'High claim': 'گرفتن توپ هوایی', 'Error led to goal': 'اشتباه منجر به گل',
  'Penalties won': 'پنالتی گرفته', 'Penalties conceded': 'پنالتی داده', 'Own goals': 'گل به خودی', 'Crosses': 'سانتر', 'Long balls': 'پاس بلند',
  'Ground duels': 'نبرد زمینی', 'Aerial duels': 'نبرد هوایی', 'Fouls': 'خطا', 'Shotmap': 'نقشه شوت', 'Shot attempts': 'تلاش برای شوت',
  'Total sprints': 'تعداد اسپرینت', 'Top speed': 'حداکثر سرعت', 'Total distance': 'مسافت طی‌شده', 'Distance': 'مسافت',
  // league leaders
  'Top scorer': 'گلزنان برتر', 'Goals + Assists': 'گل + پاس گل', 'Distance per 90': 'مسافت در ۹۰ دقیقه', 'Sprints per 90': 'اسپرینت در ۹۰ دقیقه',
  'Goals per 90': 'گل در ۹۰ دقیقه', 'Expected goals (xG) per 90': 'xG در ۹۰ دقیقه', 'Shots on target per 90': 'شوت در چارچوب در ۹۰',
  'Shots per 90': 'شوت در ۹۰ دقیقه', 'Accurate passes per 90': 'پاس دقیق در ۹۰', 'Accurate long balls per 90': 'پاس بلند دقیق در ۹۰',
  'Line-breaking passes per 90': 'پاس خط‌شکن در ۹۰', 'Expected assist (xA)': 'پاس گل مورد انتظار (xA)', 'Expected assist (xA) per 90': 'xA در ۹۰',
  'xG + xA per 90': 'xG + xA در ۹۰', 'Successful dribbles per 90': 'دریبل موفق در ۹۰', 'Defensive actions per 90': 'اقدام دفاعی در ۹۰',
  'Tackles per 90': 'تکل در ۹۰', 'Interceptions per 90': 'قطع توپ در ۹۰', 'Clearances per 90': 'دفع توپ در ۹۰', 'Blocks per 90': 'بلاک در ۹۰',
  'Recoveries per 90': 'بازپس‌گیری در ۹۰', 'Possession won final 3rd per 90': 'توپ‌ربایی در یک‌سوم حریف در ۹۰', 'Clean sheets': 'کلین‌شیت',
  'Save percentage': 'درصد سیو', 'Saves per 90': 'سیو در ۹۰', 'Goals conceded per 90': 'گل خورده در ۹۰', 'Fouls committed per 90': 'خطا در ۹۰',
  'Goals per match': 'گل زده در هر بازی', 'Goals conceded per match': 'گل خورده در هر بازی', 'Average possession': 'میانگین مالکیت',
  'Distance per match': 'مسافت در هر بازی', 'Attendance': 'تماشاگر', 'Squad value': 'ارزش تیم', 'xG difference': 'تفاضل xG',
  'Shots on target per match': 'شوت در چارچوب در هر بازی', 'Accurate passes per match': 'پاس دقیق در هر بازی',
  'Accurate long balls per match': 'پاس بلند دقیق در هر بازی', 'Accurate crosses per match': 'سانتر دقیق در هر بازی', 'Penalties awarded': 'پنالتی گرفته',
  'Set piece goals': 'گل از ضربات ایستگاهی', 'xG conceded': 'xG دریافتی', 'Interceptions per match': 'قطع توپ در هر بازی', 'Tackles per match': 'تکل در هر بازی',
  'Clearances per match': 'دفع توپ در هر بازی', 'Possession won final 3rd per match': 'توپ‌ربایی در یک‌سوم حریف', 'Set piece goals conceded': 'گل خورده از ضربات ایستگاهی',
  'Saves per match': 'سیو در هر بازی', 'Fouls per match': 'خطا در هر بازی',
  // player season stats
  'Rating': 'نمره', 'Matches': 'بازی', 'Started': 'بازی فیکس', 'xG': 'xG', 'xGOT': 'xGOT', 'xG excl. penalty': 'xG بدون پنالتی', 'xA': 'xA',
  'Penalty goals': 'گل پنالتی', 'Shooting': 'شوت‌زنی', 'Passing': 'پاس‌کاری', 'Possession': 'مالکیت', 'Defending': 'دفاع',
  'Successful passes': 'پاس موفق', 'Pass accuracy': 'دقت پاس', 'Successful long balls': 'پاس بلند موفق', 'Long ball accuracy': 'دقت پاس بلند',
  'Successful crosses': 'سانتر موفق', 'Cross accuracy': 'دقت سانتر', 'Dribble success': 'موفقیت دریبل', 'Touches in opposition box ': 'لمس در محوطه حریف',
  'Fouls won': 'خطا گرفته', 'Possession won final 3rd': 'توپ‌ربایی در یک‌سوم حریف', 'Duels won %': 'درصد نبرد برده', 'Aerial duels won %': 'درصد نبرد هوایی',
  'Save percentage ': 'درصد سیو', 'Goals conceded per 90 ': 'گل خورده در ۹۰', 'Penalties faced': 'پنالتی روبه‌رو', 'Penalty goals conceded': 'گل پنالتی خورده',
  'Penalty saves': 'پنالتی مهارشده', 'Errors led to goal': 'اشتباه منجر به گل', 'Discipline ': 'انضباطی',
  // player info
  'Height': 'قد', 'Shirt': 'شماره', 'Age': 'سن', 'Preferred foot': 'پای تخصصی', 'Country': 'ملیت', 'Market value': 'ارزش بازار', 'Contract end': 'پایان قرارداد',
  'Left': 'چپ', 'Right': 'راست', 'Both': 'هر دو',
  // positions & roles
  'Keeper': 'دروازه‌بان', 'Defender': 'مدافع', 'Midfielder': 'هافبک', 'Attacker': 'مهاجم', 'Coach': 'سرمربی',
  'coach': 'کادر فنی', 'keepers': 'دروازه‌بان‌ها', 'defenders': 'مدافعان', 'midfielders': 'هافبک‌ها', 'attackers': 'مهاجمان',
  // shot situations / types
  'RegularPlay': 'جریان بازی', 'FromCorner': 'از کرنر', 'SetPiece': 'ضربه ایستگاهی', 'FreeKick': 'ضربه آزاد', 'FastBreak': 'ضدحمله',
  'Penalty': 'پنالتی', 'ThrowInSetPiece': 'از اوت', 'IndividualPlay': 'حرکت انفرادی',
  'LeftFoot': 'پای چپ', 'RightFoot': 'پای راست', 'Header': 'ضربه سر', 'OtherBodyParts': 'سایر',
  'Goal': 'گل', 'AttemptSaved': 'مهار شد', 'Miss': 'به بیرون', 'Post': 'تیرک', 'Blocked': 'بلاک شد',
  // weather
  'Clear': 'صاف', 'Sunny': 'آفتابی', 'Cloudy': 'ابری', 'Partly cloudy': 'نیمه‌ابری', 'Mostly cloudy': 'عمدتاً ابری', 'Rain': 'بارانی', 'Light rain': 'باران سبک',
  'Showers': 'رگبار', 'Snow': 'برفی', 'Fog': 'مه', 'Overcast': 'تمام‌ابری', 'Fair': 'صاف', 'Mostly clear': 'عمدتاً صاف', 'Thunderstorms': 'رعد و برق',
  // status
  'FT': 'پایان', 'HT': 'بین دو نیمه', 'AET': 'پس از وقت اضافه', 'Pen': 'پنالتی', 'PP': 'به تعویق افتاد', 'Ab': 'نیمه‌کاره', 'Canc': 'لغو', 'AW': 'اعلام نتیجه', 'Int': 'متوقف',
};
export const tr = (s) => (s == null ? '' : T[s] ?? T[String(s).trim()] ?? s);
