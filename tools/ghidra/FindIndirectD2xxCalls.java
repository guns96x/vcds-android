// Ghidra script to find all indirect calls to D2XX function pointers
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.*;
import ghidra.program.model.scalar.Scalar;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class FindIndirectD2xxCalls extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outPath = "C:\\Users\\Admin\\.gemini\\antigravity\\scratch\\vcds_re\\reverse\\indirect_d2xx_calls.txt";
        PrintWriter writer = new PrintWriter(new FileWriter(outPath));
        writer.println("=== Indirect Calls to D2XX Function Pointer Table ===");

        // D2XX table is at 0x14018C810 to 0x14018C8E0
        long tableStart = 0x14018C810L;
        long tableEnd = 0x14018C8E0L;

        Map<Long, String> names = new HashMap<>();
        names.put(0x14018C810L, "FT_ResetDevice");
        names.put(0x14018C820L, "FT_SetDataCharacteristics");
        names.put(0x14018C828L, "FT_Close");
        names.put(0x14018C830L, "FT_GetDeviceInfo");
        names.put(0x14018C840L, "FT_Purge");
        names.put(0x14018C848L, "FT_SetTimeouts");
        names.put(0x14018C858L, "FT_Read");
        names.put(0x14018C860L, "FT_Write");
        names.put(0x14018C878L, "FT_SetLatencyTimer");
        names.put(0x14018C890L, "FT_GetQueueStatus");
        names.put(0x14018C8A8L, "FT_Open");
        names.put(0x14018C8B8L, "FT_SetBaudRate");

        Listing listing = currentProgram.getListing();
        InstructionIterator insts = listing.getInstructions(true);

        while (insts.hasNext()) {
            Instruction inst = insts.next();
            String mnemonic = inst.getMnemonicString();
            if (mnemonic.equals("CALL") || mnemonic.equals("JMP") || mnemonic.equals("MOV")) {
                Address[] flowAddrs = inst.getFlows();
                for (Reference ref : inst.getReferencesFrom()) {
                    long toAddr = ref.getToAddress().getOffset();
                    if (toAddr >= tableStart && toAddr <= tableEnd) {
                        String name = names.getOrDefault(toAddr, "D2XX_API_" + Long.toHexString(toAddr));
                        writer.println(String.format("0x%s : %s -> [%s] (Target: 0x%X)", 
                            inst.getAddress(), inst, name, toAddr));
                    }
                }
            }
        }

        writer.close();
        println("Saved indirect calls to " + outPath);
    }
}
