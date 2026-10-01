import pefile
from capstone import Cs, CS_ARCH_X86, CS_MODE_64

def disassemble_pe(pe_path, rva, length=200):
    pe = pefile.PE(pe_path)
    image_base = pe.OPTIONAL_HEADER.ImageBase
    data = pe.get_memory_mapped_image()
    
    md = Cs(CS_ARCH_X86, CS_MODE_64)
    code = data[rva : rva + length]
    
    res = []
    for ins in md.disasm(code, image_base + rva):
        res.append(f"0x{ins.address:x}: {ins.mnemonic} {ins.op_str}")
    return "\n".join(res)

if __name__ == "__main__":
    pe_path = r"C:\Ross-Tech\VCDS\VCDS.exe"
    pe = pefile.PE(pe_path)
    image_base = pe.OPTIONAL_HEADER.ImageBase
    print(f"Loaded {pe_path}, ImageBase: 0x{image_base:x}")
    
    # We want to check specific functions by virtual address
    # VA = ImageBase + RVA. Typically ImageBase is 0x140000000.
    target_vas = [
        0x140083208, # SetBoot
        0x1400832B4, # ReadBoot
        0x14007D4F0, # Test/Options mode selection
        0x14007E734, # send_frame
        0x14007E824, # read_frame
        0x14007E630, # Opcode 85 / K-Line init
        0x1400821EC, # Echo10400 (Opcode 9A)
        0x1400A14B8, # set_baud_rate
    ]
    
    for va in target_vas:
        rva = va - image_base
        print(f"\n==========================================")
        print(f"Disassembly at VA 0x{va:x} (RVA 0x{rva:x}):")
        print(f"==========================================")
        try:
            print(disassemble_pe(pe_path, rva, 150))
        except Exception as e:
            print(f"Error: {e}")
