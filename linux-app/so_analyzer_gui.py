#!/usr/bin/env python3
"""
SO Analyzer - Linux Desktop Application
A standalone GUI tool for inspecting and analyzing ELF shared object (.so) binaries on Linux.
Provides header decoding, section size breakdown, symbol table browsing, JNI detection, and security checks.
"""

import sys
import os
import struct
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

# --- ELF Parsing Engine ---

EI_MAG0 = 0
EI_MAG1 = 1
EI_MAG2 = 2
EI_MAG3 = 3
EI_CLASS = 4
EI_DATA = 5
EI_VERSION = 6
EI_OSABI = 7

ELFCLASS32 = 1
ELFCLASS64 = 2

ELFDATA2LSB = 1  # Little endian
ELFDATA2MSB = 2  # Big endian

MACHINE_NAMES = {
    0x00: "No specific instruction set",
    0x02: "SPARC",
    0x03: "x86 (Intel 80386)",
    0x08: "MIPS",
    0x14: "PowerPC",
    0x28: "ARM (32-bit)",
    0x32: "IA-64 (Itanium)",
    0x3E: "AMD x86-64",
    0xB7: "AArch64 (ARM 64-bit)",
    0xF3: "RISC-V",
}

ET_TYPES = {
    0: "ET_NONE (Unknown)",
    1: "ET_REL (Relocatable object)",
    2: "ET_EXEC (Executable)",
    3: "ET_DYN (Shared object / .so)",
    4: "ET_CORE (Core dump)"
}

SHT_TYPES = {
    0: "SHT_NULL",
    1: "SHT_PROGBITS",
    2: "SHT_SYMTAB",
    3: "SHT_STRTAB",
    4: "SHT_RELA",
    5: "SHT_HASH",
    6: "SHT_DYNAMIC",
    7: "SHT_NOTE",
    8: "SHT_NOBITS",
    9: "SHT_REL",
    10: "SHT_SHLIB",
    11: "SHT_DYNSYM",
    14: "SHT_INIT_ARRAY",
    15: "SHT_FINI_ARRAY",
    16: "SHT_PREINIT_ARRAY",
    0x6ffffff6: "SHT_GNU_HASH",
    0x6ffffffd: "SHT_GNU_verdef",
    0x6ffffffe: "SHT_GNU_verneed",
    0x6fffffff: "SHT_GNU_versym",
}

PT_TYPES = {
    0: "PT_NULL",
    1: "PT_LOAD",
    2: "PT_DYNAMIC",
    3: "PT_INTERP",
    4: "PT_NOTE",
    5: "PT_SHLIB",
    6: "PT_PHDR",
    7: "PT_TLS",
    0x6474e550: "PT_GNU_EH_FRAME",
    0x6474e551: "PT_GNU_STACK",
    0x6474e552: "PT_GNU_RELRO",
}

def parse_elf(filepath):
    """Parses an ELF / .so binary and returns structured metadata."""
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"File not found: {filepath}")

    file_size = os.path.getsize(filepath)
    with open(filepath, "rb") as f:
        data = f.read()

    if len(data) < 16 or data[0:4] != b"\x7fELF":
        raise ValueError("Not a valid ELF binary. Magic bytes \\x7fELF not found.")

    is_64 = (data[EI_CLASS] == ELFCLASS64)
    endian_flag = data[EI_DATA]
    endian = "<" if endian_flag == ELFDATA2LSB else ">"

    bitness = "64-bit" if is_64 else "32-bit"
    endian_str = "Little Endian" if endian_flag == ELFDATA2LSB else "Big Endian"

    if is_64:
        header_fmt = f"{endian}HHIQQQIHHHHHH"
        fields = struct.unpack_from(header_fmt, data, 16)
        e_type, e_machine, e_version, e_entry, e_phoff, e_shoff, e_flags, e_ehsize, e_phentsize, e_phnum, e_shentsize, e_shnum, e_shstrndx = fields
    else:
        header_fmt = f"{endian}HHIIIIIHHHHHH"
        fields = struct.unpack_from(header_fmt, data, 16)
        e_type, e_machine, e_version, e_entry, e_phoff, e_shoff, e_flags, e_ehsize, e_phentsize, e_phnum, e_shentsize, e_shnum, e_shstrndx = fields

    machine_name = MACHINE_NAMES.get(e_machine, f"Unknown machine (0x{e_machine:X})")
    type_name = ET_TYPES.get(e_type, f"Type (0x{e_type:X})")

    # Read Section Headers and Section String Table
    sections = []
    shstrtab_data = b""

    # First pass: find shstrtab offset and size
    if 0 <= e_shstrndx < e_shnum and e_shoff > 0:
        sh_offset = e_shoff + e_shstrndx * e_shentsize
        if is_64:
            sh_fields = struct.unpack_from(f"{endian}IIQQQQIIQQ", data, sh_offset)
            shstr_offset, shstr_size = sh_fields[4], sh_fields[5]
        else:
            sh_fields = struct.unpack_from(f"{endian}IIIIIIIIII", data, sh_offset)
            shstr_offset, shstr_size = sh_fields[4], sh_fields[5]
        if shstr_offset + shstr_size <= len(data):
            shstrtab_data = data[shstr_offset:shstr_offset + shstr_size]

    def get_sh_name(offset):
        if not shstrtab_data or offset >= len(shstrtab_data):
            return ""
        end = shstrtab_data.find(b"\x00", offset)
        if end == -1:
            end = len(shstrtab_data)
        return shstrtab_data[offset:end].decode("ascii", errors="replace")

    dynstr_offset = 0
    dynstr_size = 0
    dynsym_offset = 0
    dynsym_size = 0
    dynsym_entsize = 0

    for i in range(e_shnum):
        sh_offset = e_shoff + i * e_shentsize
        if sh_offset + e_shentsize > len(data):
            break

        if is_64:
            name_idx, s_type, s_flags, s_addr, s_offset, s_size, s_link, s_info, s_addralign, s_entsize = struct.unpack_from(f"{endian}IIQQQQIIQQ", data, sh_offset)
        else:
            name_idx, s_type, s_flags, s_addr, s_offset, s_size, s_link, s_info, s_addralign, s_entsize = struct.unpack_from(f"{endian}IIIIIIIIII", data, sh_offset)

        name = get_sh_name(name_idx) if shstrtab_data else f"section_{i}"
        type_str = SHT_TYPES.get(s_type, f"0x{s_type:X}")

        sections.append({
            "index": i,
            "name": name,
            "type": type_str,
            "flags": s_flags,
            "addr": s_addr,
            "offset": s_offset,
            "size": s_size,
            "align": s_addralign
        })

        if name == ".dynstr":
            dynstr_offset = s_offset
            dynstr_size = s_size
        elif name == ".dynsym" or s_type == 11:
            dynsym_offset = s_offset
            dynsym_size = s_size
            dynsym_entsize = s_entsize or (24 if is_64 else 16)

    # Read Program Headers
    program_headers = []
    has_relro = False
    has_exec_stack = False

    for i in range(e_phnum):
        ph_offset = e_phoff + i * e_phentsize
        if ph_offset + e_phentsize > len(data):
            break

        if is_64:
            p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(f"{endian}IIQQQQQQ", data, ph_offset)
        else:
            p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(f"{endian}IIIIIIII", data, ph_offset)

        type_str = PT_TYPES.get(p_type, f"0x{p_type:X}")
        if p_type == 0x6474e552:  # PT_GNU_RELRO
            has_relro = True
        elif p_type == 0x6474e551:  # PT_GNU_STACK
            if p_flags & 1:  # PF_X
                has_exec_stack = True

        flag_str = ""
        flag_str += "R" if (p_flags & 4) else "-"
        flag_str += "W" if (p_flags & 2) else "-"
        flag_str += "X" if (p_flags & 1) else "-"

        program_headers.append({
            "index": i,
            "type": type_str,
            "flags": flag_str,
            "offset": p_offset,
            "vaddr": p_vaddr,
            "filesz": p_filesz,
            "memsz": p_memsz,
            "align": p_align
        })

    # Read Symbols from .dynsym and .dynstr
    symbols = []
    dynstr_data = b""
    if dynstr_offset > 0 and dynstr_size > 0 and dynstr_offset + dynstr_size <= len(data):
        dynstr_data = data[dynstr_offset:dynstr_offset + dynstr_size]

    def get_sym_name(offset):
        if not dynstr_data or offset >= len(dynstr_data):
            return ""
        end = dynstr_data.find(b"\x00", offset)
        if end == -1:
            end = len(dynstr_data)
        return dynstr_data[offset:end].decode("ascii", errors="replace")

    if dynsym_offset > 0 and dynsym_entsize > 0:
        sym_count = dynsym_size // dynsym_entsize
        for i in range(min(sym_count, 5000)):
            offset = dynsym_offset + i * dynsym_entsize
            if offset + dynsym_entsize > len(data):
                break

            if is_64:
                st_name, st_info, st_other, st_shndx, st_value, st_size = struct.unpack_from(f"{endian}IBBHQQ", data, offset)
            else:
                st_name, st_value, st_size, st_info, st_other, st_shndx = struct.unpack_from(f"{endian}IIIBBH", data, offset)

            name = get_sym_name(st_name)
            if not name:
                continue

            binding = st_info >> 4
            sym_type = st_info & 0xF

            bind_str = {0: "LOCAL", 1: "GLOBAL", 2: "WEAK"}.get(binding, str(binding))
            type_str = {0: "NOTYPE", 1: "OBJECT", 2: "FUNC", 3: "SECTION", 4: "FILE"}.get(sym_type, str(sym_type))
            is_jni = name.startswith("Java_") or name.startswith("JNI_")

            symbols.append({
                "name": name,
                "value": hex(st_value),
                "size": st_size,
                "bind": bind_str,
                "type": type_str,
                "is_jni": is_jni,
                "is_import": (st_shndx == 0)
            })

    # Security Summary
    security = {
        "pie": (e_type == 3),
        "relro": has_relro,
        "nx_stack": not has_exec_stack,
        "canary": any("stack_chk" in s["name"] for s in symbols),
        "jni_count": sum(1 for s in symbols if s["is_jni"]),
    }

    return {
        "file_name": os.path.basename(filepath),
        "file_size": file_size,
        "file_path": filepath,
        "bitness": bitness,
        "endian": endian_str,
        "machine": machine_name,
        "type": type_name,
        "entry_point": hex(e_entry),
        "section_count": len(sections),
        "program_header_count": len(program_headers),
        "symbol_count": len(symbols),
        "sections": sections,
        "program_headers": program_headers,
        "symbols": symbols,
        "security": security
    }

# --- Linux Desktop GUI Application ---

class SOAnalyzerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("SO Analyzer - Linux Native ELF Inspector")
        self.root.geometry("1050x700")
        self.root.minsize(800, 500)

        # Style & Dark Theme
        self.bg_color = "#121418"
        self.card_color = "#1B2028"
        self.accent_cyan = "#00D1FF"
        self.accent_mint = "#00E5A3"
        self.accent_amber = "#FFB300"
        self.fg_color = "#E0E5EC"

        self.root.configure(bg=self.bg_color)
        self.setup_styles()

        self.current_data = None

        self.create_ui()

    def setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")

        style.configure(".", background=self.bg_color, foreground=self.fg_color)
        style.configure("TNotebook", background=self.bg_color, borderwidth=0)
        style.configure("TNotebook.Tab", background=self.card_color, foreground=self.fg_color, padding=[12, 6])
        style.map("TNotebook.Tab",
                  background=[("selected", self.accent_cyan)],
                  foreground=[("selected", "#000000")])

        style.configure("Treeview",
                        background=self.card_color,
                        foreground=self.fg_color,
                        fieldbackground=self.card_color,
                        borderwidth=0,
                        rowheight=24)
        style.map("Treeview", background=[("selected", "#2A3644")])
        style.configure("Treeview.Heading",
                        background="#232B36",
                        foreground=self.accent_cyan,
                        font=("Helvetica", 9, "bold"))

    def create_ui(self):
        # Top Header Bar
        top_bar = tk.Frame(self.root, bg=self.card_color, height=60, padx=16, pady=10)
        top_bar.pack(fill=tk.X, side=tk.TOP)

        title_label = tk.Label(top_bar, text="SO Analyzer", font=("Helvetica", 16, "bold"), fg=self.accent_cyan, bg=self.card_color)
        title_label.pack(side=tk.LEFT)

        subtitle = tk.Label(top_bar, text="Native Linux ELF Shared Library Inspector", font=("Helvetica", 10), fg="#8A99AD", bg=self.card_color)
        subtitle.pack(side=tk.LEFT, padx=12)

        open_btn = tk.Button(top_bar, text="📂 Open .so File", font=("Helvetica", 10, "bold"),
                             bg=self.accent_cyan, fg="#000000", activebackground="#00B8E6",
                             relief=tk.FLAT, padx=12, pady=6, cursor="hand2", command=self.open_file)
        open_btn.pack(side=tk.RIGHT, padx=6)

        # Main Notebook Tabs
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=12, pady=10)

        # Tab 1: Overview
        self.overview_frame = tk.Frame(self.notebook, bg=self.bg_color, padx=16, pady=16)
        self.notebook.add(self.overview_frame, text="  Header & Overview  ")
        self.init_overview_tab()

        # Tab 2: Sections
        self.sections_frame = tk.Frame(self.notebook, bg=self.bg_color, padx=12, pady=12)
        self.notebook.add(self.sections_frame, text="  Sections & Sizes  ")
        self.init_sections_tab()

        # Tab 3: Program Headers (Segments)
        self.segments_frame = tk.Frame(self.notebook, bg=self.bg_color, padx=12, pady=12)
        self.notebook.add(self.segments_frame, text="  Program Segments  ")
        self.init_segments_tab()

        # Tab 4: Dynamic Symbols
        self.symbols_frame = tk.Frame(self.notebook, bg=self.bg_color, padx=12, pady=12)
        self.notebook.add(self.symbols_frame, text="  Symbols & JNI  ")
        self.init_symbols_tab()

        # Bottom Status Bar
        self.status_bar = tk.Label(self.root, text="Ready. Click 'Open .so File' to select a binary.",
                                   bd=1, relief=tk.SUNKEN, anchor=tk.W, bg=self.card_color, fg="#8A99AD",
                                   padx=10, pady=4, font=("Helvetica", 9))
        self.status_bar.pack(side=tk.BOTTOM, fill=tk.X)

    def init_overview_tab(self):
        self.overview_text = tk.Text(self.overview_frame, bg=self.card_color, fg=self.fg_color,
                                     font=("Courier", 10), relief=tk.FLAT, padx=14, pady=14)
        self.overview_text.pack(fill=tk.BOTH, expand=True)
        self.overview_text.insert(tk.END, "No .so file loaded.\n\nClick 'Open .so File' at the top right to analyze an ELF shared object binary.")
        self.overview_text.config(state=tk.DISABLED)

    def init_sections_tab(self):
        cols = ("Index", "Name", "Type", "Address", "Offset", "Size (Bytes)", "Alignment")
        self.sections_tree = ttk.Treeview(self.sections_frame, columns=cols, show="headings")
        for c in cols:
            self.sections_tree.heading(c, text=c)
            self.sections_tree.column(c, width=120, anchor=tk.W)
        self.sections_tree.column("Index", width=60)
        self.sections_tree.column("Size (Bytes)", width=110, anchor=tk.E)

        scroll = ttk.Scrollbar(self.sections_frame, orient=tk.VERTICAL, command=self.sections_tree.yview)
        self.sections_tree.configure(yscroll=scroll.set)
        self.sections_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)

    def init_segments_tab(self):
        cols = ("Index", "Type", "Flags", "Offset", "Virtual Address", "File Size", "Memory Size", "Align")
        self.segments_tree = ttk.Treeview(self.segments_frame, columns=cols, show="headings")
        for c in cols:
            self.segments_tree.heading(c, text=c)
            self.segments_tree.column(c, width=120, anchor=tk.W)
        self.segments_tree.column("Index", width=60)
        self.segments_tree.column("Flags", width=70)

        scroll = ttk.Scrollbar(self.segments_frame, orient=tk.VERTICAL, command=self.segments_tree.yview)
        self.segments_tree.configure(yscroll=scroll.set)
        self.segments_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)

    def init_symbols_tab(self):
        # Filter controls
        filter_bar = tk.Frame(self.symbols_frame, bg=self.bg_color, pady=6)
        filter_bar.pack(fill=tk.X)

        tk.Label(filter_bar, text="Filter:", fg=self.fg_color, bg=self.bg_color).pack(side=tk.LEFT, padx=4)
        self.sym_filter_var = tk.StringVar()
        self.sym_filter_var.trace_add("write", lambda *args: self.filter_symbols())
        filter_entry = tk.Entry(filter_bar, textvariable=self.sym_filter_var, bg=self.card_color, fg=self.accent_cyan,
                                insertbackground=self.fg_color, relief=tk.FLAT, width=28)
        filter_entry.pack(side=tk.LEFT, padx=6)

        self.jni_only_var = tk.BooleanVar(value=False)
        chk = tk.Checkbutton(filter_bar, text="JNI Functions Only (Java_*)", variable=self.jni_only_var,
                             bg=self.bg_color, fg=self.accent_mint, selectcolor=self.card_color,
                             activebackground=self.bg_color, command=self.filter_symbols)
        chk.pack(side=tk.LEFT, padx=12)

        self.sym_count_lbl = tk.Label(filter_bar, text="", fg="#8A99AD", bg=self.bg_color)
        self.sym_count_lbl.pack(side=tk.RIGHT, padx=6)

        cols = ("Name", "Type", "Bind", "Value", "Size (Bytes)", "Class")
        self.symbols_tree = ttk.Treeview(self.symbols_frame, columns=cols, show="headings")
        for c in cols:
            self.symbols_tree.heading(c, text=c)
            self.symbols_tree.column(c, width=100, anchor=tk.W)
        self.symbols_tree.column("Name", width=400)
        self.symbols_tree.column("Value", width=120)

        scroll = ttk.Scrollbar(self.symbols_frame, orient=tk.VERTICAL, command=self.symbols_tree.yview)
        self.symbols_tree.configure(yscroll=scroll.set)
        self.symbols_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)

    def open_file(self):
        path = filedialog.askopenfilename(
            title="Select .so Binary",
            filetypes=[("Shared Object / ELF Libraries", "*.so *.so.*"), ("All Files", "*.*")]
        )
        if not path:
            return

        try:
            data = parse_elf(path)
            self.current_data = data
            self.populate_ui(data)
            self.status_bar.config(text=f"Loaded: {data['file_name']} ({data['machine']}, {data['bitness']}, {data['file_size']:,} bytes)")
        except Exception as e:
            messagebox.showerror("ELF Parse Error", f"Could not analyze file:\n{str(e)}")

    def populate_ui(self, data):
        # 1. Overview text
        sec = data["security"]
        overview_content = f"""========================================================================
 SO ANALYZER - ELF BINARY INSPECTION REPORT
========================================================================
File Name:          {data['file_name']}
File Path:          {data['file_path']}
File Size:          {data['file_size']:,} bytes ({data['file_size'] / (1024*1024):.2f} MB)

--- ARCHITECTURE & FORMAT ---
Architecture:       {data['machine']}
Class:              {data['bitness']}
Data Encoding:      {data['endian']}
File Type:          {data['type']}
Entry Point:        {data['entry_point']}

--- BINARY STRUCTURE ---
Section Headers:    {data['section_count']} sections
Program Segments:   {data['program_header_count']} segments
Dynamic Symbols:    {data['symbol_count']} symbols
JNI Functions:      {sec['jni_count']} detected

--- SECURITY MITIGATIONS ---
Position Independent (PIE/PIC):  {'[PASS] Enabled' if sec['pie'] else '[WARN] Disabled'}
Stack Canary Protection:         {'[PASS] Detected (__stack_chk)' if sec['canary'] else '[WARN] Not detected'}
Non-Executable Stack (NX/DEP):   {'[PASS] Protected (PT_GNU_STACK no-exec)' if sec['nx_stack'] else '[FAIL] Executable stack detected'}
Read-Only Relocations (RELRO):   {'[PASS] PT_GNU_RELRO Segment Present' if sec['relro'] else '[WARN] No RELRO segment'}

========================================================================
"""
        self.overview_text.config(state=tk.NORMAL)
        self.overview_text.delete("1.0", tk.END)
        self.overview_text.insert(tk.END, overview_content)
        self.overview_text.config(state=tk.DISABLED)

        # 2. Sections
        for row in self.sections_tree.get_children():
            self.sections_tree.delete(row)
        for s in data["sections"]:
            self.sections_tree.insert("", tk.END, values=(
                s["index"], s["name"], s["type"], hex(s["addr"]), hex(s["offset"]), f"{s['size']:,}", s["align"]
            ))

        # 3. Segments
        for row in self.segments_tree.get_children():
            self.segments_tree.delete(row)
        for p in data["program_headers"]:
            self.segments_tree.insert("", tk.END, values=(
                p["index"], p["type"], p["flags"], hex(p["offset"]), hex(p["vaddr"]),
                f"{p['filesz']:,}", f"{p['memsz']:,}", p["align"]
            ))

        # 4. Symbols
        self.filter_symbols()

    def filter_symbols(self):
        if not self.current_data:
            return

        for row in self.symbols_tree.get_children():
            self.symbols_tree.delete(row)

        filter_text = self.sym_filter_var.get().lower().strip()
        jni_only = self.jni_only_var.get()

        matching = 0
        for s in self.current_data["symbols"]:
            if jni_only and not s["is_jni"]:
                continue
            if filter_text and filter_text not in s["name"].lower():
                continue

            category = "JNI Function" if s["is_jni"] else ("Import" if s["is_import"] else "Export")
            self.symbols_tree.insert("", tk.END, values=(
                s["name"], s["type"], s["bind"], s["value"], f"{s['size']:,}", category
            ))
            matching += 1

        self.sym_count_lbl.config(text=f"Showing {matching} of {len(self.current_data['symbols'])} symbols")

def main():
    root = tk.Tk()
    app = SOAnalyzerApp(root)
    if len(sys.argv) > 1 and os.path.exists(sys.argv[1]):
        try:
            data = parse_elf(sys.argv[1])
            app.current_data = data
            app.populate_ui(data)
            app.status_bar.config(text=f"Loaded: {data['file_name']} ({data['machine']}, {data['bitness']}, {data['file_size']:,} bytes)")
        except Exception:
            pass
    root.mainloop()

if __name__ == "__main__":
    main()
