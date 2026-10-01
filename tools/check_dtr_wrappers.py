import json

targets = ['0x1401118F0', '0x140111930', '0x140111D6C', '0x140111D88', '0x140111DA4', '0x140111DC0']

with open('reverse/raw/decompiler.jsonl', 'r') as f:
    for line in f:
        d = json.loads(line)
        addr = d['address'].lower()
        if any(addr == t.lower() for t in targets):
            print(f"=== {addr} ===")
            print(d['decompiler_text'])
