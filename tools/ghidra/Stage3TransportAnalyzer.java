// Full Stage 3 Transport Analyzer Script
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

public class Stage3TransportAnalyzer extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\.gemini\\antigravity\\scratch\\vcds_re\\reverse\\vcds_transport_report.md";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));

        writer.println("# VCDS 26.3 Transport Layer & Adapter Protocol Analysis Report");
        writer.println();
        writer.println("**Target Program:** " + currentProgram.getName());
        writer.println("**Base Address:** " + currentProgram.getImageBase());
        writer.println("**Analysis Date:** " + new Date());
        writer.println();

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        FunctionManager funcMgr = currentProgram.getFunctionManager();

        // 1. Strings & XREFs
        writer.println("## 1. Key Protocol & Hardware Strings with XREFs");
        writer.println();

        String[] keywords = new String[] {
            "FT_", "RTUS", "USB", "ROSSTECH", "Ross-Tech", "Engine", "KWP", "K-Line", 
            "CAN", "TP2", "TP 2.0", "Measuring", "Group", "HC::", "HexInit", "TurboBaud"
        };

        Set<Address> decompiledFuncs = new HashSet<>();

        DataIterator dataIter = listing.getDefinedData(true);
        while (dataIter.hasNext()) {
            Data data = dataIter.next();
            if (data.hasStringValue()) {
                String val = data.getDefaultValueRepresentation();
                for (String kw : keywords) {
                    if (val.toUpperCase().contains(kw.toUpperCase())) {
                        writer.println("### String `\"" + val.replace("\"", "\\\"") + "\"` at `" + data.getAddress() + "`");
                        ReferenceIterator refs = refMgr.getReferencesTo(data.getAddress());
                        boolean hasRefs = false;
                        while (refs.hasNext()) {
                            hasRefs = true;
                            Reference ref = refs.next();
                            Address from = ref.getFromAddress();
                            Function f = funcMgr.getFunctionContaining(from);
                            if (f != null) {
                                writer.println("- **XREF from:** `" + from + "` in Function **`" + f.getName() + "`** (`" + f.getEntryPoint() + "`)");
                                if (!decompiledFuncs.contains(f.getEntryPoint())) {
                                    decompiledFuncs.add(f.getEntryPoint());
                                    DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                                    if (res != null && res.decompileCompleted()) {
                                        writer.println("\n```c\n// Function: " + f.getName() + " @ " + f.getEntryPoint() + "\n" + res.getDecompiledFunction().getC() + "\n```\n");
                                    }
                                }
                            } else {
                                writer.println("- **XREF from non-function address:** `" + from + "`");
                            }
                        }
                        if (!hasRefs) {
                            writer.println("- *(No direct XREFs found)*");
                        }
                        writer.println();
                        break;
                    }
                }
            }
        }

        // 2. FTDI D2XX Call Sites Analysis (0x140111700 - 0x140112300)
        writer.println("## 2. Low-Level FTDI D2XX Call Sites and Hardware Wrappers");
        writer.println();
        Address[] keyVAs = new Address[] {
            currentProgram.getAddressFactory().getAddress("0x1401117CC"),
            currentProgram.getAddressFactory().getAddress("0x140111800"),
            currentProgram.getAddressFactory().getAddress("0x140111847"),
            currentProgram.getAddressFactory().getAddress("0x140111872"),
            currentProgram.getAddressFactory().getAddress("0x140111898"),
            currentProgram.getAddressFactory().getAddress("0x14011197D"),
            currentProgram.getAddressFactory().getAddress("0x140112029"),
            currentProgram.getAddressFactory().getAddress("0x140112054"),
            currentProgram.getAddressFactory().getAddress("0x1401120C6"),
            currentProgram.getAddressFactory().getAddress("0x140112114"),
            currentProgram.getAddressFactory().getAddress("0x140112232")
        };

        Set<Address> wrapperFuncs = new HashSet<>();
        for (Address va : keyVAs) {
            Function f = funcMgr.getFunctionContaining(va);
            if (f != null && !wrapperFuncs.contains(f.getEntryPoint())) {
                wrapperFuncs.add(f.getEntryPoint());
                writer.println("### FTDI Wrapper Function: `" + f.getName() + "` @ `" + f.getEntryPoint() + "`");
                
                Set<Function> callers = f.getCallingFunctions(monitor);
                writer.println("**Callers:**");
                for (Function c : callers) {
                    writer.println("- `" + c.getName() + "` @ `" + c.getEntryPoint() + "`");
                }

                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                if (res != null && res.decompileCompleted()) {
                    writer.println("\n```c\n" + res.getDecompiledFunction().getC() + "\n```\n");
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Stage 3 Transport Report generated at " + outPath);
    }
}
