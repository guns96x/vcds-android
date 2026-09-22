// Ghidra script to fix function boundaries and decompile HexInit, HC::SendCommand, and framing
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.cmd.function.CreateFunctionCmd;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.address.*;
import ghidra.program.model.mem.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class DisassembleTransportFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\transport_decompiled_deep.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== Deep Disassembly and Decompilation of HEX Transport ===");
        writer.println();

        Listing listing = currentProgram.getListing();
        AddressFactory af = currentProgram.getAddressFactory();

        // 1. Force disassemble range 0x14007D000 to 0x140081000
        Address start = af.getAddress("0x14007D000");
        Address end = af.getAddress("0x140081000");
        disassemble(start);

        // 2. Locate instructions referencing HexInit and HC::SendCommand
        Address[] keySites = new Address[] {
            af.getAddress("0x14007D5A4"), // HC::Test:KMode
            af.getAddress("0x14007E21D"), // HexInit
            af.getAddress("0x14007E6CE"), // HC::TurboBaud
            af.getAddress("0x14007E750"), // HC::SendCommand
            af.getAddress("0x14007E988"), // HC::GetVersion
            af.getAddress("0x14007EC2C")  // HC::Com115
        };

        // Create functions backwards if needed
        for (Address site : keySites) {
            Function f = listing.getFunctionContaining(site);
            if (f == null) {
                // Find function prologue by scanning backward for push rbx / sub rsp
                Address probe = site;
                Address funcStart = null;
                for (int i = 0; i < 200; i++) {
                    Instruction inst = listing.getInstructionBefore(probe);
                    if (inst == null) break;
                    probe = inst.getAddress();
                    // Typical x64 prologues: 48 89 5c 24 ..., 48 83 ec ..., 40 53, 40 55, 48 8b c4
                    byte[] b = inst.getBytes();
                    if (inst.getMnemonicString().equals("SUB") && inst.getDefaultOperandRepresentation(0).equals("RSP")) {
                        funcStart = probe;
                    }
                    if (inst.getMnemonicString().equals("PUSH") && inst.getDefaultOperandRepresentation(0).startsWith("R")) {
                        funcStart = probe;
                    }
                    Instruction prev = listing.getInstructionBefore(probe);
                    if (prev != null && (prev.getMnemonicString().equals("RET") || prev.getMnemonicString().equals("INT 3"))) {
                        funcStart = probe;
                        break;
                    }
                }
                if (funcStart != null) {
                    CreateFunctionCmd cmd = new CreateFunctionCmd(funcStart);
                    cmd.applyTo(currentProgram);
                    writer.println("Created function at: " + funcStart + " covering site " + site);
                } else {
                    CreateFunctionCmd cmd = new CreateFunctionCmd(site);
                    cmd.applyTo(currentProgram);
                    writer.println("Created function directly at: " + site);
                }
            }
        }

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        for (Address site : keySites) {
            Function f = listing.getFunctionContaining(site);
            if (f != null) {
                writer.println("==================================================");
                writer.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint() + " (covers site " + site + ")");
                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                if (res != null && res.decompileCompleted()) {
                    writer.println("--- Decompiled C ---");
                    writer.println(res.getDecompiledFunction().getC());
                } else {
                    writer.println("Decompilation failed.");
                }

                writer.println("--- Assembly Listing ---");
                InstructionIterator insts = listing.getInstructions(f.getBody(), true);
                while (insts.hasNext()) {
                    Instruction inst = insts.next();
                    writer.println("  " + inst.getAddress() + ": " + inst);
                }
            } else {
                writer.println("No function at site: " + site);
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
