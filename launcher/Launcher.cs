using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace ZhiSuanLauncher
{
    /// <summary>
    /// 智算铝宾启动器：编译检查 -> 启动 Python 服务层 -> 健康检查 -> 启动 Java GUI。
    /// 参数原样透传给 GUI（支持 --mode=audit|dispatch、--port=8899）。
    /// </summary>
    internal static class Program
    {
        private const int DefaultPort = 8899;
        private const string LogDirName = "logs";

        private static string _root = "";
        private static string _logDir = "";

        [STAThread]
        private static int Main(string[] args)
        {
            _root = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
            _logDir = Path.Combine(_root, LogDirName);
            Application.EnableVisualStyles();
            try
            {
                Directory.CreateDirectory(_logDir);

                int port = ParsePort(args);
                string python = FindPython();
                if (python == null)
                {
                    Error("未找到可用的 Python 环境。\n\n"
                        + "请设置环境变量 ZHISUAN_PYTHON 指向 python.exe，\n"
                        + "或安装 Python 3.9+ 并加入 PATH。");
                    return 2;
                }

                string javaDir = Path.Combine(_root, "ui-java");
                string workflowOut = Path.Combine(_root, "ui-workflow", "out");
                if (!Directory.Exists(Path.Combine(javaDir, "out")) || !Directory.Exists(workflowOut))
                {
                    string bat = Path.Combine(javaDir, "build.bat");
                    if (!File.Exists(bat))
                    {
                        Error("缺少编译脚本：ui-java\\build.bat");
                        return 3;
                    }
                    if (RunHidden("cmd.exe", "/c \"" + bat + "\"", javaDir,
                            Path.Combine(_logDir, "build.log"), 300000) != 0)
                    {
                        Error("Java 源码编译失败，详见 logs\\build.log。\n\n请确认已安装 JDK 17+ 并加入 PATH。");
                        return 3;
                    }
                }

                if (!PortOpen(port))
                {
                    Process server = StartServer(python, port);
                    if (server == null)
                    {
                        Error("Python 服务层启动失败，详见 logs\\server.log。");
                        return 4;
                    }
                    if (!WaitHealthy(port, 90))
                    {
                        try { server.Kill(); } catch { }
                        Error("Python 服务层在 90 秒内未就绪，详见 logs\\server.log。");
                        return 4;
                    }
                }

                if (!StartGui(javaDir, port, args))
                {
                    Error("无法启动 Java GUI。请确认已安装 JDK 17+（需要 javaw.exe）。");
                    return 5;
                }
                return 0;
            }
            catch (Exception ex)
            {
                Error("启动失败：" + ex.Message);
                return 1;
            }
        }

        // ---------- 参数 ----------

        private static int ParsePort(string[] args)
        {
            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    string a = args[i];
                    if (a == null) continue;
                    if (a.StartsWith("--port=", StringComparison.Ordinal))
                    {
                        int p;
                        if (int.TryParse(a.Substring(7).Trim(), out p) && p > 0 && p < 65536) return p;
                    }
                }
            }
            return DefaultPort;
        }

        // ---------- Python ----------

        private static string FindPython()
        {
            string env = Environment.GetEnvironmentVariable("ZHISUAN_PYTHON");
            if (!string.IsNullOrEmpty(env) && File.Exists(env) && CanRunPython(env)) return env;

            string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string[] candidates = new string[]
            {
                Path.Combine(home, @"miniconda3\envs\rdkit-env\python.exe"),
                Path.Combine(home, @"anaconda3\envs\rdkit-env\python.exe"),
                Path.Combine(home, @"miniconda3\python.exe"),
                Path.Combine(home, @"anaconda3\python.exe"),
                "py.exe",
                "python.exe"
            };

            string fallback = null;
            for (int i = 0; i < candidates.Length; i++)
            {
                string exe = candidates[i];
                if (exe.StartsWith("py.exe", StringComparison.OrdinalIgnoreCase))
                {
                    if (CanRunPythonArgs("py.exe", "-3 -c \"import sys\"")) return "py.exe|-3";
                    continue;
                }
                if (exe.StartsWith("python.exe", StringComparison.OrdinalIgnoreCase))
                {
                    if (CanRunPython("python.exe")) { if (fallback == null) fallback = "python.exe"; }
                    continue;
                }
                if (File.Exists(exe) && CanRunPython(exe))
                {
                    if (HasChemDeps(exe)) return exe;
                    if (fallback == null) fallback = exe;
                }
            }
            return fallback;
        }

        private static bool CanRunPython(string exe)
        {
            return CanRunPythonArgs(exe, "-c \"import sys\"") || CanRunPythonArgs(exe, "-3 -c \"import sys\"");
        }

        private static bool HasChemDeps(string exe)
        {
            return CanRunPythonArgs(exe, "-c \"import rdkit,requests\"");
        }

        private static bool CanRunPythonArgs(string exe, string args)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe, args);
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.WorkingDirectory = _root;
                using (Process p = Process.Start(psi))
                {
                    p.StandardOutput.ReadToEnd();
                    p.StandardError.ReadToEnd();
                    if (!p.WaitForExit(8000)) { try { p.Kill(); } catch { } return false; }
                    return p.ExitCode == 0;
                }
            }
            catch
            {
                return false;
            }
        }

        private static Process StartServer(string python, int port)
        {
            string script = Path.Combine(_root, "server", "agent_server.py");
            if (!File.Exists(script)) return null;

            string exe = python;
            string args = "\"" + script + "\" " + port;
            int bar = python.IndexOf("|-");
            if (bar >= 0)
            {
                exe = python.Substring(0, bar);
                args = python.Substring(bar + 2) + " \"" + script + "\" " + port;
            }

            ProcessStartInfo psi = new ProcessStartInfo(exe, args);
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WorkingDirectory = _root;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.StandardOutputEncoding = Encoding.UTF8;
            psi.StandardErrorEncoding = Encoding.UTF8;

            Process p = new Process();
            p.StartInfo = psi;
            p.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) { AppendLog(e.Data); };
            p.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) { AppendLog(e.Data); };
            p.Start();
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();
            return p;
        }

        private static readonly object _logLock = new object();

        private static void AppendLog(string line)
        {
            if (line == null) return;
            try
            {
                lock (_logLock)
                {
                    File.AppendAllText(Path.Combine(_logDir, "server.log"),
                        DateTime.Now.ToString("HH:mm:ss") + "  " + line + Environment.NewLine,
                        Encoding.UTF8);
                }
            }
            catch { }
        }

        // ---------- 服务层 ----------

        private static bool PortOpen(int port)
        {
            try
            {
                using (TcpClient c = new TcpClient())
                {
                    IAsyncResult ar = c.BeginConnect("127.0.0.1", port, null, null);
                    if (!ar.AsyncWaitHandle.WaitOne(400)) return false;
                    c.EndConnect(ar);
                    return true;
                }
            }
            catch
            {
                return false;
            }
        }

        private static bool WaitHealthy(int port, int seconds)
        {
            string url = "http://127.0.0.1:" + port + "/api/health";
            DateTime deadline = DateTime.Now.AddSeconds(seconds);
            while (DateTime.Now < deadline)
            {
                try
                {
                    HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                    req.Timeout = 1500;
                    req.ReadWriteTimeout = 1500;
                    using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                    {
                        if ((int)resp.StatusCode == 200) return true;
                    }
                }
                catch { }
                Thread.Sleep(500);
            }
            return false;
        }

        // ---------- GUI ----------

        private static bool StartGui(string javaDir, int port, string[] args)
        {
            string exe = FindJavaw();
            if (exe == null) return false;

            string cp = "out" + Path.PathSeparator + @"lib\jlatexmath-1.0.7.jar"
                + Path.PathSeparator + @"..\ui-workflow\out"
                + Path.PathSeparator + @"..\assets";

            StringBuilder sb = new StringBuilder();
            sb.Append("-cp ");
            sb.Append('"').Append(cp).Append('"');
            sb.Append(" -Dfile.encoding=UTF-8");

            sb.Append(" zhisuan.Main");

            // 未显式指定端口时补上，保证 GUI 与服务层端口一致。
            // 注意：参数必须放在主类 zhisuan.Main 之后，放在之前会被 JVM 当作虚拟机选项
            // （Unrecognized option: --port=8899）导致 javaw 拒绝启动。
            bool portGiven = false;
            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    if (args[i] != null && args[i].StartsWith("--port=", StringComparison.Ordinal)) portGiven = true;
                }
            }
            if (!portGiven) sb.Append(" --port=").Append(port);
            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    if (args[i] == null) continue;
                    sb.Append(' ').Append('"').Append(args[i].Replace("\"", "")).Append('"');
                }
            }

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe, sb.ToString());
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.WorkingDirectory = javaDir;
                Process.Start(psi);
                return true;
            }
            catch
            {
                return false;
            }
        }

        private static string FindJavaw()
        {
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(home))
            {
                string p = Path.Combine(home, @"bin\javaw.exe");
                if (File.Exists(p)) return p;
            }
            string path = Environment.GetEnvironmentVariable("PATH");
            if (!string.IsNullOrEmpty(path))
            {
                string[] parts = path.Split(new char[] { ';' }, StringSplitOptions.RemoveEmptyEntries);
                for (int i = 0; i < parts.Length; i++)
                {
                    try
                    {
                        string p = Path.Combine(parts[i].Trim(), "javaw.exe");
                        if (File.Exists(p)) return p;
                    }
                    catch { }
                }
            }
            return null;
        }

        // ---------- 工具 ----------

        private static int RunHidden(string exe, string args, string workDir, string logFile, int timeoutMs)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(exe, args);
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.WorkingDirectory = workDir;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.StandardOutputEncoding = Encoding.UTF8;
                psi.StandardErrorEncoding = Encoding.UTF8;

                StringBuilder sb = new StringBuilder();
                Process p = new Process();
                p.StartInfo = psi;
                p.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                p.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) { if (e.Data != null) lock (sb) sb.AppendLine(e.Data); };
                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();

                if (!p.WaitForExit(timeoutMs))
                {
                    try { p.Kill(); } catch { }
                    File.AppendAllText(logFile, "[启动器] 编译超时" + Environment.NewLine, Encoding.UTF8);
                    return -1;
                }
                Thread.Sleep(200);
                lock (sb)
                {
                    File.WriteAllText(logFile, sb.ToString(), Encoding.UTF8);
                }
                return p.ExitCode;
            }
            catch (Exception ex)
            {
                try { File.WriteAllText(logFile, "[启动器] " + ex.Message, Encoding.UTF8); } catch { }
                return -1;
            }
        }

        private static void Error(string msg)
        {
            MessageBox.Show(msg, "智算铝宾", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }
}
