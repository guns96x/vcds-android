import re
import json

with open('reverse/vcds_transport_report.md', 'r', encoding='utf-8') as f:
    text = f.read()

# Split by '// Function: '
func_blocks = re.split(r'// Function:\s+', text)
print(f"Found {len(func_blocks)-1} function blocks in vcds_transport_report.md")

extracted = []
for block in func_blocks[1:]:
    lines = block.split('\n')
    header = lines[0].strip() # e.g. "FUN_140083208 @ 140083208"
    parts = header.split('@')
    name = parts[0].strip()
    addr = '0x' + parts[1].strip().upper()
    
    # Extract code up to the end of markdown block ```
    code_lines = []
    in_code = False
    for line in lines[1:]:
        if '```' in line:
            break
        code_lines.append(line)
    code = '\n'.join(code_lines).strip()
    extracted.append({'name': name, 'address': addr, 'code_len': len(code), 'code': code})

print(f"Successfully extracted {len(extracted)} functions.")
for item in extracted:
    print(f"{item['address']} | {item['name']:20} | code length: {item['code_len']}")

with open('tools/extracted_report_funcs.json', 'w', encoding='utf-8') as out:
    json.dump(extracted, out, indent=2)
