// Ghidra script to decompile vcds_adapter_read_frame (0x14007E824) and callers
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.cmd.function.CreateFunctionCmd;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileReadFrame extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\read_frame_decompiled.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));

        Address addr = currentProgram.getAddressFactory().getAddress("0x14007E824");
        Function f = currentProgram.getFunctionManager().getFunctionContaining(addr);
        if (f == null) {
            CreateFunctionCmd cmd = new CreateFunctionCmd(addr);
            cmd.applyTo(currentProgram);
            f = currentProgram.getFunctionManager().getFunctionAt(addr);
        }

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        writer.println("=== Decompiled vcds_adapter_read_frame @ " + addr + " ===");
        if (f != null) {
            DecompileResults res = decomp.decompileFunction(f, 60, monitor);
            if (res != null && res.decompileCompleted()) {
                writer.println(res.getDecompiledFunction().getC());
            } else {
                writer.println("Decompilation failed.");
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
