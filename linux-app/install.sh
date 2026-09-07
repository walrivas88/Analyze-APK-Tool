#!/bin/bash
# ==============================================================================
# SO Analyzer - Linux Application Installer
# ==============================================================================
set -e

echo "=== Installing SO Analyzer on Linux ==="

# Check Python 3
if ! command -v python3 &> /dev/null; then
    echo "[!] Error: python3 is not installed."
    echo "    Please install it using: sudo apt install python3 (Debian/Ubuntu) or sudo dnf install python3 (Fedora)"
    exit 1
fi

# Check python3-tk
if ! python3 -c "import tkinter" &> /dev/null; then
    echo "[!] Note: Python tkinter is required for the GUI."
    echo "    If launching fails, install it with:"
    echo "    Ubuntu/Debian: sudo apt install python3-tk"
    echo "    Fedora:        sudo dnf install python3-tkinter"
    echo "    Arch Linux:    sudo pacman -S tk"
fi

INSTALL_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"
ICON_DIR="$HOME/.local/share/icons/hicolor/256x256/apps"

mkdir -p "$INSTALL_DIR"
mkdir -p "$DESKTOP_DIR"
mkdir -p "$ICON_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Copy executable
cp "$SCRIPT_DIR/so_analyzer_gui.py" "$INSTALL_DIR/so-analyzer"
chmod +x "$INSTALL_DIR/so-analyzer"

# Copy Icon if present
if [ -f "$SCRIPT_DIR/icon.png" ]; then
    cp "$SCRIPT_DIR/icon.png" "$ICON_DIR/so-analyzer.png"
    ICON_PATH="$ICON_DIR/so-analyzer.png"
else
    ICON_PATH="utilities-terminal"
fi

# Create .desktop launcher entry
cat > "$DESKTOP_DIR/so-analyzer.desktop" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=SO Analyzer
Comment=Inspect and analyze ELF .so shared libraries
Exec=$INSTALL_DIR/so-analyzer %F
Icon=$ICON_PATH
Terminal=false
Categories=Development;Utility;
MimeType=application/x-sharedlib;application/x-executable;
EOF

chmod +x "$DESKTOP_DIR/so-analyzer.desktop"

# Update desktop database if tool exists
if command -v update-desktop-database &> /dev/null; then
    update-desktop-database "$DESKTOP_DIR" &> /dev/null || true
fi

echo ""
echo "=== Installation Succeeded! ==="
echo "You can now run SO Analyzer in two ways:"
echo "1. From your Application Menu: Search for 'SO Analyzer'"
echo "2. From your terminal: run '~/.local/bin/so-analyzer' (or 'so-analyzer' if ~/.local/bin is in your PATH)"
echo ""
