#!/bin/bash
# Direct runner for SO Analyzer on Linux without installing
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$DIR/so_analyzer_gui.py"
python3 "$DIR/so_analyzer_gui.py" "$@"
