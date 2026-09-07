#!/bin/bash
# Uninstaller for SO Analyzer Linux
rm -f "$HOME/.local/bin/so-analyzer"
rm -f "$HOME/.local/share/applications/so-analyzer.desktop"
rm -f "$HOME/.local/share/icons/hicolor/256x256/apps/so-analyzer.png"
if command -v update-desktop-database &> /dev/null; then
    update-desktop-database "$HOME/.local/share/applications" &> /dev/null || true
fi
echo "SO Analyzer has been uninstalled."
