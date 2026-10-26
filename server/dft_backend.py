# dft_backend.py
"""
DFT计算后端抽象基类与具体实现
支持: 本地Gaussian16、本地ORCA、SSH集群Gaussian16
"""

import os
import re
import shutil
import subprocess
import tempfile
import time
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional, List
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

import paramiko
from scp import SCPClient


@dataclass
class DFTInput:
    """DFT计算输入参数"""
    smiles: str                    # SMILES分子
    coordinates: Optional[List[List[float]]] = None  # 3D坐标 (Å)
    charge: int = 0                # 体系电荷
    multiplicity: int = 1          # 自旋多重度
    method: str = "B3LYP"          # 泛函
    basis: str = "6-31G(d)"        # 基组
    job_type: str = "sp"           # sp(单点), opt(优化), freq(频率)
    solvent: Optional[str] = None  # 隐式溶剂模型
    nproc: int = 4                 # CPU核心数
    mem: int = 4000                # 内存 (MB)


@dataclass
class DFTResult:
    """DFT计算结果"""
    success: bool
    energy: Optional[float] = None          # 总能量 (Hartree)
    energy_au: Optional[float] = None       # 能量 (a.u.)
    gradient: Optional[List[float]] = None  # 梯度
    frequencies: Optional[List[float]] = None  # 振动频率
    optimized_coords: Optional[List[List[float]]] = None
    raw_output: str = ""
    error_message: str = ""
    job_id: Optional[str] = None
    # 用于Multiwfn分析的中间文件
    fchk_file: Optional[str] = None
    molden_file: Optional[str] = None
    log_file: Optional[str] = None


class DFTBackend(ABC):
    """DFT计算后端抽象基类"""
    
    @abstractmethod
    def submit(self, inp: DFTInput) -> DFTResult:
        """提交DFT计算，返回结果"""
        pass
    
    @abstractmethod
    def parse_output(self, output_file: str) -> DFTResult:
        """解析输出文件"""
        pass


# ============================================================
# 2. 本地 Gaussian16 后端
# ============================================================

class LocalGaussianBackend(DFTBackend):
    """本地Gaussian16计算后端"""
    
    def __init__(self, g16_command: str = "g16", scratch_dir: Optional[str] = None):
        self.g16_command = g16_command
        self.scratch_dir = scratch_dir or tempfile.gettempdir()
    
    def _generate_input(self, inp: DFTInput) -> str:
        """生成Gaussian输入文件 (.gjf)"""
        # 路线部分
        route = f"#P {inp.method}/{inp.basis}"
        if inp.job_type == "opt":
            route += " Opt"
        elif inp.job_type == "freq":
            route += " Freq"
        elif inp.job_type == "opt+freq":
            route += " Opt Freq"
        
        if inp.solvent:
            route += f" SCRF=(SMD,Solvent={inp.solvent})"
        
        # 坐标部分
        coords_lines = []
        if inp.coordinates:
            # 使用提供的坐标
            for i, (x, y, z) in enumerate(inp.coordinates):
                # 需要原子符号，这里从SMILES无法直接获取，暂用占位
                coords_lines.append(f"C     {x:.6f}    {y:.6f}    {z:.6f}")
        else:
            # 从SMILES生成坐标 (调用mol_optimizer)
            from mol_optimizer import smiles_to_molecule_graph
            mol = smiles_to_molecule_graph(inp.smiles, add_hs=True)
            pos = mol.get_positions()
            symbols = [atom.symbol for atom in mol.atoms]
            for sym, (x, y, z) in zip(symbols, pos):
                coords_lines.append(f"{sym}     {x:.6f}    {y:.6f}    {z:.6f}")
        
        input_content = f"""%chk=job.chk
%nprocshared={inp.nproc}
%mem={inp.mem}MB
{route}

{inp.job_type} calculation for {inp.smiles}

{inp.charge} {inp.multiplicity}
{chr(10).join(coords_lines)}

"""
        return input_content
    
    def submit(self, inp: DFTInput) -> DFTResult:
        """提交Gaussian计算"""
        # 创建临时目录
        work_dir = tempfile.mkdtemp(prefix="g16_", dir=self.scratch_dir)
        input_file = os.path.join(work_dir, "input.gjf")
        output_file = os.path.join(work_dir, "output.log")
        
        # 写入输入文件
        with open(input_file, "w") as f:
            f.write(self._generate_input(inp))
        
        # 执行计算：G16W Windows 版 g16.exe 的命令行用法是参数式
        # （g16.exe input.gjf output.log），Linux 式重定向 "< in > out" 在 G16W 上不适用；
        # 且必须 cd 到工作目录（%chk=job.chk 为相对路径）
        cmd = f'cd /d "{work_dir}" && "{self.g16_command}" "{input_file}" "{output_file}"'
        try:
            result = subprocess.run(
                cmd, shell=True, capture_output=True, text=True, timeout=3600
            )
            if result.returncode != 0 or not os.path.exists(output_file):
                # 错误输出已重定向进 output_file，读取其头部用于诊断
                head = ""
                try:
                    with open(output_file, "r", errors="ignore") as f:
                        head = f.read(400).strip()
                except OSError:
                    head = ""
                err_text = ((result.stderr or "") + (result.stdout or "")).strip() or head
                if ("不是内部或外部命令" in (err_text or "")
                        or "not recognized" in (err_text or "").lower()
                        or not err_text):
                    err_text = (f"未找到 Gaussian 程序: '{self.g16_command}'。"
                                "请在设置页填写 g16 完整可执行路径")
                return DFTResult(
                    success=False,
                    error_message=f"Gaussian运行失败: {err_text}",
                    raw_output=head or err_text
                )
        except subprocess.TimeoutExpired:
            return DFTResult(success=False, error_message="计算超时 (1小时)")
        
        # 解析结果
        dft_result = self.parse_output(output_file)
        dft_result.log_file = output_file
        
        # 检查是否有chk文件，转换为fchk供Multiwfn使用（formchk 与 g16 同目录）
        chk_file = os.path.join(work_dir, "job.chk")
        if os.path.exists(chk_file):
            fchk_file = os.path.join(work_dir, "job.fchk")
            g16_dir = os.path.dirname(os.path.abspath(self.g16_command))
            formchk = os.path.join(g16_dir, "formchk.exe") if g16_dir else "formchk"
            try:
                subprocess.run(
                    f'cd /d "{work_dir}" && "{formchk}" job.chk job.fchk',
                    shell=True, capture_output=True, timeout=120
                )
                if os.path.exists(fchk_file):
                    dft_result.fchk_file = fchk_file
            except Exception:
                pass
        
        return dft_result
    
    def parse_output(self, output_file: str) -> DFTResult:
        """解析Gaussian输出文件"""
        if not os.path.exists(output_file):
            return DFTResult(success=False, error_message="输出文件不存在")
        
        with open(output_file, "r", errors="ignore") as f:
            content = f.read()
        
        result = DFTResult(success=True, raw_output=content)
        
        # 提取能量
        energy_pattern = r"SCF Done:\s+E\([^)]+\)\s+=\s+([-+]?\d+\.\d+)\s+A\.U\."
        energy_match = re.search(energy_pattern, content)
        if energy_match:
            result.energy_au = float(energy_match.group(1))
            result.energy = result.energy_au * 627.509  # Hartree -> kcal/mol
        
        # 检查是否正常终止
        if "Normal termination" not in content:
            result.success = False
            result.error_message = "计算未正常终止"
        
        return result


# ============================================================
# 3. 本地 ORCA 后端
# ============================================================

class LocalORCABackend(DFTBackend):
    """本地ORCA计算后端"""
    
    def __init__(self, orca_command: str = "orca", orca_dir: Optional[str] = None,
                 scratch_dir: Optional[str] = None):
        self.orca_command = orca_command
        self.orca_dir = orca_dir
        self.scratch_dir = scratch_dir
    
    def _generate_input(self, inp: DFTInput) -> str:
        """生成ORCA输入文件 (.inp)"""
        # 方法行（不加 "!"，由下方模板统一添加，避免生成非法的 "! !" 行）
        method_line = f"{inp.method} {inp.basis}"
        if inp.job_type == "opt":
            method_line += " Opt"
        elif inp.job_type == "freq":
            method_line += " Freq"
        elif inp.job_type == "opt+freq":
            method_line += " Opt Freq"
        
        if inp.solvent:
            method_line += f" CPCM({inp.solvent})"
        
        # 坐标部分（ORCA 的 * xyz 块要求每行带元素符号）
        coords_lines = []
        if inp.coordinates:
            for i, (x, y, z) in enumerate(inp.coordinates):
                coords_lines.append(f"X      {x:.6f}    {y:.6f}    {z:.6f}")
        else:
            from mol_optimizer import smiles_to_molecule_graph
            mol = smiles_to_molecule_graph(inp.smiles, add_hs=True)
            pos = mol.get_positions()
            symbols = [atom.symbol for atom in mol.atoms]
            for sym, (x, y, z) in zip(symbols, pos):
                coords_lines.append(f"{sym}      {x:.6f}    {y:.6f}    {z:.6f}")
        
        input_content = f"""! {method_line}
%pal nprocs {inp.nproc} end
%maxcore {inp.mem}

* xyz {inp.charge} {inp.multiplicity}
{chr(10).join(coords_lines)}
*
"""
        return input_content
    
    def submit(self, inp: DFTInput) -> DFTResult:
        """提交ORCA计算"""
        work_dir = tempfile.mkdtemp(prefix="orca_", dir=self.scratch_dir)
        input_file = os.path.join(work_dir, "input.inp")
        output_file = os.path.join(work_dir, "output.out")
        
        with open(input_file, "w") as f:
            f.write(self._generate_input(inp))
        
        # 执行ORCA (输出重定向到output_file)
        cmd = f"{self.orca_command} {input_file} > {output_file} 2>&1"
        try:
            result = subprocess.run(
                cmd, shell=True, capture_output=True, text=True, timeout=3600
            )
        except subprocess.TimeoutExpired:
            return DFTResult(success=False, error_message="计算超时 (1小时)")
        except FileNotFoundError:
            return DFTResult(success=False, error_message=f"未找到 ORCA 程序: '{self.orca_command}'")
        
        # 程序未找到（shell 返回非零且输出为空/含"不是内部或外部命令"）
        combined = ((result.stdout or "") + (result.stderr or "")).strip()
        if result.returncode != 0 and (not os.path.getsize(output_file) or "不是内部或外部命令" in combined
                                        or "not recognized" in combined.lower()):
            return DFTResult(
                success=False,
                error_message=(f"未找到 ORCA 程序: '{self.orca_command}'（"
                               f"shell 提示: {combined[:200] or '命令执行失败'}）。"
                               "请在设置页填写 ORCA 完整可执行路径"),
                raw_output=combined)
        
        dft_result = self.parse_output(output_file)
        dft_result.log_file = output_file
        
        # ORCA输出molden文件
        molden_file = os.path.join(work_dir, "input.molden")
        if os.path.exists(molden_file):
            dft_result.molden_file = molden_file
        
        return dft_result
    
    def parse_output(self, output_file: str) -> DFTResult:
        """解析ORCA输出文件"""
        if not os.path.exists(output_file):
            return DFTResult(success=False, error_message="输出文件不存在")
        
        with open(output_file, "r", errors="ignore") as f:
            content = f.read()
        
        result = DFTResult(success=True, raw_output=content)
        
        # 提取能量
        energy_pattern = r"FINAL SINGLE POINT ENERGY\s+([-+]?\d+\.\d+)"
        energy_match = re.search(energy_pattern, content, re.IGNORECASE)
        if energy_match:
            result.energy_au = float(energy_match.group(1))
            result.energy = result.energy_au * 627.509
        
        # 检查是否正常终止
        if "****ORCA TERMINATED NORMALLY****" not in content:
            result.success = False
            result.error_message = "计算未正常终止"
        
        return result


# ============================================================
# 4. SSH集群后端 (Gaussian16)
# ============================================================

class SSHClusterGaussianBackend(DFTBackend):
    """SSH集群Gaussian16后端 (支持PBS/Slurm)"""
    
    def __init__(
        self,
        host: str,
        username: str,
        password: Optional[str] = None,
        key_filename: Optional[str] = None,
        remote_work_dir: str = "/scratch",
        queue_system: str = "pbs",  # "pbs" 或 "slurm"
        queue_name: str = "",      # 队列名（PBS -q / Slurm --partition），空=用站点默认
        g16_root: str = "",        # 集群上 Gaussian 安装目录（如 /lustre/home/xxx/g16），空=假定已在 PATH
        g16_scratch: str = "",     # 集群上的 Gaussian SCRATCH 目录
        walltime: str = "48:00:00",
        g16_command: str = "g16",
        nproc: int = 32,   # 集群节点 32 核（pbsnodes 确认），整节点加速大体系扫描
        mem: int = 32000,
        scratch_dir: Optional[str] = None
    ):
        self.host = host
        self.username = username
        self.password = password
        self.key_filename = key_filename
        self.remote_work_dir = remote_work_dir
        self.queue_system = queue_system
        self.queue_name = queue_name
        self.g16_root = g16_root
        self.g16_scratch = g16_scratch
        self.walltime = walltime
        self.g16_command = g16_command
        self.nproc = nproc
        self.mem = mem
        self.scratch_dir = scratch_dir

        self._ssh_client = None
        self._scp_client = None
    
    def _connect(self):
        """建立SSH连接"""
        if self._ssh_client is None:
            self._ssh_client = paramiko.SSHClient()
            self._ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            
            connect_kwargs = {
                "hostname": self.host,
                "username": self.username,
            }
            if self.password:
                connect_kwargs["password"] = self.password
            if self.key_filename:
                connect_kwargs["key_filename"] = self.key_filename
            
            self._ssh_client.connect(**connect_kwargs)
            # 长时轮询（数小时级集群作业）期间保持连接活性
            transport = self._ssh_client.get_transport()
            if transport:
                transport.set_keepalive(30)
            self._scp_client = SCPClient(self._ssh_client.get_transport())
    
    def _disconnect(self):
        """关闭SSH连接"""
        if self._scp_client:
            self._scp_client.close()
        if self._ssh_client:
            self._ssh_client.close()
        self._ssh_client = None
        self._scp_client = None
    
    def _gauss_env_block(self) -> str:
        """集群上 Gaussian 通常不在默认 PATH，需在作业脚本内显式导出环境（按 g16.pbs 模板）"""
        if not self.g16_root:
            return ""
        scratch = self.g16_scratch or f"{self.g16_root}/../output"
        return (f"G16ROOT={self.g16_root}\n"
                "GAUSS_EXEDIR=$G16ROOT\n"
                f"GAUSS_SCRDIR={scratch}\n"
                "LD_LIBRARY_PATH=$G16ROOT/:$LD_LIBRARY_PATH\n"
                "PATH=$G16ROOT/:$PATH\n"
                "export G16ROOT GAUSS_EXEDIR GAUSS_SCRDIR LD_LIBRARY_PATH PATH\n"
                "mkdir -p $GAUSS_SCRDIR\n\n")

    def _generate_pbs_script(self, job_name: str, input_file: str) -> str:
        """生成PBS作业脚本（按集群 g16.pbs 模板）
        要点：#PBS -V 传递环境 / -S /bin/bash；g16 参数式调用（stdin 重定向对 g16 包装脚本无效，
        输出文件由 g16 自行生成：<输入名去后缀>.log）；%chk=job.chk 为相对路径 → 写入作业目录"""
        queue = f"#PBS -q {self.queue_name}\n" if self.queue_name else ""
        return f"""#!/bin/bash
#PBS -N {job_name}
#PBS -l nodes=1:ppn={self.nproc}
#PBS -l walltime={self.walltime}
{queue}#PBS -V
#PBS -S /bin/bash

{self._gauss_env_block()}cd $PBS_O_WORKDIR

# 输入文件：优先取 qsub -v 传入的 input_file（集群 g16.sh/g16.pbs 模板约定），未传则回退默认
INPUT="${{input_file:-{input_file}}}"
if [ ! -f "$INPUT" ]; then
    echo "[ERROR] 找不到输入文件: $INPUT"
    echo "[INFO] 当前目录: $PBS_O_WORKDIR"
    ls -la
    exit 1
fi

{self.g16_command} "$INPUT"
EXIT_CODE=$?

$G16ROOT/formchk job.chk job.fchk 2>/dev/null || formchk job.chk job.fchk 2>/dev/null || true

exit $EXIT_CODE
"""

    def _generate_slurm_script(self, job_name: str, input_file: str) -> str:
        """生成Slurm作业脚本（参数式 g16 调用，结束后转 fchk 供 Multiwfn 分析）"""
        partition = f"#SBATCH --partition={self.queue_name}\n" if self.queue_name else ""
        return f"""#!/bin/bash
#SBATCH --job-name={job_name}
#SBATCH --nodes=1
#SBATCH --ntasks={self.nproc}
#SBATCH --time={self.walltime}
{partition}#SBATCH --output=job.stdout
#SBATCH --error=job.stderr

{self._gauss_env_block()}cd $SLURM_SUBMIT_DIR

{self.g16_command} {input_file}
EXIT_CODE=$?

$G16ROOT/formchk job.chk job.fchk 2>/dev/null || formchk job.chk job.fchk 2>/dev/null || true

exit $EXIT_CODE
"""
    
    def submit(self, inp: DFTInput) -> DFTResult:
        """通过SSH提交Gaussian作业到集群"""
        input_content = LocalGaussianBackend()._generate_input(inp)
        return self._submit_and_wait(input_content, "dft")

    def submit_raw(self, input_content: str, tag: str = "ts") -> DFTResult:
        """提交任意Gaussian输入内容（过渡态扫描/TS优化等非标准作业）"""
        return self._submit_and_wait(input_content, tag)

    def _submit_and_wait(self, input_content: str, tag: str) -> DFTResult:
        """上传输入 → 生成调度脚本 → qsub → 轮询队列 → 回传解析"""
        self._connect()

        # 创建远程工作目录（remote_work_dir 已是用户配置的集群目录，直接在其下建作业目录）
        job_name = f"{tag}_{int(time.time())}"
        remote_dir = f"{self.remote_work_dir}/{job_name}"
        stdin, stdout, stderr = self._ssh_client.exec_command(f"mkdir -p {remote_dir}")
        stdout.channel.recv_exit_status()
        
        # 上传输入文件（按集群模板约定 .com；newline="\n" 避免 Windows CRLF 污染）
        remote_input = f"{remote_dir}/job.com"
        remote_log = f"{remote_dir}/job.log"

        with tempfile.NamedTemporaryFile(mode="w", suffix=".com", delete=False,
                                         newline="\n") as f:
            f.write(input_content)
            temp_input = f.name

        self._scp_client.put(temp_input, remote_input)
        os.unlink(temp_input)

        # 生成并上传作业脚本（同样强制 LF，CRLF 会导致 bash 报 bad interpreter）
        if self.queue_system == "slurm":
            script = self._generate_slurm_script(job_name, "job.com")
            script_name = f"{remote_dir}/job.slurm"
        else:
            script = self._generate_pbs_script(job_name, "job.com")
            script_name = f"{remote_dir}/job.pbs"

        with tempfile.NamedTemporaryFile(mode="w", suffix=".sh", delete=False,
                                         newline="\n") as f:
            f.write(script)
            temp_script = f.name

        self._scp_client.put(temp_script, script_name)
        os.unlink(temp_script)

        # 提交作业（按集群 g16.sh 模板：登录 shell 使 qsub 进入 PATH——非登录 shell
        # 会报 command not found；参数顺序照抄模板 qsub g16.pbs -N 名称 -v input_file=xx）
        if self.queue_system == "slurm":
            cmd = f'bash -lc "cd {remote_dir} && sbatch {script_name}"'
        else:
            cmd = (f'bash -lc "cd {remote_dir} && qsub {script_name} '
                   f'-N {job_name} -v input_file={os.path.basename(remote_input)}"')
        
        stdin, stdout, stderr = self._ssh_client.exec_command(cmd)
        job_id = stdout.read().decode().strip()
        submit_err = stderr.read().decode().strip()
        if submit_err:
            self._disconnect()
            return DFTResult(success=False, job_id=job_id,
                             error_message=f"集群作业提交失败: {submit_err}")
        
        # 轮询作业状态（异步任务机制下运行于后台线程；20秒一次，最多等待48小时，
        # 与默认 walltime 对齐——大体系松弛扫描+TS优化可达十余小时）
        # 同样走登录 shell（qstat/squeue 依赖 module 环境）
        result = DFTResult(success=False, job_id=job_id)
        jid = job_id.split()[-1]
        status_cmd = (f"bash -lc 'squeue -h -j {jid} 2>/dev/null | wc -l'"
                      if self.queue_system == "slurm"
                      else f"bash -lc 'qstat {jid} 2>/dev/null | wc -l'")
        poll_failures = 0
        for _ in range(8640):
            time.sleep(20)

            try:
                stdin, stdout, stderr = self._ssh_client.exec_command(status_cmd)
                count = stdout.read().decode().strip()
                poll_failures = 0
            except Exception:
                # SSH 长连接可能被网络/服务端中断：重建连接继续轮询
                poll_failures += 1
                if poll_failures >= 10:
                    self._disconnect()
                    return DFTResult(success=False, job_id=job_id,
                                     error_message=f"集群状态轮询连接中断（连续失败{poll_failures}次）")
                try:
                    self._disconnect()
                    self._connect()
                except Exception:
                    pass
                continue

            if count == "0":
                # 作业已离开队列（完成/失败/被取消）
                break
        
        # 下载结果到本地工作区
        local_dir = self.scratch_dir or tempfile.gettempdir()
        os.makedirs(local_dir, exist_ok=True)
        local_log = os.path.join(local_dir, f"{job_name}.log")
        local_fchk = os.path.join(local_dir, f"{job_name}.fchk")
        
        try:
            self._scp_client.get(remote_log, local_log)
            result.log_file = local_log
        except Exception as e:
            self._disconnect()
            return DFTResult(success=False, job_id=job_id,
                             error_message=f"下载输出文件失败: {e}")
        try:
            self._scp_client.get(f"{remote_dir}/job.fchk", local_fchk)
            result.fchk_file = local_fchk
        except Exception:
            pass  # fchk 可能不存在（作业失败或集群无 formchk）
        
        # 解析结果
        local_result = LocalGaussianBackend().parse_output(local_log)
        result.success = local_result.success
        result.energy = local_result.energy
        result.energy_au = local_result.energy_au
        result.raw_output = local_result.raw_output
        result.error_message = local_result.error_message
        
        self._disconnect()
        return result
    
    def parse_output(self, output_file: str) -> DFTResult:
        """解析输出文件 (使用本地解析器)"""
        return LocalGaussianBackend().parse_output(output_file)


# ============================================================
# 5. Multiwfn 分析接口
# ============================================================

class MultiwfnAnalyzer:
    """
    Multiwfn波函数分析接口
    支持: 键级分析、电荷分析、轨道分析、弱相互作用分析
    """
    
    def __init__(self, multiwfn_command: str = "multiwfn"):
        self.multiwfn_command = multiwfn_command
    
    def _run_multiwfn(self, input_file: str, commands: List[str]) -> str:
        """
        运行Multiwfn命令行批处理模式
        参考: Multiwfn手册5.2-5.3节[reference:0][reference:1]
        """
        # 构建命令序列 (用\n分隔)[reference:2]
        cmd_str = "\n".join(commands) + "\nq\n"
        # 以 Multiwfn 所在目录为 CWD 运行：否则找不到 settings.ini，
        # 其 "Press ENTER" 提示会吃掉第一条命令，导致整个命令序列错位
        mw_dir = os.path.dirname(os.path.abspath(self.multiwfn_command))
        # 使用subprocess管道传递命令[reference:3]
        # errors='replace' 容忍编码差异；timeout 防止交互挂起阻塞任务
        result = subprocess.run(
            [self.multiwfn_command, os.path.abspath(input_file)],
            input=cmd_str,
            capture_output=True,
            text=True,
            encoding='utf-8',
            errors='replace',
            timeout=600,
            cwd=mw_dir or None
        )
        return result.stdout or ""

    def analyze_bond_order(self, fchk_file: str) -> Dict[str, Any]:
        """
        计算Mayer键级
        命令序列: 主功能9(键级分析) -> 子功能1(Mayer) -> n(不输出矩阵文件) -> 0(返回)
        """
        commands = ["9", "1", "n", "0"]

        output = self._run_multiwfn(fchk_file, commands)

        # 解析Mayer键级（兼容 Multiwfn 各版本输出格式）
        # 新版格式: "#    1:         1(C )    2(C )    1.01131590"
        bond_orders = {}
        patterns = [
            r"#\s*\d+:\s+(\d+)\(\s*\w+\s*\)\s+(\d+)\(\s*\w+\s*\)\s+([-+]?\d+\.?\d*)",
            r"Mayer bond order between atom\s+(\d+) and atom\s+(\d+):\s*([-+]?\d+\.?\d*)",
            r"Bond order of atom\s+(\d+) and atom\s+(\d+):\s*([-+]?\d+\.?\d*)",
        ]
        for pat in patterns:
            matches = re.findall(pat, output)
            if matches:
                for i, j, bo in matches:
                    bond_orders[f"{i}-{j}"] = float(bo)
                break
        if not bond_orders:
            return {"type": "mayer_bond_order", "bond_orders": {},
                    "raw_output": output[-3000:]}
        
        return {
            "type": "mayer_bond_order",
            "bond_orders": bond_orders
        }
    
    def analyze_charge(self, fchk_file: str, scheme: str = "mulliken") -> Dict[str, Any]:
        """
        电荷分析
        scheme: "mulliken", "lowdin", "hirshfeld"
        """
        # Multiwfn 主功能7 -> 布居分析（2026 版子菜单）：
        #   5=Mulliken, 6=Lowdin, 1=Hirshfeld（旧版编号已重排）
        # Mulliken 进入子菜单后需再选 1 输出布居与电荷；Hirshfeld 选 1 使用
        # 内置球形原子密度；Lowdin 直接提示输出路径（空 ENTER = 屏幕）。
        # 三者输出后都有 .chg 询问(y/n)，统一答 n
        sub_func = {"mulliken": "5", "lowdin": "6", "hirshfeld": "1"}.get(scheme, "5")
        if scheme == "lowdin":
            commands = ["7", sub_func, "", "n", "0", "0"]
        else:
            commands = ["7", sub_func, "1", "n", "0", "0"]

        output = self._run_multiwfn(fchk_file, commands)

        # Mulliken/Lowdien 格式:
        #   Atom     1(N )    Population:  7.35526637    Net charge: -0.35526637
        # Hirshfeld 格式(Final atomic charges 块):
        #   Atom    1(N ):            -0.35527
        atoms = []
        for m in re.finditer(r"Atom\s+(\d+)\(\s*(\w+)\s*\)\s+Population:\s+"
                             r"[-+]?\d+\.\d+\s+Net charge:\s+([-+]?\d+\.\d+)", output):
            atoms.append({"index": int(m.group(1)), "symbol": m.group(2),
                          "charge": float(m.group(3))})
        if not atoms:
            for m in re.finditer(r"Atom\s+(\d+)\(\s*(\w+)\s*\):\s+([-+]?\d+\.\d+)", output):
                atoms.append({"index": int(m.group(1)), "symbol": m.group(2),
                              "charge": float(m.group(3))})

        return {
            "type": f"{scheme}_charges",
            "atoms": atoms,
            "charges": [a["charge"] for a in atoms],
            "total_charge": round(sum(a["charge"] for a in atoms), 6)
        }
    
    def _export_interaction_cubes(self, fchk_file: str, kind: str) -> Dict[str, Any]:
        """弱相互作用分析(RDG/NCI、IRI)并导出等值面 cube 文件。

        Multiwfn 2026 版命令序列（旧版主功能5已改为空间区域属性作图，
        弱相互作用分析移至主功能20）：
          20(弱相互作用) -> 子功能(1=NCI/RDG, 4=IRI) -> 1(低质量网格，
          小体系约 1s) -> 3(导出 func1.cub=sign(lambda2)rho 与
          func2.cub=RDG/IRI) -> 0 退出后处理菜单。
        cube 文件固定写在 Multiwfn 程序目录，分析后移到 fchk 同目录并按
        <文件名>_<tag>.cub 重命名。
        """
        sub = {"rdg": "1", "iri": "4"}[kind]
        output = self._run_multiwfn(fchk_file, ["20", sub, "1", "3", "0", "0"])

        mw_dir = os.path.dirname(os.path.abspath(self.multiwfn_command))
        stem = os.path.splitext(os.path.basename(fchk_file))[0]
        out_dir = os.path.dirname(os.path.abspath(fchk_file))
        cubes = {}
        for src_name, tag in (("func1.cub", "signlam2rho"), ("func2.cub", kind)):
            src = os.path.join(mw_dir, src_name)
            if os.path.isfile(src):
                dst = os.path.join(out_dir, f"{stem}_{tag}.cub")
                shutil.move(src, dst)
                cubes[tag] = dst
        return {"output": output, "cubes": cubes}

    def analyze_rdg(self, fchk_file: str) -> Dict[str, Any]:
        """
        非共价相互作用分析 (RDG/NCI)，输出 sign(lambda2)rho 与 RDG cube 文件
        参考: Multiwfn手册[reference:5]
        """
        r = self._export_interaction_cubes(fchk_file, "rdg")
        return {
            "type": "rdg_analysis",
            "cube_files": r["cubes"],
            "raw_output": r["output"][-2000:]
        }

    def analyze_iri(self, fchk_file: str) -> Dict[str, Any]:
        """
        IRI (相互作用区域指示符) 分析，输出 sign(lambda2)rho 与 IRI cube 文件
        """
        r = self._export_interaction_cubes(fchk_file, "iri")
        return {
            "type": "iri_analysis",
            "cube_files": r["cubes"],
            "raw_output": r["output"][-2000:]
        }

    def analyze_fmo_cubes(self, fchk_file: str, prefix: str = "") -> Dict[str, Any]:
        """生成 FMO(HOMO/LUMO) 与电子密度 cube 文件，供 molview.exe 渲染等值面。

        命令序列（Multiwfn 2026.7.15 已验证；轨道序号用 h/l 关键字自动定位
        HOMO/LUMO，不依赖分子电子数，可推广到任意体系）：
          主功能5(实空间函数) -> 4(轨道波函数值) -> h/l -> 2(中等网格)
            -> 2(导出cube文件) -> 0 -> 0
          主功能5 -> 1(电子密度 rho) -> 2 -> 2 -> 0 -> 0
        cube 固定输出到 Multiwfn 程序目录（MOvalue.cub / density.cub），复制到
        fchk 同目录并按 <prefix>_<tag>.cub 命名（homo_pos/homo_neg/
        lumo_pos/lumo_neg/dens，prefix 默认取 fchk 文件名主干）供 molview 自动
        发现；轨道正/负等值面（+0.03/-0.03）由查看器从同一份 cube 数据提取。
        """
        mw_dir = os.path.dirname(os.path.abspath(self.multiwfn_command))
        stem = os.path.splitext(os.path.basename(fchk_file))[0]
        out_dir = os.path.dirname(os.path.abspath(fchk_file))
        prefix = prefix or stem
        cubes: Dict[str, str] = {}

        def _export_orbital(tag_prefix: str, commands: List[str]) -> None:
            self._run_multiwfn(fchk_file, commands)
            src = os.path.join(mw_dir, "MOvalue.cub")
            if not os.path.isfile(src):
                return
            for tag in (f"{tag_prefix}_pos", f"{tag_prefix}_neg"):
                dst = os.path.join(out_dir, f"{prefix}_{tag}.cub")
                shutil.copy(src, dst)
                cubes[tag] = dst

        _export_orbital("homo", ["", "5", "4", "h", "2", "2", "0", "0"])
        _export_orbital("lumo", ["", "5", "4", "l", "2", "2", "0", "0"])

        self._run_multiwfn(fchk_file, ["", "5", "1", "2", "2", "0", "0"])
        src_d = os.path.join(mw_dir, "density.cub")
        if os.path.isfile(src_d):
            dst = os.path.join(out_dir, f"{prefix}_dens.cub")
            shutil.copy(src_d, dst)
            cubes["dens"] = dst

        return {"type": "fmo_density_cubes", "cubes": cubes}


# ============================================================
# 6. 过渡态搜索（固定-放开法 freeze-release）
# ============================================================

_ATOMIC_NUM_TO_SYMBOL = {1: "H", 6: "C", 7: "N", 8: "O", 13: "Al", 14: "Si",
                         15: "P", 16: "S", 5: "B", 9: "F", 17: "Cl", 35: "Br",
                         53: "I", 34: "Se", 52: "Te", 29: "Cu", 74: "W"}
_SYMBOL_TO_ATOMIC_NUM = {v: k for k, v in _ATOMIC_NUM_TO_SYMBOL.items()}


class TransitionStateSearcher:
    """固定-放开法(freeze-release)过渡态搜索，面向铝宾活化小分子体系：

    1) build: RDKit 生成铝宾 3D 构型，按反应网络模式放置 CO₂：
       - eta2（默认）：C 端侧向 30°/45° 倾斜 → [2+2] 型（Al-C/Al-O 协同成键）
       - eta1：O 端朝向 Al → η¹-O Lewis 酸碱加成
       多模式枚举对应化学反应网络理论(CRNT)的多条反应边：一次搜索 = 网络
       中的一条边，逐模式搜索即网络遍历，能量对比判断各边可达性。
    2) freeze: Opt=ModRedundant 沿反应坐标（eta2→Al···C，eta1→Al···O）松弛扫描；
       步长为负 = 距离缩短 = 反应进度 ξ 增大（正方向约定，扫描与报告一致）
    3) 取扫描能量最高点（∂E/∂ξ ≈ 0 的鞍点近似）
    4) release: Opt=(TS,CalcFC,NoEigenTest) Freq 全自由度 TS 优化；自动检测
       并冻结金属-甲基等易迁移基团（铝宾19/20 教训：不冻结时 release 弛豫到
       甲基迁移插入产物，虚频退化为纯甲基扭转鞍点，"Ea"并非 CO₂ 加成能垒）
    5) 验证唯一虚频 + 虚频位移与目标反应坐标的一致性（自洽性校验：
       反应坐标原子位移占比过低 → 旁观者运动主导 → 非目标鞍点）
    6) 量子效应报告：Wigner 隧道修正因子 κ(T)（由虚频计算）与含 ZPE 能垒
    仅支持 SSH 集群后端（研究级多步计算，本地单机不现实）。
    """

    CO2_DISTANCE_START = 3.2   # 初始反应坐标距离 (Å)，拥挤时自动外推（3.5/3.8/4.1）
    SCAN_STEPS = 15            # 松弛扫描步数（按实际起始距离自适应，见 scan_input）
    SCAN_STEP_SIZE = -0.10     # 每步缩短 0.10 Å → 终点约 1.7 Å（覆盖势垒顶）
    SCAN_END_ETA2 = 1.7         # eta2 模式扫描终点：Al···C (Å)
    SCAN_END_ETA1 = 1.8         # eta1 模式扫描终点：Al···O (Å)
    CO2_MIN_CLEARANCE = 2.0    # CO₂ 原子与配体重原子的最小净空 (Å)：低于近键距，
                               # 冗余坐标成键判定会产生强相互作用/错误坐标
    CO2_MIN_H_CLEARANCE = 2.4  # CO₂ 原子与配体 H 的最小净空 (Å)：O···H 落入 Gaussian
                               # 冗余坐标成键判定后，松弛优化会把氢键拉成 180° 线性
                               # 夹角，触发 l103 Bend failed/FormBX 崩溃（铝宾17教训）

    def __init__(self, manager: "DFTTaskManager"):
        self.manager = manager
        self.last_placement: Dict[str, Any] = {}

    @staticmethod
    def _rotate(v, k, deg):
        """Rodrigues 旋转：向量 v 绕单位轴 k 转 deg 度"""
        th = np.radians(deg)
        return (v * np.cos(th) + np.cross(k, v) * np.sin(th)
                + k * np.dot(k, v) * (1 - np.cos(th)))

    def _co2_placement(self, p_al, d, ligand_coords, ligand_symbols, mode="eta2"):
        """碰撞感知的 CO₂ 摆放（反应网络模式）：eta2 = C 端侧向 30°/45° 倾斜
        （[2+2] 型引导）；eta1 = CO₂ 轴沿接近方向、O 端朝 Al（η¹-O 酸碱加成）。
        在 Al 外侧半球采样多个接近方向与起始距离，以 O···H 净空优先、重原子
        净空次之（氢键线性化是 FormBX 崩溃主因）。
        返回 (c_pos, axis, min_clearance, distance)；axis 为 CO₂ 分子轴（C-O 方向）。
        eta2 的 distance 为 Al···C，eta1 的 distance 为 Al···O(near)。
        纯垂直摆放两侧 O 均够不到 Al，扫描只会在对称山脊上单调爬升，
        故 eta2 保持 30° 倾斜以引导 [2+2] 型（Al-C 与 Al-O 协同成键）过渡态。
        3.2 Å 处无净空合格解时按 3.5/3.8/4.1 Å 逐档外推。"""
        h_mask = np.array([s == "H" for s in ligand_symbols], dtype=bool)
        if not h_mask.any():
            h_mask = np.zeros(len(ligand_coords), dtype=bool)
        ref0 = np.array([0.0, 0.0, 1.0]) if abs(d[2]) < 0.9 else np.array([1.0, 0.0, 0.0])
        perp0 = np.cross(d, ref0)
        perp0 = perp0 / np.linalg.norm(perp0)
        cross0 = np.cross(d, perp0)

        # 候选方向：d 绕两个垂直轴小幅摆动 ±60°，保持背离取代基质心
        directions = [d]
        for axis in (perp0, cross0):
            for ang in (15, 30, 45, 60, -15, -30, -45, -60):
                directions.append(self._rotate(d, axis, ang))

        def _clearances(c_pos, axis):
            pts = [c_pos, c_pos + axis * 1.16, c_pos - axis * 1.16]
            md_all = min(float(np.linalg.norm(ligand_coords - p, axis=1).min()) for p in pts)
            md_h = min(float(np.linalg.norm(ligand_coords[h_mask] - p, axis=1).min())
                       for p in pts) if h_mask.any() else md_all
            return md_all, md_h

        if mode == "eta1":
            # η¹-O：axis 指向 Al，近端 O = c_pos + axis*1.16 距 Al 为 distance
            best = None
            for distance in (3.2, 3.5, 3.8, 4.1):
                best_ok = None
                for d2 in directions:
                    axis = -d2
                    c_pos = p_al + d2 * (distance + 1.16)
                    md_all, md_h = _clearances(c_pos, axis)
                    score = min(md_all, md_h)
                    if best is None or score > best[0]:
                        best = (score, c_pos, axis, md_all, md_h, distance)
                    if (md_h >= self.CO2_MIN_H_CLEARANCE
                            and md_all >= self.CO2_MIN_CLEARANCE
                            and (best_ok is None or score > best_ok[0])):
                        best_ok = (score, c_pos, axis, md_all, md_h, distance)
                if best_ok is not None:
                    return best_ok[1], best_ok[2], best_ok[3], best_ok[5]
            return best[1], best[2], best[3], best[5]

        best = None      # 全局兜底：净空分最高者
        for distance in (3.2, 3.5, 3.8, 4.1):
            best_ok = None  # 该档位中达标的净空分最高者
            for d2 in directions:
                ref = np.array([0.0, 0.0, 1.0]) if abs(d2[2]) < 0.9 else np.array([1.0, 0.0, 0.0])
                perp = np.cross(d2, ref)
                perp = perp / np.linalg.norm(perp)
                for tilt_deg in (30.0, 45.0):
                    tilt = np.radians(tilt_deg)
                    for sign in (1, -1):
                        axis = perp * sign * np.cos(tilt) - d2 * np.sin(tilt)
                        c_pos = p_al + d2 * distance
                        md_all, md_h = _clearances(c_pos, axis)
                        score = min(md_all, md_h)
                        if best is None or score > best[0]:
                            best = (score, c_pos, axis, md_all, md_h, distance)
                        if (md_h >= self.CO2_MIN_H_CLEARANCE
                                and md_all >= self.CO2_MIN_CLEARANCE
                                and (best_ok is None or score > best_ok[0])):
                            best_ok = (score, c_pos, axis, md_all, md_h, distance)
            if best_ok is not None:
                # 最近的达标档位中取净空最大者，兼顾 30° 倾斜 [2+2] 引导（采样顺序优先）
                return best_ok[1], best_ok[2], best_ok[3], best_ok[5]
        # 全部档位均不达标：返回净空分最高者（此时必然在 4.1 Å 档）
        return best[1], best[2], best[3], best[5]

    def build_complex(self, smiles: str, mode: str = "eta2"):
        """返回 (symbols, coords, al_idx)；CO₂ 三个原子追加在末尾（C, O, O）。
        mode: eta2 = [2+2] 引导构型；eta1 = η¹-O 端进攻构型（反应网络不同边）"""
        from mol_optimizer import smiles_to_molecule_graph
        mol = smiles_to_molecule_graph(smiles, add_hs=True)
        symbols = [a.symbol for a in mol.atoms]
        coords = np.array([list(a.coords) for a in mol.atoms], dtype=float)

        al_candidates = [i for i, s in enumerate(symbols) if s == "Al"]
        if not al_candidates:
            raise ValueError("分子中未找到 Al 原子：过渡态搜索面向铝宾体系")
        al_idx = al_candidates[0]

        p_al = coords[al_idx]
        nbrs = mol.atoms[al_idx].neighbors
        p_sub = coords[nbrs].mean(axis=0) if len(nbrs) else coords.mean(axis=0)

        d = p_al - p_sub                          # 取代基 → Al 外侧方向
        n = float(np.linalg.norm(d))
        d = d / n if n > 1e-6 else np.array([1.0, 0.0, 0.0])

        c_pos, axis, clearance, distance = self._co2_placement(p_al, d, coords, symbols,
                                                               mode=mode)
        symbols = symbols + ["C", "O", "O"]
        coords = np.vstack([coords,
                            c_pos,
                            c_pos + axis * 1.16,
                            c_pos - axis * 1.16])
        self.last_placement = {"mode": mode,
                               "min_clearance": round(clearance, 3),
                               "start_distance": round(distance, 2)}
        return symbols, coords, al_idx

    # ---------- 迁移基团保护（铝宾19/20 教训） ----------

    COV_R = {"H": 0.31, "C": 0.76, "N": 0.71, "O": 0.66, "Al": 1.21, "Cl": 1.02,
             "Cu": 1.32, "Se": 1.20, "Te": 1.38, "Si": 1.11, "B": 0.84, "Ga": 1.22}
    MIGRATION_METALS = {"Al", "Cu", "Zn", "Mg", "Li", "B", "Ga", "In", "Si"}
    MODE_REACTION_SHARE = 0.25   # 虚频一致性阈值：反应坐标原子位移平方占比

    @classmethod
    def _detect_migration_bonds(cls, symbols, coords):
        """检测易迁移基团（金属-末端甲基键），release 阶段需冻结。

        铝宾(19)/(20) 教训：Al-CH₃ 在 TS release 弛豫中迁移到 CO₂ 碳上
        （生成醋酸铝插入产物，C-CH₃ 1.514 Å），虚频退化为纯甲基扭转，
        所得"Ea"并非 CO₂ 加成能垒。返回 1-based 原子对 [(metal, C), ...]。"""
        n = len(symbols)
        coords = np.asarray(coords, dtype=float)
        bonds: Dict[int, list] = {}
        for i in range(n):
            for j in range(i + 1, n):
                d = float(np.linalg.norm(coords[i] - coords[j]))
                if d < cls.COV_R.get(symbols[i], 0.8) + cls.COV_R.get(symbols[j], 0.8) + 0.45:
                    bonds.setdefault(i, []).append(j)
                    bonds.setdefault(j, []).append(i)
        out = []
        for c_idx, nbrs in bonds.items():
            if symbols[c_idx] != "C":
                continue
            h_cnt = sum(1 for j in nbrs if symbols[j] == "H")
            heavy = [j for j in nbrs if symbols[j] != "H"]
            if h_cnt == 3 and len(heavy) == 1 and symbols[heavy[0]] in cls.MIGRATION_METALS:
                out.append((heavy[0] + 1, c_idx + 1))
        return sorted(out)

    # ---------- 阶段 2/4：Gaussian 输入生成 ----------

    def _atoms_block(self, symbols, coords) -> str:
        return "\n".join(f"{s:<2s} {x:>12.6f} {y:>12.6f} {z:>12.6f}"
                         for s, (x, y, z) in zip(symbols, coords))

    @staticmethod
    def _multiplicity(symbols) -> int:
        """由原子序数总和（体系净电荷 0）判定自旋多重度：
        奇电子体系（如 [Al](Cl)[Cu]C、[Al+][Te-] 铝宾均为自由基型）→ 二重态，
        否则 Gaussian 报 "The combination of multiplicity 1 and N electrons is impossible"。
        闭壳层 → 单重态。"""
        z_sum = sum(_SYMBOL_TO_ATOMIC_NUM.get(s, 0) for s in symbols)
        if z_sum == 0:
            return 1
        return 2 if z_sum % 2 else 1

    def scan_input(self, symbols, coords, al_idx, method, basis, nproc, mem,
                   scan_serial=None, scan_end=None, freeze_bonds=None) -> str:
        """Freeze 阶段：ModRedundant 松弛扫描 Al···scan_serial（反应坐标）。
        scan_serial: 1-based 原子序号，默认 CO₂ 的 C（倒数第 3 个原子）；
        eta1 模式传近端 O 序号（Al···O 反应坐标）。
        scan_end: 扫描终点距离 (Å)，默认 1.7。
        freeze_bonds: [(i, j), ...] 1-based 冻结键（迁移基团保护）。
        步长为负 = 距离缩短 = 反应进度 ξ 增大（正方向约定）。
        起始距离因拥挤外推（3.5/3.8/4.1 Å）时步数自适应，保持扫描终点不变。"""
        if scan_serial is None:
            scan_serial = len(symbols) - 2       # CO₂ 的 C（倒数第 3 个原子）
        if scan_end is None:
            scan_end = self.SCAN_END_ETA2
        dist = float(np.linalg.norm(np.array(coords[scan_serial - 1]) - np.array(coords[al_idx])))
        steps = min(max(8, round((dist - scan_end) / abs(self.SCAN_STEP_SIZE))), 25)
        mult = self._multiplicity(symbols)
        modred = [f"{al_idx + 1} {scan_serial} S {steps} {self.SCAN_STEP_SIZE}"]
        for (i, j) in (freeze_bonds or []):
            modred.append(f"B {i} {j} F")
        return (f"%chk=job.chk\n%nprocshared={nproc}\n%mem={mem}MB\n"
                f"#P {method}/{basis} Opt=ModRedundant\n\n"
                f"freeze-release scan aluminylene + CO2\n\n0 {mult}\n"
                f"{self._atoms_block(symbols, coords)}\n\n"
                + "\n".join(modred) + "\n")

    def ts_input(self, symbols, coords, method, basis, nproc, mem, freeze_bonds=None) -> str:
        """Release 阶段：全自由度 TS 优化 + 虚频验证（坐标区后必须留空行，
        否则 Gaussian 的 ZSymb 读取器在文件末尾报 End of file；鞍点区 PES
        平坦、收敛慢——铝宾(19)/(20) 实测 MaxCycles=50 优化不收敛且频率
        不计算，续算 200 步才收敛，故固定 MaxCycles=200）。
        freeze_bonds: [(i, j), ...] 1-based 原子对 → ModRedundant B i j F
        （迁移基团保护：防止 release 弛豫到基团迁移插入产物，见
        _detect_migration_bonds）"""
        mult = self._multiplicity(symbols)
        if freeze_bonds:
            route = (f"#P {method}/{basis} "
                     f"Opt=(TS,CalcFC,NoEigenTest,ModRedundant,MaxCycles=200) Freq")
            modred = "\n".join(f"B {i} {j} F" for i, j in freeze_bonds)
            tail = f"{self._atoms_block(symbols, coords)}\n\n{modred}\n"
        else:
            route = f"#P {method}/{basis} Opt=(TS,CalcFC,NoEigenTest,MaxCycles=200) Freq"
            tail = f"{self._atoms_block(symbols, coords)}\n\n"
        return (f"%chk=job.chk\n%nprocshared={nproc}\n%mem={mem}MB\n"
                f"{route}\n\ntransition state release from freeze scan\n\n0 {mult}\n{tail}")

    # ---------- 输出解析 ----------

    @staticmethod
    def _parse_orientation_block(lines, i):
        """解析 lines[i]（含 Standard orientation）起始的坐标块。

        G16 原子行为 6 列（Center/AtomicNo/Type/X/Y/Z），G09 为 5 列。
        返回 (symbols, coords数组, 块尾行号)；解析失败返回 (None, None, i)。
        """
        j, block = i + 5, []
        while j < len(lines) and "-----" not in lines[j]:
            parts = lines[j].split()
            if len(parts) in (5, 6):
                xyz = parts[2:5] if len(parts) == 5 else parts[3:6]
                block.append((_ATOMIC_NUM_TO_SYMBOL.get(int(parts[1]), "X"),
                              tuple(float(v) for v in xyz)))
            j += 1
        if not block:
            return None, None, i
        return ([b[0] for b in block], np.array([b[1] for b in block]), j)

    @staticmethod
    def parse_scan_log(log_file: str):
        """提取松弛扫描各收敛点：[(energy, symbols, coords), ...]

        每个扫描点是约束优化：Gaussian 按步打印 Standard orientation（该步
        输入几何）→ SCF → 收敛判据，收敛时输出 "Optimization completed"。
        因此收敛点构型 = 标志之前最近一次 Standard orientation，能量 = 标志
        之前最近的 SCF Done（该构型上的能量）。
        """
        with open(log_file, "r", errors="ignore") as f:
            lines = f.readlines()
        points, cur_e, cur_geom, i = [], None, None, 0
        while i < len(lines):
            m = re.search(r"SCF Done:\s+E\([^)]+\)\s+=\s+([-+]?\d+\.\d+)", lines[i])
            if m:
                cur_e = float(m.group(1))
            if "Standard orientation" in lines[i]:
                sym, xyz, j = TransitionStateSearcher._parse_orientation_block(lines, i)
                if sym:
                    cur_geom = (sym, xyz)
                i = max(j, i)
            elif "Optimization completed" in lines[i]:
                if cur_e is not None and cur_geom is not None:
                    points.append((cur_e, cur_geom[0], cur_geom[1]))
                cur_geom = None
            i += 1
        return points

    @staticmethod
    def parse_ts_log(log_file: str) -> Dict[str, Any]:
        """解析 TS 优化+频率输出：收敛标志、虚频、能量、ZPE、最终构型，
        以及首个虚频模式的原子位移（虚频-反应坐标一致性校验用）"""
        with open(log_file, "r", errors="ignore") as f:
            content = f.read()

        energies = re.findall(r"SCF Done:\s+E\([^)]+\)\s+=\s+([-+]?\d+\.\d+)", content)
        freqs = re.findall(r"Frequencies --\s+([-+]?\d+\.\d+)\s+([-+]?\d+\.\d+)\s+([-+]?\d+\.\d+)",
                           content)
        zpe = re.search(r"Zero-point correction=\s+([-+]?\d+\.\d+)", content)
        neg_freqs = [float(f[0]) for f in freqs if float(f[0]) < 0]

        coords, i, lines = None, 0, content.splitlines()
        while i < len(lines):
            if "Standard orientation" in lines[i]:
                sym, xyz, j = TransitionStateSearcher._parse_orientation_block(lines, i)
                if sym:
                    coords = (sym, xyz)
                i = max(j, i)
            i += 1

        imag_mode = TransitionStateSearcher._parse_imaginary_mode(content.splitlines())

        return {
            "converged": "Optimization completed" in content,
            "normal_termination": "Normal termination" in content,
            "energy": float(energies[-1]) if energies else None,
            "zpe": float(zpe.group(1)) if zpe else None,
            "imaginary_freqs": neg_freqs,
            "n_freqs": len(freqs),
            "coords": coords,
            "imag_mode": imag_mode,
        }

    @staticmethod
    def _parse_imaginary_mode(lines):
        """提取首个虚频模式的原子位移向量 [(serial, dx, dy, dz), ...]。

        Gaussian 频率输出按 3 个模式一行：Frequencies -- f1 f2 f3，
        其后 " Atom  AN" 块每原子 9 列（3 模式 × X/Y/Z）。取首个负频率
        所在列的位移。"""
        for i, ln in enumerate(lines):
            if not ln.strip().startswith("Frequencies --"):
                continue
            fvals = [float(x) for x in ln.split("--")[1].split()]
            neg_cols = [k for k, v in enumerate(fvals) if v < 0]
            if not neg_cols:
                continue
            k = neg_cols[0]
            j = i + 1
            while j < len(lines) and "Atom  AN" not in lines[j]:
                j += 1
            if j >= len(lines):
                return None
            disp = []
            r = j + 1
            while r < len(lines):
                parts = lines[r].split()
                if len(parts) >= 5 and parts[0].isdigit() and parts[1].isdigit():
                    vals = parts[2:]
                    if len(vals) >= 3 * len(fvals):
                        base = 3 * k
                        disp.append((int(parts[0]),
                                     float(vals[base]), float(vals[base + 1]),
                                     float(vals[base + 2])))
                    r += 1
                    continue
                break
            return {"freq": fvals[k], "displacements": disp} if disp else None
        return None

    @classmethod
    def validate_imaginary_mode(cls, mode_disp, reaction_atoms, threshold=None):
        """虚频-反应坐标一致性校验（自洽性检查）。

        反应坐标原子（Al + CO₂）位移平方占虚频模式总位移的份额：
        share < 阈值 → 虚频由旁观者运动主导（如甲基扭转），鞍点并非
        目标反应坐标的过渡态（铝宾19/20 教训）。返回 (share, consistent)。"""
        if threshold is None:
            threshold = cls.MODE_REACTION_SHARE
        if not mode_disp or not mode_disp.get("displacements"):
            return None, None
        tot = rc = 0.0
        for (serial, dx, dy, dz) in mode_disp["displacements"]:
            d2 = dx * dx + dy * dy + dz * dz
            tot += d2
            if serial in reaction_atoms:
                rc += d2
        if tot <= 0:
            return None, None
        share = rc / tot
        return round(share, 4), bool(share >= threshold)

    @staticmethod
    def wigner_tunneling_factor(nu_imag_cm1, temperature=298.15):
        """Wigner 隧道修正因子 κ = 1 + (1/24)(hc|ν‡|/kBT)²。

        量子隧道效应修正（见《化学反应正方向坐标系与平衡移动原理定量化
        诠释》§4.1）：κ > 1 表示隧穿增强穿越势垒的有效速率。"""
        x = abs(nu_imag_cm1) * 1.4387769 / temperature
        return round(1.0 + x * x / 24.0, 4)

    # ---------- 主流程 ----------

    def search(self, smiles: str, method: str = "B3LYP", basis: str = "def2SVP",
              backend_name: Optional[str] = None, mode: str = "eta2",
              freeze_migration: bool = True) -> Dict[str, Any]:
        manager = self.manager
        backend = manager.backends.get(backend_name or manager.default_backend)
        if backend is None:
            return {"success": False, "error": f"未知后端: {backend_name}"}
        if not isinstance(backend, SSHClusterGaussianBackend):
            return {"success": False,
                    "error": "过渡态搜索需要 SSH 集群后端（cluster_g16）。"
                             "请在设置页配置集群连接，并用 backend='cluster_g16' 提交。"}
        if mode not in ("eta1", "eta2"):
            return {"success": False, "error": f"未知反应网络模式: {mode}（可选 eta1/eta2）"}

        report: Dict[str, Any] = {"smiles": smiles, "method": method, "basis": basis,
                                  "backend": backend_name or manager.default_backend,
                                  "mode": mode}
        try:
            symbols, coords, al_idx = self.build_complex(smiles, mode=mode)
        except Exception as e:
            report["error"] = f"复合物构型构建失败: {e}"
            return report
        report["n_atoms"] = len(symbols)
        report["al_idx"] = al_idx + 1
        report["placement"] = dict(self.last_placement)

        # ---- 迁移基团保护（反应网络边选择，铝宾19/20 教训）----
        # 金属-末端甲基键在 release 弛豫中会迁移插入 CO₂，使虚频退化为
        # 甲基扭转——freeze 与 release 全程冻结，锁定目标反应坐标。
        freeze_bonds = self._detect_migration_bonds(symbols, coords) if freeze_migration else []
        report["freeze_bonds"] = freeze_bonds

        # 反应坐标：eta2 → Al···C（倒数第3原子），eta1 → Al···近端O（倒数第2）
        n = len(symbols)
        if mode == "eta1":
            scan_serial, scan_end = n - 1, self.SCAN_END_ETA1
        else:
            scan_serial, scan_end = n - 2, self.SCAN_END_ETA2

        # ---- Freeze：松弛扫描（ξ 正方向 = 距离缩短，步长为负）----
        r1 = backend.submit_raw(self.scan_input(symbols, coords, al_idx, method, basis,
                                                backend.nproc, backend.mem,
                                                scan_serial=scan_serial, scan_end=scan_end,
                                                freeze_bonds=freeze_bonds), "tsscan")
        if not r1.log_file:
            report["error"] = f"扫描作业失败: {r1.error_message or '未返回日志'}"
            return report
        report["scan_log"] = r1.log_file
        points = self.parse_scan_log(r1.log_file)
        if len(points) < 3:
            report["error"] = (f"扫描日志解析异常（仅 {len(points)} 个收敛点），"
                               "请检查集群日志")
            return report
        if not r1.success:
            # 扫描中途失败（鞍点附近约束优化易发散）——已收敛点仍可给出 TS 猜测
            report["scan_incomplete"] = True
        best_e, best_sym, best_xyz = max(points, key=lambda p: p[0])
        report["scan_points"] = [
            {"energy_hartree": round(p[0], 6), "step": k + 1}
            for k, p in enumerate(points)]
        report["scan_max_energy"] = round(best_e, 6)
        # 势垒近似：最高点相对扫描起点（近自由反应物构型）
        report["barrier_estimate_kcal"] = round((best_e - points[0][0]) * 627.509, 2)
        # 最高点落在扫描边界（尤其终点）说明势垒顶可能超出扫描窗口
        if points[-1][0] == best_e:
            report["scan_boundary"] = "end"
        elif points[0][0] == best_e:
            report["scan_boundary"] = "start"

        # ---- Release：TS 优化 + 虚频（迁移键继续冻结）----
        r2 = backend.submit_raw(self.ts_input(best_sym, best_xyz, method, basis,
                                               backend.nproc, backend.mem,
                                               freeze_bonds=freeze_bonds), "tsts")
        if not r2.log_file:
            report["error"] = f"TS 优化作业失败: {r2.error_message or '未返回日志'}"
            return report
        report["ts_log"] = r2.log_file
        report["ts_fchk"] = r2.fchk_file
        ts = self.parse_ts_log(r2.log_file)
        report["ts"] = {
            "converged": ts["converged"],
            "energy_hartree": ts["energy"],
            "zpe_hartree": ts["zpe"],
            "imaginary_freqs_cm1": ts["imaginary_freqs"],
            "n_freqs": ts["n_freqs"],
        }
        # 虚频-反应坐标一致性校验：反应坐标原子 = Al + CO₂(C,O,O)
        if ts["imag_mode"]:
            reaction_atoms = {al_idx + 1, n - 2, n - 1, n}
            share, consistent = self.validate_imaginary_mode(ts["imag_mode"], reaction_atoms)
            report["ts"]["imag_mode_share"] = share
            report["ts"]["imag_mode_consistent"] = consistent
        # Wigner 隧道修正（量子效应，298 K）
        if ts["imaginary_freqs"]:
            report["ts"]["wigner_kappa_298K"] = self.wigner_tunneling_factor(
                ts["imaginary_freqs"][0])
        if not r2.success:
            # 未正常终止（如步数上限）仍保留扫描曲线与末步波函数供后续分析
            report["error"] = f"TS 优化未正常终止: {r2.error_message or '计算异常'}（已保留扫描数据与末步波函数）"
        report["success"] = bool(ts["converged"] and ts["normal_termination"]
                                 and ts["energy"] is not None)
        return report


# ============================================================
# 7. DFT任务管理器 (集成到聊天界面)
# ============================================================

class DFTTaskManager:
    """
    DFT任务管理器
    管理多个计算后端，根据用户指令选择合适的后端
    """
    
    def __init__(self):
        self.backends: Dict[str, DFTBackend] = {}
        self.analyzer = MultiwfnAnalyzer()
        self.ts_searcher = TransitionStateSearcher(self)
        self.default_backend = "local_g16"
        self._init_default_backends()
    
    def apply_config(self, cfg: Dict[str, Any]) -> List[str]:
        """
        运行时应用配置（设置页保存后调用，无需重启服务）
        cfg 字段：g16_command / orca_command / multiwfn_command / workspace /
                 default_backend / ssh_host / ssh_username / ssh_password /
                 ssh_key / ssh_work_dir / ssh_queue / ssh_queue_name /
                 ssh_g16_root / ssh_g16_scratch
        返回当前可用后端列表
        """
        g16 = (cfg.get("g16_command") or "g16").strip()
        orca = (cfg.get("orca_command") or "orca").strip()
        multiwfn = (cfg.get("multiwfn_command") or "multiwfn").strip()
        workspace = (cfg.get("workspace") or "").strip()
        if workspace:
            os.makedirs(workspace, exist_ok=True)
        
        self.backends["local_g16"] = LocalGaussianBackend(
            g16_command=g16, scratch_dir=workspace or None)
        self.backends["local_orca"] = LocalORCABackend(
            orca_command=orca, scratch_dir=workspace or None)
        
        host = (cfg.get("ssh_host") or "").strip()
        username = (cfg.get("ssh_username") or "").strip()
        if host and username:
            # 注意：集群上的 g16 命令与本地 Windows 路径无关，固定用 "g16"；
            # G16ROOT/GAUSS_SCRDIR 由作业脚本按 g16.pbs 模板内联导出
            self.backends["cluster_g16"] = SSHClusterGaussianBackend(
                host=host,
                username=username,
                password=(cfg.get("ssh_password") or "").strip() or None,
                key_filename=(cfg.get("ssh_key") or "").strip() or None,
                remote_work_dir=(cfg.get("ssh_work_dir") or "/scratch").strip() or "/scratch",
                queue_system=(cfg.get("ssh_queue") or "slurm").strip() or "slurm",
                queue_name=(cfg.get("ssh_queue_name") or "").strip(),
                g16_root=(cfg.get("ssh_g16_root") or "").strip(),
                g16_scratch=(cfg.get("ssh_g16_scratch") or "").strip(),
                g16_command="g16",
                scratch_dir=workspace or None,
            )
        else:
            self.backends.pop("cluster_g16", None)
        
        self.analyzer.multiwfn_command = multiwfn
        
        wanted = (cfg.get("default_backend") or "").strip()
        if wanted in self.backends:
            self.default_backend = wanted
        elif self.default_backend not in self.backends:
            self.default_backend = "local_g16" if "local_g16" in self.backends \
                else next(iter(self.backends))
        return list(self.backends.keys())
    
    def _init_default_backends(self):
        """初始化默认后端 (从环境变量或配置文件读取)"""
        # 本地Gaussian16
        g16_cmd = os.environ.get("G16_COMMAND", "g16")
        self.backends["local_g16"] = LocalGaussianBackend(g16_command=g16_cmd)
        
        # 本地ORCA
        orca_cmd = os.environ.get("ORCA_COMMAND", "orca")
        self.backends["local_orca"] = LocalORCABackend(orca_command=orca_cmd)
        
        # SSH集群 (需配置)
        if all([
            os.environ.get("CLUSTER_HOST"),
            os.environ.get("CLUSTER_USERNAME")
        ]):
            self.backends["cluster_g16"] = SSHClusterGaussianBackend(
                host=os.environ["CLUSTER_HOST"],
                username=os.environ["CLUSTER_USERNAME"],
                key_filename=os.environ.get("CLUSTER_KEY"),
                remote_work_dir=os.environ.get("CLUSTER_WORK_DIR", "/scratch"),
                queue_system=os.environ.get("CLUSTER_QUEUE", "pbs"),
                queue_name=os.environ.get("CLUSTER_QUEUE_NAME", ""),
                g16_root=os.environ.get("CLUSTER_G16_ROOT", "")
            )
    
    def add_backend(self, name: str, backend: DFTBackend):
        """添加自定义后端"""
        self.backends[name] = backend
    
    def run_dft(self, inp: DFTInput, backend: Optional[str] = None) -> DFTResult:
        """运行DFT计算（backend 为空时使用 default_backend）"""
        backend = backend or self.default_backend
        if backend not in self.backends:
            raise ValueError(f"未知后端: {backend}")
        return self.backends[backend].submit(inp)

    def run_ts_search(self, smiles: str, method: str = "B3LYP", basis: str = "def2SVP",
                      backend: Optional[str] = None, mode: str = "eta2",
                      freeze_migration: bool = True) -> Dict[str, Any]:
        """固定-放开法过渡态搜索（铝宾活化 CO₂ 等小分子加成体系）。
        mode: 反应网络模式——eta2=[2+2] 型（Al···C 反应坐标）、
        eta1=η¹-O 酸碱加成（Al···O 反应坐标）；
        freeze_migration: 自动检测并冻结金属-末端甲基键（迁移基团保护）"""
        return self.ts_searcher.search(smiles, method=method, basis=basis,
                                        backend_name=backend, mode=mode,
                                        freeze_migration=freeze_migration)
    
    def analyze_wavefunction(self, fchk_file: str, analysis_type: str = "bond_order") -> Dict[str, Any]:
        """
        对波函数文件进行分析
        analysis_type: "bond_order", "charge", "rdg", "iri"
        """
        if analysis_type == "bond_order":
            return self.analyzer.analyze_bond_order(fchk_file)
        elif analysis_type == "charge":
            return self.analyzer.analyze_charge(fchk_file)
        elif analysis_type == "rdg":
            return self.analyzer.analyze_rdg(fchk_file)
        elif analysis_type == "iri":
            return self.analyzer.analyze_iri(fchk_file)
        else:
            raise ValueError(f"未知分析类型: {analysis_type}")
    
    def parse_user_command(self, user_input: str) -> Optional[Dict[str, Any]]:
        """
        解析用户指令，提取DFT计算参数
        示例: "计算CCO的DFT单点能，使用B3LYP/6-31G(d)"
             "优化CC(=O)O的结构并计算频率"
             "分析优化后分子的Mayer键级"
        """
        result = {}
        
        # 检测计算类型
        if "单点" in user_input or "单点能" in user_input or "sp" in user_input.lower():
            result["job_type"] = "sp"
        elif "频率" in user_input or "freq" in user_input.lower():
            result["job_type"] = "freq"
        elif "优化" in user_input and ("频率" in user_input or "freq" in user_input.lower()):
            result["job_type"] = "opt+freq"
        elif "优化" in user_input:
            result["job_type"] = "opt"
        else:
            result["job_type"] = "sp"
        
        # 检测方法
        method_match = re.search(r'(B3LYP|PBE0|M06-2X|wB97XD|HF|MP2)', user_input, re.IGNORECASE)
        if method_match:
            result["method"] = method_match.group(1).upper()
        else:
            result["method"] = "B3LYP"
        
        # 检测基组
        basis_match = re.search(r'(6-31G|6-311G|def2-SVP|def2-TZVP|cc-pVDZ|cc-pVTZ)', user_input, re.IGNORECASE)
        if basis_match:
            result["basis"] = basis_match.group(1)
        else:
            result["basis"] = "6-31G(d)"
        
        # 检测溶剂
        solvent_match = re.search(r'(水|water|甲醇|methanol|乙醇|ethanol|DMSO|二甲基亚砜)', user_input, re.IGNORECASE)
        if solvent_match:
            solvent_map = {
                "水": "water", "water": "water",
                "甲醇": "methanol", "methanol": "methanol",
                "乙醇": "ethanol", "ethanol": "ethanol",
                "DMSO": "dmso", "二甲基亚砜": "dmso"
            }
            result["solvent"] = solvent_map.get(solvent_match.group(1).lower(), "water")
        
        # 提取SMILES (简单正则)
        smile_pattern = r'\b([A-Z][a-z]?[0-9]*(?:\([^)]*\))?[A-Za-z0-9\(\)=#]*)\b'
        matches = re.findall(smile_pattern, user_input)
        if matches:
            result["smiles"] = matches[-1]
        else:
            return None
        
        # 检测后端
        if "集群" in user_input or "cluster" in user_input.lower():
            result["backend"] = "cluster_g16"
        elif "orca" in user_input.lower():
            result["backend"] = "local_orca"
        else:
            result["backend"] = "local_g16"
        
        return result