<div align="center">
  <img src="app/src/main/res/drawable-nodpi/omplayer_icon.png" alt="Iconița OMPlayer" width="112" />
  <h1>OMPlayer</h1>
  <p>Player Android pentru muzica ta, cu playlisturi, redare în fundal și descărcare audio din link.</p>
  <p><strong>Android 12+</strong> · Kotlin · Jetpack Compose · Media3</p>
</div>

## Ce poți face

- Ascultă melodiile din folderul `Music/OpenMusic`, cu titlu, artist și copertă.
- Controlează redarea din playerul complet, playerul compact sau notificarea Android.
- Folosește shuffle, repetare și bara de progres pentru a naviga în melodie.
- Creează playlisturi salvate pe telefon și schimbă-le din meniul playerului.
- Caută melodii sau artiști din lupă și actualizează biblioteca trăgând lista în jos.
- Rotește telefonul: playerul apare în stânga, iar melodiile playlistului în dreapta.
- Descarcă audio în format MP3, cu metadate și copertă atunci când sursa le oferă.
- Trimite linkuri direct din meniul **Distribuie** din YouTube sau YouTube Music.
- Alege interfața în română sau engleză din Setări.
- Accesează biblioteca prin integrarea Android Auto.

## Instalare și prima utilizare

1. Descarcă APK-ul din [Releases](https://github.com/danielbradea/Open-Music-Player/releases) sau compilează-l urmând instrucțiunile de mai jos. Instalează-l pe un dispozitiv cu **Android 12 / API 31** sau mai nou.
2. Deschide **OMPlayer** și acordă permisiunea de acces audio.
3. Copiază melodii în `Music/OpenMusic` sau descarcă o piesă dintr-un link.
4. Trage lista în jos pentru actualizare și apasă o melodie pentru redare.

Folderul este creat automat. Biblioteca afișează melodiile aflate direct în acest folder și marcate ca muzică în MediaStore.

## Biblioteca și playlisturile

| Acțiune | Cum o folosești |
| --- | --- |
| Creează un playlist | Meniul **⋮ → Playlist nou**, apoi alegi numele și melodiile. |
| Schimbă playlistul | Selectează playlistul deasupra listei sau din meniul playerului complet. |
| Afișează biblioteca completă | Alege **Toate piesele**. |
| Editează sau șterge un playlist | Ține apăsat pe numele playlistului. Ștergerea playlistului păstrează fișierele audio. |
| Deschide opțiunile unei melodii | Apasă **⋮** în dreptul ei sau ține apăsat pe rând. |
| Adaugă sau elimină o piesă din playlist | Folosește meniul melodiei. |
| Șterge un fișier audio | Alege **Șterge de pe telefon** și confirmă. Android poate cere acord suplimentar pentru fișiere create de alte aplicații. |
| Caută o melodie | Apasă lupa de lângă titlul bibliotecii. Căutarea se închide din **X**. |

Playlisturile se salvează local pe telefon. Playerul compact deschide playerul complet la atingerea copertei sau a titlului.

## Descărcare audio

### Din aplicație

Deschide **⋮ → Descarcă audio din link**, introdu un URL HTTP/HTTPS valid și apasă **Descarcă MP3**. Butonul se activează doar pentru un link valid, iar câmpul se golește după descărcarea reușită.

Se extrage o singură piesă, fără descărcarea unui playlist online. MP3-ul este salvat în `Music/OpenMusic`, iar biblioteca se actualizează automat. Miniatura sursei este încorporată drept copertă, când este disponibilă.

### Din YouTube sau YouTube Music

Alege **Distribuie → OMPlayer**. Aplicația preia linkul și pornește descărcarea. Linkurile trimise în timpul unei descărcări sunt puse în coadă și procesate pe rând.

### Progres și actualizarea motorului

- Descărcarea continuă când închizi dialogul sau treci în altă aplicație.
- Poți anula din dialog sau din notificare. Anularea golește și coada de linkuri.
- Aplicația încearcă întâi motorul instalat. Dacă extragerea eșuează, încearcă actualizarea yt-dlp și reia descărcarea o singură dată.
- Poți verifica manual actualizarea din **⋮ → Setări → Actualizează motorul de descărcare**.
- Permisiunea pentru notificări permite afișarea progresului în bara Android; refuzarea ei nu blochează descărcarea.

## Setări

În **⋮ → Setări** poți alege **Română** sau **English** și poți actualiza motorul de descărcare. Limba aleasă se păstrează la redeschiderea aplicației.

## Compilare

### Cerințe

- Android Studio și Android SDK configurate.
- Android SDK **37**, conform configurației proiectului.
- Java pentru pornirea Gradle; proiectul configurează daemonul Gradle cu toolchain **JDK 25**.
- Acces la internet la prima sincronizare pentru descărcarea dependențelor și a toolchainului, dacă nu sunt deja instalate.

### Clonează proiectul

```sh
git clone https://github.com/danielbradea/Open-Music-Player.git
cd Open-Music-Player
```

Deschide directorul în Android Studio, sincronizează Gradle și rulează configurația `app` pe un dispozitiv sau emulator compatibil.

### Din terminal

Pe Windows, cu `JAVA_HOME` configurat:

```powershell
.\gradlew.bat :app:assembleDebug
```

Pe Linux sau macOS:

```sh
./gradlew :app:assembleDebug
```

APK-ul rezultat se găsește în:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Nu adăuga în Git configurația locală `local.properties`, cheile de semnare sau fișierele generate de compilare.

## Build automat pe GitHub

Workflow-ul [Build Android APK](https://github.com/danielbradea/Open-Music-Player/actions/workflows/android-build.yml) compilează un APK **debug** și rulează testele unitare și Lint la fiecare push pe `main` sau pull request. Modificările doar în Markdown sau LICENSE nu declanșează buildul. Îl poți porni manual din **Actions → Build Android APK → Run workflow**.

După o rulare reușită:

1. Deschide fila **Actions** și selectează rularea.
2. În secțiunea **Artifacts**, descarcă **OMPlayer-debug-numărul_rulării**.
3. Dezarhivează fișierul ZIP și instalează `OMPlayer-debug.apk` pe telefon.

APK-ul se păstrează ca artifact timp de **30 de zile**, iar rapoartele de teste și Lint timp de **7 zile**. Linkul artifactului apare și în rezumatul rulării. Descărcarea artifactelor necesită autentificare pe GitHub. La pushul unui tag `v*`, APK-ul este publicat și în GitHub Releases, cu numele `OMPlayer-VERSIUNE.apk` și un fișier SHA-256. Fișierele APK nu sunt adăugate în sursele Git.

### Publicarea unei versiuni

Actualizează `versionName` și mărește `versionCode` în `app/build.gradle.kts`, publică modificările, apoi creează un tag cu versiunea respectivă:

```sh
git tag v1.3
git push origin v1.3
```

Folosește un tag nou pentru fiecare versiune. După compilare și verificări, workflow-ul creează release-ul și atașează APK-ul. APK-urile publicate sunt builduri debug; semnătura lor poate diferi între rulări și de APK-urile compilate local.

## Verificări

Teste unitare și analiză statică pe Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Teste pe un dispozitiv sau emulator conectat:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Pentru verificarea manuală, testează acordarea și refuzarea permisiunilor, redarea în fundal, deconectarea căștilor, orientarea ecranului, playlisturile și descărcarea cu succes, eroare sau anulare.

## Android Auto

Integrarea folosește un `MediaLibraryService` Media3 pentru a expune biblioteca și comenzile de redare. Acordă mai întâi accesul audio pe telefon.

Compatibilitatea trebuie verificată pe Desktop Head Unit sau într-o mașină compatibilă. Pentru un APK instalat manual poate fi necesară activarea surselor necunoscute în setările de dezvoltator Android Auto.

## Tehnologii

- **Kotlin și Jetpack Compose** pentru interfață.
- **Media3 / ExoPlayer** pentru redare și sesiunea media.
- **MediaStore** pentru biblioteca și salvarea fișierelor audio.
- [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp) și [FFmpeg](https://ffmpeg.org/) pentru extragere, conversie și metadate.

## Limitări

- Biblioteca este limitată la folderul `Music/OpenMusic`.
- Nu există autentificare pentru surse online sau sincronizare a playlisturilor între dispozitive.
- Coada de descărcări nu se păstrează după oprirea procesului; descărcările întrerupte trebuie repornite.
- Sursele private, cele cu DRM sau cele incompatibile cu motorul inclus pot eșua.
- Bibliotecile native pentru descărcare și conversie măresc dimensiunea APK-ului.

## Contribuții

Pentru o problemă, deschide un [issue](https://github.com/danielbradea/Open-Music-Player/issues) cu versiunea Android, pașii de reproducere și eventualele mesaje de eroare. Pentru modificări, trimite un pull request și descrie verificările efectuate.

## Licență

Codul proiectului este distribuit sub **GPL-3.0**, conform fișierului [LICENSE](LICENSE). Componentele terțe incluse au propriile licențe și obligații de distribuție.
