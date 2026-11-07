# المصري ماركت

تطبيق Android لمتابعة وتحليل البورصة المصرية، والجنيه، والفوركس، والذهب، والأخبار.

## إزاي بيشتغل
- **`agents/`**: كود Python بيشتغل كل ساعة على GitHub Actions (`Market agents`). بيجيب الأسعار من Yahoo Finance والأخبار من Google News، ويحسب المؤشرات الفنية (RSI وMACD والمتوسطات). وفي مواعيد التقرير (9 و1 و5 و9 مساءً بتوقيت القاهرة) فريق agents بالذكاء الاصطناعي بيحلل:
  محلل فني + محلل أخبار + محلل اقتصاد وعملات، وبعدين كبير استراتيجيين بيجمعهم في تقرير واحد.
- النتيجة بتتحفظ في `market.json` على فرع `data`.
- **`android/`**: التطبيق بيقرا الملف ده ويعرضه، وبيبعت إشعار لما ينزل تقرير جديد. الـ APK بيتبني تلقائياً (`Build APK`) وبينزل في Releases ▸ latest.

## الذكاء الاصطناعي
- **Gemini (مجاني):** اعمل مفتاح من [aistudio.google.com/apikey](https://aistudio.google.com/apikey) وحطه Secret باسم `GEMINI_API_KEY`. الـ agents بيختاروا أحدث موديل Flash مجاني تلقائياً، أو حدده بـ Variable اسمه `GEMINI_MODEL`.
- **Claude (اختياري):** لو حطيت Secret اسمه `ANTHROPIC_API_KEY` بيتقدم على Gemini.
- من غير أي مفتاح، أو لو الـ AI وقع، التقرير بيبقى تحليل فني بالمؤشرات لحد المحاولة الجاية.
- تشغيل يدوي: Actions ▸ Market agents ▸ Run workflow.

## الواتساب
كل تقرير جديد بيتبعت على الواتساب عن طريق CallMeBot (مجاني):
1. احفظ رقم البوت من [callmebot.com/blog/free-api-whatsapp-messages](https://www.callmebot.com/blog/free-api-whatsapp-messages/) وابعتله: `I allow callmebot to send me messages`
2. هيرد عليك بـ apikey.
3. Secrets: `WHATSAPP_PHONE` (رقمك بكود الدولة) و `CALLMEBOT_APIKEY`.

> تحليل للمساعدة في الفهم وليس نصيحة استثمارية.
