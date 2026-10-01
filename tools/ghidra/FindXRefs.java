// Ghidra script to trace references to vtable 0x1401AD3C0 and transport functions
//@category VCDS_Reverse
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class FindXRefs extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("=== Tracing VTable and Transport References ===");
        
        Address vtableAddr = toAddr("0x1401AD3C0");
        ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(vtableAddr);
        
        File outFile = new File("C:/Users/Admin/.gemini/antigravity/scratch/vcds-android/reverse/raw/vtable_trace.txt");
        outFile.getParentFile().mkdirs();
        
        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);
        
        List<Address> funcsToDecompile = new ArrayList<>();
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("REFERENCES TO VTABLE 0x1401AD3C0:");
            while (refs.hasNext()) {
                Reference r = refs.next();
                Address from = r.getFromAddress();
                Function f = getFunctionContaining(from);
                String funcName = (f != null) ? f.getName() + " (" + f.getEntryPoint() + ")" : "NO_FUNC";
                pw.println("  From: " + from + " | Type: " + r.getReferenceType() + " | In: " + funcName);
                if (f != null && !funcsToDecompile.contains(f.getEntryPoint())) {
                    funcsToDecompile.add(f.getEntryPoint());
                }
            }
            
            // Add other key transport vtable functions
            String[] targetAddrs = {
                "0x140080988", "0x140080D80", "0x140082000", "0x1400830A8", 
                "0x14007FEBC", "0x14007CAD8", "0x14007E3B4", "0x14005911C"
            };
            for (String a : targetAddrs) {
                Address addr = toAddr(a);
                if (!funcsToDecompile.contains(addr)) {
                    funcsToDecompile.add(addr);
                }
            }
            
            pw.println("\nDECOMPILING TARGET FUNCTIONS:");
            for (Address fAddr : funcsToDecompile) {
                Function f = getFunctionAt(fAddr);
                if (f == null) f = getFunctionContaining(fAddr);
                if (f != null) {
                    pw.println("\n" + "=".repeat(60));
                    pw.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint());
                    pw.println("=".repeat(60));
                    DecompileResults res = decomp.decompileFunction(f, 30, monitor);
                    if (res != null && res.decompileCompleted()) {
                        pw.println(res.getDecompiledFunction().getC());
                    } else {
                        pw.println("Decompilation failed: " + (res != null ? res.getErrorMessage() : "null"));
                    }
                } else {
                    pw.println("Function not found at: " + fAddr);
                }
            }
        }
        
        println("Saved trace results to: " + outFile.getAbsolutePath());
    }
}
