# UZ VPN

UZ VPN is an ad-free Android WireGuard client prototype.

## Features
- Native Kotlin Android application
- WireGuard tunnel backend
- Android VPN permission flow
- Manual WireGuard configuration input
- GitHub Actions debug APK build

## Important
A working WireGuard server and a valid client configuration are required. This repository does not include VPN servers, free exit nodes, or private keys. Use only servers you operate or trust.

The app does not intentionally persist the pasted configuration. Do not commit real .conf files or private keys.

## Build
Push to main or manually run the Build Android APK workflow. Download the UZ-VPN-debug artifact from the completed Actions run.

## Package
com.uzvpn.app
