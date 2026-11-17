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

## Objektmallar, flygbild och GNSS

- **Objektmallar** sparas per objekttyp. Lägg till textfält i formuläret
  och markera obligatoriska fält med `*`. GeoPackage lagrar egna fält i
  kolumnen `attribut`. Shapefile-ZIP har med samma metadata som GeoJSON
  vid sidan av DBF, kopplad via objektets UUID.
- **GeoPackage** samlar punkt, linje, yta och brytpunkter i en fil i WGS 84.
- **Flygbild** öppnar ett lokalt norrvänt WGS 84-ortofoto med manuellt angivet
  kartutsnitt. Färgsegmenteringen körs offline och föreslår en konvex yta.
  Granska bilden och dra hörnen vid behov. Förslaget är preliminärt.
- **Extern GNSS** läser GGA och GST via NMEA/TCP. Mottagaren ansvarar för
  RTK-korrigeringarna. En RTK-fix utan uppskattad osäkerhet sparas som
  preliminär. Mättiden är när appen tog emot positionen.
