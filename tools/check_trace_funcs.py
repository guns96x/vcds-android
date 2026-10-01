import re

for filename in ['reverse/raw/diagnostic_trace.txt', 'reverse/raw/vtable_trace.txt']:
    with open(filename, 'r', encoding='utf-8', errors='ignore') as f:
        text = f.read()
    matches = re.findall(r'FUNCTION: (FUN_[0-9a-fA-F]+) @ ([0-9a-fA-F]+)', text)
    print(f"{filename}: found {len(matches)} functions: {matches}")
