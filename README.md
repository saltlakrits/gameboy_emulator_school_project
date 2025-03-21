# gbEmu
gbEmu är en Game Boy-emulator, som alltså låter en ladda Game Boy-spel (lättast med filändelsen ".gb") och spela dem. Även avlusningsverktyg finns i programmet, som används för att utveckla programmet men också kan vara intressant för den nyfikne, eller den som skrivit sitt egna Game Boy-spel.

När du öppnar programmet är inget spel laddat. En uppsättning testprogram som demonstrerar att processorinstruktionerna fungerar följer med, men för att prova att spela något spel måste användaren tillhandahålla en ROM-fil själv.

För att ladda en ROM-fil högerklickar man på fönstret och väljer "Load ROM", eller trycker på F4-knappen på tangentbordet. I högerklicksmenyn finns även andra alternativ, som att spara eller ladda emulatorns tillstånd till fil, stega genom programmet, eller öppna det utökade avlusningsfönstret. Obs: Om tillståndet sparas till fil är det lättast att ge filen ändelsen ".state", för att underlätta att hitta dem. Du behöver inte först ladda en ROM-fil om du ämnar ladda en tillståndsfil, och tillståndsfilen behöver inte heller matcha inladdad ROM-fil, om en finns.

Avlusningsfönstret består av:
- En disassemblyvy (på vänster sida) av ROM-filen och resten av minnet, men observera att majoriteten av minnet förbi adressen \$7FFF inte tillhör ROM-filen och i stället är arbetsminne. Med andra ord är detta en vy av instruktionerna från programfilen översatta till den motsvarande assembly-instruktionen. När man pausar emulatorn, eller stegar genom programmet, markeras instruktionen som emulatorn befinner sig på.
- En vy av alla processorns register (övre högra hörnet) och vad som finns i dem. Dessa uppdateras endast när emulatorn är pausad.
- En vy av alla brytpunkter (under registervyn), som man kan lägga till brytpunkter i genom panelen i avlusningsfönstret (under "Debug") alternativt genom att trycka på F9 (lägg till brytpunk) och F10 (ta bort brytpunkt). Under "Debug" kan man även ta bort samtliga skapade brytpunkter på en gång. När brytpunkter är satta kommer emulatorn automatiskt pausas när programräknaren når brytpunktens adress.
- En vy av minnet, och vad som finns i varje adress (nedre halvan av fönstret). Observera att adresserna \$0000 till \$7FFF tillhör ROM-filen, och därför oftast är mer lämpliga att läsas av som instruktioner i disassemblyvyn snarare än numeriska värden i minnesvyn.
  Observera att gbEmu inte ännu stödjer ljud!  Det saknas ävem stöd för spel som använder någon annan memory bank controller än MBC1, alternativt ingen alls. I slutet av dokumentationen finns en lista på bekräftat fungerande spel.

### Kontroller för spel:
+ WASD: pil-knapparna
+ O: A-knappen
+ K: B-knappen
+ T: Start-knappen
+ Y: Select-knappen
### Kortkommandon:
+ F1: Öppna avlusningsfönstret (och pausa emulatorn)
+ F2: Pausa/fortsätt emulation
+ F3: Ta ett steg framåt i emulationen (pausar även emulatorn)
+ F4: Ladda in (eller byt) ROM-fil
+ F5: Spara tillstånd till fil
+ F6: Ladda tillstånd från fil
+ F9: Lägg till brytpunkt
+ F10: Ta bort brytpunkt
+ F12: Starta om (och pausa) nuvarande spel

Ett urval av dessa (de relevanta för att spela spel) finns i högerklicksmenyn i spelfönstret, men allihopa finns i avlusningsfönstrets panelmeny. Avlusningsfönstret stödjer endast kortkommandona F2 och F3 för att pausa/fortsätta respektive stega framåt.
### Bekräftat fungerande spel
- Kirby's Dream Land
- Kirby's Dream Land 2
- Super Mario Land
- Zelda: Link's Awakening
- Tetris
- Bionic Commando
- Castlevania II: Belmont's Revenge
- Dr. Mario
- Metroid II: Return of Samus
- Catrap

Det är dock troligt att många andra också fungerar!