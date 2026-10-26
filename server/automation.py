#!/usr/bin/env python3
"""
自动操作电脑模块 - 提供文件、命令、GUI自动化功能
供Agent调用，实现自动化任务。
"""

import os
import sys
import subprocess
import shutil
import tempfile
import webbrowser
try:
    import pyperclip  # 剪贴板操作（需 pip install pyperclip）
except ImportError:
    pyperclip = None  # 缺失时剪贴板功能不可用，调用处已有try-except兜底
from pathlib import Path
from typing import List, Optional, Dict, Any

# 尝试导入 pyautogui (可选，用于GUI自动化)
try:
    import pyautogui
    PY_AUTOGUI_AVAILABLE = True
except ImportError:
    PY_AUTOGUI_AVAILABLE = False
    print("警告：pyautogui 未安装，GUI自动化功能受限。安装: pip install pyautogui")

class AutomationEngine:
    """自动化引擎，提供各类操作系统交互功能"""

    def __init__(self):
        self.working_dir = os.getcwd()
        self.last_result = None

    # ---------- 文件系统操作 ----------
    def list_directory(self, path: str = None) -> List[str]:
        """列出目录内容"""
        if path is None:
            path = self.working_dir
        try:
            return os.listdir(path)
        except Exception as e:
            return [f"错误：{e}"]

    def create_directory(self, path: str) -> bool:
        """创建目录（含父目录）"""
        try:
            os.makedirs(path, exist_ok=True)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def delete_file(self, path: str) -> bool:
        """删除文件或空目录"""
        try:
            if os.path.isdir(path):
                os.rmdir(path)
            else:
                os.remove(path)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def copy_file(self, src: str, dst: str) -> bool:
        """复制文件或目录"""
        try:
            if os.path.isdir(src):
                shutil.copytree(src, dst, dirs_exist_ok=True)
            else:
                shutil.copy2(src, dst)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def move_file(self, src: str, dst: str) -> bool:
        """移动文件或目录"""
        try:
            shutil.move(src, dst)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def read_file(self, path: str) -> Optional[str]:
        """读取文本文件内容"""
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return f.read()
        except Exception as e:
            self.last_result = str(e)
            return None

    def write_file(self, path: str, content: str) -> bool:
        """写入文本文件"""
        try:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def get_file_info(self, path: str) -> Dict[str, Any]:
        """获取文件信息"""
        try:
            stat = os.stat(path)
            return {
                "size": stat.st_size,
                "modified": stat.st_mtime,
                "created": stat.st_ctime,
                "is_dir": os.path.isdir(path),
                "is_file": os.path.isfile(path),
                "exists": True
            }
        except Exception:
            return {"exists": False, "error": str(sys.exc_info()[1])}

    # ---------- 系统命令执行 ----------
    def run_command(self, command: str, timeout: int = 60) -> Dict[str, str]:
        """
        执行系统命令，返回 {stdout, stderr, returncode}
        """
        try:
            result = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=self.working_dir
            )
            return {
                "stdout": result.stdout,
                "stderr": result.stderr,
                "returncode": result.returncode
            }
        except subprocess.TimeoutExpired:
            return {"stdout": "", "stderr": "命令超时", "returncode": -1}
        except Exception as e:
            return {"stdout": "", "stderr": str(e), "returncode": -2}

    def open_file(self, path: str) -> bool:
        """用默认程序打开文件"""
        try:
            if sys.platform == 'win32':
                os.startfile(path)
            else:
                subprocess.run(['xdg-open', path], check=True)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def open_webpage(self, url: str) -> bool:
        """在默认浏览器中打开网页"""
        try:
            webbrowser.open(url)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    # ---------- 剪贴板操作 ----------
    def get_clipboard(self) -> Optional[str]:
        """获取剪贴板文本"""
        try:
            return pyperclip.paste()
        except Exception as e:
            self.last_result = str(e)
            return None

    def set_clipboard(self, text: str) -> bool:
        """设置剪贴板文本"""
        try:
            pyperclip.copy(text)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    # ---------- GUI自动化（可选） ----------
    def move_mouse(self, x: int, y: int, duration: float = 0.5) -> bool:
        """移动鼠标到指定位置"""
        if not PY_AUTOGUI_AVAILABLE:
            self.last_result = "pyautogui未安装"
            return False
        try:
            pyautogui.moveTo(x, y, duration=duration)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def click_mouse(self, x: int = None, y: int = None, button: str = 'left') -> bool:
        """点击鼠标（若指定坐标则先移动）"""
        if not PY_AUTOGUI_AVAILABLE:
            self.last_result = "pyautogui未安装"
            return False
        try:
            if x is not None and y is not None:
                pyautogui.click(x, y, button=button)
            else:
                pyautogui.click(button=button)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def type_text(self, text: str, interval: float = 0.05) -> bool:
        """模拟键盘输入文本"""
        if not PY_AUTOGUI_AVAILABLE:
            self.last_result = "pyautogui未安装"
            return False
        try:
            pyautogui.typewrite(text, interval=interval)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def press_key(self, key: str) -> bool:
        """按下指定键（如 'enter', 'ctrl+c'）"""
        if not PY_AUTOGUI_AVAILABLE:
            self.last_result = "pyautogui未安装"
            return False
        try:
            pyautogui.press(key)
            return True
        except Exception as e:
            self.last_result = str(e)
            return False

    def screenshot(self, filename: str = None) -> Optional[str]:
        """截屏并保存，返回文件路径"""
        if not PY_AUTOGUI_AVAILABLE:
            self.last_result = "pyautogui未安装"
            return None
        try:
            if filename is None:
                filename = os.path.join(tempfile.gettempdir(), "screenshot.png")
            pyautogui.screenshot(filename)
            return filename
        except Exception as e:
            self.last_result = str(e)
            return None

    # ---------- 工具方法 ----------
    def set_working_dir(self, path: str):
        """设置工作目录"""
        if os.path.exists(path) and os.path.isdir(path):
            self.working_dir = path
            os.chdir(path)
            return True
        return False

    def get_working_dir(self) -> str:
        return self.working_dir

    def get_last_result(self) -> Any:
        return self.last_result