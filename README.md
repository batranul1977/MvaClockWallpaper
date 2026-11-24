# MVA Clock Wallpaper

Live wallpaper Android (Samsung / orice Android 8+) cu cadran analogic în stilul ceasurilor Huawei/Honor:
oră digitală, dată în română, ace „sabie” albastre, secundar fluid, galaxie de stele animată,
baterie, pași de azi și vremea (Open-Meteo, fără cheie API).

## Build

1. Dezarhivează și deschide folderul în **Android Studio** (File → Open).
2. Lasă-l să facă Gradle Sync (descarcă singur Gradle și pluginul Android).
   Dacă îți propune upgrade de AGP/Gradle, poți accepta.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. APK-ul apare în `app/build/outputs/apk/debug/app-debug.apk`. Copiază-l pe telefon și instalează-l
   (permite „instalare din surse necunoscute”). Sau conectează telefonul prin USB și apasă Run.

## Pe telefon

1. Deschide aplicația **MVA Clock Wallpaper**.
2. Verifică orașul și coordonatele pentru vreme (implicit București) → **Salvează setările**.
3. **Permite numărarea pașilor** (altfel pașii apar „--”).
4. **Setează ca wallpaper** → alege ecranul.

## Note

- Pașii se numără de la miezul nopții, din senzorul hardware al telefonului. După o repornire a telefonului
  contorul pornește de la 0.
- Vremea se actualizează cel mult o dată la 30 de minute, când wallpaper-ul devine vizibil.
- Consumul de baterie scade dacă reduci „Fluiditate secundar” (de ex. 10 cadre/s). Animația se oprește
  automat când ecranul e stins sau wallpaper-ul nu e vizibil.
- Ecran de blocare: pe One UI live wallpaper-ele de la terți pot fi limitate la ecranul principal,
  în funcție de versiune.

## Noutăți v1.2
- Pași: **km în stânga**, **metri în dreapta**, bară obiectiv zilnic (verde la atingere), alternare „pași azi" ↔ „🔥 kcal" la 5 s
- **Lupă**: ține degetul pe ceas → se mărește; ridici degetul → revine (110–200%)
- **Atingere dublă** pe ceas → deschide aplicația aleasă (implicit Alarme)
- **Mod economic de noapte** (implicit 23–07): fără secundar, estompat, 1 cadru/s
- **Teme**: Albastru, Auriu, Verde, Roșu, Mov
- **Prognoză 3 ore** (alternează cu orașul) + **alertă ploaie/ninsoare** în următoarea oră
- **Istoric pași 7 zile** (grafic în aplicație, cu km pe zi)

## Noutăți v1.3 – v1.6
- Atingere dublă → opțiunea „🌤 Vremea" (Samsung/Google sau prognoza în browser)
- Lupa: o atingere mărește pentru X secunde (1–15), a doua atingere revine imediat
- Data completă: 24.11.2026
- Răsărit/apus (alternează la 5 s cu orașul și prognoza)
- Faza lunii + următoarea alarmă (între 12 și vreme)
- Animație pe bara de baterie la încărcare
- Grafic „Pași pe oră — azi" în aplicație
- Mod „doar ceas" (fără complicații)
- Salvare / restaurare setări în fișier JSON
