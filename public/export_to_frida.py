# export_to_frida.py
# Ghidra Script: Export Function Offsets & Signatures to JSON for Frida Interception
# Compatible with Ghidra GUI (Script Manager) and Headless Analyzer
#
# Usage in Ghidra GUI:
#   1. Open your target .so binary in Ghidra CodeBrowser.
#   2. Open Window -> Script Manager -> Click 'New Script' or add this script.
#   3. Run script. It will save 'ghidra_frida_export.json' in your project or home folder.
#
# Headless Usage:
#   analyzeHeadless <proj_path> <proj_name> -import libtarget.so -postScript export_to_frida.py output.json
#
# @category Android.ReverseEngineering
# @author SO Analyzer Frida Bridge

import json
import os

try:
    from ghidra.app.decompiler import DecompInterface
    from ghidra.util.task import ConsoleTaskMonitor
    GHIDRA_ENV = True
except ImportError:
    GHIDRA_ENV = False

def run():
    if not GHIDRA_ENV:
        print("[!] This script must be executed inside Ghidra (Script Manager or analyzeHeadless).")
        return

    program = currentProgram
    listing = program.getListing()
    func_manager = program.getFunctionManager()
    base_addr = program.getImageBase().getOffset()
    module_name = program.getName()

    print("[+] Starting Ghidra -> Frida Export for module: %s (Base: 0x%x)" % (module_name, base_addr))

    # Initialize Decompiler interface to extract high-level types if available
    decomp = DecompInterface()
    decomp.openProgram(program)
    monitor = ConsoleTaskMonitor()

    exported_functions = []
    functions = func_manager.getFunctions(True) # forward iteration

    count = 0
    for func in functions:
        entry_addr = func.getEntryPoint().getOffset()
        rel_offset = entry_addr - base_addr
        func_name = func.getName()
        is_jni = func_name.startswith("Java_") or "_JNIEnv" in func.getSignature().getPrototypeString()

        # Extract parameters
        params = []
        for p in func.getParameters():
            params.append({
                "name": p.getName(),
                "type": str(p.getDataType().getName())
            })

        ret_type = str(func.getReturnType().getName())
        sig = func.getSignature().getPrototypeString()

        # Generate custom Frida hook snippet
        frida_snippet = """// Hook for {name} (Offset 0x{offset:x})
var targetMod = "{module}";
var base = Module.findBaseAddress(targetMod);
if (base) {{
    var funcPtr = base.add(ptr("0x{offset:x}"));
    Interceptor.attach(funcPtr, {{
        onEnter: function(args) {{
            console.log("[+] Intercepted {name}");
            // {sig}
        }},
        onLeave: function(retval) {{
            console.log("[-] {name} returned: " + retval);
        }}
    }});
}}""".format(name=func_name, offset=rel_offset, module=module_name, sig=sig)

        exported_functions.append({
            "name": func_name,
            "offset": "0x{:x}".format(rel_offset),
            "virtual_address": "0x{:x}".format(entry_addr),
            "signature": sig,
            "return_type": ret_type,
            "parameters": params,
            "is_jni": is_jni,
            "is_external": func.isExternal(),
            "xrefs_count": len(func.getSymbol().getReferences()),
            "frida_snippet": frida_snippet
        })
        count += 1

    export_data = {
        "module_name": module_name,
        "image_base": "0x{:x}".format(base_addr),
        "total_functions": count,
        "functions": exported_functions
    }

    # Determine destination file
    args = getScriptArgs() if 'getScriptArgs' in globals() else []
    output_filename = args[0] if len(args) > 0 else "ghidra_frida_export.json"

    user_home = os.path.expanduser("~")
    dest_path = os.path.join(user_home, output_filename)

    with open(dest_path, "w") as f:
        json.dump(export_data, f, indent=2)

    print("[✓] Export completed successfully!")
    print("[✓] Total functions exported: %d" % count)
    print("[✓] Saved to: %s" % dest_path)
    print("[*] You can now import this JSON file directly into the SO Analyzer Android App!")

if __name__ == "__main__" or GHIDRA_ENV:
    run()
