import re

with open('reverse/vcds_transport_report.md', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern for function blocks
pattern = r'### String `\"([^\"]+)\"` at `([0-9a-fA-F]+)`\s+- \*\*XREF from:\*\* `([0-9a-fA-F]+)` in Function \*\*`([^`]+)`\*\*'
blocks = re.findall(pattern, content)
print(f"Total string xref function blocks: {len(blocks)}")
for str_val, str_addr, xref, func in blocks:
    print(f"{func:18} | {str_addr} | {str_val}")
