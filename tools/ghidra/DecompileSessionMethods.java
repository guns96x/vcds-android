// Ghidra script to decompile session methods operating on DAT_140701ec0
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileSessionMethods extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\Desktop\\vcds_re_scripts\\session_methods_decompiled.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));

        Address[] addrs = new Address[] {
            currentProgram.getAddressFactory().getAddress("0x140112e94"), // Send/Recv KWP
            currentProgram.getAddressFactory().getAddress("0x1401200c0"), // KWP Read Group
            currentProgram.getAddressFactory().getAddress("0x140118378"), // Protocol check
            currentProgram.getAddressFactory().getAddress("0x14011af34"), // Response processor
            currentProgram.getAddressFactory().getAddress("0x14011a0d4"), // Keepalive
            currentProgram.getAddressFactory().getAddress("0x140113214"), // K-Line frame
            currentProgram.getAddressFactory().getAddress("0x14011feb8")  // Session check
        };

        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        for (Address a : addrs) {
            Function f = currentProgram.getFunctionManager().getFunctionContaining(a);
            if (f != null) {
                writer.println("==================================================");
                writer.println("FUNCTION: " + f.getName() + " @ " + f.getEntryPoint());
                DecompileResults res = decomp.decompileFunction(f, 60, monitor);
                if (res != null && res.decompileCompleted()) {
                    writer.println(res.getDecompiledFunction().getC());
                }
            }
        }

        decomp.dispose();
        writer.close();
        println("Saved to " + outPath);
    }
}
