using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Threading;
using System.Windows.Forms;

static class Launcher {
    const string ReleasePage = "https://github.com/nlvtai25/valkaw-client/releases/latest";
    const string RuntimeUrl  = "https://github.com/nlvtai25/valkaw-client/releases/latest/download/game-content.zip";
    const string MC = "1.21.11";

    // ---- palette (set by SetPalette) ----
    static Color BgDeep, BgPanel, BgCard, BgHover, Txt, TxtDim, Line, Accent, AccentHi, AccentInk, OnAccent;
    static bool darkMode, reopen;

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
        }
    }

    static Form form;
    static NotifyIcon tray;   // icon khay khi launcher ẩn khỏi taskbar
    static Panel[] pages;
    static Button[] navBtns;
    static int activePage;
    static TrackBar ramSlider;
    static TextBox ramBox, jvmBox, dirBox, userBox;
    static RadioButton[] prioRb = new RadioButton[3];
    static RadioButton[] afterRb = new RadioButton[3];
    static Label status, versionBox;
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
    static string Marker(string root) { return Path.Combine(root, "minecraft", "versions", MC, MC + ".json"); }
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
    static void Main() {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.ApplicationExit += delegate {
            if (tray != null) { tray.Visible = false; tray.Dispose(); }
        };

        string root = InstallRoot();
        Directory.CreateDirectory(root);
        darkMode = LoadTheme(root);

        if (!File.Exists(Marker(root))) {
            SetPalette(darkMode);
            InstallForm dlg = new InstallForm();
            dlg.Icon = LoadIcon();
            if (dlg.ShowDialog() != DialogResult.OK) return; // user cancelled -> quit
        }

        bool again = true;
        while (again) {
            SetPalette(darkMode);
            reopen = false;
            BuildMain(root);
            LoadUsers(root);
            LoadUsername(root);
            LoadSettings(root);
            ShowPage(0);
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
            tray.Text = "Valkaw Launcher - v1.0";
            ContextMenuStrip menu = new ContextMenuStrip();
            menu.Items.Add("M\u1EDF launcher", null, delegate { RestoreFromTray(); });
            menu.Items.Add("Tho\u00E1t", null, delegate { Application.Exit(); });
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
        form.Text = "Valkaw Launcher - v1.0";
        form.ClientSize = new Size(560, 500);
        form.FormBorderStyle = FormBorderStyle.FixedSingle;
        form.MaximizeBox = false;
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

        // header / banner
        PictureBox banner = new PictureBox();
        banner.SetBounds(0, 0, 560, 86);
        banner.SizeMode = PictureBoxSizeMode.Zoom;
        banner.BackColor = BgDeep;
        banner.Image = LoadBanner();
        form.Controls.Add(banner);

        Panel strip = new Panel();
        strip.SetBounds(0, 86, 560, 2);
        strip.BackColor = Accent;
        form.Controls.Add(strip);

        // sidebar nav
        Panel side = new Panel();
        side.SetBounds(0, 88, 132, 304);
        side.BackColor = BgPanel;
        form.Controls.Add(side);

        string[] nav = { "RAM", "JVM Args", "Options", "Game Dir" };
        navBtns = new Button[nav.Length];
        for (int i = 0; i < nav.Length; i++) {
            Button b = MakeNav(nav[i], i);
            b.SetBounds(10, 14 + i * 46, 112, 36);
            side.Controls.Add(b);
            navBtns[i] = b;
        }

        // content
        Panel content = new Panel();
        content.SetBounds(132, 88, 428, 304);
        content.BackColor = BgDeep;
        form.Controls.Add(content);

        pages = new Panel[4];
        for (int i = 0; i < 4; i++) {
            pages[i] = new Panel();
            pages[i].SetBounds(0, 0, 428, 304);
            pages[i].BackColor = BgDeep;
            pages[i].ForeColor = Txt;
            content.Controls.Add(pages[i]);
        }
        BuildRam(pages[0]);
        BuildJvm(pages[1]);
        BuildOptions(pages[2]);
        BuildDir(pages[3]);

        // footer
        Panel foot = new Panel();
        foot.SetBounds(0, 392, 560, 108);
        foot.BackColor = BgPanel;
        form.Controls.Add(foot);

        Label capU = new Label();
        capU.Text = "Username";
        capU.ForeColor = TxtDim;
        capU.SetBounds(16, 8, 120, 14);
        foot.Controls.Add(capU);

        userBox = new TextBox();
        userBox.SetBounds(16, 24, 190, 26);
        userBox.BackColor = BgCard;
        userBox.ForeColor = Txt;
        userBox.BorderStyle = BorderStyle.FixedSingle;
        userBox.Font = new Font("Segoe UI", 10f);
        userBox.Text = "Player";
        foot.Controls.Add(userBox);

        Button addBtn = MakeFlat("", BgCard, Txt, BgHover);
        addBtn.SetBounds(212, 24, 32, 26);
        // Vẽ dấu + bằng pixel cho khớp tâm ô (chữ "+" của font bị lệch xuống dưới)
        addBtn.Paint += delegate(object s, PaintEventArgs e) {
            Graphics g = e.Graphics;
            int cx = addBtn.Width / 2, cy = addBtn.Height / 2, arm = 6;
            using (SolidBrush br = new SolidBrush(addBtn.ForeColor)) {
                g.FillRectangle(br, cx - arm, cy - 1, arm * 2, 2);
                g.FillRectangle(br, cx - 1, cy - arm, 2, arm * 2);
            }
        };
        addBtn.Click += delegate { AddUser(root); };
        foot.Controls.Add(addBtn);

        userListBtn = MakeFlat("\u25BC", BgCard, Txt, BgHover);
        userListBtn.SetBounds(248, 24, 32, 26);
        userListBtn.Click += delegate { ShowUserList(root); };
        foot.Controls.Add(userListBtn);

        Label capV = new Label();
        capV.Text = "Version";
        capV.ForeColor = TxtDim;
        capV.SetBounds(304, 8, 120, 14);
        foot.Controls.Add(capV);

        versionBox = new Label();
        versionBox.Text = MC;
        versionBox.TextAlign = ContentAlignment.MiddleCenter;
        versionBox.BackColor = BgCard;
        versionBox.ForeColor = AccentInk;
        versionBox.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
        versionBox.SetBounds(304, 24, 90, 26);
        foot.Controls.Add(versionBox);

        Button themeBtn = MakeFlat(darkMode ? "\u2600 S\u00E1ng" : "\u263D T\u1ED1i", BgCard, Txt, BgHover);
        themeBtn.SetBounds(440, 8, 104, 26);
        themeBtn.Click += delegate {
            darkMode = !darkMode;
            SaveTheme(root, darkMode);
            reopen = true;
            form.Close();
        };
        foot.Controls.Add(themeBtn);

        status = new Label();
        status.Text = "Ready.";
        status.ForeColor = TxtDim;
        status.SetBounds(16, 60, 300, 18);
        foot.Controls.Add(status);

        playBtn = MakeFlat("Play", Accent, OnAccent, AccentHi);
        playBtn.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
        playBtn.SetBounds(424, 48, 120, 42);
        playBtn.Click += delegate { OnPlay(root); };
        foot.Controls.Add(playBtn);

        LinkLabel link = new LinkLabel();
        link.Text = "GitHub Releases \u2014 tải/cập nhật thủ công";
        link.LinkColor = AccentInk;
        link.ActiveLinkColor = Accent;
        link.VisitedLinkColor = AccentInk;
        link.ForeColor = TxtDim;
        link.SetBounds(16, 84, 320, 18);
        link.LinkClicked += delegate { OpenUrl(ReleasePage); };
        foot.Controls.Add(link);
    }

    static Button MakeNav(string text, int idx) {
        Button b = new Button();
        b.Text = text;
        b.TextAlign = ContentAlignment.MiddleLeft;
        b.Padding = new Padding(12, 0, 0, 0);
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = BgPanel;
        b.ForeColor = Txt;
        b.Font = new Font("Segoe UI", 9.5f);
        b.Cursor = Cursors.Hand;
        b.MouseEnter += delegate { if (activePage != idx) b.BackColor = BgHover; };
        b.MouseLeave += delegate { if (activePage != idx) b.BackColor = BgPanel; };
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
            if (i == idx) { navBtns[i].BackColor = Accent; navBtns[i].ForeColor = OnAccent; }
            else { navBtns[i].BackColor = BgPanel; navBtns[i].ForeColor = Txt; }
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
    static void BuildRam(Panel p) {
        Label l = MakeLabel("RAM (GB, b\u01B0\u1EDBc 0.25):", 14, 14, 300);
        p.Controls.Add(l);

        ramSlider = new TrackBar();
        ramSlider.Minimum = 4;
        ramSlider.Maximum = 64;
        ramSlider.TickFrequency = 4;
        ramSlider.SmallChange = 1;
        ramSlider.LargeChange = 4;
        ramSlider.SetBounds(12, 46, 404, 45);
        ramSlider.Value = 24;
        ramSlider.BackColor = BgDeep;
        ramSlider.ValueChanged += delegate { ramBox.Text = (ramSlider.Value / 4f).ToString("0.##"); };
        p.Controls.Add(ramSlider);

        ramBox = MakeInput(12, 104, 90);
        ramBox.Text = "6";
        ramBox.TextChanged += delegate {
            float g;
            if (float.TryParse(ramBox.Text, out g)) {
                int v = (int)Math.Round(g * 4);
                if (v < 4) v = 4;
                if (v > 64) v = 64;
                if (ramSlider.Value != v) ramSlider.Value = v;
            }
        };
        p.Controls.Add(ramBox);

        Label u = MakeLabel("GB", 108, 108, 40);
        p.Controls.Add(u);
    }

    static void BuildJvm(Panel p) {
        p.Controls.Add(MakeLabel("JVM arguments th\u00EAm (c\u00E1ch nhau b\u1EB1ng d\u1EA5u c\u00E1ch):", 14, 14, 400));

        jvmBox = new TextBox();
        jvmBox.Multiline = true;
        jvmBox.ScrollBars = ScrollBars.Vertical;
        jvmBox.SetBounds(12, 44, 404, 246);
        jvmBox.BackColor = BgCard;
        jvmBox.ForeColor = Txt;
        jvmBox.BorderStyle = BorderStyle.FixedSingle;
        jvmBox.Font = new Font("Consolas", 9f);
        p.Controls.Add(jvmBox);
    }

    static void BuildOptions(Panel p) {
        GroupBox gp = MakeGroup("Priority", 12, 16, 404, 64);
        p.Controls.Add(gp);
        string[] pr = { "Normal", "High", "Realtime" };
        for (int i = 0; i < 3; i++) {
            prioRb[i] = new RadioButton();
            prioRb[i].Text = pr[i];
            prioRb[i].ForeColor = Txt;
            prioRb[i].BackColor = BgPanel;
            prioRb[i].SetBounds(16 + i * 128, 24, 120, 22);
            prioRb[i].Checked = (i == 0);
            gp.Controls.Add(prioRb[i]);
        }

        GroupBox ga = MakeGroup("After run", 12, 94, 404, 64);
        p.Controls.Add(ga);
        string[] ar = { "Hide", "Keep", "Close" };
        for (int i = 0; i < 3; i++) {
            afterRb[i] = new RadioButton();
            afterRb[i].Text = ar[i];
            afterRb[i].ForeColor = Txt;
            afterRb[i].BackColor = BgPanel;
            afterRb[i].SetBounds(16 + i * 128, 24, 120, 22);
            afterRb[i].Checked = (i == 0);
            ga.Controls.Add(afterRb[i]);
        }

        Label hint = MakeLabel("High/Realtime s\u1EBD y\u00EAu c\u1EA7u quy\u1EC1n Admin (UAC).", 14, 176, 400);
        hint.ForeColor = TxtDim;
        p.Controls.Add(hint);
    }

    static void BuildDir(Panel p) {
        p.Controls.Add(MakeLabel("Th\u01B0 m\u1EE5c game:", 14, 14, 300));

        dirBox = MakeInput(12, 44, 300);
        p.Controls.Add(dirBox);

        Button b = MakeFlat("Ch\u1ECDn...", BgCard, Txt, BgHover);
        b.SetBounds(318, 42, 98, 28);
        b.Click += delegate {
            FolderBrowserDialog fbd = new FolderBrowserDialog();
            if (fbd.ShowDialog() == DialogResult.OK) dirBox.Text = fbd.SelectedPath;
        };
        p.Controls.Add(b);

        Label h = MakeLabel("Tr\u1ED1ng = %AppData%\\.valkaw\\game", 14, 82, 400);
        h.ForeColor = TxtDim;
        p.Controls.Add(h);
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
        t.SetBounds(x, y, w, 26);
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
        if (users.Count == 0) users.Add("Player");
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

    /** Lưu Priority / After run / JVM Args. File nằm trong %APPDATA%\.valkaw nên cập nhật launcher vẫn giữ nguyên. */
    static void SaveSettings(string root) {
        try {
            string prio = prioRb[1].Checked ? "High" : (prioRb[2].Checked ? "Realtime" : "Normal");
            string after = afterRb[1].Checked ? "Keep" : (afterRb[2].Checked ? "Close" : "Hide");
            File.WriteAllText(SettingsFile(root),
                "ram=" + ramBox.Text.Trim() + "\r\npriority=" + prio + "\r\nafter=" + after + "\r\n");
            File.WriteAllText(Path.Combine(root, "jvmargs.txt"), jvmBox.Text.Trim());
        } catch { }
    }

    static void LoadSettings(string root) {
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
                        if (float.TryParse(v, out g)) {
                            if (g < 1) g = 1;
                            if (g > 16) g = 16;
                            int val = (int)Math.Round(g * 4);
                            if (val < ramSlider.Minimum) val = ramSlider.Minimum;
                            if (val > ramSlider.Maximum) val = ramSlider.Maximum;
                            ramSlider.Value = val;              // ValueChanged tự cập nhật ramBox
                            ramBox.Text = g.ToString("0.##");
                        }
                    } else if (k == "priority" && (v == "Normal" || v == "High" || v == "Realtime")) {
                        prioRb[0].Checked = v == "Normal";
                        prioRb[1].Checked = v == "High";
                        prioRb[2].Checked = v == "Realtime";
                    } else if (k == "after" && (v == "Hide" || v == "Keep" || v == "Close")) {
                        afterRb[0].Checked = v == "Hide";
                        afterRb[1].Checked = v == "Keep";
                        afterRb[2].Checked = v == "Close";
                    }
                }
            }
            string jf = Path.Combine(root, "jvmargs.txt");
            if (File.Exists(jf)) jvmBox.Text = File.ReadAllText(jf).Trim();
        } catch { }
    }
    static void SetCurrentUser(string root, string u) {
        if (u == null || u.Length == 0) return;
        userBox.Text = u;
        SaveUsername(root, u);
    }
    static void AddUser(string root) {
        string name = Prompt("Th\u00EAm username", "Nh\u1EADp t\u00EAn ng\u01B0\u1EDDi ch\u01A1i:", userBox.Text.Trim());
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
        Button ca = MakeFlat("H\u1EE7y", BgCard, Txt, BgHover);
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
        if (!File.Exists(Marker(root))) {
            InstallForm dlg = new InstallForm();
            dlg.Icon = LoadIcon();
            if (dlg.ShowDialog() != DialogResult.OK) return;
        }
        string gameDir = dirBox.Text.Trim();
        if (gameDir.Length == 0) gameDir = Path.Combine(root, "game");
        playBtn.Enabled = false;
        Thread t = new Thread(new ThreadStart(delegate { Launch(root, gameDir); }));
        t.IsBackground = true;
        t.Start();
    }

    static void Launch(string root, string gameDir) {
        try {
            Directory.CreateDirectory(gameDir);
            WriteResource("launch.ps1_text", Path.Combine(root, "launch.ps1"));
            SaveSettings(root);

            float g;
            if (!float.TryParse(ramBox.Text.Trim(), out g) || g < 1) g = 6;
            string ramArg = g.ToString("0.##") + "G";

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
                    status.Text = (code == 0) ? "Ready." : "L\u1ED7i khi ch\u1EA1y game \u2014 xem log_launcher\\latest.log";
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
            status.Text = "L\u1ED7i: " + ex.Message;
        }
    }

    static void SetPlay() {
        try { form.BeginInvoke((MethodInvoker)delegate { playBtn.Enabled = true; }); } catch { }
    }

    // ---------- install ----------
    internal static void Install(bool online, string root, Action<string> setStatus, Action<int> setProgress) {
        Directory.CreateDirectory(root);
        if (File.Exists(Marker(root))) return;

        string zip;
        if (online) {
            setStatus("\u0110ang t\u1EA3i runtime t\u1EEB GitHub...");
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
            zip = Path.Combine(Path.GetTempPath(), "valkaw-game-content.zip");
            using (WebClient wc = new WebClient()) {
                bool done = false;
                Exception err = null;
                wc.DownloadProgressChanged += delegate(object s, DownloadProgressChangedEventArgs e) {
                    setProgress(e.ProgressPercentage);
                };
                wc.DownloadFileCompleted += delegate(object s, System.ComponentModel.AsyncCompletedEventArgs e) {
                    err = e.Error; done = true;
                };
                wc.DownloadFileAsync(new Uri(RuntimeUrl), zip);
                while (!done) Thread.Sleep(150);
                if (err != null) throw err;
            }
        } else {
            zip = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "game-content.zip");
            if (!File.Exists(zip)) throw new Exception("Kh\u00F4ng t\u00ECm th\u1EA5y game-content.zip c\u1EA1nh launcher.");
        }

        setProgress(0);
        setStatus("\u0110ang gi\u1EA3i n\u00E9n...");
        Extract(zip, root);
        if (online) { try { File.Delete(zip); } catch { } }
    }

    static void Extract(string zip, string root) {
        using (ZipArchive za = ZipFile.OpenRead(zip)) {
            foreach (ZipArchiveEntry e in za.Entries) {
                string dest = Path.Combine(root, e.FullName);
                if (string.IsNullOrEmpty(e.Name)) { Directory.CreateDirectory(dest); continue; }
                Directory.CreateDirectory(Path.GetDirectoryName(dest));
                e.ExtractToFile(dest, true);
            }
        }
    }

    internal static bool OfflineAvailable() {
        return File.Exists(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "game-content.zip"));
    }

    // ---------- helpers ----------
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

    // ---------- first-run install dialog ----------
    class InstallForm : Form {
        Button onlineBtn, offlineBtn, cancelBtn;
        ProgressBar bar;
        Label status;

        public InstallForm() {
            Text = "Valkaw Launcher - C\u00E0i \u0111\u1EB7t";
            ClientSize = new Size(460, 300);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            StartPosition = FormStartPosition.CenterScreen;
            MaximizeBox = false;
            MinimizeBox = false;
            BackColor = BgPanel;
            ForeColor = Txt;
            Font = new Font("Segoe UI", 9f);

            Label title = new Label();
            title.Text = "C\u00E0i \u0111\u1EB7t Valkaw 1.21.11";
            title.Font = new Font("Segoe UI", 14f, FontStyle.Bold);
            title.ForeColor = AccentInk;
            title.SetBounds(24, 20, 420, 30);
            Controls.Add(title);

            Label desc = new Label();
            desc.Text = "Ch\u01B0a c\u00F3 d\u1EEF li\u1EC7u game. Ch\u1ECDn c\u00E1ch c\u00E0i \u0111\u1EB7t:";
            desc.ForeColor = TxtDim;
            desc.SetBounds(24, 56, 420, 20);
            Controls.Add(desc);

            onlineBtn = MakeFlat("Online  \u2014  t\u1EA3i t\u1EEB GitHub Releases", Accent, OnAccent, AccentHi);
            onlineBtn.TextAlign = ContentAlignment.MiddleLeft;
            onlineBtn.Padding = new Padding(16, 0, 0, 0);
            onlineBtn.SetBounds(24, 90, 412, 46);
            onlineBtn.Click += delegate { Start(true); };
            Controls.Add(onlineBtn);

            offlineBtn = MakeFlat("Offline  \u2014  gi\u1EA3i n\u00E9n game-content.zip c\u1EA1nh launcher", BgCard, Txt, BgHover);
            offlineBtn.TextAlign = ContentAlignment.MiddleLeft;
            offlineBtn.Padding = new Padding(16, 0, 0, 0);
            offlineBtn.SetBounds(24, 146, 412, 46);
            offlineBtn.Enabled = OfflineAvailable();
            offlineBtn.Click += delegate { Start(false); };
            Controls.Add(offlineBtn);

            bar = new ProgressBar();
            bar.SetBounds(24, 206, 412, 20);
            bar.Style = ProgressBarStyle.Continuous;
            bar.Visible = false;
            Controls.Add(bar);

            status = new Label();
            status.Text = OfflineAvailable()
                ? "C\u00F3 s\u1EB5n game-content.zip \u2014 c\u00F3 th\u1EC3 c\u00E0i Offline."
                : "Kh\u00F4ng th\u1EA5y game-content.zip \u2014 ch\u1EC9 c\u00F3 th\u1EC3 c\u00E0i Online.";
            status.ForeColor = TxtDim;
            status.SetBounds(24, 206, 412, 20);
            Controls.Add(status);

            cancelBtn = MakeFlat("H\u1EE7y", BgCard, Txt, BgHover);
            cancelBtn.SetBounds(348, 240, 88, 32);
            cancelBtn.Click += delegate { DialogResult = DialogResult.Cancel; Close(); };
            Controls.Add(cancelBtn);

            if (darkMode) {
                try {
                    int v = 1;
                    if (DwmSetWindowAttribute(Handle, 20, ref v, 4) != 0)
                        DwmSetWindowAttribute(Handle, 19, ref v, 4);
                } catch { }
            }
        }

        void SetStatus(string s) {
            try { BeginInvoke((MethodInvoker)delegate { status.Text = s; }); } catch { }
        }
        void SetProgress(int p) {
            try {
                BeginInvoke((MethodInvoker)delegate {
                    bar.Style = ProgressBarStyle.Continuous;
                    if (p < 0) p = 0; if (p > 100) p = 100;
                    bar.Value = p;
                });
            } catch { }
        }

        void Start(bool online) {
            onlineBtn.Enabled = false;
            offlineBtn.Enabled = false;
            cancelBtn.Enabled = false;
            bar.Visible = true;
            status.Visible = false;
            Thread t = new Thread(new ThreadStart(delegate {
                try {
                    Install(online, InstallRoot(),
                        delegate(string s) { SetStatusNetwork(s); },
                        delegate(int p) { SetProgress(p); });
                    BeginInvoke((MethodInvoker)delegate { DialogResult = DialogResult.OK; Close(); });
                } catch (Exception ex) {
                    BeginInvoke((MethodInvoker)delegate {
                        onlineBtn.Enabled = true;
                        offlineBtn.Enabled = OfflineAvailable();
                        cancelBtn.Enabled = true;
                        bar.Visible = false;
                        status.Visible = true;
                        status.Text = "L\u1ED7i: " + ex.Message;
                    });
                }
            }));
            t.IsBackground = true;
            t.Start();
        }

        void SetStatusNetwork(string s) {
            try {
                BeginInvoke((MethodInvoker)delegate {
                    status.Visible = true;
                    status.Text = s;
                });
            } catch { }
        }
    }
}
