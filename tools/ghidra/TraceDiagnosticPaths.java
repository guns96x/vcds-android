// Ghidra script to trace diagnostic protocol references and BlockDlg paths
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

public class TraceDiagnosticPaths extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("=== Tracing Diagnostic Protocol Paths ===");
        
        File outFile = new File("C:/Users/Admin/.gemini/antigravity/scratch/vcds-android/reverse/raw/diagnostic_trace.txt");
        outFile.getParentFile().mkdirs();
        
        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);
        
        String[] targetStrings = {
            "0x14019C670", // KWP2000
            "0x14019C6F0", // UDS Std Diag
            "0x14019BCB8", // ADPUDSDlg
            "0x1401AA320", // BlockDlg::OnGraph
            "0x1401AA398", // BlockDlg::OnGraph2
            "0x1401B2EE8"  // Engine:
        };
        
        Set<Address> funcsToDecompile = new LinkedHashSet<>();
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            for (String strAddrStr : targetStrings) {
                Address strAddr = toAddr(strAddrStr);
                pw.println("\n------------------------------------------------------------");
                pw.println("REFERENCES TO STRING " + strAddrStr + ":");
                ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(strAddr);
                int count = 0;
                while (refs.hasNext()) {
                    Reference r = refs.next();
                    Address from = r.getFromAddress();
                    Function f = getFunctionContaining(from);
                    String funcInfo = (f != null) ? f.getName() + " @ " + f.getEntryPoint() : "NO_FUNC";
                    pw.println("  [" + r.getReferenceType() + "] from " + from + " in " + funcInfo);
                    if (f != null) {
                        funcsToDecompile.add(f.getEntryPoint());
                    }
                    count++;
                }
                pw.println("  Total refs found: " + count);
            }
            
            // Also trace callers of BlockDlg functions
            Address blockDlgOnGraph = toAddr("0x14005911C");
            Function fOnGraph = getFunctionAt(blockDlgOnGraph);
            if (fOnGraph != null) {
                funcsToDecompile.add(blockDlgOnGraph);
                pw.println("\nCALLERS OF BlockDlg::OnGraph (0x14005911C):");
                ReferenceIterator callers = currentProgram.getReferenceManager().getReferencesTo(blockDlgOnGraph);
                while (callers.hasNext()) {
                    Reference r = callers.next();
                    Address from = r.getFromAddress();
                    Function cf = getFunctionContaining(from);
                    String funcInfo = (cf != null) ? cf.getName() + " @ " + cf.getEntryPoint() : "NO_FUNC";
                    pw.println("  [" + r.getReferenceType() + "] from " + from + " in " + funcInfo);
                    if (cf != null) funcsToDecompile.add(cf.getEntryPoint());
                }
            }
            
            // Now decompile all identified functions
            pw.println("\n============================================================");
            pw.println("DECOMPILED FUNCTIONS (" + funcsToDecompile.size() + " total):");
            pw.println("============================================================");
            for (Address fAddr : funcsToDecompile) {
                Function f = getFunctionAt(fAddr);
                if (f != null) {
                    pw.println("\n/* " + "=".repeat(60) + " */");
                    pw.println("/* FUNCTION: " + f.getName() + " @ " + f.getEntryPoint() + " */");
                    pw.println("/* " + "=".repeat(60) + " */");
                    DecompileResults res = decomp.decompileFunction(f, 45, monitor);
                    if (res != null && res.decompileCompleted()) {
                        pw.println(res.getDecompiledFunction().getC());
                    } else {
                        pw.println("/* Decompilation error: " + (res != null ? res.getErrorMessage() : "null") + " */");
                    }
                }
            }
        }
        
        println("Diagnostic trace completed. Saved to: " + outFile.getAbsolutePath());
    }
}
