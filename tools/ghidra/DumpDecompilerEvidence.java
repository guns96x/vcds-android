// Ghidra script to dump structured evidence for IMPLEMENTATION_SPEC.md and evidence.json
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

public class DumpDecompilerEvidence extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\evidence_dump.json";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("{");
        writer.println("  \"program\": \"" + currentProgram.getName() + "\",");
        writer.println("  \"imageBase\": \"" + currentProgram.getImageBase() + "\",");
        writer.println("  \"evidence\": [");

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        String[] targets = new String[] {
            "HexInit", "HC::SendCommand", "HC::TurboBaud", "HC::GetVersion", 
            "HC::Com115", "HC::Test:KMode", "HC::Reset", "B: %d KW: %02X%02X",
            "SendCANMsg", "GetCANMsg", "Group 011", "01-Engine", "Measuring"
        };

        boolean first = true;
        Set<Address> seenFuncs = new HashSet<>();

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
                            if (f != null && !seenFuncs.contains(f.getEntryPoint())) {
                                seenFuncs.add(f.getEntryPoint());
                                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                                if (res != null && res.decompileCompleted()) {
                                    if (!first) writer.println(",");
                                    first = false;
                                    writer.println("    {");
                                    writer.println("      \"tag\": \"" + escape(t) + "\",");
                                    writer.println("      \"string_address\": \"" + data.getAddress() + "\",");
                                    writer.println("      \"string_value\": \"" + escape(val) + "\",");
                                    writer.println("      \"function_name\": \"" + escape(f.getName()) + "\",");
                                    writer.println("      \"function_entry\": \"" + f.getEntryPoint() + "\",");
                                    writer.println("      \"decompiled_c\": \"" + escape(res.getDecompiledFunction().getC()) + "\"");
                                    writer.print("    }");
                                }
                            }
                        }
                    }
                }
            }
        }

        writer.println();
        writer.println("  ]");
        writer.println("}");

        decomp.dispose();
        writer.close();
        println("Done! JSON evidence dumped to " + outPath);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
