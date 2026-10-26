# 使用 Python 进行 DFT 输出结果分析与出图（完整技能手册）

> 本文档基于 Python 科学计算生态（NumPy、SciPy、Matplotlib）、cclib、PySCF、ASE、各量子化学程序输出格式等资料整理，涵盖从读取 DFT 输出文件到绘制各类分析图的完整工作流。

---

## 一、Python 科学计算环境

### 1.1 推荐环境配置 [pdf_85]

```bash
# 核心依赖
pip install numpy scipy matplotlib

# 量子化学输出解析
pip install cclib

# 分子模拟与可视化
pip install ase pymatgen

# 交互式环境
pip install jupyter notebook ipywidgets

# 高级绘图
pip install seaborn plotly
```

### 1.2 核心库速查 [pdf_85]

| 库 | 用途 | 关键模块 |
|------|------|----------|
| `numpy` | 数组与数值计算 | `np.array`, `np.linalg`, `np.fft` |
| `scipy` | 科学计算 | `scipy.integrate`, `scipy.interpolate`, `scipy.signal` |
| `matplotlib` | 基础绘图 | `plt.plot`, `plt.figure`, `plt.subplots` |
| `cclib` | 解析量子化学输出 | `ccopen`, `ccread`, `ccwrite` |
| `ase` | 原子模拟环境 | `ase.io.read`, `ase.visualize` |
| `pymatgen` | 材料基因组 | `pymatgen.io.vasp`, `pymatgen.electronic_structure` |

---

## 二、解析 DFT 输出文件

### 2.1 使用 cclib 解析主流程序输出 [pdf_14]

**cclib** 是一个支持解析 Gaussian、ORCA、NWChem、Q-Chem、Molpro、ADF、GAMESS 等主流量子化学程序输出文件的 Python 库。

```python
import cclib
import numpy as np

# 解析 Gaussian 输出文件
data = cclib.io.ccread("water.log")

# 解析 ORCA 输出文件
data = cclib.io.ccread("water.out")

# 解析 Multiwfn 输出文件
data = cclib.io.ccread("water.fch")
```

**cclib 可提取的常用属性** [pdf_14]：

| 属性 | 说明 | 类型 |
|------|------|------|
| `data.natom` | 原子数 | int |
| `data.atomcoords` | 原子坐标（所有步） | list of ndarray |
| `data.atomnos` | 原子序数 | ndarray |
| `data.charge` | 总电荷 | int |
| `data.mult` | 自旋多重度 | int |
| `data.scfenergies` | SCF 能量 | list |
| `data.mpenergies` | MP2/MP3 等能量 | list |
| `data.moenergies` | 轨道能量 | list of ndarray |
| `data.mooccnos` | 轨道占据数 | list of ndarray |
| `data.mosyms` | 轨道对称性 | list |
| `data.homos` | HOMO 索引 | list of int |
| `data.atomcharges` | 原子电荷 | dict of list |
| `data.etens` | 电多极矩 | list |
| `data.vibfreqs` | 振动频率 | ndarray |
| `data.vibirs` | IR 强度 | ndarray |
| `data.vibramans` | Raman 强度 | ndarray |
| `data.rotconsts` | 转动常数 | ndarray |
| `data.enthalpy` | 焓 | float |
| `data.freeenergy` | 自由能 | float |
| `data.entropy` | 熵 | float |

### 2.2 手动解析 Gaussian 输出文件 [pdf_76]

**提取 SCF 能量**：
```python
import re

def extract_energy_gaussian(filename):
    """从 Gaussian 输出文件中提取 SCF 能量"""
    energies = []
    pattern = r"SCF Done:\s+E\([^)]+\)\s*=\s*(-?\d+\.\d+)"
    with open(filename, 'r') as f:
        for line in f:
            match = re.search(pattern, line)
            if match:
                energies.append(float(match.group(1)))
    return energies

# 使用
energies = extract_energy_gaussian("water.log")
print(f"Final energy: {energies[-1]:.6f} Hartree")
```

**提取优化轨迹**：
```python
def extract_optimization_trajectory_gaussian(filename):
    """从 Gaussian 优化输出中提取每一步的坐标和能量"""
    import re
    
    coords = []
    energies = []
    current_coords = []
    in_std_orient = False
    
    energy_pattern = r"SCF Done:\s+E\([^)]+\)\s*=\s*(-?\d+\.\d+)"
    coord_start = r"Standard orientation:"
    coord_end = r"---------------------------------------------------------------------"
    
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # 提取能量
        match = re.search(energy_pattern, line)
        if match:
            energies.append(float(match.group(1)))
        
        # 提取标准坐标
        if re.search(coord_start, line):
            current_coords = []
            # 跳过表头
            for _ in range(4):
                i += 1
                if i >= len(lines):
                    break
            i += 1  # 跳过分隔线
            while i < len(lines) and not re.search(coord_end, lines[i]):
                parts = lines[i].split()
                if len(parts) >= 6:
                    # 原子序数, 坐标数, 电荷, X, Y, Z
                    atom_num = int(parts[1])
                    x, y, z = float(parts[3]), float(parts[4]), float(parts[5])
                    current_coords.append([x, y, z])
                i += 1
            coords.append(np.array(current_coords))
        
        i += 1
    
    return np.array(coords), np.array(energies)


coords, energies = extract_optimization_trajectory_gaussian("opt.log")
print(f"Optimization steps: {len(energies)}")
print(f"Converged: {energies[-1]:.6f} Hartree")
```

**提取振动频率和 IR 强度** [pdf_76]：
```python
def extract_vibrations_gaussian(filename):
    """从 Gaussian 频率输出中提取频率和 IR 强度"""
    freqs = []
    irs = []
    ramans = []
    
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    for i, line in enumerate(lines):
        if "Frequencies ---" in line:
            parts = line.split()
            for p in parts[2:]:
                try:
                    freqs.append(float(p))
                except:
                    pass
        
        if "IR Intensities ---" in line:
            parts = line.split()
            for p in parts[3:]:
                try:
                    irs.append(float(p))
                except:
                    pass
        
        if "Raman Activities ---" in line:
            parts = line.split()
            for p in parts[3:]:
                try:
                    ramans.append(float(p))
                except:
                    pass
    
    return np.array(freqs), np.array(irs), np.array(ramans)

freqs, irs, ramans = extract_vibrations_gaussian("freq.log")
```

**提取 Mulliken 电荷** [pdf_76]：
```python
def extract_mulliken_charges_gaussian(filename):
    """从 Gaussian 输出中提取 Mulliken 电荷"""
    charges = []
    in_mulliken = False
    
    with open(filename, 'r') as f:
        for line in f:
            if "Mulliken atomic charges:" in line:
                in_mulliken = True
                continue
            if in_mulliken:
                parts = line.split()
                if len(parts) >= 3 and parts[0].isdigit():
                    charges.append(float(parts[2]))
                elif "Sum of Mulliken charges" in line:
                    break
    
    return np.array(charges)
```

**提取 NMR 屏蔽和化学位移** [pdf_75]：
```python
def extract_nmr_gaussian(filename):
    """从 Gaussian NMR 输出中提取各向同性屏蔽值"""
    shielding = []
    
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    # 搜索 "Isotropic = " 行
    for line in lines:
        if "Isotropic =" in line:
            parts = line.split()
            for i, p in enumerate(parts):
                if p == "=":
                    shielding.append(float(parts[i+1]))
                    break
    
    return np.array(shielding)
```

### 2.3 手动解析 ORCA 输出文件 [pdf_14]

**提取 SCF 能量**：
```python
def extract_energy_orca(filename):
    """从 ORCA 输出文件中提取 SCF 能量"""
    energies = []
    pattern = r"FINAL SINGLE POINT ENERGY\s+(-?\d+\.\d+)"
    with open(filename, 'r') as f:
        for line in f:
            match = re.search(pattern, line)
            if match:
                energies.append(float(match.group(1)))
    return energies
```

**提取优化轨迹**：
```python
def extract_optimization_trajectory_orca(filename):
    """从 ORCA 优化输出中提取能量和坐标轨迹"""
    energies = []
    energy_pattern = r"Total Energy\s*:\s*(-?\d+\.\d+)\s+Eh"
    
    with open(filename, 'r') as f:
        for line in f:
            match = re.search(energy_pattern, line)
            if match:
                energies.append(float(match.group(1)))
    
    return np.array(energies)
```

**提取振动频率和 IR 强度**：
```python
def extract_vibrations_orca(filename):
    """从 ORCA 频率输出中提取频率和 IR 强度"""
    freqs = []
    irs = []
    
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    for i, line in enumerate(lines):
        if "Frequencies in cm" in line:
            # 下一行有频率值
            freq_line = lines[i+1] if i+1 < len(lines) else ""
            parts = freq_line.split()
            for p in parts:
                try:
                    freqs.append(float(p))
                except:
                    pass
        
        if "IR Intensities" in line:
            # 下一行有强度值
            ir_line = lines[i+1] if i+1 < len(lines) else ""
            parts = ir_line.split()
            for p in parts:
                try:
                    irs.append(float(p))
                except:
                    pass
    
    return np.array(freqs), np.array(irs)
```

### 2.4 使用 ASE 读取结构文件

```python
from ase.io import read

# 读取 Gaussian 输出文件
mol = read("water.log")
print(f"Atoms: {mol.get_chemical_formula()}")
print(f"Energy: {mol.get_potential_energy():.6f} eV")

# 读取 ORCA 输出文件
mol = read("water.out")

# 读取 XYZ 文件
mol = read("water.xyz")

# 读取优化轨迹
traj = read("opt.log", index=":")
print(f"Number of steps: {len(traj)}")
```

---

## 三、DFT 结果可视化

### 3.1 优化收敛过程图

**能量与梯度收敛曲线** [pdf_68]：
```python
import matplotlib.pyplot as plt
import numpy as np

def plot_optimization_convergence(energies, coords=None, gradient_norms=None):
    """
    绘制优化收敛过程图
    
    参数:
        energies: 每步能量列表
        coords: 每步坐标（可选，用于计算 RMS 位移）
        gradient_norms: 每步梯度范数（可选）
    """
    steps = np.arange(1, len(energies) + 1)
    energy_kcal = (energies - energies[-1]) * 627.509  # 转换为 kcal/mol
    
    fig, axes = plt.subplots(2, 2, figsize=(12, 8))
    
    # 1. 能量变化 (Hartree)
    axes[0, 0].plot(steps, energies, 'o-', color='#1f77b4', markersize=4)
    axes[0, 0].axhline(y=energies[-1], color='gray', linestyle='--', alpha=0.5)
    axes[0, 0].set_xlabel('Optimization Step')
    axes[0, 0].set_ylabel('Energy (Hartree)')
    axes[0, 0].set_title('Total Energy')
    axes[0, 0].grid(True, alpha=0.3)
    
    # 2. 能量差 (kcal/mol)
    axes[0, 1].plot(steps[1:], energy_kcal[1:], 's-', color='#d62728', markersize=4)
    axes[0, 1].axhline(y=0, color='gray', linestyle='--', alpha=0.5)
    axes[0, 1].set_xlabel('Optimization Step')
    axes[0, 1].set_ylabel('ΔE (kcal/mol)')
    axes[0, 1].set_title('Energy Change from Final')
    axes[0, 1].grid(True, alpha=0.3)
    
    # 3. 能量变化 (逐步)
    energy_diff = np.abs(np.diff(energies)) * 627.509
    axes[1, 0].plot(steps[1:], energy_diff, '^--', color='#2ca02c', markersize=4)
    axes[1, 0].set_yscale('log')
    axes[1, 0].axhline(y=1e-6*627.509, color='red', linestyle=':', label='Tight')
    axes[1, 0].axhline(y=3e-5*627.509, color='orange', linestyle=':', label='Default')
    axes[1, 0].set_xlabel('Optimization Step')
    axes[1, 0].set_ylabel('|ΔE| (kcal/mol)')
    axes[1, 0].set_title('Energy Change (Step by Step)')
    axes[1, 0].legend()
    axes[1, 0].grid(True, alpha=0.3)
    
    # 4. RMS 位移（如果有坐标数据）
    if coords is not None and len(coords) > 1:
        rmsd = []
        for i in range(1, len(coords)):
            diff = coords[i] - coords[i-1]
            rmsd.append(np.sqrt(np.mean(np.sum(diff**2, axis=1))))
        
        axes[1, 1].plot(steps[1:], rmsd, 'o-', color='#9467bd', markersize=4)
        axes[1, 1].set_yscale('log')
        axes[1, 1].axhline(y=1.8e-3, color='red', linestyle=':', label='Max')
        axes[1, 1].set_xlabel('Optimization Step')
        axes[1, 1].set_ylabel('RMS Displacement (Å)')
        axes[1, 1].set_title('RMS Displacement')
        axes[1, 1].legend()
        axes[1, 1].grid(True, alpha=0.3)
    elif gradient_norms is not None:
        axes[1, 1].plot(steps, gradient_norms, 'o-', color='#9467bd', markersize=4)
        axes[1, 1].set_yscale('log')
        axes[1, 1].set_xlabel('Optimization Step')
        axes[1, 1].set_ylabel('RMS Gradient (a.u.)')
        axes[1, 1].set_title('RMS Gradient')
        axes[1, 1].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('optimization_convergence.png', dpi=150, bbox_inches='tight')
    plt.show()
```

### 3.2 轨道能级图

```python
def plot_orbital_energy_level(mo_energies, homo_index, title="Molecular Orbital Energy Levels",
                               color_occ='#1f77b4', color_virt='#d62728', 
                               energy_range=None, filename=None):
    """
    绘制分子轨道能级图
    
    参数:
        mo_energies: 轨道能量列表或数组（eV）
        homo_index: HOMO 索引（从0开始）
        title: 图标题
        color_occ: 占据轨道颜色
        color_virt: 虚轨道颜色
        energy_range: (emin, emax) 显示范围
        filename: 保存文件名
    """
    mo_energies = np.array(mo_energies)
    n_mo = len(mo_energies)
    
    fig, ax = plt.subplots(figsize=(8, 10))
    
    # 区分占据和虚轨道
    occ = mo_energies[:homo_index+1]
    virt = mo_energies[homo_index+1:]
    
    # 绘制轨道能级线
    for i, e in enumerate(occ):
        ax.hlines(y=e, xmin=i, xmax=i+0.8, color=color_occ, linewidth=2.5, alpha=0.8)
    
    for i, e in enumerate(virt):
        idx = homo_index + 1 + i
        ax.hlines(y=e, xmin=idx, xmax=idx+0.8, color=color_virt, linewidth=2.5, alpha=0.8)
    
    # 标记 HOMO 和 LUMO
    if homo_index >= 0 and homo_index + 1 < n_mo:
        ax.annotate(f'HOMO\n{mo_energies[homo_index]:.2f} eV',
                    xy=(homo_index, mo_energies[homo_index]),
                    xytext=(homo_index + 0.3, mo_energies[homo_index] + 0.5),
                    fontsize=10, color='black',
                    arrowprops=dict(arrowstyle='->', color='black'))
        
        ax.annotate(f'LUMO\n{mo_energies[homo_index+1]:.2f} eV',
                    xy=(homo_index+1, mo_energies[homo_index+1]),
                    xytext=(homo_index+1 + 0.3, mo_energies[homo_index+1] - 0.5),
                    fontsize=10, color='black',
                    arrowprops=dict(arrowstyle='->', color='black'))
    
    # 设置显示范围
    if energy_range is None:
        e_min = min(mo_energies) - 1.0
        e_max = max(mo_energies) + 1.0
    else:
        e_min, e_max = energy_range
    
    ax.set_ylim(e_min, e_max)
    ax.set_xlim(-0.5, n_mo + 0.5)
    
    ax.set_xlabel('Molecular Orbital Index', fontsize=12)
    ax.set_ylabel('Energy (eV)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.grid(True, alpha=0.2, axis='y')
    
    # 添加 HOMO-LUMO gap 标注
    if homo_index >= 0 and homo_index + 1 < n_mo:
        gap = mo_energies[homo_index+1] - mo_energies[homo_index]
        ax.annotate(f'HL Gap = {gap:.2f} eV',
                    xy=(0.5, (mo_energies[homo_index] + mo_energies[homo_index+1])/2),
                    fontsize=12, color='green',
                    bbox=dict(boxstyle='round,pad=0.3', facecolor='lightyellow', alpha=0.8))
    
    # 图例
    from matplotlib.patches import Patch
    legend_elements = [Patch(facecolor=color_occ, label='Occupied'),
                       Patch(facecolor=color_virt, label='Virtual')]
    ax.legend(handles=legend_elements, loc='upper right')
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
    return gap if homo_index >= 0 and homo_index + 1 < n_mo else None
```

### 3.3 态密度（DOS）绘制

```python
from scipy.signal import gaussian

def compute_dos(energies, homos, erange=(-20, 10), sigma=0.2, npoints=500):
    """
    计算态密度 (DOS) 数据
    
    参数:
        energies: 轨道能量（eV）
        homos: HOMO 索引列表
        erange: 能量范围 (emin, emax)
        sigma: 展宽（eV）
        npoints: 采样点数
    """
    emin, emax = erange
    e_grid = np.linspace(emin, emax, npoints)
    dos = np.zeros(npoints)
    
    for e in energies:
        # 高斯展宽
        dos += gaussian(1, sigma)[0] * np.exp(-((e_grid - e) ** 2) / (2 * sigma ** 2)) / (sigma * np.sqrt(2 * np.pi))
    
    return e_grid, dos


def plot_dos(mo_energies, homo_index, sigma=0.2, title="Density of States",
             color_total='black', xlim=None, filename=None):
    """
    绘制总态密度图 (TDOS)
    
    参数:
        mo_energies: 轨道能量（eV）
        homo_index: HOMO 索引
        sigma: 展宽 (eV)
        title: 图标题
        color_total: TDOS 颜色
        xlim: x 轴范围
        filename: 保存文件名
    """
    mo_energies = np.array(mo_energies)
    
    # 区分 α 和 β 轨道（如果是开壳层）
    if len(mo_energies.shape) > 1 and mo_energies.shape[0] == 2:
        alpha_energies = mo_energies[0]
        beta_energies = mo_energies[1]
        homo_alpha = homo_index[0]
        homo_beta = homo_index[1]
    else:
        alpha_energies = mo_energies
        beta_energies = None
        homo_alpha = homo_index
        homo_beta = None
    
    # 确定能量范围
    if xlim is None:
        e_min = min(alpha_energies) - 2.0
        e_max = max(alpha_energies) + 2.0
    else:
        e_min, e_max = xlim
    
    e_grid, dos_total = compute_dos(alpha_energies, homo_alpha, 
                                     erange=(e_min, e_max), sigma=sigma)
    
    fig, ax = plt.subplots(figsize=(8, 6))
    
    # 绘制 TDOS
    ax.fill_between(e_grid, dos_total, 0, alpha=0.3, color=color_total, label='TDOS')
    ax.plot(e_grid, dos_total, color=color_total, linewidth=1.5)
    
    # 如果有 β 轨道，绘制 β-DOS
    if beta_energies is not None:
        _, dos_beta = compute_dos(beta_energies, homo_beta,
                                   erange=(e_min, e_max), sigma=sigma)
        ax.fill_between(e_grid, -dos_beta, 0, alpha=0.3, color='red', label='β-DOS')
        ax.plot(e_grid, -dos_beta, color='red', linewidth=1.5)
    
    # 标记 HOMO 位置
    if beta_energies is None:
        homo_e = alpha_energies[homo_alpha]
        ax.axvline(x=homo_e, color='red', linestyle='--', linewidth=1, alpha=0.7)
        ax.annotate('HOMO', xy=(homo_e, ax.get_ylim()[1]*0.9),
                    fontsize=10, color='red',
                    ha='center')
    
    ax.set_xlabel('Energy (eV)', fontsize=12)
    ax.set_ylabel('DOS (states/eV)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.legend()
    ax.grid(True, alpha=0.2)
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
```

### 3.4 振动光谱绘制

```python
def plot_ir_spectrum(frequencies, intensities, fwhm=10, title="IR Spectrum",
                      xlim=(0, 4000), npoints=2000, filename=None):
    """
    绘制 IR 光谱（使用 Lorentz 展宽）
    
    参数:
        frequencies: 振动频率 (cm⁻¹)
        intensities: IR 强度 (KM/mol)
        fwhm: 半峰宽 (cm⁻¹)
        title: 图标题
        xlim: x 轴范围 (cm⁻¹)
        npoints: 采样点数
        filename: 保存文件名
    """
    frequencies = np.array(frequencies)
    intensities = np.array(intensities)
    
    xmin, xmax = xlim
    x = np.linspace(xmin, xmax, npoints)
    y = np.zeros(npoints)
    
    # Lorentz 展宽
    for freq, intens in zip(frequencies, intensities):
        if intens > 0:
            gamma = fwhm / 2
            y += intens * gamma / (np.pi * ((x - freq) ** 2 + gamma ** 2))
    
    fig, ax = plt.subplots(figsize=(10, 5))
    
    ax.plot(x, y, color='#1f77b4', linewidth=1.5)
    ax.fill_between(x, y, alpha=0.3, color='#1f77b4')
    
    # 标记显著峰
    threshold = np.max(y) * 0.1
    for freq, intens in zip(frequencies, intensities):
        if intens > threshold:
            ax.annotate(f'{freq:.0f}', xy=(freq, y[np.argmin(np.abs(x - freq))]),
                        xytext=(freq, y[np.argmin(np.abs(x - freq))] + np.max(y)*0.05),
                        fontsize=8, ha='center', rotation=45)
    
    ax.set_xlabel('Wavenumber (cm⁻¹)', fontsize=12)
    ax.set_ylabel('IR Intensity (arb. units)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.set_xlim(xmin, xmax)
    ax.grid(True, alpha=0.2)
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()


def plot_uv_vis_spectrum(wavelengths, oscillator_strengths, fwhm=20,
                          title="UV-Vis Spectrum", xlim=(200, 800), 
                          npoints=2000, filename=None):
    """
    绘制 UV-Vis 光谱
    
    参数:
        wavelengths: 波长 (nm)
        oscillator_strengths: 振子强度
        fwhm: 半峰宽 (nm)
        title: 图标题
        xlim: x 轴范围 (nm)
        npoints: 采样点数
        filename: 保存文件名
    """
    wls = np.array(wavelengths)
    oscs = np.array(oscillator_strengths)
    
    xmin, xmax = xlim
    x = np.linspace(xmin, xmax, npoints)
    y = np.zeros(npoints)
    
    # Gauss 展宽
    sigma = fwhm / (2 * np.sqrt(2 * np.log(2)))
    for wl, osc in zip(wls, oscs):
        if osc > 0:
            y += osc * np.exp(-((x - wl) ** 2) / (2 * sigma ** 2)) / (sigma * np.sqrt(2 * np.pi))
    
    fig, ax = plt.subplots(figsize=(10, 5))
    
    ax.plot(x, y, color='#d62728', linewidth=1.5)
    ax.fill_between(x, y, alpha=0.3, color='#d62728')
    
    # 用竖线标注单个跃迁
    for wl, osc in zip(wls, oscs):
        if osc > 0.01:
            ax.axvline(x=wl, ymax=osc/np.max(oscs)*0.8, 
                       color='gray', linestyle='--', linewidth=0.5, alpha=0.5)
    
    ax.set_xlabel('Wavelength (nm)', fontsize=12)
    ax.set_ylabel('Oscillator Strength (arb. units)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.set_xlim(xmin, xmax)
    ax.grid(True, alpha=0.2)
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
```

### 3.5 势能面扫描图

```python
def plot_scan_results(scan_coords, scan_energies, xlabel="Reaction Coordinate",
                       title="Potential Energy Scan", filename=None):
    """
    绘制势能面扫描曲线
    
    参数:
        scan_coords: 扫描坐标值（如键长、键角等）
        scan_energies: 对应能量
        xlabel: x 轴标签
        title: 图标题
        filename: 保存文件名
    """
    scan_coords = np.array(scan_coords)
    scan_energies = np.array(scan_energies)
    
    # 计算相对能量（kcal/mol 或 kJ/mol）
    rel_energies = (scan_energies - scan_energies[0]) * 627.509  # kcal/mol
    
    fig, ax = plt.subplots(figsize=(8, 5))
    
    ax.plot(scan_coords, rel_energies, 'o-', color='#1f77b4', 
            markersize=6, linewidth=2, markerfacecolor='white')
    
    # 标记极小值
    min_idx = np.argmin(rel_energies)
    ax.plot(scan_coords[min_idx], rel_energies[min_idx], 's', 
            color='red', markersize=10, zorder=5)
    ax.annotate(f'Minimum\n{rel_energies[min_idx]:.2f} kcal/mol',
                xy=(scan_coords[min_idx], rel_energies[min_idx]),
                xytext=(scan_coords[min_idx] + 0.1, rel_energies[min_idx] + 2),
                fontsize=10, color='red',
                arrowprops=dict(arrowstyle='->', color='red'))
    
    # 标记过渡态（如果存在）
    if len(scan_energies) > 2:
        # 找到极大值
        from scipy.signal import argrelextrema
        local_max = argrelextrema(rel_energies, np.greater)[0]
        for idx in local_max:
            ax.plot(scan_coords[idx], rel_energies[idx], 'v', 
                    color='orange', markersize=10, zorder=5)
    
    ax.set_xlabel(xlabel, fontsize=12)
    ax.set_ylabel('Relative Energy (kcal/mol)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
```

### 3.6 原子电荷分布图

```python
def plot_charge_distribution(charges, atom_labels, title="Atomic Charge Distribution",
                              filename=None):
    """
    绘制原子电荷柱状图
    
    参数:
        charges: 原子电荷列表
        atom_labels: 原子标签列表
        title: 图标题
        filename: 保存文件名
    """
    charges = np.array(charges)
    n_atoms = len(charges)
    indices = np.arange(n_atoms)
    
    colors = ['#1f77b4' if c > 0 else '#d62728' for c in charges]
    
    fig, ax = plt.subplots(figsize=(max(8, n_atoms * 0.4), 5))
    
    bars = ax.bar(indices, charges, color=colors, edgecolor='black', linewidth=0.5)
    
    # 在柱子上标注数值
    for bar, charge in zip(bars, charges):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., 
                0.02 if charge >= 0 else height - 0.05,
                f'{charge:.3f}',
                ha='center', fontsize=8, 
                color='white' if abs(charge) > 0.3 else 'black')
    
    ax.set_xticks(indices)
    ax.set_xticklabels(atom_labels, rotation=45, ha='right')
    ax.set_xlabel('Atom', fontsize=12)
    ax.set_ylabel('Charge (e)', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.axhline(y=0, color='black', linewidth=0.5)
    ax.grid(True, alpha=0.2, axis='y')
    
    # 总和
    total_charge = np.sum(charges)
    ax.text(0.95, 0.95, f'Total: {total_charge:.3f} e',
            transform=ax.transAxes, fontsize=11,
            verticalalignment='top', horizontalalignment='right',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
```

---

## 四、使用 PySCF 执行与分析 DFT [pdf_86]

### 4.1 PySCF 基础

```python
import pyscf

# 创建分子
mol = pyscf.M(
    atom='''
        O    0.0000    0.0000    0.0626
        H   -0.7920    0.0000   -0.4973
        H    0.7920    0.0000   -0.4973
    ''',
    basis='def2-TZVP',
    charge=0,
    spin=0
)

# 构建 DFT 对象
mf = mol.RKS(xc='B3LYP')
mf.kernel()  # 运行 DFT 计算

print(f"Total energy: {mf.e_tot:.8f} Hartree")
print(f"Number of orbitals: {len(mf.mo_energy)}")
```

### 4.2 提取轨道信息

```python
# 轨道能量 (eV)
mo_energies = mf.mo_energy * 27.2114  # Hartree → eV
print(f"Orbital energies (first 5): {mo_energies[:5]}")

# 轨道占据数
mo_occ = mf.mo_occ
print(f"Occupied orbitals: {sum(mo_occ > 0)}")

# HOMO 索引
homo_idx = np.where(mo_occ > 0)[0][-1]
print(f"HOMO index: {homo_idx}, HOMO energy: {mo_energies[homo_idx]:.2f} eV")
print(f"LUMO energy: {mo_energies[homo_idx+1]:.2f} eV")
print(f"HL Gap: {mo_energies[homo_idx+1] - mo_energies[homo_idx]:.2f} eV")
```

### 4.3 生成 Cube 文件 [pdf_86]

```python
from pyscf import gto, dft, cube

# 创建 Cube 文件（电子密度）
cubegen = cube.Cube(mf, resolution=0.2)
cubegen.cube_file('density.cube')

# 创建 Cube 文件（HOMO）
cubegen = cube.Cube(mf, resolution=0.2)
cubegen.orbital = homo_idx  # HOMO 轨道索引
cubegen.cube_file('homo.cube')

# 创建 Cube 文件（LUMO）
cubegen.orbital = homo_idx + 1  # LUMO 轨道索引
cubegen.cube_file('lumo.cube')
```

### 4.4 解析 Cube 文件

```python
def read_cube(filename):
    """读取 Cube 格式文件"""
    with open(filename, 'r') as f:
        # 跳过前两行注释
        f.readline()
        f.readline()
        
        # 读取原子数、原点坐标
        line = f.readline().split()
        natoms = int(line[0])
        origin = np.array([float(line[1]), float(line[2]), float(line[3])])
        
        # 读取网格信息和步长
        nx, vx = f.readline().split()[:4]
        nx = int(nx)
        vx = np.array([float(x) for x in [vx] + f.readline().split()[:2]])
        
        ny, vy = f.readline().split()[:4]
        ny = int(ny)
        vy = np.array([float(x) for x in [vy] + f.readline().split()[:2]])
        
        nz, vz = f.readline().split()[:4]
        nz = int(nz)
        vz = np.array([float(x) for x in [vz] + f.readline().split()[:2]])
        
        # 读取原子信息
        atoms = []
        for _ in range(natoms):
            line = f.readline().split()
            atoms.append((int(line[0]), float(line[2]), float(line[3]), float(line[4])))
        
        # 读取网格数据
        data = np.fromstring(f.read(), sep='\n').reshape(nx, ny, nz)
    
    return {
        'natoms': natoms,
        'origin': origin,
        'nx': nx, 'ny': ny, 'nz': nz,
        'vx': vx, 'vy': vy, 'vz': vz,
        'atoms': atoms,
        'data': data
    }
```

---

## 五、结合 Multiwfn 与 Python [pdf_1]

### 5.1 批量调用 Multiwfn 处理

```python
import subprocess
import os

def run_multiwfn_script(input_file, script_commands, output_file="multiwfn.out"):
    """
    通过静默模式运行 Multiwfn 脚本
    
    参数:
        input_file: Multiwfn 输入文件路径
        script_commands: 命令列表（每行一条）
        output_file: 输出文件路径
    """
    # 创建脚本文件
    script_file = "multiwfn_script.txt"
    with open(script_file, 'w') as f:
        for cmd in script_commands:
            f.write(f"{cmd}\n")
    
    # 运行 Multiwfn
    cmd = f"Multiwfn {input_file} -silent < {script_file} > {output_file}"
    subprocess.run(cmd, shell=True)
    
    # 清理临时文件
    os.remove(script_file)
    
    return output_file


def extract_atomic_charges_multiwfn(output_file, charge_type="mulliken"):
    """从 Multiwfn 输出中提取原子电荷"""
    charges = []
    in_charge = False
    
    with open(output_file, 'r') as f:
        for line in f:
            if charge_type.lower() in line.lower() and "charges" in line.lower():
                in_charge = True
                continue
            if in_charge and "-----" in line:
                # 跳过表头
                continue
            if in_charge:
                parts = line.split()
                if len(parts) >= 3 and parts[0].isdigit():
                    charges.append(float(parts[-1]))
                elif "Sum" in line:
                    break
    
    return np.array(charges)
```

### 5.2 批量处理多个 DFT 结果

```python
import glob
import pandas as pd

def batch_analyze_gaussian_results(pattern="*.log"):
    """
    批量分析 Gaussian 输出文件，生成汇总 DataFrame
    
    参数:
        pattern: 文件匹配模式
    """
    results = []
    
    for filename in glob.glob(pattern):
        try:
            data = cclib.io.ccread(filename)
            
            result = {
                'filename': filename,
                'natoms': data.natom,
                'energy': data.scfenergies[-1] if data.scfenergies else None,
                'homo_index': data.homos[0] if data.homos else None,
                'homo_energy': data.moenergies[0][data.homos[0]] * 27.2114 if data.moenergies and data.homos else None,
                'lumo_energy': data.moenergies[0][data.homos[0] + 1] * 27.2114 if data.moenergies and data.homos and data.homos[0] + 1 < len(data.moenergies[0]) else None,
            }
            
            if result['homo_energy'] and result['lumo_energy']:
                result['hl_gap'] = result['lumo_energy'] - result['homo_energy']
            
            # 提取 Mulliken 电荷
            if 'mulliken' in data.atomcharges:
                result['max_charge'] = np.max(np.abs(data.atomcharges['mulliken']))
                result['sum_charge'] = np.sum(data.atomcharges['mulliken'])
            
            # 提取偶极矩
            if hasattr(data, 'moments') and data.moments:
                result['dipole'] = np.sqrt(np.sum(np.array(data.moments[0])**2))
            
            results.append(result)
            
        except Exception as e:
            print(f"Error processing {filename}: {e}")
    
    return pd.DataFrame(results)


# 使用
df = batch_analyze_gaussian_results("*.log")
print(df[['filename', 'energy', 'hl_gap', 'dipole']].to_string())
```

---

## 六、多图综合出图模板

### 6.1 综合计算结果报告

```python
def generate_dft_report(filename, program='gaussian', output_prefix='report'):
    """
    生成 DFT 计算结果综合报告图
    
    参数:
        filename: 输出文件路径
        program: 'gaussian' 或 'orca'
        output_prefix: 输出文件前缀
    """
    import matplotlib.gridspec as gridspec
    
    # 解析数据
    if program.lower() == 'gaussian':
        energies = extract_energy_gaussian(filename)
        freqs, irs, _ = extract_vibrations_gaussian(filename)
        charges = extract_mulliken_charges_gaussian(filename)
        data = cclib.io.ccread(filename)
    elif program.lower() == 'orca':
        energies = extract_energy_orca(filename)
        freqs, irs = extract_vibrations_orca(filename)
        # 更多 ORCA 分析...
        data = cclib.io.ccread(filename)
    
    # 创建综合报告图
    fig = plt.figure(figsize=(16, 12))
    gs = gridspec.GridSpec(3, 3, figure=fig, hspace=0.3, wspace=0.3)
    
    # 1. 优化收敛曲线
    ax1 = fig.add_subplot(gs[0, :])
    steps = np.arange(1, len(energies) + 1)
    rel_energies = (energies - energies[-1]) * 627.509
    ax1.plot(steps, rel_energies, 'o-', color='#1f77b4')
    ax1.axhline(y=0, color='gray', linestyle='--', alpha=0.5)
    ax1.set_xlabel('Optimization Step')
    ax1.set_ylabel('Relative Energy (kcal/mol)')
    ax1.set_title(f'Optimization Convergence  |  Final E = {energies[-1]:.6f} Hartree')
    ax1.grid(True, alpha=0.3)
    
    # 2. 轨道能级图
    ax2 = fig.add_subplot(gs[1, 0])
    mo_energies = data.moenergies[0] * 27.2114
    homo_idx = data.homos[0]
    occ = mo_energies[:homo_idx+1]
    virt = mo_energies[homo_idx+1:]
    
    for i, e in enumerate(occ):
        ax2.hlines(y=e, xmin=i, xmax=i+0.8, color='#1f77b4', linewidth=2)
    for i, e in enumerate(virt):
        idx = homo_idx + 1 + i
        ax2.hlines(y=e, xmin=idx, xmax=idx+0.8, color='#d62728', linewidth=2)
    
    ax2.set_xlim(-0.5, len(mo_energies) + 0.5)
    ax2.set_ylabel('Energy (eV)')
    ax2.set_title('Molecular Orbital Levels')
    ax2.grid(True, alpha=0.2, axis='y')
    
    # 3. DOS 图
    ax3 = fig.add_subplot(gs[1, 1])
    e_grid, dos = compute_dos(mo_energies, homo_idx)
    ax3.fill_between(e_grid, dos, 0, alpha=0.3, color='#2ca02c')
    ax3.plot(e_grid, dos, color='#2ca02c', linewidth=1.5)
    ax3.axvline(x=mo_energies[homo_idx], color='red', linestyle='--')
    ax3.set_xlabel('Energy (eV)')
    ax3.set_ylabel('DOS')
    ax3.set_title('Density of States')
    ax3.grid(True, alpha=0.2)
    
    # 4. 原子电荷
    ax4 = fig.add_subplot(gs[1, 2])
    if len(charges) > 0:
        colors = ['#1f77b4' if c > 0 else '#d62728' for c in charges]
        ax4.bar(np.arange(len(charges)), charges, color=colors, edgecolor='black')
        ax4.axhline(y=0, color='black', linewidth=0.5)
        ax4.set_xlabel('Atom Index')
        ax4.set_ylabel('Charge (e)')
        ax4.set_title('Mulliken Charges')
        ax4.grid(True, alpha=0.2, axis='y')
    
    # 5. IR 光谱
    ax5 = fig.add_subplot(gs[2, :2])
    if len(freqs) > 0:
        x = np.linspace(0, 4000, 2000)
        y = np.zeros(2000)
        for freq, intens in zip(freqs, irs):
            if intens > 0:
                gamma = 5.0
                y += intens * gamma / (np.pi * ((x - freq) ** 2 + gamma ** 2))
        ax5.plot(x, y, color='#9467bd', linewidth=1.5)
        ax5.fill_between(x, y, alpha=0.3, color='#9467bd')
        ax5.set_xlabel('Wavenumber (cm⁻¹)')
        ax5.set_ylabel('IR Intensity')
        ax5.set_title('IR Spectrum')
        ax5.set_xlim(0, 4000)
        ax5.grid(True, alpha=0.2)
    
    # 6. 信息面板
    ax6 = fig.add_subplot(gs[2, 2])
    ax6.axis('off')
    
    hl_gap = mo_energies[homo_idx+1] - mo_energies[homo_idx] if homo_idx + 1 < len(mo_energies) else 0
    
    info_text = f"""
    Calculation Summary
    {'='*25}
    Method: {data.methods[0] if data.methods else 'DFT'}
    Basis: {data.basis_sets[0][0] if data.basis_sets else 'N/A'}
    
    Final Energy: {energies[-1]:.8f} Ha
    {'='*25}
    HOMO: {mo_energies[homo_idx]:.3f} eV
    LUMO: {mo_energies[homo_idx+1]:.3f} eV
    HL Gap: {hl_gap:.3f} eV
    {'='*25}
    No. Atoms: {data.natom}
    No. Electrons: {data.charge}
    No. Basis Functions: {len(mo_energies)}
    {'='*25}
    File: {filename.split('/')[-1]}
    """
    ax6.text(0.1, 0.5, info_text, fontsize=9, fontfamily='monospace',
             verticalalignment='center',
             bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
    
    plt.savefig(f'{output_prefix}_summary.png', dpi=150, bbox_inches='tight')
    plt.show()
```

---

## 七、实用脚本集合

### 7.1 批量提取 HOMO-LUMO Gap

```python
def batch_extract_hl_gap(pattern="*.log", program='gaussian'):
    """批量提取 HOMO-LUMO Gap"""
    results = []
    for filename in glob.glob(pattern):
        try:
            data = cclib.io.ccread(filename)
            mo_energies = data.moenergies[0]
            homo_idx = data.homos[0]
            gap = (mo_energies[homo_idx + 1] - mo_energies[homo_idx]) * 27.2114
            results.append({
                'file': filename,
                'homo': mo_energies[homo_idx] * 27.2114,
                'lumo': mo_energies[homo_idx + 1] * 27.2114,
                'gap': gap
            })
        except:
            pass
    
    return pd.DataFrame(results)
```

### 7.2 提取热力学数据

```python
def extract_thermochemistry(filename, program='gaussian'):
    """提取热力学数据"""
    data = cclib.io.ccread(filename)
    
    therm = {
        'SCF_energy': data.scfenergies[-1] if data.scfenergies else None,
        'ZPE_Hartree': getattr(data, 'zpe', None),
        'enthalpy': getattr(data, 'enthalpy', None),
        'free_energy': getattr(data, 'freeenergy', None),
        'entropy': getattr(data, 'entropy', None),
    }
    
    # 转换为常用单位
    if therm['SCF_energy']:
        therm['SCF_energy_kcal'] = therm['SCF_energy'] * 627.509
    if therm['ZPE_Hartree']:
        therm['ZPE_kcal'] = therm['ZPE_Hartree'] * 627.509
    
    return therm
```

### 7.3 绘制 NMR 谱

```python
def plot_nmr_spectrum_shifts(shifts, intensities=None, reference=0, 
                               title="NMR Spectrum", filename=None):
    """
    绘制 NMR 谱图
    
    参数:
        shifts: 化学位移值 (ppm)
        intensities: 峰强度（默认均为 1）
        reference: 参考位移
        title: 图标题
        filename: 保存文件名
    """
    shifts = np.array(shifts) - reference
    if intensities is None:
        intensities = np.ones_like(shifts)
    
    fig, ax = plt.subplots(figsize=(10, 4))
    
    # 绘制 NMR 峰
    for shift, intensity in zip(shifts, intensities):
        ax.axvline(x=shift, ymin=0, ymax=intensity/np.max(intensities),
                   color='#1f77b4', linewidth=2)
    
    # 标注位移值
    for shift, intensity in zip(shifts, intensities):
        if intensity > np.max(intensities) * 0.1:
            ax.text(shift, intensity/np.max(intensities) + 0.02,
                    f'{shift:.1f}', fontsize=8, ha='center', rotation=45)
    
    ax.set_xlabel('Chemical Shift (ppm)', fontsize=12)
    ax.set_ylabel('Intensity', fontsize=12)
    ax.set_title(title, fontsize=14)
    ax.invert_xaxis()  # NMR 习惯从大到小
    ax.grid(True, alpha=0.2, axis='x')
    
    plt.tight_layout()
    if filename:
        plt.savefig(filename, dpi=150, bbox_inches='tight')
    plt.show()
```

---

## 八、常见问题与技巧

### 8.1 单位转换 [pdf_76]

| 转换 | 换算因子 |
|------|:--------:|
| 1 Hartree = 627.509 kcal/mol | 能量 |
| 1 Hartree = 27.2114 eV | 能量 |
| 1 Hartree = 2625.5 kJ/mol | 能量 |
| 1 Bohr = 0.529177 Å | 长度 |
| 1 Debye = 0.393430 a.u. | 偶极矩 |
| 1 cm⁻¹ = 0.000123984 eV | 频率→能量 |

### 8.2 常用 Matplotlib 样式设置

```python
import matplotlib.pyplot as plt

# 全局样式设置
plt.rcParams.update({
    'figure.dpi': 150,
    'figure.figsize': (8, 6),
    'font.size': 12,
    'font.family': 'serif',
    'axes.labelsize': 12,
    'axes.titlesize': 14,
    'xtick.labelsize': 10,
    'ytick.labelsize': 10,
    'legend.fontsize': 10,
    'lines.linewidth': 1.5,
    'lines.markersize': 6,
    'grid.alpha': 0.3,
})
```

### 8.3 保存高清图

```python
# 矢量格式（推荐用于论文）
plt.savefig('figure.pdf', dpi=300, bbox_inches='tight')
plt.savefig('figure.svg', dpi=300, bbox_inches='tight')

# 位图格式
plt.savefig('figure.png', dpi=300, bbox_inches='tight')
plt.savefig('figure.tiff', dpi=300, bbox_inches='tight')
```

### 8.4 与 VMD 结合可视化

```python
# 生成 Cube 文件后，可用 VMD 脚本自动渲染
vmd_script = """
set mol [mol new density.cube type cube]
mol addfile homo.cube type cube
mol addfile lumo.cube type cube
mol modstyle 0 0 Isosurface 0.02 0 0.0 1 1
mol modcolor 0 0 ColorID 0
mol modstyle 1 1 Isosurface 0.02 0 0.0 1 1
mol modcolor 1 1 ColorID 1
render TachyonInternal snapshot.png
"""

with open('render.vmd', 'w') as f:
    f.write(vmd_script)

# 运行: vmd -e render.vmd
```

---

## 九、快速参考

### 9.1 核心函数速查

| 函数 | 用途 | 输入 | 输出 |
|------|------|------|------|
| `extract_energy_gaussian` | 提取 Gaussian 能量 | `.log` 文件 | 能量列表 |
| `extract_vibrations_gaussian` | 提取振动频率/IR | `.log` 文件 | 频率、IR 强度 |
| `extract_mulliken_charges_gaussian` | 提取 Mulliken 电荷 | `.log` 文件 | 电荷列表 |
| `extract_energy_orca` | 提取 ORCA 能量 | `.out` 文件 | 能量列表 |
| `plot_optimization_convergence` | 绘图：优化收敛 | 能量/坐标 | 四子图 |
| `plot_orbital_energy_level` | 绘图：轨道能级 | 轨道能量/HOMO | 能级图 |
| `compute_dos` | 计算 DOS | 轨道能量 | 能量网格 + DOS 值 |
| `plot_ir_spectrum` | 绘图：IR 光谱 | 频率/强度 | 光谱图 |
| `plot_uv_vis_spectrum` | 绘图：UV-Vis 光谱 | 波长/振子强度 | 光谱图 |
| `plot_scan_results` | 绘图：势能面扫描 | 坐标/能量 | 扫描曲线 |
| `plot_charge_distribution` | 绘图：原子电荷 | 电荷/标签 | 柱状图 |
| `generate_dft_report` | 综合报告 | 输出文件 | 多子图报告 |
| `batch_analyze_gaussian_results` | 批量分析 | 文件模式 | DataFrame |

### 9.2 数据来源对照

| 数据来源 | 解析方式 | 推荐函数 |
|----------|----------|----------|
| Gaussian `.log` / `.out` | cclib / 正则表达式 | `cclib.io.ccread` / `extract_energy_gaussian` |
| ORCA `.out` | cclib / 正则表达式 | `cclib.io.ccread` / `extract_energy_orca` |
| Gaussian `.fch` / `.fchk` | cclib / Multiwfn | `cclib.io.ccread` |
| Cube 文件 `.cub` | 自定义解析 | `read_cube` |
| XYZ 文件 `.xyz` | ASE / 自定义 | `ase.io.read` |
| VASP `OUTCAR` | pymatgen / cclib | `pymatgen.io.vasp.outputs.Outcar` |
| VASP `vasprun.xml` | pymatgen | `pymatgen.io.vasp.outputs.Vasprun` |

---

## 十、参考文献

- Python for Quantum Chemistry: A Full-stack Programming Guide（pdf_86）
- 用 Python 做科学计算（pdf_85）
- Gaussian 09 中文使用手册（pdf_74）
- 额外赠送高斯量子化学手册（pdf_76）
- 关于高斯入门的小小指南 V1.2（pdf_75）
- cclib 官方文档：https://cclib.github.io
- ASE 官方文档：https://wiki.fysik.dtu.dk/ase
- PySCF 官方文档：https://pyscf.org
- Matplotlib 官方文档：https://matplotlib.org