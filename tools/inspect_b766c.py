import json

with open('tools/extracted_report_funcs.json', 'r') as f:
    funcs = json.load(f)

for func in funcs:
    if func['address'] == '0x1400B766C':
        code = func['code']
        print(f"Name: {func['name']}")
        print(f"Length: {len(code)}")
        # Check first 50 lines
        lines = code.split('\n')
        print("First 30 lines:")
        print('\n'.join(lines[:30]))
        # Search for interesting strings or calls
        for kw in ['SetBoot', 'ReadBoot', 'Boot', 'COM', 'Test', 'Port', 'baud', '10400', '115200', '0x84', '0x82', '0x0e', '0x0d']:
            matches = [l.strip() for l in lines if kw in l.lower()]
            print(f"Keyword '{kw}' matches: {len(matches)}")
            if matches:
                print(f"  Sample: {matches[:3]}")
