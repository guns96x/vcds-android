import json

# Check D2XX bindings for FT_SetDtr / FT_ClrDtr / FT_SetRts / FT_ClrRts
# From reverse/d2xx_bindings.md:
# 0x000000014018C8C8 | FT_ClrDtr
# What are the slots for SetDtr, SetRts, ClrRts?

with open('reverse/d2xx_bindings.md', 'r') as f:
    text = f.read()
    for line in text.split('\n'):
        if 'Dtr' in line or 'Rts' in line or 'BitMode' in line:
            print(line)
