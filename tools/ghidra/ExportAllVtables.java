// Ghidra script to find all VTables and RTTI class names in VCDS
//@category VCDS_Reverse
import ghidra.app.script.GhidraScript;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.symbol.SymbolType;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class ExportAllVtables extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("=== Discovering All VTables and RTTI Classes ===");
        
        File outFile = new File("C:/Users/Admin/.gemini/antigravity/scratch/vcds-android/reverse/raw/all_vtables.jsonl");
        outFile.getParentFile().mkdirs();
        
        Memory memory = currentProgram.getMemory();
        SymbolIterator symbols = currentProgram.getSymbolTable().getAllSymbols(true);
        
        List<Map<String, Object>> vtables = new ArrayList<>();
        
        while (symbols.hasNext()) {
            Symbol sym = symbols.next();
            String name = sym.getName();
            if (name.contains("vftable") || name.startsWith("`vftable'") || name.contains("RTTI")) {
                Address vtableAddr = sym.getAddress();
                String className = sym.getParentNamespace().getName();
                
                // Read slots until non-pointer or null
                int slot = 0;
                while (slot < 0x200) {
                    try {
                        long target = memory.getLong(vtableAddr.add(slot));
                        Address targetAddr = toAddr(target);
                        Function targetFunc = getFunctionAt(targetAddr);
                        if (targetFunc == null && memory.contains(targetAddr)) {
                            targetFunc = getFunctionContaining(targetAddr);
                        }
                        if (targetFunc != null) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("vtable", String.format("0x%X", vtableAddr.getOffset()));
                            entry.put("slot", String.format("0x%X", slot));
                            entry.put("target", String.format("0x%X", targetAddr.getOffset()));
                            entry.put("target_name", targetFunc.getName());
                            entry.put("class", className);
                            entry.put("symbol", name);
                            vtables.add(entry);
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                    slot += 8;
                }
            }
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            for (Map<String, Object> e : vtables) {
                pw.println(String.format(
                    "{\"vtable\":\"%s\",\"slot\":\"%s\",\"target\":\"%s\",\"target_name\":\"%s\",\"class\":\"%s\",\"symbol\":\"%s\"}",
                    e.get("vtable"), e.get("slot"), e.get("target"), e.get("target_name"), e.get("class"), e.get("symbol")
                ));
            }
        }
        
        println("Discovered " + vtables.size() + " vtable slots across binary! Saved to: " + outFile.getAbsolutePath());
    }
}
