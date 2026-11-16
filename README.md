# Buffett Top 10 (Android)

A home-screen widget plus app showing 10 stocks picked by a Buffett-style screen. The widget refreshes at
8:30 AM New York time on weekdays (one hour before the NYSE opens). Tap any stock, in the widget or in the app, for details.

## How it works

1. **GitHub Action `Generate daily picks`** runs `backend/generate_picks.py` on weekdays. It sends
   `prompts/buffett_screen.md` to the Claude API (with web search, web fetch and code execution), validates the JSON, and
   commits it to `data/picks.json`.
2. **The app** downloads that file from `raw.githubusercontent.com` at 8:30 AM ET, stores it, and redraws the widget.
3. **GitHub Action `Build APK`** compiles the app in the cloud and gives you `app-debug.apk` to download.

## Setup (about 15 minutes, no Android Studio needed)

1. Create a **public** GitHub repository and upload everything in this folder (the repo must be public so the phone can
   read the raw file without a token. The picks will be public too).
2. Repo **Settings > Secrets and variables > Actions > New repository secret**: name `ANTHROPIC_API_KEY`, value is your key.
3. **Actions > Generate daily picks > Run workflow.** When it finishes, `data/picks.json` contains real picks.
   If it fails, open the run log: the script rejects anything that is not exactly 10 valid picks.
4. **Actions > Build APK > Run workflow.** Download the `BuffettPicks-debug-apk` artifact, unzip it, and copy
   `app-debug.apk` to your phone.
5. On the phone, open the APK and allow "install unknown apps" when asked. Then long-press the home screen,
   choose Widgets, and add **Buffett Top 10**.

Open the app once after installing; that is what schedules the daily refresh. If you force-stop the app,
open it again to re-arm the schedule.

## Known limits

- The Kotlin code has not been compiled by its author. If the Build APK run fails, paste the error log back to Claude.
- Android may delay the 8:30 AM job by a few minutes under battery saver; it never runs early. The widget also has a Refresh button.
- Market holidays are not skipped. The app simply re-downloads the same file.
- A single model run cannot truly read every NYSE filing. The `coverage_notes` field shows what the run says it did and
  did not cover, and the app displays it.
- This is a debug-signed APK for personal use, not a Play Store build.
- Educational output, not financial advice.
