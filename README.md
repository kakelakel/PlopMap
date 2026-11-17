# PlopMap

Gratis Androidapp för GIS-inmätning och inventering i fält.

Prototypen sparar brunnspunkter och gräsytor lokalt med ID, skick och
anteckning. GeoJSON-exporten kan öppnas i QGIS. Höjd rapporteras i meter över
WGS 84-ellipsoiden. För gräsytor sparas höjd, mättid och uppskattad osäkerhet
per hörn i attributet `hornmatningar`, i samma ordning som polygonens hörn.
Objekt med okänd eller rapporterad planosäkerhet över 20 cm markeras
preliminära. Verifiera faktisk noggrannhet mot kända referenspunkter.

## Bygg och prova

Öppna repots rotmapp i Android Studio, synka projektet och installera SDK 35
via SDK Manager. Gradle Wrapper hämtar Gradle 8.9. Med JDK 17 kan du även bygga
med `gradlew.bat :app:assembleDebug` på Windows eller
`./gradlew :app:assembleDebug` på macOS/Linux. GitHub Actions publicerar
en debug-APK när byggjobbet lyckas.

1. Starta appen på en telefon eller Android Studio-emulator.
2. Tryck **Aktivera GPS** och ge appen exakt platsbehörighet. I emulatorn
   kan du mata in en testposition under **Extended controls → Location**.
3. Spara en brunn med ID. Byt till **Gräsyta** och lägg till minst tre hörn
   runt ytan i ordning. Mata in en ny position i emulatorn för varje hörn.
4. Ångra och ersätt ett hörn, ange ett yt-ID och spara. Starta om appen
   och kontrollera att båda objekten är kvar.
5. Exportera GeoJSON till en lokal mapp. Kontrollera punkt, polygon och
   attribut i QGIS. Prova sedan flödet på en telefon utomhus utan datatrafik.

## Nästa leveranser

- Projekt, redigerbara objektmallar, kartvy och linjer.
- Extern GNSS med RTK och fältverifiering mot kända referenspunkter för
  20 cm i plan. Separat mål för höjd.
- Shapefile och GeoPackage, med SWEREF 99 TM och hörnpunktslager.
- Klickstyrda ytförslag från georefererade flygbilder för snabb basinventering.
  Börja med gräsytor och jämför tiden för förslag plus korrigering med manuell
  ritning. Spara källa och håll bildtolkad geometri spårbar vid senare inmätning.
