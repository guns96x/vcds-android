// Ghidra script to find D2XX / FTDI resolvers and function pointers
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.address.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class FindD2xxResolvers extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\d2xx_resolvers_report.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== D2XX / RTUS Resolvers and Function Pointers Report ===");
        writer.println("Program: " + currentProgram.getName());
        writer.println("Date: " + new Date());
        writer.println();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        // Search for GetProcAddress and LoadLibrary references
        String[] apis = new String[] { "GetProcAddress", "LoadLibraryA", "LoadLibraryW", "LoadLibraryExA", "LoadLibraryExW" };
        for (String api : apis) {
            SymbolIterator syms = currentProgram.getSymbolTable().getSymbols(api);
            while (syms.hasNext()) {
                Symbol sym = syms.next();
                writer.println("--------------------------------------------------");
                writer.println("API Symbol: " + sym.getName() + " at " + sym.getAddress());
                ReferenceIterator refs = refMgr.getReferencesTo(sym.getAddress());
                Set<Address> analyzed = new HashSet<>();
                while (refs.hasNext()) {
                    Reference ref = refs.next();
                    Address from = ref.getFromAddress();
                    Function f = funcMgr.getFunctionContaining(from);
                    if (f != null && !analyzed.contains(f.getEntryPoint())) {
                        analyzed.add(f.getEntryPoint());
                        writer.println("  Called from Function: " + f.getName() + " at " + f.getEntryPoint());
                        DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                        if (res != null && res.decompileCompleted()) {
                            writer.println("  Decompiled C:");
                            writer.println(res.getDecompiledFunction().getC());
                        }
                    }
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Done! Report written to " + outPath);
    }
}
