import json

with open('reverse/raw/functions.jsonl', 'r') as f:
    for line in f:
        d = json.loads(line)
        addr = int(d['address'], 16)
        if 0x14007A000 <= addr <= 0x140085000:
            print(f"{d['address']} | size: {d['size']:5} | {d['name']}")
