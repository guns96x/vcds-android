// Dump all imports and external symbols
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class DumpImports extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\.gemini\\antigravity\\scratch\\vcds_re\\reverse\\imports_dump.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outPath))) {
            writer.println("=== External Symbols and Imports for: " + currentProgram.getName() + " ===");
            SymbolTable symTable = currentProgram.getSymbolTable();
            SymbolIterator extSyms = symTable.getExternalSymbols();
            while (extSyms.hasNext()) {
                Symbol sym = extSyms.next();
                writer.println(String.format("External: %s -> %s (Addr: %s)", 
                    sym.getParentNamespace().getName(), sym.getName(), sym.getAddress()));
            }

            writer.println("\n=== Functions Matching FT_ or RTUS ===");
            FunctionIterator funcs = currentProgram.getFunctionManager().getFunctions(true);
            while (funcs.hasNext()) {
                Function f = funcs.next();
                String name = f.getName();
                if (name.contains("FT_") || name.contains("RTUS") || name.contains("D2XX")) {
                    writer.println(String.format("Func: %s at %s", name, f.getEntryPoint()));
                }
            }
        }
        println("Saved imports dump to " + outPath);
    }
}
