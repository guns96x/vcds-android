import json

targets = ['0x1401118F0', '0x140111930', '0x140111D6C', '0x140111D88', '0x140111DA4', '0x140111DC0']

callers = []
with open('reverse/raw/calls.jsonl', 'r') as f:
    for line in f:
        d = json.loads(line)
        callee = d['callee'].lower()
        if any(t.lower() in callee for t in targets):
            callers.append(d)

print(f"Found {len(callers)} calls to wrapper targets:")
for c in callers:
    print(f"Caller: {c['caller']} | Callsite: {c['callsite']} | Callee: {c['callee']}")
