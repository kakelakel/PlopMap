# PlopMap – testschema

Testa grenen `plopmap/android-foundation`. Anteckna commit, telefonmodell,
Androidversion, datum och resultat **Godkänt**, **Underkänt** eller **Blockerat**
för varje rad. Använd ett eget testprojekt och spara exportfilerna vid fel.

## A. Bygg och testa i Android Studio

Öppna repots rotmapp. Installera Android SDK 35, synka Gradle och starta
en emulator, exempelvis API 35. Välj appmodulen och tryck **Run**.
I emulatorn skickar du testpositioner under **Extended controls → Location**.
Ange latitud respektive longitud i rätt fält. När positionen är aktuell kan
du spara den. Vänta in en färsk position om knappen blir inaktiv.

| ID | Gör så här | Godkänt när |
|---|---|---|
| A1 | Bygg med `gradlew.bat :app:assembleDebug` på Windows och kör appen. | Bygget lyckas och PlopMap startar. |
| A2 | Neka platsbehörighet. Tryck sedan **Aktivera GPS**, välj exakt plats och skicka en testposition. | Punkt går inte att spara före en färsk position. Status och sparknapp uppdateras när en sådan finns. |
| A3 | Skapa projektet **Fälttest**. Välj **Brunn** och position 57.65000, 14.69000. Försök spara med tomt ID. Fyll sedan i `B-01`, **Bra** och `Åäö – kontroll`. | Tomt ID markeras. Efter giltig sparning visas en punkt och projektet har ett objekt. |
| A4 | Välj **Linje**. Lägg till 57.65000, 14.69000 och 57.65000, 14.69020. Spara som `L-01`. | Spara aktiveras efter två brytpunkter. Översikten visar en linje och två objekt i projektet. |
| A5 | Välj **Gräsyta**. Lägg till 57.65000, 14.69000, sedan 57.65000, 14.69020, sedan 57.65015, 14.69020. Ångra sista hörnet, lägg till det igen och spara som `Y-01`. | Hörnräknaren minskar vid ångra. Spara blir tillgängligt igen vid tre hörn. Översikten visar en sluten yta. |
| A6 | Mät en yta med de fyra positionerna NV, SO, NO, SV i den ordningen. Kombinera latituderna 57.65000/57.65015 och longituderna 14.69000/14.69020. | Appen avvisar den korsande polygonen och behåller utkastet för rättning. |
| A7 | Byt projekt medan utkastet finns och välj **Fortsätt mäta**. Upprepa, bekräfta byte till **Standard** och starta om appen. Byt tillbaka till **Fälttest**. | Avbryt behåller utkastet. Bekräftat byte öppnar Standard. Efter omstart finns de tre sparade objekten kvar i Fälttest. Objekt från äldre versioner utan projektnamn visas i Standard. |
| A8 | Testa ett utkast vid skärmrotation. Välj sedan export och avbryt Androids filväljare. Exportera på nytt och välj en lokal mapp. | Utkastets hörn finns kvar efter rotation. Val av filformat och export går att upprepa efter avbrott. |

## B. Export i QGIS

Exportera från **Fälttest** med både **GeoJSON** och **Shapefile (ZIP)**.
Packa upp ZIP-filen och lägg till `.shp`-filerna som vektorlager i QGIS.
Om QGIS delar GeoJSON efter geometrityp, lägg till alla geometrier.

| ID | Kontroll | Godkänt när |
|---|---|---|
| B1 | Öppna GeoJSON. Granska karta och attribut. | En brunn, en linje och en yta ligger i Nässjö. ID, projekt, skick och anteckning stämmer. |
| B2 | Öppna Shapefile-lagren `punkter`, `linjer`, `ytor` och `brytpunkter`. | Lagren har 1, 1, 1 och 5 objekt. Koordinatsystemet är WGS 84 (EPSG:4326). Inga objekt från andra projekt finns med. |
| B3 | Öppna attributtabellerna. Jämför `uuid` mellan brytpunkter och geometrilager. Kontrollera `horn_nr`, `hojd_m`, `hojdref` och `plan_m`. | Linjen har två kopplade brytpunkter och ytan tre. Tillgängliga mätvärden stämmer med källan. Saknade numeriska mätvärden är tomma. |
| B4 | Kontrollera `anteckn` på brunnen. Kör **Kontrollera giltighet** på ytorna. | Svenska tecken visas rätt och ytan är giltig. Shapefile lagrar höjd i attribut och textfält kan kortas till DBF-fältets bredd. |

## C. Fälttest på fysisk telefon

| ID | Gör så här | Godkänt när |
|---|---|---|
| C1 | Stäng av Wi-Fi och mobildata, behåll plats aktiverad och invänta GPS utomhus. Mät ett objekt, starta om appen och exportera till en lokal mapp. | Inmätning, återöppning och export fungerar offline när telefonen har en färsk fix. |
| C2 | Mät samma kända referenspunkt minst fem gånger. Prova gärna fri sikt och närhet till träd eller vägg. | Varje försök sparar mätkälla och tillgänglig osäkerhet. Markeringen preliminär visas vid okänd eller rapporterad osäkerhet över 0,20 m. |
| C3 | Jämför exportens punkter med referensen i QGIS i ett metriskt koordinatsystem, exempelvis SWEREF 99 TM. Anteckna planavvikelsen per försök. | Det faktiska felet är uppmätt. Endast försök inom 0,20 m räknas som godkända för positionstoleransen. |
| C4 | Jämför höjd mot en referens med samma höjdreferens eller efter korrekt omräkning. | Höjdskillnad och höjdreferens finns dokumenterade. Höjd har ännu ingen beslutad acceptansgräns. |

## Kommande acceptanstester

- **RTK**. Mät kända punkter med en extern mottagare, logga fixstatus och
  verifiera planavvikelse mot 0,20 m vid upprepade mätningar.
- **Flygbild**. Klicka på gräsytor i georefererade flygbilder vid asfalt,
  grus och skugga. Jämför tid för förslag plus korrigering med manuell
  ritning. Kontrollera att källan följer med vid export.
- **Objektmallar, GeoPackage och SWEREF-export**. Skapa en egen mall och
  jämför fälttyper, obligatoriska värden, geometri och referenssystem i QGIS.
