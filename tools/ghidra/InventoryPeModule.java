// Ghidra script to record PE module inventory metrics after headless analysis
//@category VCDS_Reverse
import ghidra.app.script.GhidraScript;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.DataIterator;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class InventoryPeModule extends GhidraScript {
    @Override
    public void run() throws Exception {
        String progName = currentProgram.getName();
        String sha256 = currentProgram.getExecutableSHA256();
        long imageBase = currentProgram.getImageBase().getOffset();
        
        int funcCount = 0;
        FunctionIterator fIter = currentProgram.getFunctionManager().getFunctions(true);
        while (fIter.hasNext()) {
            fIter.next();
            funcCount++;
        }
        
        int strCount = 0;
        DataIterator dIter = currentProgram.getListing().getDefinedData(true);
        while (dIter.hasNext()) {
            if (dIter.next().hasStringValue()) strCount++;
        }
        
        File outFile = new File("C:/Users/Admin/.gemini/antigravity/scratch/vcds-android/reverse/raw/pe_modules_analyzed.jsonl");
        outFile.getParentFile().mkdirs();
        
        synchronized (InventoryPeModule.class) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile, true))) {
                pw.println(String.format(
                    "{\"module\":\"%s\",\"sha256\":\"%s\",\"image_base\":\"0x%X\",\"functions\":%d,\"strings\":%d,\"status\":\"ANALYZED_HEADLESS\"}",
                    progName, sha256.toUpperCase(), imageBase, funcCount, strCount
                ));
            }
        }
        println("Inventoried module " + progName + ": " + funcCount + " funcs, " + strCount + " strings.");
    }
}
