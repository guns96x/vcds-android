// Ghidra script to decompile BlockDlg (Measuring Blocks dialog) methods
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileBlockDlg extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\block_dlg_decompiled.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Address start = currentProgram.getAddressFactory().getAddress("0x140057000");
        Address end = currentProgram.getAddressFactory().getAddress("0x14005C000");

        FunctionIterator funcs = currentProgram.getFunctionManager().getFunctions(start, true);
        while (funcs.hasNext()) {
            Function f = funcs.next();
            if (f.getEntryPoint().compareTo(end) > 0) break;
            writer.println("==================================================");
            writer.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint());
            DecompileResults res = decomp.decompileFunction(f, 60, monitor);
            if (res != null && res.decompileCompleted()) {
                String c = res.getDecompiledFunction().getC();
                writer.println(c);
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
