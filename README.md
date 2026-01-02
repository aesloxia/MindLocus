# MindLocus

MindLocus is a focus-blocking tool designed to help you reclaim your time from distracting apps. Instead of relying on willpower alone, it uses physical objects like an NFC-enabled card or a printed QR code, working like keys to lock and unlock your digital workspace.

I don't think there is any Android app which has physical blocking feature with any NFC or QR, so it's best to make it open-source.

## How it works

The concept is simple: you choose which apps distract you the most, and MindLocus prevents you from opening them unless you have your physical key. By requiring a physical action to unlock your phone's distractions, it forces a moment of mindfulness and makes it much harder to mindlessly scroll through social media or games.

## Download

You can download MindLocus app by clicking on Releases menu in repository, or by this link:
https://github.com/aesloxia/MindLocus/releases/download/1.0.0/mindlocus_1.0.0.apk

## Getting Started

To get the app running properly on your phone, follow these steps:

1. **Permissions**: When you first open the app, it will ask for several permissions. These are necessary for the app to detect when a blocked app is opened and to display the "Mindful Moment" screen over it. Some can be ignored, like notification blocking permission, or "Uninstall Prevention" (**don't really recommend** unless you love to bypass block by deleting app)
2. **Registration**: Go to the Settings menu to register your first key. You can use any NFC tag (like a transit card or a dedicated sticker) or any QR code. You can register multiple keys if you want to keep one at your desk and one in your wallet.
3. **App Selection**: While in Settings, scroll through your app list and check the ones you want to block during your focus sessions.
4. **Starting a Session**: Return to the main screen and tap the Scan button. Scan your registered NFC tag or QR code, and Focus Mode will activate. To unlock your apps, simply scan the same key again.

## Privacy and Data Security

MindLocus completely private and secure:

- **No Tracking**: MindLocus does not use any tracking, analytics, or telemetry. It does not monitor what you do inside other apps; it only detects which app is currently in the foreground to decide whether to show the block screen.
- **Offline by Design**: The app does not require an internet connection to function. Your data never leaves your phone.

## Future Plans

This is an ongoing project, and several features are planned for future updates:

- **Scheduled Sessions**: The ability to automatically start and end focus mode at specific times of the day.
- **Focus Statistics**: A simple dashboard to see how much time you have saved by staying out of distracting apps.
- **Strict Mode**: Creating another version of app which utilizes Accessibility Services instead of present permissions. This will make all bypasses ineffective.
- **Customization**: Options to personalize the message or appearance of the "Mindful Moment" screen. Options to change app primary color in the settings.
