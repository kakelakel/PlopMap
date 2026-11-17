# PlopMap

Gratis Androidapp för GIS-inmätning och inventering i fält.

Prototypen mäter brunnar, linjer och gräsytor med telefonens GPS. Du kan
samla objekt i lokala projekt och granska dem i en offlineöversikt.
Exportera projektet som GeoJSON i WGS 84 eller Shapefile-ZIP i WGS 84 eller SWEREF 99 TM. ZIP-filen har
separata lager för punkt, linje, yta och uppmätta brytpunkter, när sådana finns.
Objekt med okänd eller rapporterad planosäkerhet över 20 cm markeras
preliminära. Höjder från telefonen anges över WGS 84-ellipsoiden. SWEREF-exporten
använder en approximation utan datum- eller epokskifte för telefonens
positioner. Den ändrar planens koordinatrepresentation. Höjdreferens och
uppskattad mätosäkerhet följer med som attribut.

## Bygg och prova

Öppna repots rotmapp i Android Studio, synka projektet och installera SDK 35
via SDK Manager. Gradle Wrapper hämtar Gradle 8.9. Med JDK 17 kan du även bygga
med `gradlew.bat :app:assembleDebug` på Windows eller
`./gradlew :app:assembleDebug` på macOS/Linux. GitHub Actions publicerar
en debug-APK när byggjobbet lyckas.

Följ [testschemat](docs/TESTSCHEMA.md) för emulator, QGIS och fälttest.

## Nästa leveranser

- Redigerbara objektmallar och georefererad bakgrundskarta.
- Extern GNSS med RTK och fältverifiering mot kända referenspunkter för
  20 cm i plan. Separat mål för höjd.
- GeoPackage.
- Klickstyrda ytförslag från georefererade flygbilder för snabb basinventering.
  Börja med gräsytor och jämför tiden för förslag plus korrigering med manuell
  ritning. Spara källa och håll bildtolkad geometri spårbar vid senare inmätning.
