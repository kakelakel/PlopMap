# PlopMap

Gratis Androidapp för GIS-inmätning och inventering i fält.

Den första prototypen sparar brunnspunkter lokalt med brunns-ID, skick och
anteckning. GPS-källa, mättid, uppskattad osäkerhet i plan och höjd samt
höjdreferens följer med i en GeoJSON-export som kan öppnas i QGIS.
En punkt markeras preliminär när telefonen rapporterar en större uppskattad
osäkerhet än 20 cm eller saknar en uppskattning. Gränsen är ett kvalitetsmål
för inmätning i plan. Verifiera faktisk noggrannhet mot kända punkter.

## Bygg och prova

Öppna projektet i Android Studio med SDK 35, Gradle 8.9 och JDK 17 eller kör
`gradle :app:assembleDebug` med samma verktyg. GitHub Actions bygger en
debug-APK och lägger upp den som byggartefakt.

1. Installera APK:n på en Androidtelefon och starta appen utomhus.
2. Ge appen exakt platsbehörighet och invänta en GPS-position.
3. Spara två brunnar med olika ID och kontrollera att antalet sparade punkter
   finns kvar när appen startas om.
4. Exportera GeoJSON till en lokal mapp. Öppna filen i QGIS och granska punkternas
   läge, attribut och uppskattade mätosäkerhet. Höjdvärdet i geometrin, när det
   finns, är i meter över WGS 84-ellipsoiden.
5. Prova flödet utan datatrafik med GPS aktiverad.

## Nästa leveranser

- Projekt, redigerbara objektmallar, kartvy, linjer och polygoner med höjd
  och mätkvalitet per hörn.
- Extern GNSS med RTK och fältverifiering mot kända referenspunkter för
  20 cm i plan. Separat mål för höjd.
- Shapefile och GeoPackage, med SWEREF 99 TM och hörnpunktslager.
- Klickstyrda ytförslag från georefererade flygbilder för snabb basinventering.
  Börja med gräsytor och jämför tiden för förslag plus korrigering med manuell
  ritning. Spara källa och håll bildtolkad geometri spårbar vid senare inmätning.
