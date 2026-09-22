// Ghidra script to decompile FUN_14007e3b4 (K-Line/5-baud init) and session establishment
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.cmd.function.CreateFunctionCmd;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileSessionInit extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\session_init_decompiled.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));

        Address[] addrs = new Address[] {
            currentProgram.getAddressFactory().getAddress("0x14007e3b4"), // Connect / Init
            currentProgram.getAddressFactory().getAddress("0x14007f1d8"), // Reset/Disconnect
            currentProgram.getAddressFactory().getAddress("0x14008285c"), // CAN/K-Line test
            currentProgram.getAddressFactory().getAddress("0x14007ffc8")  // HC::Test:KMode
        };

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        for (Address a : addrs) {
            Function f = currentProgram.getFunctionManager().getFunctionContaining(a);
            if (f == null) {
                CreateFunctionCmd cmd = new CreateFunctionCmd(a);
                cmd.applyTo(currentProgram);
                f = currentProgram.getFunctionManager().getFunctionAt(a);
            }
            if (f != null) {
                writer.println("==================================================");
                writer.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint());
                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                if (res != null && res.decompileCompleted()) {
                    writer.println(res.getDecompiledFunction().getC());
                } else {
                    writer.println("Decompilation failed.");
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
