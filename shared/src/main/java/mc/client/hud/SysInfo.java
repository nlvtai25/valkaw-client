package mc.client.hud;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.sun.management.OperatingSystemMXBean;

/**
 * Thông tin hệ thống cho module RAM/CPU/GPU. Poll 1Hz ở thread nền;
 * widget chỉ đọc cache (không gọi OS/nvidia-smi mỗi frame).
 */
final class SysInfo {
    static volatile int cpuPct = -1;
    static volatile String cpuName = "CPU";
    static volatile String gpuName = "GPU";
    static volatile int gpuPct = -1;

    private static boolean started;

    private SysInfo() {}

    static synchronized void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(SysInfo::loop, "mcclient-sysinfo");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        cpuName = readCpuName();
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        while (true) {
            double load = os.getCpuLoad();
            cpuPct = load < 0 ? -1 : (int) Math.round(load * 100);
            pollGpu();
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
        }
    }

    /** Lấy tên + % dùng GPU qua nvidia-smi (GPU đang render game trên máy này). */
    private static void pollGpu() {
        String out = exec("nvidia-smi", "--query-gpu=name,utilization.gpu", "--format=csv,noheader,nounits");
        if (out == null) return;
        String line = out.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        int comma = line.lastIndexOf(',');
        if (comma < 0) return;
        gpuName = line.substring(0, comma).trim();
        try { gpuPct = (int) Math.round(Double.parseDouble(line.substring(comma + 1).trim())); }
        catch (NumberFormatException ignored) { }
    }

    private static String readCpuName() {
        String out = exec("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString");
        if (out == null) return "CPU";
        int i = out.indexOf("REG_SZ");
        if (i < 0) return "CPU";
        String name = out.substring(i + 6).trim();
        return name.isEmpty() ? "CPU" : name;
    }

    private static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            p.waitFor(2, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
