// Ghidra script to trace the transport call graph and framing
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

public class DumpTransportCallGraph extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\transport_callgraph_report.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== Transport Call Graph and Framing Logic Report ===");
        writer.println("Program: " + currentProgram.getName());
        writer.println("Date: " + new Date());
        writer.println();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        // Target functions by string reference
        String[] targets = new String[] {
            "HC::SendCommand -1", "HexInit: %d", "HC::GetVersion -1", 
            "HC::TurboBaud -1", "HC::Com115 -1", "HC::Reset -1"
        };

        Set<Function> keyFunctions = new HashSet<>();

        DataIterator dataIter = listing.getDefinedData(true);
        while (dataIter.hasNext()) {
            Data data = dataIter.next();
            if (data.hasStringValue()) {
                String val = data.getDefaultValueRepresentation();
                for (String t : targets) {
                    if (val.contains(t)) {
                        ReferenceIterator refs = refMgr.getReferencesTo(data.getAddress());
                        while (refs.hasNext()) {
                            Reference ref = refs.next();
                            Function f = funcMgr.getFunctionContaining(ref.getFromAddress());
                            if (f != null) {
                                keyFunctions.add(f);
                            }
                        }
                    }
                }
            }
        }

        writer.println("Identified " + keyFunctions.size() + " key transport functions directly referencing strings.");
        for (Function f : keyFunctions) {
            writer.println("==================================================");
            writer.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint());
            
            // Callers
            Set<Function> callers = f.getCallingFunctions(monitor);
            writer.println("  Callers (" + callers.size() + "):");
            for (Function caller : callers) {
                writer.println("    <- " + caller.getName() + " @ " + caller.getEntryPoint());
            }

            // Callees
            Set<Function> callees = f.getCalledFunctions(monitor);
            writer.println("  Callees (" + callees.size() + "):");
            for (Function callee : callees) {
                writer.println("    -> " + callee.getName() + " @ " + callee.getEntryPoint());
            }

            // Decompiled source
            DecompileResults res = decomp.decompileFunction(f, 60, monitor);
            if (res != null && res.decompileCompleted()) {
                writer.println("  Decompiled Code:");
                writer.println(res.getDecompiledFunction().getC());
            }
        }

        decomp.dispose();
        writer.close();
        println("Done! Report written to " + outPath);
    }
}
