using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace ZhiSuanInstaller
{
    /// <summary>
    /// 智算铝宾安装程序：解压内嵌 payload.zip 到用户目录，创建快捷方式与卸载项。
    /// 参数：--silent 静默安装；--dir=路径 指定安装目录；--no-shortcuts 不建快捷方式；--uninstall 卸载。
    /// </summary>
    internal static class Program
    {
        internal const string AppName = "智算铝宾";
        internal const string AppId = "ZhiSuan-Al";
        internal const string AppVersion = "1.0.0";
        internal const string Publisher = "ZhiSuan-Al";

        internal static string InstallDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            @"Programs\ZhiSuan-Al");

        [STAThread]
        private static int Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            bool silent = HasArg(args, "--silent");
            bool noShortcuts = HasArg(args, "--no-shortcuts");
            string dirArg = GetArgValue(args, "--dir=");
            if (!string.IsNullOrEmpty(dirArg)) InstallDir = Path.GetFullPath(dirArg);

            if (HasArg(args, "--uninstall"))
            {
                if (string.IsNullOrEmpty(dirArg)) InstallDir = ResolveExistingInstall();
                if (!silent)
                {
                    DialogResult r = MessageBox.Show(
                        "确定卸载 " + AppName + " 吗？\n\n安装目录：" + InstallDir,
                        AppName, MessageBoxButtons.YesNo, MessageBoxIcon.Question);
                    if (r != DialogResult.Yes) return 0;
                }
                return Uninstall(noShortcuts);
            }

            if (silent)
            {
                try
                {
                    DoInstall(noShortcuts, delegate(string msg) { });
                    return 0;
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine(ex.Message);
                    return 1;
                }
            }

            Application.Run(new InstallForm(noShortcuts));
            return 0;
        }

        // ---------- 安装 ----------

        internal static void DoInstall(bool noShortcuts, Action<string> progress)
        {
            progress("正在解压程序文件…");
            PreservedConfigs keep = PreservedConfigs.Capture(InstallDir);
            ExtractPayload(InstallDir);
            keep.Restore(InstallDir);

            progress("正在写入卸载程序…");
            string self = Application.ExecutablePath;
            string uninstaller = Path.Combine(InstallDir, "uninstall.exe");
            if (!string.Equals(self, uninstaller, StringComparison.OrdinalIgnoreCase))
            {
                try { File.Copy(self, uninstaller, true); } catch { }
            }

            if (!noShortcuts)
            {
                progress("正在创建快捷方式…");
                string target = Path.Combine(InstallDir, "ZhiSuan-Al.exe");
                string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string programs = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                CreateShortcut(Path.Combine(desktop, AppName + ".lnk"), target, InstallDir);
                string menuDir = Path.Combine(programs, AppName);
                Directory.CreateDirectory(menuDir);
                CreateShortcut(Path.Combine(menuDir, AppName + ".lnk"), target, InstallDir);
                CreateShortcut(Path.Combine(menuDir, "卸载 " + AppName + ".lnk"), uninstaller, InstallDir);
            }

            progress("正在注册卸载信息…");
            WriteRegistry();
            progress("安装完成");
        }

        private static void ExtractPayload(string target)
        {
            string tmp = Path.Combine(Path.GetTempPath(), "zs_payload_" + Guid.NewGuid().ToString("N") + ".zip");
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
            {
                if (s == null) throw new InvalidOperationException("安装包内缺少 payload.zip 资源。");
                using (FileStream fs = File.Create(tmp)) { s.CopyTo(fs); }
            }
            try
            {
                Directory.CreateDirectory(target);
                int code = Run("tar.exe", "-xf \"" + tmp + "\" -C \"" + target + "\"", 300000);
                if (code != 0)
                {
                    string ps = "Expand-Archive -LiteralPath '" + tmp + "' -DestinationPath '" + target + "' -Force";
                    code = Run("powershell.exe", "-NoProfile -NonInteractive -Command \"" + ps + "\"", 300000);
                }
                if (code != 0) throw new InvalidOperationException("解压失败（tar 与 PowerShell 均未成功）。");
            }
            finally
            {
                try { File.Delete(tmp); } catch { }
            }
        }

        private static void WriteRegistry()
        {
            string exe = Path.Combine(InstallDir, "ZhiSuan-Al.exe");
            using (RegistryKey k = Registry.CurrentUser.CreateSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Uninstall\" + AppId))
            {
                k.SetValue("DisplayName", AppName);
                k.SetValue("DisplayVersion", AppVersion);
                k.SetValue("Publisher", Publisher);
                k.SetValue("InstallLocation", InstallDir);
                k.SetValue("DisplayIcon", exe + ",0");
                k.SetValue("UninstallString", "\"" + Path.Combine(InstallDir, "uninstall.exe") + "\" --uninstall");
                k.SetValue("NoModify", 1, RegistryValueKind.DWord);
                k.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
        }

        private static void CreateShortcut(string lnkPath, string target, string workDir)
        {
            try
            {
                Type t = Type.GetTypeFromProgID("WScript.Shell");
                if (t == null) return;
                object shell = Activator.CreateInstance(t);
                object sc = t.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell,
                    new object[] { lnkPath });
                Type st = sc.GetType();
                st.InvokeMember("TargetPath", BindingFlags.SetProperty, null, sc, new object[] { target });
                st.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, sc, new object[] { workDir });
                st.InvokeMember("IconLocation", BindingFlags.SetProperty, null, sc, new object[] { target + ",0" });
                st.InvokeMember("Description", BindingFlags.SetProperty, null, sc,
                    new object[] { AppName + " 启动器" });
                st.InvokeMember("Save", BindingFlags.InvokeMethod, null, sc, null);
            }
            catch { }
        }

        // ---------- 卸载 ----------

        private static int Uninstall(bool noShortcuts)
        {
            if (!noShortcuts)
            {
                RemoveShortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
                    AppName + ".lnk"));
                string menuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), AppName);
                try { if (Directory.Exists(menuDir)) Directory.Delete(menuDir, true); } catch { }
            }

            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\" + AppId, false);
            }
            catch { }

            string self = Application.ExecutablePath;
            if (self.StartsWith(InstallDir, StringComparison.OrdinalIgnoreCase))
            {
                // 从安装目录运行时，自身无法删除，交给系统在进程退出后重试清理
                string cmd = "/c for /l %i in (1,1,30) do @(ping -n 2 127.0.0.1 >nul"
                    + " & rmdir /s /q \"" + InstallDir + "\" 2>nul"
                    + " & if not exist \"" + InstallDir + "\" exit /b 0)";
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", cmd);
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.WorkingDirectory = Path.GetTempPath();
                try { Process.Start(psi); } catch { }
            }
            else
            {
                try { if (Directory.Exists(InstallDir)) Directory.Delete(InstallDir, true); } catch { }
            }
            return 0;
        }

        private static void RemoveShortcut(string path)
        {
            try { if (File.Exists(path)) File.Delete(path); } catch { }
        }

        // ---------- 工具 ----------

        /// <summary>卸载时确定安装目录：优先程序自身所在目录，其次注册表记录。</summary>
        private static string ResolveExistingInstall()
        {
            try
            {
                string selfDir = Path.GetDirectoryName(Path.GetFullPath(Application.ExecutablePath));
                if (File.Exists(Path.Combine(selfDir, "ZhiSuan-Al.exe"))) return selfDir;
            }
            catch { }
            try
            {
                object loc = Registry.GetValue(
                    @"HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\" + AppId,
                    "InstallLocation", null);
                if (loc is string && ((string)loc).Length > 0) return Path.GetFullPath((string)loc);
            }
            catch { }
            return InstallDir;
        }

        internal static int Run(string exe, string args, int timeoutMs)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe, args);
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                using (Process p = Process.Start(psi))
                {
                    p.StandardOutput.ReadToEnd();
                    p.StandardError.ReadToEnd();
                    if (!p.WaitForExit(timeoutMs)) { try { p.Kill(); } catch { } return -1; }
                    return p.ExitCode;
                }
            }
            catch
            {
                return -1;
            }
        }

        private static bool HasArg(string[] args, string name)
        {
            if (args == null) return false;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] != null && args[i].Equals(name, StringComparison.OrdinalIgnoreCase)) return true;
            }
            return false;
        }

        private static string GetArgValue(string[] args, string prefix)
        {
            if (args == null) return null;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] != null && args[i].StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                    return args[i].Substring(prefix.Length).Trim('"');
            }
            return null;
        }

        internal sealed class PreservedConfigs
        {
            private readonly string _dft;
            private readonly string _model;
            private readonly string _dftPath;
            private readonly string _modelPath;

            private PreservedConfigs(string dft, string model, string dftPath, string modelPath)
            {
                _dft = dft; _model = model; _dftPath = dftPath; _modelPath = modelPath;
            }

            public static PreservedConfigs Capture(string dir)
            {
                string dftPath = Path.Combine(dir, "server", "dft_config.json");
                string modelPath = Path.Combine(dir, "server", "model_config.json");
                return new PreservedConfigs(ReadAll(dftPath), ReadAll(modelPath), dftPath, modelPath);
            }

            public void Restore(string dir)
            {
                if (!string.IsNullOrEmpty(_dft))
                {
                    try { Directory.CreateDirectory(Path.GetDirectoryName(_dftPath)); File.WriteAllText(_dftPath, _dft); }
                    catch { }
                }
                if (!string.IsNullOrEmpty(_model))
                {
                    try { Directory.CreateDirectory(Path.GetDirectoryName(_modelPath)); File.WriteAllText(_modelPath, _model); }
                    catch { }
                }
            }

            private static string ReadAll(string path)
            {
                try { return File.Exists(path) ? File.ReadAllText(path) : null; } catch { return null; }
            }
        }
    }

    /// <summary>深色安装向导窗口。</summary>
    internal sealed class InstallForm : Form
    {
        private static readonly Color Bg = Color.FromArgb(11, 18, 32);
        private static readonly Color Card = Color.FromArgb(19, 32, 51);
        private static readonly Color Accent = Color.FromArgb(56, 189, 248);
        private static readonly Color Fg = Color.FromArgb(229, 237, 247);
        private static readonly Color MutedColor = Color.FromArgb(139, 160, 184);

        private readonly bool _noShortcuts;
        private readonly Button _action = new Button();
        private readonly Button _secondary = new Button();
        private readonly ProgressBar _bar = new ProgressBar();
        private readonly Label _status = new Label();
        private bool _installed;

        public InstallForm(bool noShortcuts)
        {
            _noShortcuts = noShortcuts;
            Text = Program.AppName + " 安装程序";
            ClientSize = new Size(560, 340);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            BackColor = Bg;
            ForeColor = Fg;
            Font = new Font("Microsoft YaHei UI", 9f);

            Label title = new Label();
            title.Text = Program.AppName;
            title.Font = new Font("Microsoft YaHei UI", 22f, FontStyle.Bold);
            title.ForeColor = Fg;
            title.SetBounds(28, 26, 300, 42);
            Controls.Add(title);

            Label sub = new Label();
            sub.Text = "三语言智能体科研工作台 · v" + Program.AppVersion;
            sub.ForeColor = MutedColor;
            sub.SetBounds(30, 70, 420, 22);
            Controls.Add(sub);

            Panel card = new Panel();
            card.BackColor = Card;
            card.SetBounds(28, 108, 504, 130);
            Controls.Add(card);

            Label where = new Label();
            where.Text = "安装目录";
            where.ForeColor = MutedColor;
            where.SetBounds(18, 16, 160, 20);
            card.Controls.Add(where);

            Label path = new Label();
            path.Text = Program.InstallDir;
            path.ForeColor = Fg;
            path.AutoEllipsis = true;
            path.SetBounds(18, 40, 468, 20);
            card.Controls.Add(path);

            Label note = new Label();
            note.Text = "运行前需已安装 Python 3.9+（rdkit / paramiko / requests）、JDK 17+，并准备 Ollama 模型。";
            note.ForeColor = MutedColor;
            note.SetBounds(18, 72, 470, 40);
            card.Controls.Add(note);

            _bar.SetBounds(28, 252, 360, 10);
            Controls.Add(_bar);

            _status.Text = "准备就绪";
            _status.ForeColor = MutedColor;
            _status.SetBounds(28, 270, 360, 22);
            Controls.Add(_status);

            _action.Text = "开始安装";
            _action.Font = new Font("Microsoft YaHei UI", 10f, FontStyle.Bold);
            _action.FlatStyle = FlatStyle.Flat;
            _action.FlatAppearance.BorderSize = 0;
            _action.BackColor = Accent;
            _action.ForeColor = Color.FromArgb(8, 15, 26);
            _action.SetBounds(400, 244, 132, 38);
            _action.Click += OnAction;
            Controls.Add(_action);

            _secondary.Text = "打开目录";
            _secondary.FlatStyle = FlatStyle.Flat;
            _secondary.FlatAppearance.BorderColor = Color.FromArgb(45, 65, 90);
            _secondary.BackColor = Bg;
            _secondary.ForeColor = MutedColor;
            _secondary.SetBounds(400, 288, 132, 30);
            _secondary.Visible = false;
            _secondary.Click += delegate
            {
                try { Process.Start("explorer.exe", "\"" + Program.InstallDir + "\""); } catch { }
            };
            Controls.Add(_secondary);
        }

        private void OnAction(object sender, EventArgs e)
        {
            if (_installed)
            {
                try { Process.Start(Path.Combine(Program.InstallDir, "ZhiSuan-Al.exe")); } catch { }
                Close();
                return;
            }

            _action.Enabled = false;
            _action.Text = "安装中…";
            _bar.Style = ProgressBarStyle.Marquee;
            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    Program.DoInstall(_noShortcuts, delegate(string msg)
                    {
                        BeginInvoke((MethodInvoker)delegate { _status.Text = msg; });
                    });
                    BeginInvoke((MethodInvoker)delegate
                    {
                        _installed = true;
                        _bar.Style = ProgressBarStyle.Continuous;
                        _bar.Value = 100;
                        _status.Text = "安装完成：" + Program.InstallDir;
                        _status.ForeColor = Accent;
                        _action.Text = "启动程序";
                        _action.Enabled = true;
                        _secondary.Visible = true;
                    });
                }
                catch (Exception ex)
                {
                    BeginInvoke((MethodInvoker)delegate
                    {
                        _bar.Style = ProgressBarStyle.Continuous;
                        _bar.Value = 0;
                        _status.Text = "安装失败：" + ex.Message;
                        _action.Text = "重试";
                        _action.Enabled = true;
                        MessageBox.Show(ex.Message, Program.AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    });
                }
            });
        }
    }
}
