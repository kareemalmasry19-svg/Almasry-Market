"""What the pipeline tracks. Yahoo Finance symbols (Egyptian stocks use the .CA suffix)."""

EGX_INDICES = [
    ("^CASE30", "EGX30", "مؤشر EGX30"),
]

EGX_STOCKS = [
    ("COMI.CA", "COMI", "البنك التجاري الدولي"),
    ("TMGH.CA", "TMGH", "طلعت مصطفى"),
    ("FWRY.CA", "FWRY", "فوري"),
    ("EAST.CA", "EAST", "الشرقية للدخان"),
    ("HRHO.CA", "HRHO", "إي إف جي القابضة"),
    ("ABUK.CA", "ABUK", "أبوقير للأسمدة"),
    ("SWDY.CA", "SWDY", "السويدي إليكتريك"),
    ("ETEL.CA", "ETEL", "المصرية للاتصالات"),
    ("ORAS.CA", "ORAS", "أوراسكوم كونستراكشون"),
    ("ESRS.CA", "ESRS", "حديد عز"),
    ("MFPC.CA", "MFPC", "مصر لإنتاج الأسمدة (موبكو)"),
    ("AMOC.CA", "AMOC", "الإسكندرية للزيوت المعدنية"),
    ("JUFO.CA", "JUFO", "جهينة"),
    ("PHDC.CA", "PHDC", "بالم هيلز"),
    ("EFIH.CA", "EFIH", "إي فاينانس"),
    ("CIEB.CA", "CIEB", "كريدي أجريكول مصر"),
]

EGP_PAIRS = [
    ("EGP=X", "USD/EGP", "الدولار / الجنيه"),
    ("EUREGP=X", "EUR/EGP", "اليورو / الجنيه"),
    ("GBPEGP=X", "GBP/EGP", "الإسترليني / الجنيه"),
    ("SAREGP=X", "SAR/EGP", "الريال السعودي / الجنيه"),
]

FX_MAJORS = [
    ("EURUSD=X", "EUR/USD", "اليورو / الدولار"),
    ("GBPUSD=X", "GBP/USD", "الإسترليني / الدولار"),
    ("USDJPY=X", "USD/JPY", "الدولار / الين"),
    ("USDCHF=X", "USD/CHF", "الدولار / الفرنك"),
    ("AUDUSD=X", "AUD/USD", "الأسترالي / الدولار"),
    ("USDCAD=X", "USD/CAD", "الدولار / الكندي"),
    ("NZDUSD=X", "NZD/USD", "النيوزيلندي / الدولار"),
    ("DX-Y.NYB", "DXY", "مؤشر الدولار"),
]

COMMODITIES = [
    ("GC=F", "XAU/USD", "الذهب (أوقية)"),
    ("SI=F", "XAG/USD", "الفضة (أوقية)"),
    ("BZ=F", "BRENT", "خام برنت"),
]

GLOBAL_INDICES = [
    ("^GSPC", "S&P500", "إس آند بي 500"),
    ("^IXIC", "NASDAQ", "ناسداك"),
    ("^GDAXI", "DAX", "داكس الألماني"),
    ("BTC-USD", "BTC", "بيتكوين"),
]

# Google News RSS searches (no API key needed).
NEWS_FEEDS = {
    "egypt": [
        ("البورصة المصرية", "ar", "EG"),
        ("سعر الدولار الجنيه البنك المركزي المصري", "ar", "EG"),
        ("الاقتصاد المصري", "ar", "EG"),
    ],
    "global": [
        ("forex dollar federal reserve", "en", "US"),
        ("ECB OR \"bank of japan\" OR \"bank of england\" rates", "en", "US"),
        ("gold price oil price markets", "en", "US"),
    ],
}
