// Ghidra Headless Script to export DLL exports for independent D2XX verification
//@category VCDS_Reverse
import ghidra.app.script.GhidraScript;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.symbol.SymbolType;
import ghidra.program.model.address.Address;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class ExportDllExports extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("Exporting DLL exports for: " + currentProgram.getName());
        
        File outFile = new File("C:/Users/Admin/.gemini/antigravity/scratch/vcds-android/vcds-kb/tests/ghidra_rtus64_exports.json");
        outFile.getParentFile().mkdirs();
        
        long imageBase = currentProgram.getImageBase().getOffset();
        List<Map<String, Object>> exportsList = new ArrayList<>();
        
        SymbolIterator symbols = currentProgram.getSymbolTable().getAllSymbols(true);
        while (symbols.hasNext()) {
            Symbol sym = symbols.next();
            if (sym.getSymbolType() == SymbolType.FUNCTION || sym.getSymbolType() == SymbolType.LABEL) {
                String name = sym.getName();
                if (name.startsWith("FT_")) {
                    Address addr = sym.getAddress();
                    long va = addr.getOffset();
                    long rva = va - imageBase;
                    
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("rva", String.format("0x%X", rva));
                    entry.put("va", String.format("0x%X", va));
                    exportsList.add(entry);
                }
            }
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            writer.println("[");
            for (int i = 0; i < exportsList.size(); i++) {
                Map<String, Object> e = exportsList.get(i);
                writer.print("  {\"name\": \"" + e.get("name") + "\", \"rva\": \"" + e.get("rva") + "\", \"va\": \"" + e.get("va") + "\"}");
                if (i < exportsList.size() - 1) writer.println(",");
                else writer.println();
            }
            writer.println("]");
        }
        println("Saved " + exportsList.size() + " exports to " + outFile.getAbsolutePath());
    }
}
