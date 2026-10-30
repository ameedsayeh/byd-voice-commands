#!/bin/zsh
# Starts the BYD_Tablet emulator FROM TERMINAL so macOS can grant Terminal microphone access.
# (An emulator launched by a background process can't open the Mac's mic: "coreaudio: Could not
# initialize record".) Allow the microphone prompt the first time.
SDK="$HOME/Library/Android/sdk"
echo "Starting BYD_Tablet with host microphone… (keep this window open)"
exec "$SDK/emulator/emulator" -avd BYD_Tablet -no-snapshot -gpu auto -allow-host-audio
