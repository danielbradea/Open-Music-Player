# OMPlayer

Aplicație Android nativă (Kotlin + Compose), pentru Android 12 / API 31 sau mai nou.

## Funcții

- Catalog din folderul dedicat `Music/OpenMusic`, ordonat după titlu, cu titlu, artist și album, inclusiv în Android Auto.
- Permisiune audio solicitată pe telefon; biblioteca se actualizează la revenirea în aplicație sau manual.
- ExoPlayer într-un MediaLibraryService, cu redare în fundal și controale media de sistem.
- Tratarea audio focus și pauză la deconectarea căștilor.
- Catalog paginat expus către Android Auto și rezolvarea pieselor după ID.
- Rezolvarea cererilor de redare vocală după titlu sau artist (prima potrivire).
- Redare, pauză și navigare între piesele listei pe telefon.
- Descărcare audio din link cu yt-dlp și FFmpeg, direct pe telefon: MP3, progres, anulare și notificare în fundal.
- Primire prin meniul Distribuie din YouTube / YouTube Music, cu pornire automată și coadă pentru linkurile trimise în timpul unei descărcări.
- Încorporarea miniaturii sursei drept copertă în MP3, când sursa oferă o imagine.
- Apăsare lungă pe o melodie pentru ștergere, cu confirmare; fișierul este șters din `Music/OpenMusic` și eliminat din lista de redare. Pentru fișiere copiate de altă aplicație, Android poate cere suplimentar acordul de ștergere.

## Interfață și playlisturi

- Player complet cu copertă, bară de progres, shuffle și repetare; player compact sub bibliotecă.
- În orientare orizontală, playerul și lista playlistului sunt afișate alăturat.
- Playlisturi salvate pe telefon. Crearea se face din meniul principal; apăsarea lungă pe un playlist deschide editarea și ștergerea.
- Coperți și meniuri pentru fiecare melodie, căutare din lupă și actualizare prin tragere în jos.
- Setări pentru limba română sau engleză și actualizarea manuală a motorului de descărcare.

## Descărcare audio

Din YouTube sau YouTube Music, alege **Distribuie → OMPlayer**. Aplicația extrage linkul din mesaj și începe descărcarea automat, inclusiv dacă era deja deschisă. Alte linkuri distribuite în timpul descărcării sunt procesate pe rând. Anularea oprește descărcarea curentă și golește coada; coada nu se păstrează după oprirea procesului.

Miniatura videoclipului este convertită în JPEG și încorporată în metadatele MP3 cu yt-dlp și FFmpeg. Imaginea rămâne în fișier când îl copiezi pe alt dispozitiv. Pentru o sursă fără miniatură se salvează doar sunetul; fișierele descărcate anterior nu sunt modificate.

Apasă „Descarcă audio din link”, introdu un URL HTTP/HTTPS și alege „Descarcă MP3”. Se descarcă o singură piesă, fără playlist; se extrage numai sunetul. Rezultatul este publicat prin MediaStore în `Music/OpenMusic` și biblioteca se actualizează automat. Folderul este creat automat la deschiderea aplicației și este vizibil în managerul de fișiere. Biblioteca aplicației și Android Auto afișează numai melodiile aflate direct în acest folder. Permisiunea audio este necesară pentru afișarea bibliotecii. Permisiunea notificărilor permite afișarea progresului în bara de notificări; refuzarea ei nu blochează descărcarea.

Aplicația încearcă întâi descărcarea cu motorul instalat. Dacă extragerea eșuează, actualizează yt-dlp de pe canalul stabil și reîncearcă o singură dată. Motorul poate fi actualizat și manual din Setări. Componentele EJS necesare sunt încărcate din proiectul oficial yt-dlp. Dacă sursa refuză accesul, dialogul afișează eroarea concretă pentru diagnostic.

Descărcarea continuă când închizi dialogul sau treci în altă aplicație. Poate fi anulată din dialog sau din notificare. Fișierele temporare sunt șterse după terminare sau anulare; o descărcare întreruptă de oprirea procesului trebuie repornită. Sursele care cer autentificare, DRM sau nu sunt compatibile cu versiunea inclusă de yt-dlp pot eșua.

Integrarea folosește [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp) și [FFmpeg](https://ffmpeg.org/). Pachetele native măresc dimensiunea APK-ului. La distribuire se aplică licențele componentelor incluse.

## Rulare

Deschide directorul în Android Studio și sincronizează Gradle. Rulează configurația `app` pe un telefon cu Android 12+. Descarcă o piesă din link sau copiază fișiere audio în `Music/OpenMusic`, acordă accesul audio și selectează o piesă.

Cu un JDK compatibil cu proiectul:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Verificare manuală

1. Verifică permisiunea refuzată, acordată și revocată din setări, precum și biblioteca goală.
2. Pornește o piesă și verifică pauză, reluare, anterior și următor.
3. Blochează telefonul și verifică redarea și comenzile din notificarea media.
4. Deconectează căștile și verifică pauza; testează și întreruperea prin altă sursă audio.
5. Pentru Android Auto, acordă întâi permisiunea pe telefon. Testează cu Desktop Head Unit sau o mașină compatibilă. Pentru un APK instalat manual poate fi necesară opțiunea pentru surse necunoscute din setările de dezvoltator Android Auto.
6. În Android Auto verifică biblioteca, selectarea unei piese și comenzile de transport.

Implementarea urmează modelul Media3 documentat la https://developer.android.com/media/media3/session/background-playback și https://developer.android.com/media/implement/playback-app.

## Limite

Nu există încă autentificare la sursele online sau restaurarea cozii după oprirea procesului. Catalogul include înregistrările marcate ca muzică de MediaStore. Compatibilitatea efectivă cu Android Auto necesită verificare pe DHU sau dispozitiv; declararea serviciului nu înlocuiește testarea și cerințele de distribuție.

## Licență

Codul proiectului este distribuit sub GPL-3.0, conform fișierului [LICENSE](LICENSE). Dependențele și componentele native incluse au propriile licențe; distribuirea APK-ului trebuie să respecte și obligațiile acestora.