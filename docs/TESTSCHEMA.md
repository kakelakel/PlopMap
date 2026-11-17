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

Exportera från **Fälttest** med **GeoJSON · WGS 84**, **Shapefile · WGS 84** och **Shapefile · SWEREF 99 TM**.
Packa upp ZIP-filen och lägg till `.shp`-filerna som vektorlager i QGIS.
Om QGIS delar GeoJSON efter geometrityp, lägg till alla geometrier.

| ID | Kontroll | Godkänt när |
|---|---|---|
| B1 | Öppna GeoJSON. Granska karta och attribut. | En brunn, en linje och en yta ligger i Nässjö. ID, projekt, skick och anteckning stämmer. |
| B2 | Öppna Shapefile-lagren `punkter`, `linjer`, `ytor` och `brytpunkter`. | Lagren har 1, 1, 1 och 5 objekt. Koordinatsystemet är WGS 84 (EPSG:4326). Inga objekt från andra projekt finns med. |
| B3 | Öppna attributtabellerna. Jämför `uuid` mellan brytpunkter och geometrilager. Kontrollera `horn_nr`, `hojd_m`, `hojdref` och `plan_m`. | Linjen har två kopplade brytpunkter och ytan tre. Tillgängliga mätvärden stämmer med källan. Saknade numeriska mätvärden är tomma. |
| B4 | Kontrollera `anteckn` på brunnen. Kör **Kontrollera giltighet** på ytorna. | Svenska tecken visas rätt och ytan är giltig. Shapefile lagrar höjd i attribut och textfält kan kortas till DBF-fältets bredd. |
| B5 | Öppna ZIP-lagren från SWEREF-exporten. Lägg dem ovanpå WGS 84-versionen i QGIS och kontrollera koordinaterna. | Lagren identifieras som EPSG:3006 och sammanfaller på kartan. ID, höjdreferens och mätosäkerhet är oförändrade. |
| B6 | Skapa ett eget projekt och spara en punkt vid 48.86, 2.35 i emulatorn. Prova SWEREF-export. | Vid koordinater utanför exportens geografiska intervall visas ett fel före filvalet. Export till WGS 84 fungerar fortfarande. |

## C. Fälttest på fysisk telefon

| ID | Gör så här | Godkänt när |
|---|---|---|
| C1 | Stäng av Wi-Fi och mobildata, behåll plats aktiverad och invänta GPS utomhus. Mät ett objekt, starta om appen och exportera till en lokal mapp. | Inmätning, återöppning och export fungerar offline när telefonen har en färsk fix. |
| C2 | Mät samma kända referenspunkt minst fem gånger. Prova gärna fri sikt och närhet till träd eller vägg. | Varje försök sparar mätkälla och tillgänglig osäkerhet. Markeringen preliminär visas vid okänd eller rapporterad osäkerhet över 0,20 m. |
| C3 | Jämför exportens punkter med referensen i QGIS i ett metriskt koordinatsystem, exempelvis SWEREF 99 TM. Anteckna planavvikelsen per försök. | Det faktiska felet är uppmätt. Endast försök inom 0,20 m räknas som godkända för positionstoleransen. |
| C4 | Jämför höjd mot en referens med samma höjdreferens eller efter korrekt omräkning. | Höjdskillnad och höjdreferens finns dokumenterade. Höjd har ännu ingen beslutad acceptansgräns. |

## D. GeoPackage och objektmallar

| ID | Gör så här | Godkänt när |
|---|---|---|
| D1 | Exportera projektet från A3–A5 som **GeoPackage · WGS 84**. Öppna filen i QGIS och lägg till alla lager. | En fil innehåller fyra lager i EPSG:4326. Antal objekt per lager är 1, 1, 1 och 5. Höjder och UUID-kopplingar motsvarar Shapefile-exporten. |
| D2 | Välj **Brunn → Redigera objektmall**. Skriv `Inventeringsnummer*` och `Material` på varsin rad. Försök spara med brunns-ID men utan inventeringsnummer. Fyll sedan i `INV-001` och `Gjutjärn` och spara. | Appen kräver inventeringsnummer. De två textvärdena följer med i `attribut` i GeoJSON och GeoPackage. Shapefile-ZIP har även `objektattribut.geojson` med värdena och samma UUID. |
| D3 | Byt till **Linje**. Skapa en annan mall. Starta om appen, byt tillbaka till **Brunn** och ändra eller ta bort fältet Material. Exportera den redan sparade brunnen igen. | Varje objekttyp har sin egen sparade mall. Ändrad mall påverkar nya formulär. Tidigare objekt behåller lagrade attribut. |

## E. Flygbild och ytförslag

Använd först en enkel PNG med en grön rektangel på grå bakgrund. Skapa den
till exempel i Paint. Lägg bilden i emulatorns nedladdningsmapp. Använd sedan
ett eget norrvänt ortofoto exporterat i WGS 84 och anteckna dess riktiga
kartutsnitt i QGIS.

| ID | Gör så här | Godkänt när |
|---|---|---|
| E1 | Välj **Föreslå yta från flygbild**, öppna PNG:n och ange väst 14.69000, syd 57.65000, öst 14.69100 och nord 57.65100. Tryck först på grått, sedan mitt i rektangeln. | Ingen yta föreslås på grått. På grönt visas ett polygonförslag runt rektangeln. |
| E2 | Dra ett av hörnen, använd förslaget, ge ytan ID och spara. Stäng av datatrafiken vid testet. | Utkastet går att spara offline utan GPS. Ytan markeras preliminär med källa Flygbild. Exporten innehåller koordinater inom utsnittet och tomma höjdfält. |
| E3 | Gör om med ortofotot. Testa gränser mot asfalt och grus, skuggor och en yta med inbuktning. Jämför förslaget mot ortofotot i QGIS och notera hur lång tid justeringen tar. | Förslaget går att justera före sparning. Registrera även missade ytor och ytor som fylls ut för mycket så att metoden kan jämföras med manuell ritning. |
| E4 | Ange omvänd ordning på syd och nord. Prova även att avbryta filvalet och förhandsvisningen. | Ogiltiga koordinater stoppas. Avbrott sparar inget objekt. |

## F. Extern GNSS och RTK

Använd en mottagare eller brygga med checksummekontrollerade NMEA-meddelanden
över TCP. Konfigurera GGA och GST från samma epok. NMEA-korrigeringar för RTK
hanteras av mottagaren eller dess brygga. Kontrollera mottagarens datum och
höjdreferens innan du jämför med referenspunkterna.

För ett första test i emulatorn kan du köra [NMEA-fixturen](nmea_fixture.py)
på datorn med `python docs/nmea_fixture.py`. Anslut PlopMap till
`10.0.2.2:9000`. Använd `--quality 5` för float eller `--no-gst` för en
RTK-fix utan rapporterad osäkerhet. Stoppa servern mellan försöken.

| ID | Gör så här | Godkänt när |
|---|---|---|
| F1 | Tryck **Extern GNSS via TCP**, anslut till fixturen med standardinställning och spara en brunn. | Källan är Extern GNSS, status RTK fix och uppskattad osäkerhet cirka 0,05 m. Punkten ligger vid cirka 57.65, 14.69. Ellipsoidhöjden är 258 m. |
| F2 | Anslut med `--quality 5` eller `--no-gst`. Stoppa sedan servern. | RTK float eller saknad osäkerhet ger preliminär mätning. Vid avbrott blir sparknappen inaktiv och appen visar ett fel. |
| F3 | Starta fixturen igen och anslut. Tryck sedan **Aktivera telefonens GPS** och skicka en separat emulatorposition. | Källan byts till telefonens GPS. Ingen tidigare position från mottagaren kan sparas som telefonmätning. |
| F4 | Mät upprepade gånger mot en känd punkt med verklig RTK-mottagare. Spara punkt och minst en linje. Exportera till QGIS och kontrollera fixstatus, källa, höjd och faktiska planfel. | Exporten kan spåras till mätkällan per brytpunkt. Redovisa antal avvikelser över 0,20 m i plan. Rapportera höjdavvikelse separat. |

## G. Baskarta

| ID | Gör så här | Godkänt när |
|---|---|---|
| G1 | Ha nätåtkomst, slå på **Baskarta (OpenStreetMap)** och skicka position 57.65000, 14.69000. | Vägar och andra kartdetaljer visas bakom positionspunkten, med OpenStreetMap-attribution. |
| G2 | Spara en punkt och mät in en linje i samma område. Slå av baskartan. | Objekten ligger kvar i översikten och går att exportera även när baskartan är av. |
| G3 | Koppla ned nätet efter att kartan har laddats. Återgå till samma position. | Cachade kartbilder visas när de finns. Plats, objekt och export fungerar även när delar av kartan saknas. |
