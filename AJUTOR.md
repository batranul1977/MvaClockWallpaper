# MVA Clock Wallpaper — Ajutor

**Versiunea 1.7** · wallpaper animat (live wallpaper) cu ceas analogic în stil Huawei/Honor, pentru telefoane Android (testat pe Samsung, One UI).

---

## Cuprins
1. [Instalare și pornire](#1-instalare-și-pornire)
2. [Ce vezi pe cadran](#2-ce-vezi-pe-cadran)
3. [Gesturi pe ceas](#3-gesturi-pe-ceas)
4. [Setările aplicației](#4-setările-aplicației)
5. [Graficele de pași](#5-graficele-de-pași)
6. [Salvare și restaurare setări](#6-salvare-și-restaurare-setări)
7. [Probleme frecvente și soluții](#7-probleme-frecvente-și-soluții)
8. [Actualizare (build APK prin GitHub)](#8-actualizare-build-apk-prin-github)

---

## 1. Instalare și pornire

1. Instalează APK-ul (`app-debug.apk`) pe telefon. Dacă telefonul cere, permite instalarea din surse necunoscute pentru aplicația cu care ai deschis fișierul.
2. Deschide **MVA Clock Wallpaper**.
3. Apasă **„Permite numărarea pașilor”** și acceptă permisiunea *Activitate fizică*.
4. Completează **Oraș**, **Latitudine**, **Longitudine** (implicit București: 44.4268 / 26.1025) și apasă **„Salvează setările”**.
5. Apasă **„Setează ca wallpaper”** → alege **Ecran de pornire** (sau *Ambele*, dacă telefonul permite).

> Majoritatea setărilor (glisoare, bife, temă) se salvează **imediat**, fără să apeși „Salvează”. Butonul „Salvează setările” e necesar doar pentru oraș, coordonate și fluiditate.

---

## 2. Ce vezi pe cadran

| Zonă | Ce afișează |
|---|---|
| **Deasupra cadranului** | Ora digitală mare și data (ex. *Marți, 24 noiembrie*) — se poate ascunde din setări |
| **Sus, sub cifra 12** | 🌕 **Faza lunii** (calculată din dată, fără internet) și ⏰ **următoarea alarmă** (ex. `06:30` sau `Jo 06:30` dacă e peste mai mult de 24 h) |
| **Sus** | Vremea acum (ex. *☀ Senin*), temperatura, minima/maxima zilei |
| **Sus – rândul de jos** | Se schimbă la fiecare 5 secunde: **oraș** → **prognoză 3 ore** (ex. `10h ☁3°   11h ⛅4°   12h ☀5°`) → **răsărit/apus** (`☀↑ 07:28   ☀↓ 16:42`) |
| **Alertă precipitații** | Dacă în următoarea oră vine ploaie/ninsoare, în locul descrierii vremii apare cu portocaliu, ex. `☔ Ploaie ~10:00 · 70%` |
| **Stânga** | Ziua săptămânii (caseta roșie, ex. `MA`) și data completă `24.11.2026` |
| **Dreapta** | Baterie (%), ⚡ la încărcare, ora și secundele în casete, bara de baterie (roșie sub 15%). La încărcare, bara are o **reflexie animată** și un contur verde care pulsează |
| **Jos – mijloc** | 🏃 **Pași azi**; eticheta alternează la 5 s cu 🔥 **calorii (kcal)** |
| **Jos – stânga / dreapta** | Distanța: **km** în stânga, **metri** în dreapta |
| **Jos – bara subțire** | Progresul spre **obiectivul zilnic** de pași; devine **verde** când l-ai atins |

**Calcule folosite**
- Distanță = pași × lungimea pasului (din setări).
- Calorii ≈ pași × greutate (kg) × 0,00057.
- Pașii se resetează automat la miezul nopții.

---

## 3. Gesturi pe ceas

| Gest | Efect |
|---|---|
| **O atingere scurtă pe ceas** | 🔍 **Lupă**: cadranul se mărește și rămâne mărit câteva secunde (setabil 1–15 s), apoi revine singur |
| **Încă o atingere cât e mărit** | Revine imediat la mărimea normală |
| **O atingere pe zona vremii** (sus pe cadran) | Deschide aplicația aleasă — *mod implicit, recomandat pe Samsung* |
| **Atingere dublă oriunde pe ceas** | Deschide aplicația aleasă — *doar dacă ai ales acest mod în setări* |
| Apăsare lungă / glisare | Ignorate de ceas (rămân ale telefonului: editare ecran, schimbare pagină) |

> Gesturile funcționează pe **ecranul de pornire**, pe zonele fără iconițe. Pe ecranul de blocare, One UI nu transmite atingerile către wallpaper.

---

## 4. Setările aplicației

### Vreme și oraș
- **Oraș** — numele afișat pe cadran.
- **Latitudine / Longitudine** — locația pentru vreme (sursa: Open-Meteo, gratuit, fără cont). Vremea se actualizează cel mult o dată la 20 de minute, când wallpaper-ul devine vizibil.

### Aspect
- **Fluiditate secundar** (1–60 cadre/s) — mai puțin = consum mai mic de baterie.
- **Mărime ceas** (40–100%) și **Poziție verticală** (20–80%).
- **Arată ora digitală și data deasupra cadranului**.
- **Fundal**: imagine din galerie sau negru; **Întunecare imagine** (0–90%); **Cerc întunecat sub cadran** pentru lizibilitate.
- **Temă culoare**: Albastru, Auriu, Verde, Roșu, Mov (acele, marcajele, stelele, casetele).
- **Mod „doar ceas”** — ascunde vremea, pașii, bateria, data, luna și alarma; rămân acele, cifrele și ora digitală.

### Pași, distanță, calorii
- **Lungime pas** (40–100 cm, implicit 70). Sfat: înălțimea × 0,415 (ex. 175 cm → ~73 cm).
- **Obiectiv zilnic** (1.000–20.000 pași, implicit 6.000).
- **Greutate** (40–150 kg, implicit 80) — pentru calcul kcal.

### Lupă
- **Activare** — o atingere pe ceas îl mărește.
- **Mărire lupă** (110–200%, implicit 150%).
- **Cât rămâne mărit** (1–15 secunde, implicit 3).

### Mod economic de noapte
- Activat implicit, **23:00 → 07:00** (orele se pot schimba).
- Noaptea: fără secundar, culori estompate, un singur cadru pe secundă → consum minim. Dacă atingi ceasul (lupa), se afișează normal cât e mărit.

### Deschide o aplicație din ceas
- **Activat** — pornește/oprește funcția.
- **Mod**: *O atingere pe zona vremii* (recomandat) sau *Atingere dublă oriunde pe ceas*.
- **Alege aplicația**: ⏰ Alarme (implicit), 🌤 **Vremea**, sau orice aplicație instalată.
  - *Vremea* deschide aplicația Vremea Samsung → Vremea Google → iar dacă niciuna nu e disponibilă, prognoza orașului tău în browser.

---

## 5. Graficele de pași

În partea de sus a aplicației:

- **Istoric pași — ultimele 7 zile**: bare pe zile (verde = obiectiv atins), linia portocalie punctată = obiectivul, sub fiecare zi km parcurși.
- **Pași pe oră — azi**: 24 de bare (0–23), cea mai activă oră evidențiată, totalul zilei.

> Pașii sunt citiți de wallpaper când ecranul e aprins. Pașii făcuți cu ecranul stins nu se pierd, dar în graficul pe ore apar la ora la care te uiți prima dată la telefon. Istoricul mai vechi de 60 de zile se șterge automat.

---

## 6. Salvare și restaurare setări

- **„Salvează setările într-un fișier”** — creează `MvaClock_setari_AAAA-LL-ZZ.json` (setări + istoricul pe zile) unde alegi tu (ex. Descărcări, Drive).
- **„Restaurează setările din fișier”** — încarcă fișierul și repornește ecranul de setări.
- După reinstalare, **imaginea de fundal trebuie aleasă din nou** (permisiunea de acces la imagine nu se poate păstra).

---

## 7. Probleme frecvente și soluții

| Problemă | Soluție |
|---|---|
| **La atingere dublă ecranul se stinge** | E funcția Samsung „Atingeți de 2 ori pentru a opri ecranul”. Folosește modul **„O atingere pe zona vremii”** sau dezactivează funcția: *Setări → Funcții avansate → Mișcări și gesturi*. Pe unele versiuni: ții apăsat pe ecranul de pornire → *Setări*. |
| **Pașii arată `--`** | Apasă „Permite numărarea pașilor” sau: *Setări telefon → Aplicații → MVA Clock Wallpaper → Permisiuni → Activitate fizică → Permite*. |
| **Pașii par mai puțini decât în Samsung Health** | Numărătoarea pornește de la prima afișare a wallpaper-ului din ziua respectivă; după o repornire a telefonului contorul senzorului se resetează. |
| **Vremea „se încarcă…” mult timp** | Verifică internetul și coordonatele (cu punct: `44.4268`). Vremea se reîncearcă la următoarea afișare a ecranului. |
| **Nu apare alarma** | Apare doar dacă ai o alarmă activă în aplicația Ceas. Se verifică la 30 de secunde. |
| **Lupa nu reacționează** | Atinge o zonă a ceasului fără iconițe deasupra; verifică bifa „Lupă” în setări. |
| **Aplicația aleasă nu se deschide** | Încearcă alt mod de deschidere sau altă aplicație; unele versiuni Android blochează pornirea aplicațiilor din wallpaper. |
| **Wallpaper-ul nu apare pe ecranul de blocare** | One UI permite live wallpaper doar pe ecranul de pornire pe multe modele — limitare a telefonului. |
| **Consum de baterie** | Scade *Fluiditate secundar* la 10–15 cadre/s și păstrează activ modul de noapte. |
| **Fundalul a dispărut** | Alege din nou imaginea („Alege imagine de fundal din galerie”). |

---

## 8. Actualizare (build APK prin GitHub)

1. Copiază fișierele modificate în depozitul **batranul1977/MvaClockWallpaper** (upload pe GitHub sau `git push`).
2. GitHub Actions construiește automat APK-ul (fișierul `.github/workflows/build-apk.yml`).
3. Deschide fila **Actions** → ultima rulare → secțiunea **Artifacts** → descarcă **MvaClockWallpaper-apk**.
4. Instalează peste versiunea existentă — setările se păstrează.

**Structura proiectului**
```
app/src/main/java/ro/mva/clockwallpaper/
  ClockRenderer.kt          desenarea cadranului
  ClockWallpaperService.kt  wallpaper: senzori, vreme, atingeri, lupă
  MainActivity.kt           ecranul de setări
  StepHistoryView.kt        grafic 7 zile
  HourlyStepsView.kt        grafic pe ore
```
Copiile versiunilor anterioare sunt în folderele `_backup_v1.x` (nu se urcă pe GitHub).
