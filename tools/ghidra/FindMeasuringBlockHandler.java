// Ghidra script to find Measuring Blocks / Group handlers
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

public class FindMeasuringBlockHandler extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\measuring_blocks_report.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== Measuring Blocks & Group 011 Protocol Analysis ===");
        writer.println();

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        // Search for strings referencing Measuring, Group, Block
        String[] targets = new String[] {
            "Measuring", "Block", "Group", "021", "0x21", "Group %d", "Group %03d"
        };

        Set<Address> seen = new HashSet<>();
        DataIterator dataIter = listing.getDefinedData(true);
        while (dataIter.hasNext()) {
            Data d = dataIter.next();
            if (d.hasStringValue()) {
                String s = d.getDefaultValueRepresentation();
                for (String t : targets) {
                    if (s.contains(t) && !s.contains("Visual Studio") && !s.contains("Microsoft")) {
                        writer.println("Found string: " + s + " at " + d.getAddress());
                        ReferenceIterator refs = refMgr.getReferencesTo(d.getAddress());
                        while (refs.hasNext()) {
                            Reference r = refs.next();
                            Function f = funcMgr.getFunctionContaining(r.getFromAddress());
                            if (f != null && !seen.contains(f.getEntryPoint())) {
                                seen.add(f.getEntryPoint());
                                writer.println("  In Function: " + f.getName() + " @ " + f.getEntryPoint());
                                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                                if (res != null && res.decompileCompleted()) {
                                    writer.println("  Decompiled C:");
                                    writer.println(res.getDecompiledFunction().getC());
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
