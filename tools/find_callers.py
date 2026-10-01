import json

with open('tools/extracted_report_funcs.json', 'r') as f:
    funcs = json.load(f)

for func in funcs:
    code = func['code']
    for target in ['140083208', '1400832b4', '1400832B4', 'SetBoot', 'ReadBoot', '14007ed84', '14007ec2c', '14007e988', '14007e3b4']:
        if target.lower() in code.lower() and func['address'].lower() not in ['0x140083208', '0x1400832b4']:
            print(f"Match {target} in {func['name']} ({func['address']})")
