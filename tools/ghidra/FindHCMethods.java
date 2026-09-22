// Ghidra script to find and decompile all HEX and transport methods
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.address.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class FindHCMethods extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\hc_methods_report.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== Ross-Tech HEX Transport & Protocol Methods Report ===");
        writer.println("Program: " + currentProgram.getName());
        writer.println("Date: " + new Date());
        writer.println();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        String[] targetKeywords = new String[] {
            "HC::SendCommand", "HexInit", "HC::TurboBaud", "HC::GetVersion", 
            "HC::Com115", "HC::Test:KMode", "HC::Reset", "HC::KLineTest",
            "SendCANMsg", "GetCANMsg", "GetResponse", "uCh::SendCommand",
            "KWP2000", "01-Engine", "Measuring", "Ross-Tech", "FT_ResetDevice"
        };

        Set<Address> analyzedFuncs = new HashSet<>();

        DataIterator dataIter = listing.getDefinedData(true);
        while (dataIter.hasNext()) {
            Data data = dataIter.next();
            if (data.hasStringValue()) {
                String val = data.getDefaultValueRepresentation();
                for (String kw : targetKeywords) {
                    if (val.contains(kw)) {
                        Address strAddr = data.getAddress();
                        writer.println("--------------------------------------------------");
                        writer.println("Found String [" + kw + "] at " + strAddr + ": " + val);

                        ReferenceIterator refs = refMgr.getReferencesTo(strAddr);
                        while (refs.hasNext()) {
                            Reference ref = refs.next();
                            Address fromAddr = ref.getFromAddress();
                            Function f = funcMgr.getFunctionContaining(fromAddr);
                            if (f != null) {
                                writer.println("  XREF from: " + fromAddr + " in Function: " + f.getName() + " (Entry: " + f.getEntryPoint() + ")");
                                if (!analyzedFuncs.contains(f.getEntryPoint())) {
                                    analyzedFuncs.add(f.getEntryPoint());
                                    DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                                    if (res != null && res.decompileCompleted()) {
                                        writer.println("  Decompiled C:");
                                        writer.println(res.getDecompiledFunction().getC());
                                    } else {
                                        writer.println("  Decompilation failed or timed out.");
                                    }
                                }
                            } else {
                                writer.println("  XREF from non-function address: " + fromAddr);
                            }
                        }
                        break;
                    }
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Done! Report written to " + outPath);
    }
}
