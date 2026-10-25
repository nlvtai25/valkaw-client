using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

static class Launcher {
        const string DefaultRepo = "https://github.com/nlvtai25/valkaw-client";

    // Version launcher (doc lap voi version client). Asset phat hanh phai ten
    // "Valkaw.Launcher-<AppVersion>.exe" - launcher doc version trong ten de biet co ban moi.
    const string AppVersion = "1.6";
    const string ClientVersion = "1.5";   // khop ten pack <Variant>_v<ver>.jar, hien trong header changelog
    const string SelfPrefix = "Valkaw.Launcher-";

    // Installer co the ghi launcher.repo canh exe de doi repo (fork/self-host).
    static string RepoRoot
    {
        get
        {
            try
            {
                string f = Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location), "launcher.repo");
                if (File.Exists(f))
                {
                    string s = File.ReadAllText(f).Trim().TrimEnd('/');
                    if (s.Length > 0 && !s.Contains(" ")) return s;
                }
            }
            catch { }
            return DefaultRepo;
        }
    }

    static string ReleasePage { get { return RepoRoot + "/releases/latest"; } }
    static string JarBase { get { return RepoRoot + "/releases/latest/download/"; } }

    const string MC = "1.21.11";

    // Variant client: 1 JAR gom mods/ + settings, launcher tai roi cai vao gameDir
    static readonly string[] VariantNames = { "Sodium", "VulkanMod" };
    static string PackUrl(string packName) { return JarBase + packName; }

    /** Ten moi asset trong release moi nhat, lay tu GitHub API; cache lai de chay offline van biet.
        Giu ca ten khong phai .jar (file launcher) - PickPack/PickSelf tu loc. */
    static string[] LatestAssets(string root) {
        List<string> names = new List<string>();
        string cache = Path.Combine(root, "release-assets.txt");
        try {
            using (WebClient wc = new WebClient()) {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                wc.Headers.Add("User-Agent", "ValkawLauncher");
                string api = RepoRoot.StartsWith("https://github.com/")
                    ? "https://api.github.com/repos/" + RepoRoot.Substring("https://github.com/".Length) + "/releases/latest"
                    : RepoRoot + "/releases/latest";   // self-host: khong co API, dung cache
                string json = wc.DownloadString(api);
                foreach (Match m in Regex.Matches(json, "\"name\"\\s*:\\s*\"([^\"]+)\""))
                    names.Add(m.Groups[1].Value);
                if (names.Count > 0) File.WriteAllLines(cache, names.ToArray());
            }
        } catch { }
        if (names.Count == 0) {
            try { if (File.Exists(cache)) names.AddRange(File.ReadAllLines(cache)); } catch { }
        }
        return names.ToArray();
    }

    /** JAR cua variant (Sodium_* / Vulkan_*) trong release moi nhat; nhieu ban thi lay version cao nhat. */
    static string PickPackName(string[] names, string prefix) {
        string best = null;
        Version bestVer = null;
        foreach (string n in names) {
            if (!n.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) continue;
            if (!n.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)) continue;
            Version ver = ParseVer(n);
            if (best == null || (ver != null && (bestVer == null || ver > bestVer))) {
                best = n; bestVer = ver;
            }
        }
        return best;
    }

    static string PickPack(string root, int v) {
        string prefix = (v == 1 ? "Vulkan" : "Sodium") + "_";
        string best = PickPackName(LatestAssets(root), prefix);
        if (best == null)
            throw new Exception("Khong tim thay " + prefix + "*.jar tren GitHub Releases - kiem tra mang hoac tai tay.");
        return best;
    }

    static void VerifyBundle(string jar, int variant, string mcVersion) {
        using (ZipArchive a = ZipFile.OpenRead(jar)) {
            ZipArchiveEntry mf = a.GetEntry("fabric.mod.json");
            if (mf == null) throw new Exception("Bundle thieu fabric.mod.json");
            int jars = 0;
            foreach (ZipArchiveEntry e in a.Entries)
                if (e.FullName.StartsWith("mods/", StringComparison.OrdinalIgnoreCase) && !e.FullName.EndsWith("/")) jars++;
            if (jars == 0) throw new Exception("Bundle khong co mod nao trong mods/");
            string text;
            using (StreamReader r = new StreamReader(mf.Open())) text = r.ReadToEnd();
            int declared = Regex.Matches(text, "\"file\"\\s*:").Count;
            if (declared == 0) throw new Exception("fabric.mod.json khong co mang jars[]");
            if (declared != jars) throw new Exception("Bundle lech: " + jars + " file trong mods/, " + declared + " khai bao");
            string id = Regex.Match(text, "\"id\"\\s*:\\s*\"([^\"]+)\"").Groups[1].Value;
            string expectedId = (variant == 1 ? "valkaw-pack-vulkan" : "valkaw-pack-sodium");
            if (id != expectedId) throw new Exception("Bundle variant khong khop: can " + expectedId + ", co " + id);
            Match mc = Regex.Match(text, "\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
            if (mc.Success && mc.Groups[1].Value != mcVersion) throw new Exception("Bundle MC dependency khong khop: can " + mcVersion + ", co " + mc.Groups[1].Value);
            // path safety
            foreach (ZipArchiveEntry e in a.Entries) {
                string n = e.FullName.Replace('\\', '/');
                if (n.StartsWith("/") || n.Contains("..")) throw new Exception("Entry path khong an toan: " + n);
            }
        }
    }

    static string FetchPack(string root, string name, bool local, out bool isLocal) {
        isLocal = local;
        if (local) return name;
        SetStatus("Downloading " + name + " from GitHub...");
        string tmp = Path.Combine(Path.GetTempPath(), name);
        using (WebClient wc = new WebClient()) {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
            wc.DownloadFile(PackUrl(name), tmp);
        }
        return tmp;
    }

    // ---------- self-update ----------
    // Asset phat hanh: Valkaw.Launcher-<ver>.exe (Windows), Valkaw.Launcher-linux-<ver>.zip.
    static bool updateChecked;
    static bool cfgUpdate = true;   // update=0 trong settings.txt -> tat kiem tra
    static string cfgSkip = "";     // update.skip=<ver> -> khong nhac lai ban nay

    static string UpdateDir(string root) { return Path.Combine(root, "update"); }

    static string SelfExe() {
        try { return Assembly.GetExecutingAssembly().Location; } catch { return null; }
    }

    /** Version trong ten file: "Valkaw.Launcher-1.10.exe" -> 1.10; khong co so -> null. */
    static Version ParseVer(string s) {
        Match m = Regex.Match(s == null ? "" : s, "\\d+(?:\\.\\d+)+");
        if (!m.Success) return null;
        try { return new Version(m.Value); } catch { return null; }
    }

    static bool Newer(Version a, Version b) { return a != null && (b == null || a > b); }

    /** Asset launcher Windows moi nhat (bo qua zip cua Linux va ten khong co version). */
    static string PickSelf(string[] names) {
        string best = null;
        Version bestVer = null;
        foreach (string n in names) {
            if (!n.StartsWith(SelfPrefix, StringComparison.OrdinalIgnoreCase)) continue;
            if (!n.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) continue;
            Version v = ParseVer(n);
            if (v == null) continue;
            if (bestVer == null || v > bestVer) { best = n; bestVer = v; }
        }
        return best;
    }

    /** Ban dev (nam canh Launcher.cs) khong duoc tu ghi de chinh no. */
    static bool DevCopy() {
        try {
            string d = Path.GetDirectoryName(SelfExe());
            return d == null || File.Exists(Path.Combine(d, "Launcher.cs"))
                || File.Exists(Path.Combine(d, "launcher.dev"));
        } catch { return true; }
    }

    static void CleanOld() {
        try { File.Delete(SelfExe() + ".old"); } catch { }
        try { foreach (string f in Directory.GetFiles(UpdateDir(InstallRoot()), "*.part")) File.Delete(f); } catch { }
    }

    static void StartUpdateCheck(string root) {
        if (updateChecked) return;
        updateChecked = true;
        if (DevCopy()) return;
        Thread t = new Thread(new ThreadStart(delegate { CheckUpdate(root); }));
        t.IsBackground = true;
        t.Start();
    }

    static void CheckUpdate(string root) {
        try {
            if (!cfgUpdate) return;
            string name = PickSelf(LatestAssets(root));
            if (name == null) return;
            Version v = ParseVer(name);
            if (!Newer(v, ParseVer(AppVersion))) return;
            if (cfgSkip != null && cfgSkip.Trim() == v.ToString()) return;
            DialogResult ans = DialogResult.Cancel;
            try {
                object r = form.Invoke(new Func<DialogResult>(delegate {
                    return MessageBox.Show(form,
                        "Da co Valkaw Launcher " + v + " (ban dang dung " + AppVersion + "). Cap nhat ngay?",
                        "Valkaw Launcher", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Question);
                }));
                if (r is DialogResult) ans = (DialogResult)r;
            } catch { return; }
            if (ans == DialogResult.No) {
                cfgSkip = v.ToString();
                try { form.Invoke((MethodInvoker)delegate { SaveSettings(root); }); } catch { }
            } else if (ans == DialogResult.Yes) {
                DownloadUpdate(root, name, v);
            }
        } catch { }
    }

    static void DownloadUpdate(string root, string name, Version v) {
        string dir = UpdateDir(root);
        string part = Path.Combine(dir, "pending.exe.part");
        string dest = Path.Combine(dir, "pending.exe");
        try {
            Directory.CreateDirectory(dir);
            SetStatus("Dang tai ban " + v + " ...");
            using (WebClient wc = new WebClient()) {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                wc.Headers.Add("User-Agent", "ValkawLauncher");
                wc.DownloadFile(JarBase + name, part);
            }
            bool good;
            using (FileStream fs = File.OpenRead(part))
                good = fs.Length > 200000 && fs.ReadByte() == 'M' && fs.ReadByte() == 'Z';
            if (!good) throw new Exception("file tai ve khong hop le");
            File.WriteAllText(Path.Combine(dir, "pending.ver"), v.ToString());
            if (File.Exists(dest)) File.Delete(dest);
            File.Move(part, dest);
            SetStatus("Da tai ban " + v + ", dang thay file ...");
            if (SwapIn(root)) {
                try { Process.Start(SelfExe()); } catch { }
                try { form.BeginInvoke((MethodInvoker)delegate { Application.Exit(); }); }
                catch { Application.Exit(); }
            } else {
                SetStatus("Khong thay duoc file - se cap nhat o lan mo sau.");
            }
        } catch (Exception ex) {
            try { if (File.Exists(part)) File.Delete(part); } catch { }
            SetStatus("Cap nhat loi: " + ex.Message);
        }
    }

    /** Doi exe dang chay: Windows cho phep rename chinh no, roi dat file moi vao cho cu. */
    static bool SwapIn(string root) {
        try {
            string self = SelfExe();
            string pending = Path.Combine(UpdateDir(root), "pending.exe");
            if (self == null || !File.Exists(pending)) return false;
            string old = self + ".old";
            try { if (File.Exists(old)) File.Delete(old); } catch { }
            File.Move(self, old);
            try { File.Move(pending, self); }
            catch { try { File.Move(old, self); } catch { } return false; }
            try { File.Delete(Path.Combine(UpdateDir(root), "pending.ver")); } catch { }
            return true;
        } catch { return false; }
    }

    /** Ban tai tu lan truoc (user bam Huy hoac thay file that bai): thay roi chay lai ban moi. */
    static bool ApplyPendingAtStart(string root) {
        string dir = UpdateDir(root);
        string pending = Path.Combine(dir, "pending.exe");
        if (!File.Exists(pending) || DevCopy()) return false;
        Version v = null;
        try { v = ParseVer(File.ReadAllText(Path.Combine(dir, "pending.ver"))); } catch { }
        if (!Newer(v, ParseVer(AppVersion))) {
            try { File.Delete(pending); } catch { }
            return false;
        }
        if (!SwapIn(root)) return false;
        try { Process.Start(SelfExe()); } catch { return false; }
        return true;
    }

    /** Kiem tra logic update, khong mang + khong GUI: chay exe voi --self-test. */
    static int SelfTest() {
        List<string> log = new List<string>();
        bool ok = true;
        string[] names = { "Sodium_v1.3.jar", "Vulkan_v1.3.jar", "Valkaw.Launcher-linux-1.20.zip",
                           "Valkaw.Launcher-1.9.exe", "Valkaw.Launcher-1.10.exe", "Valkaw.Launcher.exe" };
        string picked = PickSelf(names);
        ok &= Chk(log, picked == "Valkaw.Launcher-1.10.exe", "pick cao nhat: " + picked);
        ok &= Chk(log, PickSelf(new string[] { "Sodium_v1.3.jar" }) == null, "khong co asset launcher");
        ok &= Chk(log, PickSelf(new string[] { "Valkaw.Launcher-9.9.exe" }) == "Valkaw.Launcher-9.9.exe", "dung prefix");
        ok &= Chk(log, Newer(ParseVer("1.10"), ParseVer("1.9")), "1.10 > 1.9");
        ok &= Chk(log, !Newer(ParseVer("1.4"), ParseVer(AppVersion)), "1.4 khong moi hon " + AppVersion);
        ok &= Chk(log, !Newer(ParseVer("1.3"), ParseVer(AppVersion)), "1.3 cu hon");

        string cltext = ReadResourceText("changelog_text");
        List<ClBlock> cl = ParseChangelog(cltext);
        ok &= Chk(log, cl.Count >= 5, "changelog: " + cl.Count + " khoi");
        ok &= Chk(log, cl.Count > 0 && cl[0].Title.StartsWith("Valkaw "), "changelog: tieu de khoi dau");
        ok &= Chk(log, cl.Count > 0 && cl[0].Rows.Count >= 3, "changelog: co dong noi dung");
        ok &= Chk(log, cl.Count > 0 && cl[0].Rows[0].Text == "Launcher", "changelog: nhom Launcher truoc");
        // Tieu de khoi moi nhat phai lay version tu const, khong phai so viet tay trong CHANGELOG.
        ok &= Chk(log, cl.Count > 0 && cl[0].Title.StartsWith("Valkaw " + AppVersion + " - "),
                  "changelog: version tu AppVersion");
        ok &= Chk(log, cl.Count > 0 && cl[0].Title.Contains("client v" + ClientVersion),
                  "changelog: version client tu ClientVersion");

        // Runtime (khong mang): duong dan + loc rules + nhan dien thu muc da san sang
        ok &= Chk(log, MavenRel("net.fabricmc:intermediary:1.21.11") == "net/fabricmc/intermediary/1.21.11/intermediary-1.21.11.jar", "runtime: maven path");
        ok &= Chk(log, MavenRel("a.b:c:1.0:natives-windows") == "a/b/c/1.0/c-1.0-natives-windows.jar", "runtime: maven classifier");
        ok &= Chk(log, !LibAllowed(JParse("{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"linux\"}}]}")), "runtime: bo library linux");
        ok &= Chk(log, LibAllowed(JParse("{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}]}")), "runtime: giu library windows");
        ok &= Chk(log, !LibAllowed(JParse("{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\",\"arch\":\"arm64\"}}]}")), "runtime: bo library arm64");
        ok &= Chk(log, LoaderFromProfile("{\"id\":\"fabric-loader-0.19.5-1.21.11\"}") == "0.19.5", "runtime: loader tu profile");
        ok &= Chk(log, AssetPath("C:\\x", "ab12") == "C:\\x\\assets\\objects\\ab\\ab12", "runtime: duong dan asset");
        ok &= Chk(log, !RuntimeReady(Path.Combine(Path.GetTempPath(), "valkaw-no-runtime")), "runtime: thu muc rong chua san sang");
        log.Add(ok ? "OK" : "FAILED");
        File.WriteAllLines(Path.Combine(Path.GetTempPath(), "valkaw-selftest.txt"), log.ToArray());
        return ok ? 0 : 1;
    }

    static bool Chk(List<string> log, bool cond, string msg) {
        log.Add((cond ? "PASS " : "FAIL ") + msg);
        return cond;
    }

    // ---- palette (set by SetPalette) ----
    static Color BgDeep, BgPanel, BgCard, BgHover, Txt, TxtDim, Line, Accent, AccentHi, AccentInk, OnAccent, Success, Error;
    static bool darkMode, reopen;
    static bool loading;   // dang nap settings -> khong ghi nguoc ra file (tranh ghi de bang gia tri trong)

    static void SetPalette(bool d) {
        if (d) {
            BgDeep   = Color.FromArgb(27, 27, 31);
            BgPanel  = Color.FromArgb(38, 38, 44);
            BgCard   = Color.FromArgb(52, 52, 60);
            BgHover  = Color.FromArgb(64, 64, 74);
            Txt      = Color.FromArgb(245, 245, 247);
            TxtDim   = Color.FromArgb(185, 185, 196);
            Line     = Color.FromArgb(60, 60, 69);
            Accent   = Color.FromArgb(245, 158, 11);
            AccentHi = Color.FromArgb(251, 191, 36);
            AccentInk = Color.FromArgb(251, 191, 36);
            OnAccent = Color.FromArgb(26, 18, 0);
            Success  = Color.FromArgb(63, 185, 80);
            Error    = Color.FromArgb(248, 81, 73);
        } else {
            BgDeep   = Color.FromArgb(246, 246, 248);
            BgPanel  = Color.FromArgb(236, 236, 241);
            BgCard   = Color.FromArgb(255, 255, 255);
            BgHover  = Color.FromArgb(226, 226, 232);
            Txt      = Color.FromArgb(32, 32, 36);
            TxtDim   = Color.FromArgb(107, 107, 115);
            Line     = Color.FromArgb(211, 211, 218);
            Accent   = Color.FromArgb(245, 158, 11);
            AccentHi = Color.FromArgb(251, 191, 36);
            AccentInk = Color.FromArgb(180, 83, 9);
            OnAccent = Color.FromArgb(42, 29, 0);
            Success  = Color.FromArgb(63, 185, 80);
            Error    = Color.FromArgb(248, 81, 73);
        }
    }

    static Form form;
    static NotifyIcon tray;   // icon khay khi launcher ẩn khỏi taskbar
    static Panel[] pages;
    static Button[] navBtns;
    static int activePage;
    static int variantIdx;   // 0 = Sodium, 1 = VulkanMod
    static string sourceMode = "Online";   // Online = GitHub, Offline = local JAR
    static string offlineJar = null;       // session-only selected local JAR
    static string offlineLastDir = "";     // persisted in settings.txt
    static TrackBar ramSlider;
    static TextBox ramBox, jvmBox, dirBox, userBox;
    static ComboBox variantBox;
    static Label capVersion;   // o "Version" bi an khi Source = Offline (chi hien sau khi cai pack local)
    static RadioButton[] prioRb = new RadioButton[3];
    static RadioButton[] afterRb = new RadioButton[3];
    static Label status;
    static NoPadLabel heroName;
    static Button playBtn, userListBtn;
    static List<string> users = new List<string>();
    static MemoryStream iconStream;

    [DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int val, int size);

    static void ApplyTitleBarTheme() {
        try {
            int v = darkMode ? 1 : 0;
            if (DwmSetWindowAttribute(form.Handle, 20, ref v, 4) != 0)
                DwmSetWindowAttribute(form.Handle, 19, ref v, 4);
        } catch { }
    }

    // ---------- paths ----------
    static string InstallRoot() {
        return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".valkaw");
    }
    static string UserFile(string b) { return Path.Combine(b, "username.txt"); }
    static string UsersFile(string b) { return Path.Combine(b, "usernames.txt"); }

    static bool IsAdmin() {
        try {
            using (WindowsIdentity id = WindowsIdentity.GetCurrent())
                return new WindowsPrincipal(id).IsInRole(WindowsBuiltInRole.Administrator);
        } catch { return false; }
    }

    // ---------- entry ----------
    [STAThread]
    static void Main(string[] args) {
        for (int i = 0; i < args.Length; i++)
            if (args[i] == "--self-test") { Environment.Exit(SelfTest()); return; }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.ApplicationExit += delegate {
            if (tray != null) { tray.Visible = false; tray.Dispose(); }
        };

        string root = InstallRoot();
        Directory.CreateDirectory(root);
        CleanOld();
        // Ban tai tu lan truoc: thay file roi chay ban moi (ban cu van giu nguyen neu that bai).
        if (ApplyPendingAtStart(root)) return;
        darkMode = LoadTheme(root);

        bool again = true;
        while (again) {
            SetPalette(darkMode);
            reopen = false;
            BuildMain(root);
            LoadUsers(root);
            LoadUsername(root);
            LoadSettings(root);
            ShowPage(0);
            StartUpdateCheck(root);
            Application.Run(form);
            again = reopen; // toggled theme -> rebuild with other palette
        }
    }

    // ---------- khay hệ thống (ẩn khỏi taskbar) ----------
    static void HideToTray() {
        if (form == null || !form.Visible) return;
        if (tray == null) {
            tray = new NotifyIcon();
            tray.Icon = form.Icon;
            tray.Text = "Valkaw Launcher - v" + AppVersion;
            ContextMenuStrip menu = new ContextMenuStrip();
            menu.Items.Add("Open launcher", null, delegate { RestoreFromTray(); });
            menu.Items.Add("Exit", null, delegate { Application.Exit(); });
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += delegate { RestoreFromTray(); };
        }
        form.ShowInTaskbar = false;
        form.Hide();
        tray.Visible = true;
    }

    static void RestoreFromTray() {
        if (tray != null) tray.Visible = false;
        form.ShowInTaskbar = true;
        form.Show();
        form.WindowState = FormWindowState.Normal;
        form.Activate();
    }

    // ---------- main window ----------
    static void BuildMain(string root) {
        if (tray != null) { tray.Visible = false; tray.Dispose(); tray = null; }   // đổi theme -> form mới
        form = new Form();
        form.Text = "Valkaw Launcher - v" + AppVersion;
        form.ClientSize = new Size(1600, 900);
        form.FormBorderStyle = FormBorderStyle.FixedSingle;
        form.MaximizeBox = false;
        form.MinimumSize = new Size(1600, 900);
        form.MaximumSize = new Size(1600, 900);
        form.StartPosition = FormStartPosition.CenterScreen;
        form.BackColor = BgDeep;
        form.ForeColor = Txt;
        form.Font = new Font("Segoe UI", 9f);
        form.Icon = LoadIcon();
        ApplyTitleBarTheme();
        form.FormClosing += delegate { SaveSettings(root); };
        form.Resize += delegate {
            if (form.WindowState == FormWindowState.Minimized) HideToTray();
        };

        // header
        PictureBox banner = new PictureBox();
        banner.SetBounds(24, 8, 80, 80);
        banner.SizeMode = PictureBoxSizeMode.Zoom;
        banner.BackColor = BgDeep;
        banner.Image = LoadBanner();
        form.Controls.Add(banner);

        Label brand = new Label();
        brand.Text = "VALKAW";
        brand.SetBounds(120, 14, 500, 36);
        brand.ForeColor = Accent;
        brand.BackColor = BgDeep;
        brand.Font = new Font("Segoe UI", 22f, FontStyle.Bold);
        form.Controls.Add(brand);

        Label sub = new Label();
        sub.Text = "Minecraft " + MC + "  \u00b7  Sodium / VulkanMod";
        sub.SetBounds(124, 52, 700, 20);
        sub.ForeColor = TxtDim;
        sub.BackColor = BgDeep;
        sub.Font = new Font("Segoe UI", 10f);
        form.Controls.Add(sub);

        Panel strip = new Panel();
        strip.SetBounds(0, 96, 1600, 2);
        strip.BackColor = Accent;
        form.Controls.Add(strip);

        // sidebar nav
        Panel side = new Panel();
        side.SetBounds(0, 98, 220, 802);
        side.BackColor = BgPanel;
        form.Controls.Add(side);

        Label playStage = new Label();
        playStage.Text = "PLAY STAGE";
        playStage.SetBounds(20, 18, 180, 16);
        playStage.ForeColor = TxtDim;
        playStage.BackColor = BgPanel;
        playStage.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
        side.Controls.Add(playStage);

        string[] nav = { "Home", "RAM", "JVM Args", "Options", "Game Dir" };
        navBtns = new Button[nav.Length];
        for (int i = 0; i < nav.Length; i++) {
            Button b = MakeNav(nav[i], i);
            b.SetBounds(12, 44 + i * 56, 196, 44);
            side.Controls.Add(b);
            navBtns[i] = b;
        }

        // content
        Panel content = new Panel();
        content.SetBounds(220, 98, 980, 802);
        content.BackColor = BgDeep;
        form.Controls.Add(content);

        pages = new Panel[5];
        for (int i = 0; i < 5; i++) {
            pages[i] = new Panel();
            pages[i].SetBounds(0, 0, 980, 802);
            pages[i].BackColor = BgDeep;
            pages[i].ForeColor = Txt;
            content.Controls.Add(pages[i]);
        }
        BuildChangelog(pages[0]);
        BuildRam(pages[1]);
        BuildJvm(pages[2]);
        BuildOptions(pages[3]);
        BuildDir(pages[4]);

        // right instance panel
        Panel right = new Panel();
        right.SetBounds(1200, 98, 400, 802);
        right.BackColor = BgPanel;
        form.Controls.Add(right);

        Label inst = new Label();
        inst.Text = "INSTANCE";
        inst.SetBounds(24, 20, 200, 16);
        inst.ForeColor = TxtDim;
        inst.BackColor = BgPanel;
        inst.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
        right.Controls.Add(inst);

        Panel rcard = new Panel();
        rcard.SetBounds(24, 44, 352, 320);
        rcard.BackColor = BgCard;
        right.Controls.Add(rcard);

        // Account
        Label capU = new Label();
        capU.Text = "Username";
        capU.ForeColor = TxtDim;
        capU.BackColor = BgCard;
        capU.Font = new Font("Segoe UI", 8.5f);
        capU.SetBounds(16, 16, 200, 16);
        rcard.Controls.Add(capU);

        userBox = new TextBox();
        userBox.SetBounds(16, 34, 232, 30);
        userBox.BackColor = BgCard;
        userBox.ForeColor = Txt;
        userBox.BorderStyle = BorderStyle.FixedSingle;
        userBox.Font = new Font("Segoe UI", 10f);
        userBox.TextChanged += delegate {
            SaveUsername(InstallRoot(), userBox.Text.Trim());
            if (heroName != null) heroName.Text = "Hello " + (string.IsNullOrWhiteSpace(userBox.Text) ? "Player" : userBox.Text.Trim());
        };
        rcard.Controls.Add(userBox);

        Button addBtn = MakeFlat("", BgCard, Txt, BgHover);
        addBtn.SetBounds(256, 34, 36, 30);
        addBtn.Paint += delegate(object s, PaintEventArgs e) {
            Graphics g = e.Graphics;
            int cx = addBtn.Width / 2, cy = addBtn.Height / 2, arm = 6;
            using (SolidBrush br = new SolidBrush(addBtn.ForeColor)) {
                g.FillRectangle(br, cx - arm, cy - 1, arm * 2, 2);
                g.FillRectangle(br, cx - 1, cy - arm, 2, arm * 2);
            }
        };
        addBtn.Click += delegate { AddUser(root); };
        rcard.Controls.Add(addBtn);

        userListBtn = MakeFlat("\u25BC", BgCard, Txt, BgHover);
        userListBtn.SetBounds(296, 34, 36, 30);
        userListBtn.Click += delegate { ShowUserList(root); };
        rcard.Controls.Add(userListBtn);

        // Source
        Label capS = new Label();
        capS.Text = "Source";
        capS.ForeColor = TxtDim;
        capS.BackColor = BgCard;
        capS.Font = new Font("Segoe UI", 8.5f);
        capS.SetBounds(16, 80, 160, 16);
        rcard.Controls.Add(capS);

        ComboBox sourceBox = new ComboBox();
        sourceBox.DropDownStyle = ComboBoxStyle.DropDownList;
        sourceBox.Items.AddRange(new object[] { "Online", "Offline" });
        sourceBox.SelectedIndex = 0;
        sourceBox.BackColor = BgCard;
        sourceBox.ForeColor = Txt;
        sourceBox.FlatStyle = FlatStyle.Flat;
        sourceBox.Font = new Font("Segoe UI", 10f);
        sourceBox.SetBounds(16, 98, 320, 30);
        sourceBox.SelectedIndexChanged += delegate {
            sourceMode = sourceBox.SelectedItem.ToString();
            ShowVersion(sourceMode != "Offline");   // Offline: variant do chinh file jar quyet dinh
            if (sourceMode == "Offline" && string.IsNullOrEmpty(offlineJar)) {
                using (OpenFileDialog ofd = new OpenFileDialog()) {
                    ofd.Filter = "Fabric bundle JAR (*.jar)|*.jar|All files (*.*)|*.*";
                    ofd.InitialDirectory = string.IsNullOrEmpty(offlineLastDir) ? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads" : offlineLastDir;
                    if (ofd.ShowDialog() == DialogResult.OK) {
                        offlineJar = ofd.FileName;
                        offlineLastDir = Path.GetDirectoryName(ofd.FileName);
                        variantBox.SelectedIndex = VariantFromJar(offlineJar);
                        SaveSettings(InstallRoot());
                    } else {
                        sourceMode = "Online";
                        sourceBox.SelectedIndex = 0;
                        ShowVersion(true);
                    }
                }
            }
        };
        rcard.Controls.Add(sourceBox);

        // Version
        capVersion = new Label();
        capVersion.Text = "Version";
        capVersion.ForeColor = TxtDim;
        capVersion.BackColor = BgCard;
        capVersion.Font = new Font("Segoe UI", 8.5f);
        capVersion.SetBounds(16, 144, 160, 16);
        rcard.Controls.Add(capVersion);

        variantBox = new ComboBox();
        variantBox.DropDownStyle = ComboBoxStyle.DropDownList;
        variantBox.Items.AddRange(VariantNames);
        variantBox.SelectedIndex = 0;
        variantBox.BackColor = BgCard;
        variantBox.ForeColor = Txt;
        variantBox.FlatStyle = FlatStyle.Flat;
        variantBox.Font = new Font("Segoe UI", 10f);
        variantBox.SetBounds(16, 162, 150, 30);
        variantBox.SelectedIndexChanged += delegate { variantIdx = variantBox.SelectedIndex; SaveSettings(InstallRoot()); };
        rcard.Controls.Add(variantBox);
        ShowVersion(sourceMode != "Offline");

        // MC version
        Label capMc = new Label();
        capMc.Text = "MC";
        capMc.ForeColor = TxtDim;
        capMc.BackColor = BgCard;
        capMc.Font = new Font("Segoe UI", 8.5f);
        capMc.SetBounds(180, 144, 160, 16);
        rcard.Controls.Add(capMc);

        ComboBox mcBox = new ComboBox();
        mcBox.DropDownStyle = ComboBoxStyle.DropDownList;
        mcBox.Items.AddRange(new object[] { "1.21.11" });
        mcBox.SelectedIndex = 0;
        mcBox.BackColor = BgCard;
        mcBox.ForeColor = Txt;
        mcBox.FlatStyle = FlatStyle.Flat;
        mcBox.Font = new Font("Segoe UI", 10f);
        mcBox.SetBounds(180, 162, 150, 30);
        rcard.Controls.Add(mcBox);

        status = new Label();
        status.Text = "Ready.";
        status.ForeColor = Accent;
        status.BackColor = BgCard;
        status.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
        status.TextAlign = ContentAlignment.MiddleLeft;
        status.SetBounds(16, 206, 320, 20);
        rcard.Controls.Add(status);

        playBtn = MakeFlat("Play", Accent, OnAccent, AccentHi);
        playBtn.Font = new Font("Segoe UI", 12f, FontStyle.Bold);
        playBtn.SetBounds(16, 236, 320, 52);
        playBtn.Click += delegate { OnPlay(root); };
        rcard.Controls.Add(playBtn);

        // theme + release link under the instance card
        Button themeBtn = MakeFlat(darkMode ? "\u2600 Light" : "\u263D Dark", BgCard, Txt, BgHover);
        themeBtn.SetBounds(24, 390, 120, 32);
        themeBtn.Click += delegate {
            darkMode = !darkMode;
            SaveTheme(root, darkMode);
            reopen = true;
            form.Close();
        };
        right.Controls.Add(themeBtn);

        LinkLabel link = new LinkLabel();
        link.Text = "GitHub Releases \u2014 download / update manually";
        link.LinkColor = AccentInk;
        link.ActiveLinkColor = Accent;
        link.VisitedLinkColor = AccentInk;
        link.ForeColor = TxtDim;
        link.BackColor = BgPanel;
        link.Font = new Font("Segoe UI", 8.5f);
        link.SetBounds(24, 430, 352, 40);
        link.LinkClicked += delegate { OpenUrl(ReleasePage); };
        right.Controls.Add(link);

        rcard.SendToBack();
    }

    static Button MakeNav(string text, int idx) {
        Button b = new Button();
        b.Text = text;
        b.TextAlign = ContentAlignment.MiddleLeft;
        b.Padding = new Padding(16, 0, 0, 0);
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = BgPanel;
        b.ForeColor = TxtDim;
        b.Font = new Font("Segoe UI", 9.5f);
        b.Cursor = Cursors.Hand;
        b.MouseEnter += delegate { if (activePage != idx) { b.BackColor = BgHover; b.ForeColor = Txt; } };
        b.MouseLeave += delegate { if (activePage != idx) { b.BackColor = BgPanel; b.ForeColor = TxtDim; } };
        b.Paint += delegate(object s, PaintEventArgs e) {
            if (activePage == idx) {
                using (SolidBrush br = new SolidBrush(Accent)) {
                    e.Graphics.FillRectangle(br, 0, 0, 3, b.Height);
                }
            }
        };
        b.Click += delegate { ShowPage(idx); };
        return b;
    }

    static Button MakeFlat(string text, Color bg, Color fg, Color hover) {
        Button b = new Button();
        b.Text = text;
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = bg;
        b.ForeColor = fg;
        b.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
        b.Cursor = Cursors.Hand;
        b.MouseEnter += delegate { b.BackColor = hover; };
        b.MouseLeave += delegate { b.BackColor = bg; };
        return b;
    }

    static void ShowPage(int idx) {
        activePage = idx;
        for (int i = 0; i < pages.Length; i++) pages[i].Visible = (i == idx);
        for (int i = 0; i < navBtns.Length; i++) {
            if (i == idx) { navBtns[i].BackColor = BgCard; navBtns[i].ForeColor = Txt; }
            else { navBtns[i].BackColor = BgPanel; navBtns[i].ForeColor = TxtDim; }
            navBtns[i].Invalidate();
        }
    }

    static string ThemeFile(string b) { return Path.Combine(b, "theme.txt"); }
    static bool LoadTheme(string b) {
        try {
            string f = ThemeFile(b);
            if (File.Exists(f)) return File.ReadAllText(f).Trim().ToLower() == "dark";
        } catch { }
        return false; // default: Light
    }
    static void SaveTheme(string b, bool d) {
        try { File.WriteAllText(ThemeFile(b), d ? "dark" : "light"); } catch { }
    }

    // ---------- pages ----------
    // ---------- changelog ----------
    // Noi dung: khoi CHANGELOG trong launcher\linux\valkaw.py (1 nguon duy nhat),
    // build-launcher.ps1 nhung vao exe thanh resource "changelog_text" de 2 ban giong nhau.
    class ClRow { public bool Item; public string Text; }
    class ClBlock { public string Title; public List<ClRow> Rows = new List<ClRow>(); }

    static List<ClBlock> ParseChangelog(string text) {
        List<ClBlock> blocks = new List<ClBlock>();
        ClBlock cur = null;
        string[] lines = (text ?? "").Replace("\r\n", "\n").Split('\n');
        for (int i = 0; i < lines.Length; i++) {
            string line = lines[i].TrimEnd();
            if (line.Trim().Length == 0) continue;
            if (line.StartsWith("Valkaw ")) {
                cur = new ClBlock();
                // {launcher}/{client}: version tu const, khong bao gio lech ten version.
                cur.Title = line.Trim().Replace("{launcher}", AppVersion).Replace("{client}", ClientVersion);
                blocks.Add(cur);
            } else if (cur != null) {
                ClRow r = new ClRow();
                r.Item = line.StartsWith("+");
                r.Text = r.Item ? line.Substring(1).Trim() : line.Trim();
                cur.Rows.Add(r);
            }
        }
        return blocks;
    }

    static string ReadResourceText(string name) {
        try {
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name)) {
                if (s == null) return "";
                using (StreamReader r = new StreamReader(s, Encoding.UTF8)) return r.ReadToEnd();
            }
        } catch { return ""; }
    }

    /** Home: hero + changelog. */
    static void BuildChangelog(Panel p) {
        Panel hero = new Panel();
        hero.SetBounds(32, 20, p.Width - 64, 140);
        hero.BackColor = BgCard;
        p.Controls.Add(hero);

        heroName = new NoPadLabel();
        heroName.Text = "Hello Player";
        heroName.SetBounds(20, 20, hero.Width - 20, 40);
        heroName.Padding = new Padding(0);
        heroName.TextAlign = ContentAlignment.MiddleLeft;
        heroName.ForeColor = Txt;
        heroName.BackColor = BgCard;
        heroName.Font = new Font("Segoe UI", 24f, FontStyle.Bold);
        hero.Controls.Add(heroName);

        NoPadLabel heroSub = new NoPadLabel();
        heroSub.Text = "Ready to play? Pick an instance and start the game.";
        heroSub.SetBounds(20, 66, hero.Width - 20, 20);
        heroSub.Padding = new Padding(0);
        heroSub.TextAlign = ContentAlignment.MiddleLeft;
        heroSub.ForeColor = TxtDim;
        heroSub.BackColor = BgCard;
        heroSub.Font = new Font("Segoe UI", 10f);
        hero.Controls.Add(heroSub);

        NoPadLabel heroMeta = new NoPadLabel();
        heroMeta.Text = "Minecraft " + MC + "  \u00b7  Sodium / VulkanMod";
        heroMeta.SetBounds(20, 92, hero.Width - 20, 18);
        heroMeta.Padding = new Padding(0);
        heroMeta.TextAlign = ContentAlignment.MiddleLeft;
        heroMeta.ForeColor = AccentInk;
        heroMeta.BackColor = BgCard;
        heroMeta.Font = new Font("Segoe UI", 9f);
        hero.Controls.Add(heroMeta);

        Label title = new Label();
        title.Text = "Changelog";
        title.SetBounds(32, 180, 400, 26);
        title.ForeColor = Txt;
        title.BackColor = BgDeep;
        title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
        p.Controls.Add(title);

        Label desc = new Label();
        desc.Text = "Latest updates and fixes for Valkaw.";
        desc.SetBounds(32, 210, 800, 20);
        desc.ForeColor = TxtDim;
        desc.BackColor = BgDeep;
        desc.Font = new Font("Segoe UI", 10f);
        p.Controls.Add(desc);

        Panel card = new Panel();
        card.SetBounds(32, 238, p.Width - 64, p.Height - 262);
        card.BackColor = BgCard;
        p.Controls.Add(card);

        FlowLayoutPanel flow = new FlowLayoutPanel();
        flow.Dock = DockStyle.Fill;
        flow.FlowDirection = FlowDirection.TopDown;
        flow.WrapContents = false;
        flow.AutoScroll = true;
        flow.BackColor = BgCard;
        flow.Padding = new Padding(10, 10, 20, 16);
        card.Controls.Add(flow);

        List<ClBlock> blocks = ParseChangelog(ReadResourceText("changelog_text"));
        if (blocks.Count == 0) {
            Label none = new Label();
            none.Text = "Ban nay khong co changelog.";
            none.ForeColor = TxtDim;
            none.AutoSize = true;
            flow.Controls.Add(none);
            return;
        }

        for (int i = 0; i < blocks.Count; i++) {
            ClBlock blk = blocks[i];

            Panel head = new Panel();
            head.Size = new Size(card.Width - 40, 40);
            head.BackColor = BgPanel;
            head.Cursor = Cursors.Hand;
            head.Margin = new Padding(0, i == 0 ? 0 : 6, 0, 0);

            Label arrow = new Label();
            arrow.Text = i == 0 ? "\u25BE" : "\u25B8";
            arrow.SetBounds(10, 6, 22, 28);
            arrow.TextAlign = ContentAlignment.MiddleCenter;
            arrow.BackColor = BgPanel;
            arrow.ForeColor = Accent;
            arrow.Font = new Font("Segoe UI", 12f, FontStyle.Bold);
            arrow.Cursor = Cursors.Hand;
            head.Controls.Add(arrow);

            Label cap = new Label();
            cap.Text = blk.Title;
            cap.SetBounds(34, 0, head.Width - 40, 40);
            cap.TextAlign = ContentAlignment.MiddleLeft;
            cap.BackColor = BgPanel;
            cap.ForeColor = Txt;
            cap.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
            cap.Cursor = Cursors.Hand;
            head.Controls.Add(cap);

            FlowLayoutPanel body = new FlowLayoutPanel();
            body.FlowDirection = FlowDirection.TopDown;
            body.WrapContents = false;
            body.AutoSize = true;
            body.AutoSizeMode = AutoSizeMode.GrowAndShrink;
            body.MinimumSize = new Size(card.Width - 44, 0);
            body.BackColor = BgPanel;
            body.Padding = new Padding(14, 4, 10, 8);
            body.Margin = new Padding(0, 0, 0, 8);
            body.Visible = (i == 0);

            for (int r = 0; r < blk.Rows.Count; r++) {
                ClRow row = blk.Rows[r];
                Label l = new Label();
                l.AutoSize = true;
                l.MaximumSize = new Size(card.Width - 90, 0);
                l.Text = row.Item ? "\u2022  " + row.Text : row.Text;
                l.Font = new Font("Segoe UI", 10f, row.Item ? FontStyle.Regular : FontStyle.Bold);
                l.ForeColor = row.Item ? TxtDim : Accent;
                l.BackColor = BgPanel;
                l.Margin = new Padding(row.Item ? 12 : 0, row.Item ? 2 : 10, 0, 0);
                body.Controls.Add(l);
            }

            EventHandler toggle = delegate {
                body.Visible = !body.Visible;
                arrow.Text = body.Visible ? "\u25BE" : "\u25B8";
            };
            head.Click += toggle;
            cap.Click += toggle;
            arrow.Click += toggle;

            flow.Controls.Add(head);
            flow.Controls.Add(body);
        }
    }

    static void BuildRam(Panel p) {
        Label title = new Label();
        title.Text = "Memory";
        title.SetBounds(32, 20, 400, 26);
        title.ForeColor = Txt;
        title.BackColor = BgDeep;
        title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
        p.Controls.Add(title);

        Label desc = new Label();
        desc.Text = "RAM allocated to Minecraft.";
        desc.SetBounds(32, 50, 800, 20);
        desc.ForeColor = TxtDim;
        desc.BackColor = BgDeep;
        desc.Font = new Font("Segoe UI", 10f);
        p.Controls.Add(desc);

        Panel card = new Panel();
        card.SetBounds(32, 78, p.Width - 64, p.Height - 102);
        card.BackColor = BgCard;
        p.Controls.Add(card);

        Label l = new Label();
        l.Text = "RAM (GB, step 0.25):";
        l.ForeColor = Txt;
        l.BackColor = BgCard;
        l.Font = new Font("Segoe UI", 11f);
        l.SetBounds(24, 24, 400, 24);
        card.Controls.Add(l);

        ramSlider = new TrackBar();
        ramSlider.Minimum = 4;
        ramSlider.Maximum = 64;
        ramSlider.TickFrequency = 4;
        ramSlider.SmallChange = 1;
        ramSlider.LargeChange = 4;
        ramSlider.SetBounds(20, 64, card.Width - 60, 64);
        ramSlider.Value = 24;
        ramSlider.BackColor = BgCard;
        ramSlider.ValueChanged += delegate { ramBox.Text = (ramSlider.Value / 4f).ToString("0.##", CultureInfo.InvariantCulture); };
        card.Controls.Add(ramSlider);

        ramBox = MakeInput(24, 160, 140);
        ramBox.BackColor = BgDeep;
        ramBox.Text = "6";
        ramBox.TextChanged += delegate {
            float g;
            if (TryNum(ramBox.Text, out g)) {
                int v = (int)Math.Round(g * 4);
                if (v < 4) v = 4;
                if (v > 64) v = 64;
                if (ramSlider.Value != v) ramSlider.Value = v;
            }
            SaveSettings(InstallRoot());   // luu ngay khi doi RAM
        };
        card.Controls.Add(ramBox);

        Label u = new Label();
        u.Text = "GB";
        u.ForeColor = TxtDim;
        u.BackColor = BgCard;
        u.Font = new Font("Segoe UI", 11f);
        u.SetBounds(176, 166, 60, 24);
        card.Controls.Add(u);
    }

    static void BuildJvm(Panel p) {
        Label title = new Label();
        title.Text = "JVM Arguments";
        title.SetBounds(32, 20, 400, 26);
        title.ForeColor = Txt;
        title.BackColor = BgDeep;
        title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
        p.Controls.Add(title);

        Label desc = new Label();
        desc.Text = "Extra flags passed to Java. Edit only if you know what you are doing.";
        desc.SetBounds(32, 50, 1000, 20);
        desc.ForeColor = TxtDim;
        desc.BackColor = BgDeep;
        desc.Font = new Font("Segoe UI", 10f);
        p.Controls.Add(desc);

        Panel card = new Panel();
        card.SetBounds(32, 78, p.Width - 64, p.Height - 102);
        card.BackColor = BgCard;
        p.Controls.Add(card);

        jvmBox = new TextBox();
        jvmBox.Multiline = true;
        jvmBox.ScrollBars = ScrollBars.Vertical;
        jvmBox.SetBounds(16, 16, card.Width - 32, card.Height - 32);
        jvmBox.BackColor = BgDeep;
        jvmBox.ForeColor = Txt;
        jvmBox.BorderStyle = BorderStyle.FixedSingle;
        jvmBox.Font = new Font("Consolas", 11f);
        jvmBox.TextChanged += delegate { SaveSettings(InstallRoot()); };   // luu ngay khi sua JVM args
        card.Controls.Add(jvmBox);
    }

    static void BuildOptions(Panel p) {
        Label title = new Label();
        title.Text = "Launch Options";
        title.SetBounds(32, 20, 400, 26);
        title.ForeColor = Txt;
        title.BackColor = BgDeep;
        title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
        p.Controls.Add(title);

        Label desc = new Label();
        desc.Text = "Process priority and launcher behavior after starting the game.";
        desc.SetBounds(32, 50, 1000, 20);
        desc.ForeColor = TxtDim;
        desc.BackColor = BgDeep;
        desc.Font = new Font("Segoe UI", 10f);
        p.Controls.Add(desc);

        Panel card = new Panel();
        card.SetBounds(32, 78, p.Width - 64, p.Height - 102);
        card.BackColor = BgCard;
        p.Controls.Add(card);

        GroupBox gp = MakeGroup("Priority", 24, 24, card.Width - 48, 96);
        card.Controls.Add(gp);
        string[] pr = { "Normal", "High", "Realtime" };
        for (int i = 0; i < 3; i++) {
            prioRb[i] = new RadioButton();
            prioRb[i].Text = pr[i];
            prioRb[i].ForeColor = Txt;
            prioRb[i].BackColor = BgPanel;
            prioRb[i].Font = new Font("Segoe UI", 11f);
            prioRb[i].SetBounds(24 + i * 200, 36, 180, 28);
            prioRb[i].Checked = (i == 0);
            prioRb[i].CheckedChanged += delegate { SaveSettings(InstallRoot()); };   // lưu ngay, khỏi mất khi tắt/kill
            gp.Controls.Add(prioRb[i]);
        }

        GroupBox ga = MakeGroup("After run", 24, 136, card.Width - 48, 96);
        card.Controls.Add(ga);
        string[] ar = { "Hide", "Keep", "Close" };
        for (int i = 0; i < 3; i++) {
            afterRb[i] = new RadioButton();
            afterRb[i].Text = ar[i];
            afterRb[i].ForeColor = Txt;
            afterRb[i].BackColor = BgPanel;
            afterRb[i].Font = new Font("Segoe UI", 11f);
            afterRb[i].SetBounds(24 + i * 200, 36, 180, 28);
            afterRb[i].Checked = (i == 0);
            afterRb[i].CheckedChanged += delegate { SaveSettings(InstallRoot()); };
            ga.Controls.Add(afterRb[i]);
        }

        Label hint = new Label();
        hint.Text = "High/Realtime requires Admin rights (UAC).";
        hint.ForeColor = TxtDim;
        hint.BackColor = BgCard;
        hint.Font = new Font("Segoe UI", 10f);
        hint.SetBounds(24, 248, 600, 22);
        card.Controls.Add(hint);
    }

    static void BuildDir(Panel p) {
        Label title = new Label();
        title.Text = "Game Directory";
        title.SetBounds(32, 20, 400, 26);
        title.ForeColor = Txt;
        title.BackColor = BgDeep;
        title.Font = new Font("Segoe UI", 15f, FontStyle.Bold);
        p.Controls.Add(title);

        Label desc = new Label();
        desc.Text = "Folder where game files, mods, and saves are stored.";
        desc.SetBounds(32, 50, 1000, 20);
        desc.ForeColor = TxtDim;
        desc.BackColor = BgDeep;
        desc.Font = new Font("Segoe UI", 10f);
        p.Controls.Add(desc);

        Panel card = new Panel();
        card.SetBounds(32, 78, p.Width - 64, p.Height - 102);
        card.BackColor = BgCard;
        p.Controls.Add(card);

        Label l = new Label();
        l.Text = "Game directory:";
        l.ForeColor = Txt;
        l.BackColor = BgCard;
        l.Font = new Font("Segoe UI", 11f);
        l.SetBounds(24, 24, 400, 24);
        card.Controls.Add(l);

        dirBox = MakeInput(24, 56, card.Width - 400);
        dirBox.BackColor = BgDeep;
        dirBox.TextChanged += delegate { SaveSettings(InstallRoot()); };   // luu ngay khi doi thu muc game
        card.Controls.Add(dirBox);

        Button b = MakeFlat("Browse...", BgDeep, Txt, BgHover);
        b.SetBounds(card.Width - 360, 56, 160, 34);
        b.Click += delegate {
            FolderBrowserDialog fbd = new FolderBrowserDialog();
            if (fbd.ShowDialog() == DialogResult.OK) dirBox.Text = fbd.SelectedPath;
        };
        card.Controls.Add(b);

        Button o = MakeFlat("Open .valkaw", BgDeep, Txt, BgHover);
        o.SetBounds(card.Width - 184, 56, 160, 34);
        o.Click += delegate {
            try {
                string root = InstallRoot();
                Directory.CreateDirectory(root);
                Process.Start(new ProcessStartInfo(root) { UseShellExecute = true });
            } catch { }
        };
        card.Controls.Add(o);

        Label h = new Label();
        h.Text = "Empty = %AppData%\\.valkaw\\game_sodium (Vulkan: game_vulkan)";
        h.ForeColor = TxtDim;
        h.BackColor = BgCard;
        h.Font = new Font("Segoe UI", 10f);
        h.SetBounds(24, 108, card.Width - 48, 22);
        card.Controls.Add(h);
    }

    static Label MakeLabel(string text, int x, int y, int w) {
        Label l = new Label();
        l.Text = text;
        l.ForeColor = Txt;
        l.BackColor = BgDeep;
        l.SetBounds(x, y, w, 20);
        return l;
    }

    static TextBox MakeInput(int x, int y, int w) {
        TextBox t = new TextBox();
        t.SetBounds(x, y, w, 28);
        t.BackColor = BgCard;
        t.ForeColor = Txt;
        t.BorderStyle = BorderStyle.FixedSingle;
        t.Font = new Font("Segoe UI", 10f);
        return t;
    }

    static GroupBox MakeGroup(string text, int x, int y, int w, int h) {
        GroupBox g = new GroupBox();
        g.Text = text;
        g.ForeColor = TxtDim;
        g.BackColor = BgPanel;
        g.SetBounds(x, y, w, h);
        return g;
    }

    // ---------- username management ----------
    static void LoadUsers(string root) {
        users.Clear();
        try {
            string f = UsersFile(root);
            if (File.Exists(f)) {
                foreach (string line in File.ReadAllLines(f)) {
                    string u = line.Trim();
                    if (u.Length > 0 && !users.Contains(u)) users.Add(u);
                }
            }
        } catch { }
        SaveUsers(root);
    }
    static void SaveUsers(string root) {
        try { File.WriteAllLines(UsersFile(root), users.ToArray()); } catch { }
    }
    static void LoadUsername(string root) {
        try {
            string f = UserFile(root);
            if (File.Exists(f)) { string u = File.ReadAllText(f).Trim(); if (u.Length > 0) userBox.Text = u; }
        } catch { }
    }
    static void SaveUsername(string root, string u) {
        try { File.WriteAllText(UserFile(root), u); } catch { }
    }

    static string SettingsFile(string b) { return Path.Combine(b, "settings.txt"); }

    /** Doc so nguoi dung go: nhan ca "6.5" lan "6,5", khong phu thuoc locale. */
    static bool TryNum(string s, out float v) {
        v = 0f;
        if (s == null) return false;
        return float.TryParse(s.Trim().Replace(',', '.'), NumberStyles.Float,
            CultureInfo.InvariantCulture, out v);
    }

    /** Lưu Priority / After run / JVM Args. File nằm trong %APPDATA%\.valkaw nên cập nhật launcher vẫn giữ nguyên. */
    static void SaveSettings(string root) {
        if (loading) return;
        try {
            string prio = prioRb[1].Checked ? "High" : (prioRb[2].Checked ? "Realtime" : "Normal");
            string after = afterRb[1].Checked ? "Keep" : (afterRb[2].Checked ? "Close" : "Hide");
            File.WriteAllText(SettingsFile(root),
                "ram=" + ramBox.Text.Trim() + "\r\npriority=" + prio + "\r\nafter=" + after +
                "\r\ngamedir=" + dirBox.Text.Trim() + "\r\nvariant=" + VariantNames[variantIdx] +
                "\r\noffline.lastdir.windows=" + (offlineLastDir == null ? "" : offlineLastDir) +
                "\r\nupdate=" + (cfgUpdate ? "1" : "0") + "\r\n" +
                (cfgSkip.Length > 0 ? "update.skip=" + cfgSkip + "\r\n" : ""));
            File.WriteAllText(Path.Combine(root, "jvmargs.txt"), jvmBox.Text.Trim());
        } catch { }
    }

    static void LoadSettings(string root) {
        loading = true;
        try {
            string f = SettingsFile(root);
            if (File.Exists(f)) {
                foreach (string raw in File.ReadAllLines(f)) {
                    int eq = raw.IndexOf('=');
                    if (eq < 0) continue;
                    string k = raw.Substring(0, eq).Trim();
                    string v = raw.Substring(eq + 1).Trim();
                    if (k == "ram") {
                        float g;
                        if (TryNum(v, out g)) {
                            if (g < 1) g = 1;
                            if (g > 16) g = 16;
                            int val = (int)Math.Round(g * 4);
                            if (val < ramSlider.Minimum) val = ramSlider.Minimum;
                            if (val > ramSlider.Maximum) val = ramSlider.Maximum;
                            ramSlider.Value = val;              // ValueChanged tự cập nhật ramBox
                            ramBox.Text = g.ToString("0.##", CultureInfo.InvariantCulture);
                        }
                    } else if (k == "gamedir") {
                        if (dirBox != null) dirBox.Text = v;
                    } else if (k == "priority" && (v == "Normal" || v == "High" || v == "Realtime")) {
                        prioRb[0].Checked = v == "Normal";
                        prioRb[1].Checked = v == "High";
                        prioRb[2].Checked = v == "Realtime";
                    } else if (k == "after" && (v == "Hide" || v == "Keep" || v == "Close")) {
                        afterRb[0].Checked = v == "Hide";
                        afterRb[1].Checked = v == "Keep";
                        afterRb[2].Checked = v == "Close";
                    } else if (k == "variant" && Array.IndexOf(VariantNames, v) >= 0) {
                        variantIdx = Array.IndexOf(VariantNames, v);
                        if (variantBox != null) variantBox.SelectedIndex = variantIdx;
                    } else if (k == "update") {
                        cfgUpdate = !(v == "0" || v.Equals("false", StringComparison.OrdinalIgnoreCase)
                                   || v.Equals("off", StringComparison.OrdinalIgnoreCase));
                    } else if (k == "update.skip") {
                        cfgSkip = v;
                    } else if (k == "offline.lastdir.windows") {
                        offlineLastDir = v;
                    }
                }
            }
            string jf = Path.Combine(root, "jvmargs.txt");
            if (File.Exists(jf)) jvmBox.Text = File.ReadAllText(jf).Trim();
        } catch { }
        loading = false;
    }
    static void SetCurrentUser(string root, string u) {
        if (u == null || u.Length == 0) return;
        userBox.Text = u;
        SaveUsername(root, u);
    }
    static void AddUser(string root) {
        string name = Prompt("Add username", "Enter player name:", userBox.Text.Trim());
        if (name == null || name.Length == 0) return;
        if (!users.Contains(name)) { users.Add(name); SaveUsers(root); }
        SetCurrentUser(root, name);
    }
    static void ShowUserList(string root) {
        ContextMenuStrip m = new ContextMenuStrip();
        m.BackColor = BgCard;
        m.ForeColor = Txt;
        foreach (string u in users) {
            string name = u;
            ToolStripMenuItem it = new ToolStripMenuItem(name);
            it.ForeColor = Txt;
            it.Click += delegate { SetCurrentUser(root, name); };
            m.Items.Add(it);
        }
        m.Show(userListBtn, new Point(0, userListBtn.Height));
    }

    static string Prompt(string title, string label, string initial) {
        Form d = new Form();
        d.Text = title;
        d.ClientSize = new Size(330, 132);
        d.FormBorderStyle = FormBorderStyle.FixedDialog;
        d.StartPosition = FormStartPosition.CenterParent;
        d.MaximizeBox = false;
        d.MinimizeBox = false;
        d.BackColor = BgPanel;
        d.ForeColor = Txt;
        d.Font = new Font("Segoe UI", 9f);

        Label l = new Label();
        l.Text = label;
        l.ForeColor = Txt;
        l.SetBounds(16, 16, 300, 20);
        d.Controls.Add(l);

        TextBox t = MakeInput(16, 42, 298);
        t.Text = initial;
        d.Controls.Add(t);

        Button ok = MakeFlat("OK", Accent, OnAccent, AccentHi);
        ok.SetBounds(126, 84, 88, 30);
        Button ca = MakeFlat("Cancel", BgCard, Txt, BgHover);
        ca.SetBounds(224, 84, 88, 30);
        d.Controls.Add(ok);
        d.Controls.Add(ca);
        d.AcceptButton = ok;
        d.CancelButton = ca;
        ok.Click += delegate { d.DialogResult = DialogResult.OK; d.Close(); };
        ca.Click += delegate { d.DialogResult = DialogResult.Cancel; d.Close(); };

        string result = null;
        if (d.ShowDialog(form) == DialogResult.OK) result = t.Text.Trim();
        return result;
    }

    // ---------- launch ----------
    static void OnPlay(string root) {
        // Chua nhap username -> bat buoc nhap truoc khi choi (lan chay dau tien)
        if (userBox.Text.Trim().Length == 0) {
            MessageBox.Show(form, "Nhập Username trước khi chơi.", "Valkaw Launcher",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        // Offline: chon JAR (session-only) neu chua co
        if (sourceMode == "Offline" && string.IsNullOrEmpty(offlineJar)) {
            using (OpenFileDialog ofd = new OpenFileDialog()) {
                ofd.Filter = "Fabric bundle JAR (*.jar)|*.jar|All files (*.*)|*.*";
                ofd.InitialDirectory = string.IsNullOrEmpty(offlineLastDir) ? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads" : offlineLastDir;
                if (ofd.ShowDialog() != DialogResult.OK) {
                    sourceMode = "Online";
                    return;
                }
                offlineJar = ofd.FileName;
                offlineLastDir = Path.GetDirectoryName(ofd.FileName);
                SaveSettings(root);
            }
        }
        string gameDir = VariantGameDir(root);
        string localJar = (sourceMode == "Offline") ? offlineJar : null;
        playBtn.Enabled = false;
        Thread t = new Thread(new ThreadStart(delegate { Launch(root, gameDir, localJar); }));
        t.IsBackground = true;
        t.Start();
    }

    /** GameDir riêng từng variant: custom dir (nếu có) dùng y nguyên cho Sodium,
       Variant Vulkan + custom dir -> thêm "_vulkan"; rỗng -> .valkaw\game_sodium / .valkaw\game_vulkan. */
    static string VariantGameDir(string root) {
        string baseDir = dirBox.Text.Trim();
        if (variantIdx == 0) return baseDir.Length > 0 ? baseDir : SodiumDir(root);
        return baseDir.Length > 0 ? baseDir + "_vulkan" : Path.Combine(root, "game_vulkan");
    }

    /** Thư mục Sodium: .valkaw\game_sodium, doi tu .valkaw\game cu (doi ten de giu save/settings). */
    static string SodiumDir(string root) {
        string dir = Path.Combine(root, "game_sodium");
        string old = Path.Combine(root, "game");
        try {
            if (!Directory.Exists(dir) && Directory.Exists(old)) Directory.Move(old, dir);
        } catch { }
        return dir;
    }

    static void Launch(string root, string gameDir, string localJar) {
        try {
            // Goi zip cua variant phai co trong gameDir — thieu/het version thi tai tu GitHub Releases
            EnsurePack(root, gameDir, variantIdx, sourceMode == "Offline" ? offlineJar : null);
            // Offline: cai xong pack local roi moi hien lai o Version (chon dung variant vua cai)
            if (!string.IsNullOrEmpty(localJar)) {
                try { form.BeginInvoke((MethodInvoker)delegate { ShowVersion(true); }); } catch { }
            }
            // Chua co Minecraft/Fabric/Java -> tai vao <root>\minecraft; co roi thi kiem tra qua
            EnsureRuntime(root);
            WriteResource("launch.ps1_text", Path.Combine(root, "launch.ps1"));
            SaveSettings(root);

            float g;
            if (!TryNum(ramBox.Text, out g) || g < 1) g = 6;
            if (g > 64) g = 64;
            // JVM chi nhan so nguyen + don vi -> doi GB sang MB (n * 1024), tranh "6,5G".
            string ramArg = ((int)Math.Round(g * 1024f)) + "M";

            string user = userBox.Text.Trim();
            if (user.Length == 0) user = "Player";
            SaveUsername(root, user);

            string prio = "Normal";
            if (prioRb[1].Checked) prio = "High";
            else if (prioRb[2].Checked) prio = "Realtime";

            string after = "Hide";
            if (afterRb[1].Checked) after = "Keep";
            else if (afterRb[2].Checked) after = "Close";

            bool hide = (after == "Hide");
            bool closeAfter = (after == "Close");
            bool needElev = ((prio == "High" || prio == "Realtime") && !IsAdmin());

            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = "powershell.exe";
            psi.Arguments = string.Format(
                "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"{0}\" -Ram {1} -GameDir \"{2}\" -Username \"{3}\" -Priority {4}",
                Path.Combine(root, "launch.ps1"), ramArg, gameDir, user, prio);
            psi.WorkingDirectory = root;
            if (needElev) { psi.UseShellExecute = true; psi.Verb = "runas"; }
            else {
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.WindowStyle = ProcessWindowStyle.Hidden;
            }

            Process proc = new Process();
            proc.StartInfo = psi;
            proc.EnableRaisingEvents = true;
            proc.Exited += delegate {
                int code = 0;
                try { code = proc.ExitCode; } catch { }
                form.BeginInvoke((MethodInvoker)delegate {
                    if (closeAfter) { Application.Exit(); return; }
                    playBtn.Enabled = true;
                    status.Text = (code == 0) ? "Ready." : "Game exited with an error \u2014 see log_launcher";
                    RestoreFromTray();
                });
            };
            proc.Start();
            status.Text = "Running...";
            if (hide) {
                try { form.BeginInvoke((MethodInvoker)delegate { HideToTray(); }); } catch { }
            }
        } catch (Exception ex) {
            SetPlay();
            status.Text = "Error: " + ex.Message;
        }
    }

    /** Lan chay dau (hoac khi release co JAR moi): tai JAR mods+settings va cai vao gameDir.
        mods/ ghi de (do minh quan ly), settings chi ghi khi file chua co -> nguoi choi chinh roi thi giu nguyen. */
    static void EnsurePack(string root, string gameDir, int v, string localJar) {
        string packName;
        bool isLocal = !string.IsNullOrEmpty(localJar);
        if (isLocal) {
            packName = Path.GetFileName(localJar);
            VerifyBundle(localJar, v, MC);
        } else {
            packName = PickPack(root, v);
        }
        string marker = Path.Combine(gameDir, ".jar-" + Regex.Replace(packName, "[^0-9A-Za-z._-]", "_"));
        // Check if already installed with same content
        if (File.Exists(marker)) {
            if (isLocal) {
                try {
                    string oldHash = File.ReadAllText(marker).Trim();
                    if (!string.IsNullOrEmpty(oldHash)) {
                        using (SHA256 sha = SHA256.Create()) {
                            using (FileStream fs = File.OpenRead(localJar)) {
                                string newHash = BitConverter.ToString(sha.ComputeHash(fs)).Replace("-", "").ToLower();
                                if (newHash == oldHash) return;
                            }
                        }
                    }
                } catch { }
            } else {
                return;
            }
        }
        string src = isLocal ? localJar : null;
        if (!isLocal) {
            SetStatus("Downloading " + packName + " from GitHub...");
            src = Path.Combine(Path.GetTempPath(), packName);
            using (WebClient wc = new WebClient()) {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                wc.DownloadFile(PackUrl(packName), src);
            }
        }
        Directory.CreateDirectory(gameDir);
        string mods = Path.Combine(gameDir, "mods");
        Directory.CreateDirectory(mods);
        // Remove old managed jars
        foreach (string f in Directory.GetFiles(mods, "*.jar")) {
            string n = Path.GetFileName(f);
            if (n.StartsWith("Sodium_") || n.StartsWith("Vulkan_") || n.StartsWith("VulkanMod_") || n.StartsWith("mc-client-standalone-"))
                try { File.Delete(f); } catch { }
        }
        // Clean old markers
        foreach (string p in new string[] { ".pack-*", ".jar-*", ".jar-v*", ".defaults-v*" }) {
            foreach (string m in Directory.GetFiles(mods, p)) { try { File.Delete(m); } catch { } }
            foreach (string m in Directory.GetFiles(gameDir, p)) { try { File.Delete(m); } catch { } }
        }
        using (ZipArchive a = ZipFile.OpenRead(src)) {
            foreach (ZipArchiveEntry e in a.Entries) {
                if (e.FullName.EndsWith("/")) continue;
                string rel = e.FullName.Replace('/', '\\');
                if (rel.StartsWith("mods\\", StringComparison.OrdinalIgnoreCase) || rel.Equals("fabric.mod.json", StringComparison.OrdinalIgnoreCase)) continue;
                string dest = Path.Combine(gameDir, rel);
                Directory.CreateDirectory(Path.GetDirectoryName(dest));
                if (File.Exists(dest)) continue;
                e.ExtractToFile(dest, true);
            }
        }
        string destJar = Path.Combine(mods, packName);
        if (!string.Equals(Path.GetFullPath(src), Path.GetFullPath(destJar), StringComparison.OrdinalIgnoreCase))
            File.Copy(src, destJar, true);
        if (!isLocal) try { File.Delete(src); } catch { }
        // Write content hash marker
        using (SHA256 sha = SHA256.Create()) {
            using (FileStream fs = File.OpenRead(isLocal ? localJar : destJar)) {
                string hash = BitConverter.ToString(sha.ComputeHash(fs)).Replace("-", "").ToLower();
                File.WriteAllText(marker, hash);
            }
        }
    }

    // ---------- runtime: Minecraft + Fabric + Java ----------
    // May chua co Minecraft 1.21.11 kem Fabric loader thi tai vao <root>\minecraft dung layout
    // ma launch.ps1 da doc san (versions\<MC>, libraries, assets); thieu Java 21 thi lay Temurin.
    // Da co cache thi chi kiem tra file, khong tai lai. May co .minecraft day du thi khong dung toi.
    const string FabricLoader = "0.19.5";
    const string VanillaManifest = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    const string FabricMetaUrl = "https://meta.fabricmc.net/v2/versions/loader";
    const string TemurinUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse";

    static string HomeMc() {
        return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
    }

    /** true = thu muc nay chay duoc: co vanilla json + client jar + fabric loader profile. */
    static bool RuntimeReady(string mcRoot) {
        try {
            if (!File.Exists(Path.Combine(mcRoot, "versions", MC, MC + ".json"))) return false;
            if (!File.Exists(Path.Combine(mcRoot, "versions", MC, MC + ".jar"))) return false;
            return FabricProfilePath(mcRoot) != null;
        } catch { return false; }
    }

    static string FabricProfilePath(string mcRoot) {
        try {
            foreach (string d in Directory.GetDirectories(Path.Combine(mcRoot, "versions"), "fabric-loader-*-" + MC)) {
                string n = Path.GetFileName(d);
                if (File.Exists(Path.Combine(d, n + ".json"))) return Path.Combine(d, n + ".json");
            }
        } catch { }
        return null;
    }

    // ---- JSON (System.Web.Extensions) ----
    static object JParse(string text) {
        System.Web.Script.Serialization.JavaScriptSerializer s = new System.Web.Script.Serialization.JavaScriptSerializer();
        s.MaxJsonLength = int.MaxValue;
        s.RecursionLimit = 200;
        return s.DeserializeObject(text);
    }
    static Dictionary<string, object> JObj(object o) { return o as Dictionary<string, object>; }
    static object JGet(object o, string key) {
        Dictionary<string, object> d = JObj(o);
        object v;
        return (d != null && d.TryGetValue(key, out v)) ? v : null;
    }
    static string JStr(object o, string key) { object v = JGet(o, key); return v == null ? null : v.ToString(); }
    static long JLong(object o, string key) {
        object v = JGet(o, key);
        if (v == null) return -1;
        try { return Convert.ToInt64(v); } catch { return -1; }
    }
    static object[] JArr(object o) { return o as object[] ?? new object[0]; }

    static string AssetPath(string mcRoot, string hash) {
        return Path.Combine(mcRoot, "assets", "objects", hash.Substring(0, 2), hash);
    }

    /** net.fabricmc:intermediary:1.21.11[:classifier] -> net/fabricmc/intermediary/1.21.11/intermediary-1.21.11[-classifier].jar */
    static string MavenRel(string name) {
        string[] p = name.Split(':');
        string cls = p.Length > 3 ? "-" + p[3] : "";
        string file = string.Format("{0}-{1}{2}.jar", p[1], p[2], cls);
        return p[0].Replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + file;
    }

    /** Rules Mojang: khong co rules = giu; arch = "x86" nghia la ho x86 (x64 van tinh). */
    static bool LibAllowed(object lib) {
        object[] rules = JArr(JGet(lib, "rules"));
        if (rules.Length == 0) return true;
        bool allow = false;
        foreach (object r in rules) {
            object os = JGet(r, "os");
            string osName = JStr(os, "name");
            if (osName != null && osName != "windows") continue;
            string arch = JStr(os, "arch");
            if (arch != null && arch != "x86") continue;
            object action = JGet(r, "action");
            allow = action != null && action.ToString() == "allow";
        }
        return allow;
    }

    static string DownloadString(string url) {
        using (WebClient wc = new WebClient()) {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
            wc.Headers.Add("User-Agent", "ValkawLauncher");
            return wc.DownloadString(url);
        }
    }

    /** Tai 1 file; da co dung size thi bo qua. Loi mang thi thu lai 3 lan. */
    static void Fetch(string url, string dest, long size) {
        if (File.Exists(dest)) {
            if (size < 0 || new FileInfo(dest).Length == size) return;
            try { File.Delete(dest); } catch { }
        }
        Directory.CreateDirectory(Path.GetDirectoryName(dest));
        for (int attempt = 0; ; attempt++) {
            try {
                using (WebClient wc = new WebClient()) {
                    ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                    wc.Headers.Add("User-Agent", "ValkawLauncher");
                    wc.DownloadFile(url, dest);
                }
                return;
            } catch (Exception) {
                if (attempt >= 2) throw;
                Thread.Sleep(500 * (attempt + 1));
            }
        }
    }

    static int JavaMajor(string exe) {
        try {
            ProcessStartInfo psi = new ProcessStartInfo(exe, "-version");
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardError = true;
            psi.RedirectStandardOutput = true;
            using (Process p = Process.Start(psi)) {
                string outp = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                p.WaitForExit(5000);
                Match m = Regex.Match(outp, "version \"(\\d+)");
                return m.Success ? int.Parse(m.Groups[1].Value) : 0;
            }
        } catch { return 0; }
    }

    /** Java 21 (uu tien dung ban 21) trong JAVA_HOME / PATH / <root>\jdk / Program Files; null = khong co. */
    static string FindJava(string root) {
        List<string> cands = new List<string>();
        string jh = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(jh)) cands.Add(Path.Combine(jh, "bin", "java.exe"));
        foreach (string d in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';'))
            if (d.Trim().Length > 0) cands.Add(Path.Combine(d.Trim(), "java.exe"));
        try {
            string jd = Path.Combine(root, "jdk");
            if (Directory.Exists(jd)) {
                foreach (string d in Directory.GetDirectories(jd)) {
                    cands.Add(Path.Combine(d, "bin", "java.exe"));
                    foreach (string d2 in Directory.GetDirectories(d)) cands.Add(Path.Combine(d2, "bin", "java.exe"));
                }
            }
        } catch { }
        try {
            foreach (string baseDir in new string[] { Environment.GetEnvironmentVariable("ProgramFiles"),
                                                      Environment.GetEnvironmentVariable("ProgramFiles(x86)") }) {
                if (string.IsNullOrEmpty(baseDir) || !Directory.Exists(baseDir)) continue;
                foreach (string vendor in Directory.GetDirectories(baseDir)) {
                    string[] jdks = Directory.GetDirectories(vendor);
                    for (int i = 0; i < jdks.Length && i < 12; i++) cands.Add(Path.Combine(jdks[i], "bin", "java.exe"));
                }
            }
        } catch { }
        string best = null;
        foreach (string c in cands) {
            if (!File.Exists(c)) continue;
            int major = JavaMajor(c);
            if (major == 21) return c;
            if (major >= 21 && best == null) best = c;
        }
        return best;
    }

    static void EnsureJava(string root) {
        if (FindJava(root) != null) return;
        SetStatus("Runtime: downloading Java 21 (~180 MB)...");
        string zip = Path.Combine(root, "jdk21.zip");
        Fetch(TemurinUrl, zip, -1);
        SetStatus("Runtime: extracting Java 21...");
        string dest = Path.Combine(root, "jdk");
        Directory.CreateDirectory(dest);
        using (ZipArchive a = ZipFile.OpenRead(zip)) {
            foreach (ZipArchiveEntry e in a.Entries) {
                if (e.FullName.EndsWith("/")) continue;
                string target = Path.Combine(dest, e.FullName.Replace('/', '\\'));
                Directory.CreateDirectory(Path.GetDirectoryName(target));
                try { e.ExtractToFile(target, true); } catch { }
            }
        }
        try { File.Delete(zip); } catch { }
        if (FindJava(root) == null) throw new Exception("Tai Java 21 xong nhung khong thay java.exe trong " + dest);
    }

    /** Json version vanilla (1.21.11) tu Mojang; mat mang thi lay cache <root>\cache. */
    static string VanillaText(string root) {
        string cache = Path.Combine(root, "cache", MC + ".json");
        try {
            string url = null;
            foreach (object v in JArr(JGet(JParse(DownloadString(VanillaManifest)), "versions"))) {
                if (JStr(v, "id") == MC) { url = JStr(v, "url"); break; }
            }
            if (url == null) throw new Exception("Mojang khong con version " + MC);
            string text = DownloadString(url);
            Directory.CreateDirectory(Path.GetDirectoryName(cache));
            File.WriteAllText(cache, text);
            return text;
        } catch {
            if (File.Exists(cache)) return File.ReadAllText(cache);
            throw;
        }
    }

    /** Profile json cua fabric-loader (mainClass + libraries); ghim FabricLoader neu meta con. */
    static string FabricText(string root, out string loader) {
        string cache = Path.Combine(root, "cache", "fabric-" + MC + ".json");
        loader = FabricLoader;
        try {
            List<string> have = new List<string>();
            foreach (object v in JArr(JParse(DownloadString(FabricMetaUrl + "/" + MC)))) {
                string lv = JStr(JGet(v, "loader"), "version");
                if (lv != null) have.Add(lv);
            }
            if (have.Count == 0) throw new Exception("Fabric meta khong tra loader cho " + MC);
            loader = have.Contains(FabricLoader) ? FabricLoader : have[0];
            string text = DownloadString(FabricMetaUrl + "/" + MC + "/" + loader + "/profile/json");
            Directory.CreateDirectory(Path.GetDirectoryName(cache));
            File.WriteAllText(cache, text);
            return text;
        } catch {
            if (File.Exists(cache)) return File.ReadAllText(cache);
            throw;
        }
    }

    static string LoaderFromProfile(string text) {
        string id = JStr(JParse(text), "id") ?? "";
        const string prefix = "fabric-loader-";
        if (id.StartsWith(prefix, StringComparison.Ordinal)) {
            int cut = id.LastIndexOf("-" + MC, StringComparison.Ordinal);
            if (cut > prefix.Length) return id.Substring(prefix.Length, cut - prefix.Length);
        }
        return FabricLoader;
    }

    /** Cai du runtime vao <root>\minecraft. Goi lai nhieu lan duoc: file nao co roi thi bo qua. */
    static void EnsureRuntime(string root) {
        if (RuntimeReady(HomeMc())) return;   // may da co .minecraft (vanilla + fabric) -> dung nguyen
        string mcRoot = Path.Combine(root, "minecraft");
        EnsureJava(root);

        string vtext = VanillaText(root);
        Dictionary<string, object> vanilla = JObj(JParse(vtext));
        object client = JGet(JGet(vanilla, "downloads"), "client");
        string vdir = Path.Combine(mcRoot, "versions", MC);
        Directory.CreateDirectory(vdir);
        string vjson = Path.Combine(vdir, MC + ".json");
        if (!File.Exists(vjson)) File.WriteAllText(vjson, vtext);
        string cjar = Path.Combine(vdir, MC + ".jar");
        if (!File.Exists(cjar)) {
            SetStatus("Runtime: downloading Minecraft " + MC + " client...");
            Fetch(JStr(client, "url"), cjar, JLong(client, "size"));
        }

        string loader;
        string ftext = FabricText(root, out loader);
        string fname = "fabric-loader-" + loader + "-" + MC;
        string fdir = Path.Combine(mcRoot, "versions", fname);
        Directory.CreateDirectory(fdir);
        if (!File.Exists(Path.Combine(fdir, fname + ".json"))) File.WriteAllText(Path.Combine(fdir, fname + ".json"), ftext);

        // ---- libraries (vanilla + fabric), bo entry khong danh cho Windows ----
        List<string[]> jobs = new List<string[]>();   // url, dest, size
        string libdir = Path.Combine(mcRoot, "libraries");
        foreach (object lib in JArr(JGet(vanilla, "libraries"))) {
            if (!LibAllowed(lib)) continue;
            object art = JGet(JGet(lib, "downloads"), "artifact");
            string path = JStr(art, "path");
            if (path != null) {
                jobs.Add(new string[] { JStr(art, "url"), Path.Combine(libdir, path.Replace('/', '\\')), JLong(art, "size").ToString() });
            } else if (JStr(lib, "name") != null) {
                string rel = MavenRel(JStr(lib, "name"));
                jobs.Add(new string[] { "https://libraries.minecraft.net/" + rel, Path.Combine(libdir, rel.Replace('/', '\\')), "-1" });
            }
        }
        foreach (object lib in JArr(JGet(JObj(JParse(ftext)), "libraries"))) {
            string name = JStr(lib, "name");
            if (name == null || !LibAllowed(lib)) continue;
            string rel = MavenRel(name);
            string baseUrl = (JStr(lib, "url") ?? "https://maven.fabricmc.net/").TrimEnd('/') + "/";
            jobs.Add(new string[] { baseUrl + rel, Path.Combine(libdir, rel.Replace('/', '\\')), "-1" });
        }
        int total = jobs.Count;
        int done = 0;
        int failed = 0;
        SetStatus(string.Format("Runtime: checking {0} libraries...", total));
        Parallel.ForEach(jobs, new ParallelOptions { MaxDegreeOfParallelism = 12 }, job => {
            try { Fetch(job[0], job[1], long.Parse(job[2])); } catch { Interlocked.Increment(ref failed); }
            int n = Interlocked.Increment(ref done);
            if (n % 20 == 0) SetStatus(string.Format("Runtime: libraries {0}/{1}", n, total));
        });

        // ---- assets (index + objects) ----
        object idx = JGet(vanilla, "assetIndex");
        string idxId = JStr(idx, "id");
        string idxPath = Path.Combine(mcRoot, "assets", "indexes", idxId + ".json");
        Fetch(JStr(idx, "url"), idxPath, JLong(idx, "size"));
        List<string[]> todo = new List<string[]>();   // hash, dest, size
        long bytes = 0;
        foreach (KeyValuePair<string, object> kv in JObj(JGet(JObj(JParse(File.ReadAllText(idxPath))), "objects"))) {
            string hash = JStr(kv.Value, "hash");
            if (hash == null || hash.Length < 2) continue;
            long size = JLong(kv.Value, "size");
            string dest = AssetPath(mcRoot, hash);
            if (File.Exists(dest) && (size < 0 || new FileInfo(dest).Length == size)) continue;
            todo.Add(new string[] { hash, dest, size.ToString() });
            bytes += Math.Max(0, size);
        }
        if (todo.Count > 0) {
            int atotal = todo.Count;
            int adone = 0;
            int afailed = 0;
            SetStatus(string.Format("Runtime: downloading {0} assets (~{1} MB)...", atotal, bytes / (1 << 20)));
            Parallel.ForEach(todo, new ParallelOptions { MaxDegreeOfParallelism = 16 }, item => {
                string hash = item[0];
                try { Fetch("https://resources.download.minecraft.net/" + hash.Substring(0, 2) + "/" + hash, item[1], long.Parse(item[2])); }
                catch { Interlocked.Increment(ref afailed); }
                int n = Interlocked.Increment(ref adone);
                if (n % 200 == 0) SetStatus(string.Format("Runtime: assets {0}/{1}", n, atotal));
            });
            if (afailed > 0) throw new Exception(string.Format("{0}/{1} asset tai loi - bam Play lai de tai tiep", afailed, atotal));
        }
        if (failed > 0) throw new Exception(string.Format("{0}/{1} library tai loi - bam Play lai de tai tiep", failed, total));
        SetStatus("Runtime ready.");
    }

    static void SetPlay() {
        try { form.BeginInvoke((MethodInvoker)delegate { playBtn.Enabled = true; }); } catch { }
    }

    static void SetStatus(string s) {
        try {
            form.BeginInvoke((MethodInvoker)delegate {
                status.Text = s;
                bool isError = s != null && (s.StartsWith("Error:") || s.Contains("exited with an error") || s.StartsWith("Cap nhat loi"));
                status.ForeColor = isError ? Error : Txt;
            });
        } catch { }
    }

    // ---------- helpers ----------
    /** An/hien o Version: Offline chi hien lai sau khi pack local da cai xong. */
    static void ShowVersion(bool on) {
        try {
            if (capVersion != null) capVersion.Visible = on;
            if (variantBox != null) variantBox.Visible = on;
        } catch { }
    }

    /** Ten bundle quyet dinh variant khi cai offline: Vulkan_* -> 1, con lai -> Sodium (0). */
    static int VariantFromJar(string jar) {
        return Path.GetFileName(jar).IndexOf("vulkan", StringComparison.OrdinalIgnoreCase) >= 0 ? 1 : 0;
    }

    static void OpenUrl(string url) {
        try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
    }

    static void WriteResource(string name, string path) {
        using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name)) {
            if (s == null) throw new Exception("Missing resource: " + name);
            using (FileStream fs = File.Create(path)) s.CopyTo(fs);
        }
    }

    static Image LoadBanner() {
        try {
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("valkaw_logo.jpg")) {
                if (s == null) return null;
                MemoryStream ms = new MemoryStream();
                s.CopyTo(ms);
                ms.Position = 0;
                return new Bitmap(ms);
            }
        } catch { return null; }
    }

    static Icon LoadIcon() {
        try {
            iconStream = new MemoryStream();
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("valkaw_icon.ico")) {
                if (s == null) return null;
                s.CopyTo(iconStream);
            }
            iconStream.Position = 0;
            return new Icon(iconStream);
        } catch { return null; }
    }

}






sealed class NoPadLabel : Label {
    public NoPadLabel() {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
    }
    protected override void OnPaint(PaintEventArgs e) {
        e.Graphics.Clear(BackColor);
        TextRenderer.DrawText(e.Graphics, Text, Font, new Rectangle(0, 0, Width, Height), ForeColor, BackColor,
            TextFormatFlags.NoPadding | TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);
    }
}
