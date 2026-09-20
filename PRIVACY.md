# Privacy

Serein has no accounts, no sign-in, and no server. There is nothing to sign
up for and nothing to connect. Here's exactly what that means in practice.

## What Serein reads

Serein uses Android's **Usage Access** permission to read how long apps run
in the foreground, when they're opened, and how often the screen is
unlocked. This is the same data Android's own Digital Wellbeing / Screen
Time features use. Serein cannot see what you do *inside* other apps —
messages, browsing content, etc. — only how much time is spent in each app.

## Where that data goes

Nowhere but your device. Serein stores everything in a local database on
your phone. There is no backend server, no analytics SDK, and no network
request that sends your usage data anywhere. If you're offline, Serein
works exactly the same.

## Backups

Serein's local database is included in Android's own Auto Backup, which (if
you have it enabled on your device) saves it to your personal Google
account's private app-data storage — the same mechanism most Android apps
use for backup. Google, not Serein, controls and encrypts that storage;
Serein never sees or transmits it directly. You can disable this for Serein
specifically in Android's Settings → Apps → Serein → Backup, or disable
device backups entirely in your system settings.

## Third parties

None. Serein doesn't use crash reporting, analytics, or advertising
services of any kind, and doesn't share data with any third party, because
there's no channel for it to travel through in the first place.

## Zen Launcher integration

If you also use Zen Launcher, Serein can receive a broadcast from it when
an app limit is hit, entirely on-device via Android's local broadcast
system — this never leaves your phone either.

## Changes

If this ever changes — for example, if a future version adds optional
cloud sync — this file will be updated first, and any such feature will be
opt-in, not on by default.