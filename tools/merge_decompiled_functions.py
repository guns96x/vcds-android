import json
from pathlib import Path

ROOT = Path('.')
RAW_DIR = ROOT / 'reverse' / 'raw'
DECOMP_JSONL = RAW_DIR / 'decompiler.jsonl'
FUNCS_JSONL = RAW_DIR / 'functions.jsonl'

def norm_addr(a: str) -> str:
    clean = a.replace('0x', '').replace('0X', '').strip()
    return f"0x{int(clean, 16):X}"

# 1. Load existing decompiler chunks
existing_decomp = {}
with open(DECOMP_JSONL, 'r', encoding='utf-8') as f:
    for line in f:
        if line.strip():
            d = json.loads(line)
            existing_decomp[norm_addr(d['address'])] = d

print(f"Existing decompiled functions: {len(existing_decomp)}")

# 2. Load extracted report functions
with open('tools/extracted_report_funcs.json', 'r', encoding='utf-8') as f:
    report_funcs = json.load(f)

# 3. Add FUN_14007ca6c from vtable_trace.txt
ca6c_code = """undefined8 * FUN_14007ca6c(undefined8 *param_1)

{
  FUN_140098af8();
  DAT_1405a554c = 0xffffffff;
  *(undefined1 *)((longlong)param_1 + 0x4c) = 1;
  *(undefined1 *)((longlong)param_1 + 0x49) = 1;
  *param_1 = &PTR_LAB_1401ad3c0;
  *(undefined1 *)((longlong)param_1 + 0x4b) = 1;
  *(undefined1 *)(param_1 + 9) = 1;
  *(undefined1 *)(param_1 + 7) = 0;
  *(undefined1 *)((longlong)param_1 + 0x9cd) = 0;
  *(undefined1 *)(param_1 + 0x36) = 0;
  FUN_140158320(param_1 + 0x39,0,0x800);
  *(undefined4 *)(param_1 + 0x139) = 0;
  *(undefined1 *)(param_1 + 0x113c) = 0;
  return param_1;
}"""

report_funcs.append({
    'name': 'FUN_14007ca6c',
    'address': '0x14007CA6C',
    'code_len': len(ca6c_code),
    'code': ca6c_code
})

added = 0
for rf in report_funcs:
    addr = norm_addr(rf['address'])
    if addr not in existing_decomp:
        existing_decomp[addr] = {
            'address': addr,
            'decompiler_text': rf['code'],
            'assembly_text': f"// Exported from Ghidra decompilation for {rf['name']} at {addr}"
        }
        added += 1

print(f"Added {added} new decompiled functions. Total now: {len(existing_decomp)}")

# 4. Write back decompiler.jsonl
# Sort by address for consistency
sorted_addrs = sorted(existing_decomp.keys(), key=lambda a: int(a, 16))
with open(DECOMP_JSONL, 'w', encoding='utf-8') as f:
    for addr in sorted_addrs:
        f.write(json.dumps(existing_decomp[addr]) + '\n')

print(f"Wrote {len(sorted_addrs)} functions to {DECOMP_JSONL}")

# 5. Update functions.jsonl decompiler_status
decomp_set = set(existing_decomp.keys())
updated_funcs = []
funcs_updated_count = 0
with open(FUNCS_JSONL, 'r', encoding='utf-8') as f:
    for line in f:
        if not line.strip(): continue
        fd = json.loads(line)
        addr = norm_addr(fd['address'])
        if addr in decomp_set and fd.get('decompiler_status') != 'DECOMPILED':
            fd['decompiler_status'] = 'DECOMPILED'
            funcs_updated_count += 1
        updated_funcs.append(fd)

with open(FUNCS_JSONL, 'w', encoding='utf-8') as f:
    for fd in updated_funcs:
        f.write(json.dumps(fd) + '\n')

print(f"Updated {funcs_updated_count} functions in {FUNCS_JSONL} to DECOMPILED status.")
