import json

slots = {
    '0x000000014018C838': 'FT_ClrRts',
    '0x000000014018C868': 'FT_SetDtr',
    '0x000000014018C870': 'FT_SetRts',
    '0x000000014018C8C8': 'FT_ClrDtr',
    '0x14018C838': 'FT_ClrRts',
    '0x14018C868': 'FT_SetDtr',
    '0x14018C870': 'FT_SetRts',
    '0x14018C8C8': 'FT_ClrDtr'
}

callers = []
with open('reverse/raw/calls.jsonl', 'r') as f:
    for line in f:
        d = json.loads(line)
        callee = d['callee'].upper()
        if callee in [s.upper() for s in slots]:
            callers.append((d, slots.get(callee, '')))

print(f"Found {len(callers)} calls to DTR/RTS slots:")
for c, name in callers:
    print(f"Caller: {c['caller']} | Callsite: {c['callsite']} | Function: {name}")
