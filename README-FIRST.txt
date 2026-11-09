ROUTINE WATCH APP  -  your Fire-Boltt Dream as the watch of the Pi reminder app
================================================================================
What it does
  * Shows a clock, what is NEXT, and today's schedule (DONE / NOW), plus dated recordings under LATER.
  * Rings at each item's time: screen wakes, vibrates, three beeps, says "It is lunch time" (if the watch has a voice).
    The alarms run from a copy kept ON the watch, so they still ring when the Pi is off or you are away from home.
  * "Brief me" button: the Pi speaks your daily briefing and the text also shows on the watch.

How the sync works (what you asked for)
  * FIRST TIME the watch connects: the Pi sends EVERYTHING (the whole schedule + dated recordings).
  * AFTER THAT the watch only says "I have version 17". The Pi answers with ONLY what changed (added / renamed /
    changed / deleted items). If you edit the Pi while the watch is away, the watch gets just those changes when it
    is back on the home Wi-Fi. If nothing changed the answer is about 170 bytes.
  * The watch checks every answer ("do I now hold exactly what the Pi says?"). If something is ever missing it
    asks for a full copy again by itself. If the Pi's data is ever reset, the watch also takes a full copy.
  * While the app is open the news arrives within about a second. With the screen off the watch looks every 15 minutes.

PART 1 - THE PI (5 minutes)
  1. Copy these into the routine-reminder folder on the Pi (replace reminder_app.py):
        watch_api.py    reminder_app.py
     (nothing to install - it uses only what Python already has)
  2. Restart the reminder app. On the HOME page, under "Brief me", a small line appears (just a status, not a button):
        orange dot   "Watch not connected   code 123 456"   <- type this code on the watch the first time
        green dot    "Watch connected"                       <- the watch checked in during the last 20 minutes
        grey dot     "Watch not connected"                   <- paired before, but away / switched off right now
     The code is shown only until a watch has connected once. The Pi must be on the same home Wi-Fi as the watch.
     If you ever need the code or the Pi's address again, run on the Pi:   python3 watch_api.py --info
     The watch link uses port 8765 (TCP) and 8766 (UDP, for "Find Pi"). The Pi OS has no firewall by default.

PART 2 - MAKE THE APK (I cannot make it for you: the Android build tools are blocked where I work)
  Folder: routine-watch/   (everything needed is inside; no extra downloads from you)
  ROUTE A - GitHub builds it for you (free, no software to install):
     1. Make a free account at github.com and click  +  >  New repository.  Name: routine-watch. Private is fine.
     2. On the new repository page click "uploading an existing file". Unzip routine-watch-android.zip on your PC,
        open the routine-watch folder, select EVERYTHING inside it (including the .github folder) and drag it into the
        browser. Click "Commit changes".
     3. Click the "Actions" tab. A run called "Build the watch APK" starts by itself (about 5 minutes; green tick = done).
        If there is no run, click "Add file > Create new file", type the name  .github/workflows/build-apk.yml , paste the
        contents of that file from the folder, and commit.
     4. Click the finished run, scroll down to "Artifacts", click routine-watch-apk. It downloads a zip; inside is
        app-debug.apk. Rename it routine-watch.apk.
  ROUTE B - Android Studio on your Windows PC:
     1. Install Android Studio (free). File > Open > choose the routine-watch folder. Wait for "Gradle sync" to finish
        (first time it downloads a lot).
     2. Build > Build Bundle(s) / APK(s) > Build APK(s). When it says done click "locate". That is app-debug.apk.

PART 3 - PUT THE APK ON THE WATCH
  First on the watch: Settings > allow "Install unknown apps" (or "Unknown sources") for the browser / file app you use.
  OPTION 1 (no PC tools): on the Pi, put routine-watch.apk in the routine-reminder folder and run
        python3 -m http.server 8000
     On the watch's browser open   http://<Pi address>:8000/routine-watch.apk   (address from:  python3 watch_api.py --info),
     download it, tap it, Install. Stop the server on the Pi with Ctrl+C afterwards.
  OPTION 2 (PC + adb): on the watch Settings > About, tap "Build number" 7 times, then Developer options > turn on
     ADB debugging (and wireless debugging if there is one). On the PC (platform-tools):   adb install routine-watch.apk
  Then open the app "Routine" ONCE (Android only lets an app set alarms after it has been opened once).

PART 4 - FIRST CONNECTION
  On the watch open Routine > Setup > Find Pi. Tap the Pi if it lists it. Type the 6-digit code from the Pi's home screen
  with the on-screen number pad > Connect. It says "Connected! Got N items." That was the FULL copy. From now on only
  changes travel. If "Find Pi" finds nothing (some routers stop devices talking to each other), type the Pi's address
  with the number pad (dots included) instead.
  New code (e.g. to cut off a lost watch): on the Pi run   python3 watch_api.py --newcode   then restart the reminder app.
  The home page shows the new code again; connect the watch again with it.

IF SOMETHING GOES WRONG
  "Pi not reachable"      watch not on the home Wi-Fi, or the reminder app is not running. The saved schedule still works.
  "Wrong code"            look at the code on the Pi's home page (or run  python3 watch_api.py --info ); Setup again.
  Alarm did not ring      open the app once; check the watch is not set to kill background apps / battery saver for "Routine".
  Screen looks cramped    tell me the watch's screen size and I will adjust the sizes.
  On the Pi:              python3 watch_api.py --info     prints the address and the code.

HONEST LIMITS
  * Tested here: the Pi side (28 checks) and the sync logic of the Android app against the real Pi code (28 checks:
    first full copy, changes only, deletes, missed changes, repair, long wait, wrong code, auto-find, the clock rules),
    and the whole Android code compiles against Android 8.1. NOT tested: the screens, the vibration/beeps/voice and the
    alarms on a real watch, and the APK build itself (it happens on GitHub / your PC). The first install is the real test -
    tell me exactly what you see and I will fix it.
  * Away from home (4G only) the watch cannot reach the Pi; it keeps ringing from its saved copy. Remote access
    (e.g. Tailscale) can be added later if you want it.
  * The watch only READS the schedule from the Pi (you edit on the Pi). Editing on the watch, and playing recordings
    on the watch, are not in this first version.
  * The link is plain HTTP on your home Wi-Fi protected by the 6-digit code: fine for a home network, not for public Wi-Fi.
  * The voice on the watch ("It is lunch time") works only if the watch has a text-to-speech voice installed; beeps and
    vibration work either way.
