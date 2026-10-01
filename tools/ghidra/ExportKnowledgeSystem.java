// Comprehensive Ghidra Headless Exporter for VCDS Knowledge Base
// @category Analysis
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.address.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.pcode.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.*;

public class ExportKnowledgeSystem extends GhidraScript {

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void run() throws Exception {
        String baseDir = "C:\\Users\\Admin\\.gemini\\antigravity\\scratch\\vcds-android\\reverse\\raw";
        new File(baseDir).mkdirs();

        println("=== Starting VCDS Knowledge System Export ===");
        println("Target Output Directory: " + baseDir);
        println("Program Name: " + currentProgram.getName());
        println("Image Base: " + currentProgram.getImageBase());

        // 1. Program Info
        PrintWriter infoWriter = new PrintWriter(new FileWriter(new File(baseDir, "program_info.json")));
        infoWriter.println("{");
        infoWriter.println("  \"name\": \"" + escapeJson(currentProgram.getName()) + "\",");
        infoWriter.println("  \"image_base\": \"" + currentProgram.getImageBase().toString() + "\",");
        infoWriter.println("  \"creation_date\": \"" + currentProgram.getCreationDate().toString() + "\",");
        infoWriter.println("  \"language_id\": \"" + currentProgram.getLanguageID().toString() + "\",");
        infoWriter.println("  \"executable_path\": \"" + escapeJson(currentProgram.getExecutablePath()) + "\",");
        infoWriter.println("  \"executable_sha256\": \"" + currentProgram.getExecutableSHA256() + "\"");
        infoWriter.println("}");
        infoWriter.close();
        println("Exported program_info.json");

        // Initialize Decompiler
        DecompInterface decomp = new DecompInterface();
        decomp.openProgram(currentProgram);

        Listing listing = currentProgram.getListing();
        FunctionManager funcMgr = currentProgram.getFunctionManager();
        ReferenceManager refMgr = currentProgram.getReferenceManager();
        Memory memory = currentProgram.getMemory();

        // 2. Export Functions & Calls
        PrintWriter funcWriter = new PrintWriter(new FileWriter(new File(baseDir, "functions.jsonl")));
        PrintWriter callWriter = new PrintWriter(new FileWriter(new File(baseDir, "calls.jsonl")));
        PrintWriter decompWriter = new PrintWriter(new FileWriter(new File(baseDir, "decompiler.jsonl")));

        FunctionIterator funcs = funcMgr.getFunctions(true);
        int funcCount = 0;
        int callCount = 0;
        int decompCount = 0;

        // Key addresses of high interest for deep decompilation
        Set<String> criticalAddresses = new HashSet<>(Arrays.asList(
            "14007e734", "14007e824", "14007e988", "14007ec2c", "14007ed84", 
            "14007e3b4", "14007f758", "14007f1d8", "14007e630",
            "1401117bc", "140111828", "14011185c", "140111f40", "140112160",
            "14011feb8", "14005911c"
        ));

        while (funcs.hasNext() && !monitor.isCancelled()) {
            Function f = funcs.next();
            funcCount++;
            Address entry = f.getEntryPoint();
            String addrStr = "0x" + entry.toString().toUpperCase();
            String rawHex = entry.toString().toLowerCase().replace("0x", "");
            long size = f.getBody().getNumAddresses();

            // Hash assembly instructions
            StringBuilder asmBuilder = new StringBuilder();
            InstructionIterator instIter = listing.getInstructions(f.getBody(), true);
            while (instIter.hasNext()) {
                Instruction inst = instIter.next();
                asmBuilder.append(inst.getAddressString(false, true)).append(": ").append(inst.toString()).append("\n");

                // Check calls
                FlowType flow = inst.getFlowType();
                if (flow.isCall()) {
                    callCount++;
                    Reference[] refs = inst.getReferencesFrom();
                    String callee = "UNKNOWN";
                    String dispatchType = flow.isComputed() ? "INDIRECT" : "DIRECT";
                    if (refs.length > 0) {
                        callee = "0x" + refs[0].getToAddress().toString().toUpperCase();
                    }
                    callWriter.println(String.format(
                        "{\"caller\":\"%s\",\"callsite\":\"0x%s\",\"callee\":\"%s\",\"dispatch_type\":\"%s\"}",
                        addrStr, inst.getAddressString(false, true).toUpperCase(), callee, dispatchType
                    ));
                }
            }

            String asmText = asmBuilder.toString();
            String asmHash = hashString(asmText);

            // Decompile critical or sampled transport functions
            String decompStatus = "SKIPPED";
            if (criticalAddresses.contains(rawHex) || funcCount <= 100) {
                try {
                    DecompileResults res = decomp.decompileFunction(f, 30, monitor);
                    if (res != null && res.decompileCompleted()) {
                        decompCount++;
                        decompStatus = "DECOMPILED";
                        String cCode = res.getDecompiledFunction().getC();
                        decompWriter.println(String.format(
                            "{\"address\":\"%s\",\"decompiler_text\":\"%s\",\"assembly_text\":\"%s\"}",
                            addrStr, escapeJson(cCode), escapeJson(asmText)
                        ));
                    }
                } catch (Exception ex) {
                    decompStatus = "FAILED";
                }
            }

            funcWriter.println(String.format(
                "{\"address\":\"%s\",\"rva\":\"0x%08X\",\"size\":%d,\"name\":\"%s\",\"namespace\":\"%s\",\"decompiler_status\":\"%s\",\"assembly_hash\":\"%s\"}",
                addrStr, (entry.getOffset() - currentProgram.getImageBase().getOffset()), size,
                escapeJson(f.getName()), escapeJson(f.getParentNamespace().getName()), decompStatus, asmHash
            ));

            if (funcCount % 500 == 0) {
                println("Processed " + funcCount + " functions...");
            }
        }

        funcWriter.close();
        callWriter.close();
        decompWriter.close();
        println("Exported " + funcCount + " functions, " + callCount + " calls, " + decompCount + " decompiler chunks.");

        // 3. Export Strings
        PrintWriter strWriter = new PrintWriter(new FileWriter(new File(baseDir, "strings.jsonl")));
        DataIterator dataIter = listing.getDefinedData(true);
        int strCount = 0;
        while (dataIter.hasNext() && !monitor.isCancelled()) {
            Data data = dataIter.next();
            if (data.hasStringValue()) {
                strCount++;
                String val = data.getDefaultValueRepresentation();
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                Address sAddr = data.getAddress();
                strWriter.println(String.format(
                    "{\"address\":\"0x%s\",\"value\":\"%s\"}",
                    sAddr.toString().toUpperCase(), escapeJson(val)
                ));
            }
        }
        strWriter.close();
        println("Exported " + strCount + " strings.");

        // 4. Export Known VTable: 0x1401AD3C0
        PrintWriter vtableWriter = new PrintWriter(new FileWriter(new File(baseDir, "vtables.jsonl")));
        Address vtableAddr = currentProgram.getAddressFactory().getAddress("0x1401AD3C0");
        if (vtableAddr != null) {
            for (int slot = 0; slot <= 0x110; slot += 8) {
                try {
                    long target = memory.getLong(vtableAddr.add(slot));
                    vtableWriter.println(String.format(
                        "{\"vtable\":\"0x1401AD3C0\",\"slot\":\"0x%X\",\"target\":\"0x%X\",\"class\":\"TransportInterface\"}",
                        slot, target
                    ));
                } catch (Exception ignored) {}
            }
        }
        vtableWriter.close();
        println("Exported vtables.jsonl.");

        decomp.dispose();
        println("=== Knowledge System Export Completed Successfully ===");
    }
}
