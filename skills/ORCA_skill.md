# ORCA 使用技能手册（完整指令版）

> 本文档基于 ORCA 6.1 官方手册及 ORCA Input Library 整理，涵盖安装配置、输入文件语法、**全部方法模块（第3章～第9章）** 的关键词指令与操作示例。

---

## 一、ORCA 简介

**ORCA** 是由 Frank Neese 于 1999 年发起的通用量子化学程序包，由 Max-Planck-Institut für Kohlenforschung 的分子理论与光谱学部门主导开发 [pdf_14]。ORCA 免费供学术使用，涵盖：

- 半经验方法、DFT、多体微扰理论、耦合簇、多参考方法
- 几何优化、过渡态搜索、IRC、势能面扫描
- 激发态计算（TDDFT、CIS、EOM-CCSD、CASSCF 等）
- 光谱与性质（IR、Raman、UV-Vis、ECD、NMR、EPR、Mössbauer 等）
- QM/MM 多尺度模拟、从头算分子动力学
- 能量分解分析（EDA-NOCV、LED）
- 工作流自动化（OPI Python 接口、Compound）

- **官方网站**：https://orcaforum.kofo.mpg.de [pdf_14]
- **引用**：使用 ORCA 时需引用对应版本的通用文献及所用具体方法的关联文献 [pdf_14]

---

## 二、安装与配置

### 2.1 获取 ORCA [pdf_53]

从官方站点获取对应系统的压缩包：
- 解压到指定目录（如 `/opt/orca/`）
- ORCA **无图形界面**，仅通过命令行运行

### 2.2 环境变量设置 [pdf_53]

**Linux/Mac**（在 `~/.bashrc` 中添加）：
```bash
# ORCA 路径
export PATH=/path/to/orca:$PATH
export LD_LIBRARY_PATH=/path/to/orca:$LD_LIBRARY_PATH

# OpenMPI 路径（并行需要）
export PATH=/path/to/openmpi/bin:$PATH
export LD_LIBRARY_PATH=/path/to/openmpi/lib:$LD_LIBRARY_PATH
```

**Windows**（在系统环境变量中添加）：
- 将 ORCA 目录加入 `PATH`
- 建议使用命令行运行（cmd 或 PowerShell）

### 2.3 运行 ORCA [pdf_53]

```bash
# 串行运行
orca input.inp > output.out

# 后台运行（Linux/Mac）
orca input.inp > output.out &

# 后台运行（Windows）
start /b orca input.inp > output.out

# 并行运行（使用完整路径！）
/full/path/to/orca/orca input.inp > output.out
```

> **重要**：并行时**不要**使用 `mpirun -np 4 orca`，ORCA 会自动管理 MPI 通信，只需在输入文件中通过 `!PalX` 或 `%pal` 块指定进程数 [pdf_53]。

### 2.4 并行配置 [pdf_53]

```orca
! BP86 def2-SVP def2-SVP/J Opt PAL4

# 或使用块输入：
%pal
  nprocs 4
end
```

### 2.5 PBS 作业提交脚本示例 [pdf_53]

```bash
#!/bin/bash
#PBS -l nodes=1:ppn=8
#PBS -q short

export PATH=$PATH:/opt/openmpi/bin
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/opt/openmpi/lib
export orcadir=/data/users/user/orca_6_1_linux

# 创建本地临时目录
tdir=$(mktemp -d /scratch/$USER/orcajob__$PBS_JOBID-XXXX)
cp $PBS_O_WORKDIR/*.inp $tdir/
cp $PBS_O_WORKDIR/*.gbw $tdir/

cd $tdir
$orcadir/orca $tdir/$job.inp >> $PBS_O_WORKDIR/$job.out

# 复制结果文件
cp $tdir/*.gbw $PBS_O_WORKDIR
cp $tdir/*.xyz $PBS_O_WORKDIR
```

---

## 三、输入文件结构

### 3.1 输入文件语法 [pdf_54]

ORCA 输入文件有两种语法：

**Simple Input（简单关键词行）**：
```orca
! B3LYP def2-TZVP D3BJ Opt TightSCF
```

**Block Input（块输入，优先级更高）**：
```orca
%scf
  MaxIter 500
  TolE 1e-8
end
```

**基本规则** [pdf_54]：
- 不区分大小写
- `#` 开头为注释行
- 空行允许
- 块输入中的设置优先级高于简单关键词

### 3.2 最简输入文件 [pdf_14]

```orca
! HF DEF2-SVP

*xyz 0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
*
```

**必须指定的三要素** [pdf_14]：
1. **几何结构**（坐标块或外部文件）
2. **方法**（如 HF、B3LYP、RI-MP2）
3. **基组**（如 def2-SVP、cc-pVTZ）

### 3.3 坐标输入 [pdf_55]

**XYZ 笛卡尔坐标（默认，Å）**：
```orca
*xyz 0 1
C    0.0000    0.0000    0.0000
O    1.2500    0.0000    0.0000
*
```

**内部坐标**：
```orca
*int 0 1
C    0 0 0    0.0000    0.0000    0.0000
O    1 0 0    1.2500    0.0000    0.0000
H    1 2 0    1.1075  122.016    0.0000
H    1 2 3    1.1075  122.016  180.0000
*
```

**从外部文件读取**：
```orca
*xyzfile 0 1 molecule.xyz
```

**点电荷** [pdf_55]：
```orca
*xyz 0 1
O    0.0000   -0.0059    0.0000
H    0.7660    0.5880    0.0000
H   -0.7660    0.5880    0.0000
Q   -0.834    1.3742   -2.1459    0.0000   # 电荷 -0.834
Q    0.417    2.3280   -1.9740    0.0000   # 电荷 0.417
*
```

> **注意**：ORCA 中原子和轨道编号**从 0 开始** [pdf_55]。

### 3.4 输入块总览 [pdf_54]

| 块名称 | 用途 |
|--------|------|
| `%basis` | 基组设置 |
| `%casscf` | CASSCF/NEVPT2/DMRG 控制 |
| `%cis` / `%tddft` | CIS/TD-DFT 控制 |
| `%coords` | 原子坐标输入 |
| `%cosmo` | COSMO 溶剂模型 |
| `%elprop` | 电性质计算 |
| `%epnmr` | EPR/NMR 计算 |
| `%freq` | 频率计算 |
| `%geom` | 几何优化控制 |
| `%md` | 分子动力学 |
| `%loc` | 轨道定域化 |
| `%mdci` | 单参考相关方法 |
| `%method` | 计算方法指定 |
| `%mp2` | MP2 计算细节 |
| `%mrci` | MRCI 计算控制 |
| `%output` | 输出控制 |
| `%pal` | 并行控制 |
| `%paras` | 半经验参数 |
| `%plots` | 绘图生成 |
| `%rel` | 相对论选项 |
| `%rr` | 共振 Raman 和光谱形状 |
| `%scf` | SCF 过程控制 |

### 3.5 任务类型关键词 [pdf_54]

| 关键词 | 任务类型 |
|--------|----------|
| `SP` 或 `Energy` | 单点能（默认，可省略） |
| `Opt` | 几何优化 |
| `OptTS` | 过渡态搜索 |
| `Freq` | 频率计算 |
| `NumFreq` | 数值频率 |
| `Scan` | 势能面扫描 |
| `IRC` | 内禀反应坐标 |
| `MD` | 分子动力学 |
| `OptTS` | 鞍点搜索 |

### 3.6 输出文件 [pdf_14]

| 文件 | 说明 |
|------|------|
| `*.out` | 输出文件 |
| `*.gbw` | 二进制波函数文件（Geometry-Basis-Wavefunction） |
| `*.tmp` | 临时文件（正常退出后自动删除） |
| `*.cis` | TDDFT/CIS 结果文件 |
| `*.hess` | Hessian 文件 |
| `*.xyz` | 优化后的坐标 |
| `*.engrad` | 能量梯度 |

---

## 四、模型化学方法（第3章）

### 4.1 波函数类型 [pdf_14]

| 关键词 | 说明 |
|--------|------|
| `RHF` / `RKS` | 限制性闭壳层 HF/DFT |
| `UHF` / `UKS` | 非限制性开壳层 HF/DFT |
| `ROHF` / `ROKS` | 限制性开壳层 HF/DFT |
| `CASSCF(n,m)` | 完全活性空间 SCF |

### 4.2 HF 理论（3.2）

```orca
! HF def2-TZVP TightSCF
! RHF def2-TZVP         # 闭壳层
! UHF def2-TZVP         # 开壳层
! ROHF def2-TZVP        # 限制性开壳层
```

### 4.3 DFT 计算（3.3）[pdf_59]

**常用泛函**：
```orca
! B3LYP def2-TZVP D3BJ TightSCF
! BP86 def2-SVP def2-SVP/J Opt
! PBE0 def2-TZVP RIJCOSX
! CAM-B3LYP def2-TZVP         # 长程校正泛函
! M06-2X def2-TZVP            # 明尼苏达泛函
! TPSS def2-TZVP              # meta-GGA
! wB97X-D3 def2-TZVP          # 长程校正+色散
```

**B3LYP 风格** [pdf_57]：
```orca
! B3LYP def2-TZVP Opt D3BJ          # ORCA 风格（VWN5）
! B3LYP/G def2-TZVP Opt D3BJ        # Gaussian 风格（VWN3）
```

**色散校正** [pdf_59]：
```orca
! B3LYP D3ZERO def2-TZVP      # DFT-D3 原始阻尼
! B3LYP D3BJ def2-TZVP         # DFT-D3 Becke-Johnson 阻尼（推荐）
! B3LYP D4 def2-TZVP           # DFT-D4（ORCA 6.1 支持）
```

**非迭代 DFT**（使用其他方法轨道）[pdf_59]：
```orca
! HF def2-TZVP
%base "hf"
*xyz 0 1
coordinates
*
$new_job
! BP86 def2-TZVP MOREAD
%base "dft"
%moinp "hf.gbw"
%scf
  maxiter 1
end
*xyzfile 0 1 coordinates.xyz
```

### 4.4 双杂化 DFT（3.3 / 3.6）[pdf_66]

```orca
! B2-PLYP def2-TZVP def2-TZVP/C RI-MP2   # 原始 B2-PLYP
! B2GP-PLYP def2-TZVP def2-TZVP/C          # B2GP-PLYP
! DSD-PBEP86 def2-TZVP def2-TZVP/C         # 自旋标度双杂化
! PBE0-DH def2-TZVP def2-TZVP/C            # PBE0 双杂化
! RI-B2-PLYP RIJCOSX def2-TZVP def2-TZVP/C def2-TZVP/J
```

> **注意**：双杂化泛函对基组依赖性大，常需四 zeta 基组 [pdf_66]。推荐先用 GGA/hybrid-GGA 优化几何，再用双杂化做单点能。

### 4.5 半经验方法（3.5）[pdf_59]

```orca
! PM3                           # PM3 半经验
! AM1                           # AM1 半经验
! PM6                           # PM6 半经验
! PM7                           # PM7 半经验
! HF-3c                         # HF-3c 复合方法
! PBEh-3c                       # PBEh-3c 复合方法
! B97-3c                        # B97-3c 复合方法
! r2SCAN-3c                     # r2SCAN-3c 复合方法
```

### 4.6 MP2 及相关方法（3.9）[pdf_64]

**标准 MP2**：
```orca
! MP2 cc-pVTZ TightSCF
! RI-MP2 cc-pVTZ cc-pVTZ/C TightSCF                     # 推荐
! RI-MP2 RIJK cc-pVTZ cc-pVTZ/C cc-pVTZ/JK TightSCF     # RIJK 加速
! RI-MP2 RIJCOSX cc-pVTZ cc-pVTZ/C cc-pVTZ/J TightSCF   # RIJCOSX 加速
```

**SCS-MP2 与 OO-MP2**：
```orca
! RI-SCS-MP2 cc-pVTZ cc-pVTZ/C TightSCF                 # 自旋分量标度
! OO-RI-MP2 cc-pVTZ cc-pVTZ/C TightSCF                  # 轨道优化
! OO-RI-SCS-MP2 cc-pVTZ cc-pVTZ/C TightSCF
```

**MP2-F12**：
```orca
! RHF MP2-F12 cc-pVTZ-F12 cc-pVTZ-F12-CABS TightSCF
! RHF RI-MP2-F12 cc-pVTZ-F12 cc-pVTZ-F12-CABS cc-pVQZ/C TightSCF
! RHF RIJK RI-MP2-F12 cc-pVTZ-F12 cc-pVTZ-F12-CABS cc-pVTZ/JK cc-pVTZ/C TightSCF
```

**MP3**：
```orca
! MP3 cc-pVTZ TightSCF
! SCS-MP3 cc-pVTZ TightSCF
! SCS-MP3 RIJCOSX cc-pVTZ cc-pVTZ/J TightSCF
```

### 4.7 耦合簇方法（3.10）[pdf_14]

```orca
! CCSD(T) cc-pVTZ TightSCF                          # 标准 CCSD(T)
! DLPNO-CCSD(T) cc-pVTZ cc-pVTZ/C TightSCF          # 域近似 CCSD(T)
! DLPNO-CCSD(T) cc-pVTZ cc-pVTZ/C TightSCF TightPNO # 高精度 DLPNO
! DLPNO-CCSD(T1) cc-pVTZ cc-pVTZ/C TightSCF         # 迭代 triples 近似
! DLPNO-CCSD cc-pVTZ cc-pVTZ/C TightSCF             # 无 triples 校正
```

### 4.8 CASSCF 方法（3.13）[pdf_65]

**基本输入**：
```orca
! RHF def2-SVP
%casscf
  nel  4       # 活性电子数
  norb 3       # 活性轨道数
  mult 1       # 自旋多重度
end
```

**态平均 CASSCF**（SA-CASSCF）：
```orca
! RHF def2-SVP
%casscf
  nel    4
  norb   3
  mult   1,3          # 多个多重度
  nroots 2,2          # 每个多重度对应的根数
  # 权重默认等权，可手动设置：
  # weight [1.0, 1.0, 1.0, 1.0]
end
```

**CASSCF 激发态**：
```orca
%casscf
  nel    4
  norb   3
  mult   1
  nroots 5            # 计算 5 个根（态）
end
```

**NEVPT2 校正**：
```orca
%casscf
  nel    4
  norb   3
  mult   1
  nroots 5
  nevpt2 true         # 开启 NEVPT2 校正
end
```

**CASSCF 性质计算**：
```orca
%casscf
  nel    4
  norb   3
  mult   1
  nroots 5
  rel    dosoc         # 开启自旋-轨道耦合
end
```

### 4.9 DMRG（3.15）

```orca
%casscf
  nel    20
  norb   20
  mult   1
  nroots 1
  dmrg   true
  DMRG_StartM 250     # 初始 bond 维度
  DMRG_EndM   1000    # 最终 bond 维度
  DMRG_NSweeps 10     # 扫描次数
end
```

### 4.10 MRCI（3.19）

```orca
%mrci
  mult 1
  nroots 3
  tsel 1e-8           # 选择阈值
  csel 1e-6
end
```

### 4.11 分子力学（3.23）

```orca
! MM
%mm
  UFF                  # 或 GAFF、MMFF94
end
```

---

## 五、结构与反应性（第4章）

### 5.1 几何优化（4.1）[pdf_57]

**基础优化**：
```orca
! B3LYP def2-TZVP Opt D3BJ TightSCF
! BP86 def2-SVP def2-SVP/J Opt D3BJ
! RI BP86 def2-SVP def2-SVP/J D3BJ TightSCF Opt Grid3 FinalGrid5
```

**精确优化控制**：
```orca
%geom
  Calc_Hess true     # 第一步计算精确 Hessian
  Recalc_Hess 10     # 每 10 步重新计算
  TolE     1e-6      # 能量收敛阈值
  TolRMSG  3e-5      # 均方根梯度
  TolMaxG  1e-4      # 最大梯度
  TolRMSD  6e-4      # 均方根位移
  TolMaxD  1e-3      # 最大位移
end
```

**TIGHTOPT 等价于**：
```orca
! ... TightOpt
```

**读取旧 Hessian**：
```orca
%geom
  InHess Read
  InHessName "previous.hess"
end
```

**混合 Hessian**（仅部分原子计算 Hessian）：
```orca
%geom
  Hybrid_Hess true
  Hess2nd_Switch 5   # 5 个原子在第二层
end
```

**RIJCOSX 优化 + 无近似精修** [pdf_57]：
```orca
! B3LYP RIJCOSX D3BJ def2-TZVP def2-TZVP/J TightSCF Opt
%base "rijcosx"
*xyz 0 1
coordinates
*
$new_job
! NORI B3LYP D3BJ def2-TZVP TightSCF Opt MOREAD
%base "normal"
%moinp "rijcosx.gbw"
*xyzfile 0 1 coordinates.xyz
```

### 5.2 势能面扫描（4.2）[pdf_57]

**松弛扫描**（relaxed scan）：
```orca
! PM3 Opt
%geom
  Scan
    B 5 0 = 3.0, 1.2, 15   # 扫描原子 5 和 0 间的距离从 3.0 到 1.2 Å，15 步
  end
end
*xyz -1 1
  coordinates
*
```

**柔性扫描**（flexible scan，使用 `{var}` 变量）：
```orca
! BP86 def2-SVP def2-SVP/J
%paras
  Alpha = 0, 180, 18   # 从 0 到 180，18 步
end
*int 0 1
C 0 0 0 0 0 0
C 1 0 0 1.3385 0 0
H 1 2 0 1.07 120 0
H 1 2 3 1.07 120 180
H 2 1 3 1.07 120 {Alpha}
H 2 1 3 1.07 120 {Alpha+180}
*
```

### 5.3 过渡态搜索（4.3）[pdf_61]

**步骤 1：松弛扫描获取初始结构**：
```orca
! PM3 Opt
%geom
  Scan
    B 5 0 = 3.0, 1.2, 15
  end
end
*xyz -1 1
...
```

**步骤 2：近似 Hessian 的 TS 搜索**：
```orca
! PM3 OptTS
%geom
  Calc_Hess true
  NumHess  true
end
*xyz -1 1
  (从扫描结果中取能量最高的结构)
*
```

**步骤 3：精确 Hessian 的 TS 搜索**：
```orca
! PM3 OptTS
%geom
  InHess Read
  InHessName "pm3_ts_approx.hess"
end
*xyz -1 1
  (从步骤 2 的结果中取结构)
*
```

### 5.4 IRC 计算（4.4）[pdf_62]

```orca
! PM3 IRC
%geom
  IRC_direction Forward   # 或 Both
  IRC_StepSize 0.1        # 步长 (amu¹/²·Bohr)
  IRC_MaxIter 100
end
*xyz -1 1
  (TS 结构)
*
```

### 5.5 NEB 方法（4.5）

```orca
! B3LYP def2-SVP Opt NEB
%neb
  NEB_Images 7           # 中间镜像数
  NEB_Spring 0.1         # 弹簧常数
end
```

### 5.6 频率计算（4.6）[pdf_57]

```orca
! B3LYP def2-TZVP Freq D3BJ TightSCF
! BP86 def2-SVP Freq            # 解析频率
! BP86 def2-SVP NumFreq          # 数值频率（meta-GGA 等）
```

### 5.7 热化学（4.7）

频率计算完成后，ORCA 自动输出热化学数据（ZPE、焓、熵、Gibbs 自由能）。

### 5.8 圆锥交叉与 MECP（4.8 / 4.9）

```orca
! B3LYP def2-SVP Opt MECP
%mecp
  State1 1   # 第一态（根索引）
  State2 2   # 第二态（根索引）
end
```

### 5.9 GOAT 全局优化（4.10）

```orca
! B3LYP def2-SVP GOAT
%goat
  PopSize 50
  MaxGen 100
end
```

### 5.10 SOLVATOR 自动显式溶剂化（4.11）

```orca
! B3LYP def2-SVP SOLVATOR
%solvator
  Solvent "water"
  Nmolecules 100
end
```

---

## 六、光谱与性质（第5章）

### 6.1 布居分析（5.1）[pdf_14]

```orca
! B3LYP def2-TZVP
%output
  Print[P_Mulliken] 1    # Mulliken 布居
  Print[P_LOEWDIN]  1    # Löwdin 布居
end
```

### 6.2 NBO 分析（5.2）

```orca
! B3LYP def2-TZVP
%nbo
  NBO true
end
```

### 6.3 FOD 分析（5.3）

```orca
! TPSS def2-TZVP FOD
```

### 6.4 TDDFT 激发态计算（5.4 / 5.6）[pdf_72]

**GGA 泛函 TDDFT（自动 TDA）**：
```orca
! BP86 def2-SVP def2-SVP/J Grid5 FinalGrid6 TightSCF
%maxcore 1000
%tddft
  nroots 150
  maxdim 700
end
```

**GGA 泛函 Full-TDDFT（非 TDA）**：
```orca
%tddft
  nroots 150
  maxdim 700
  tda false
end
```

**杂化泛函 TDDFT（推荐 RIJCOSX）**：
```orca
! B3LYP def2-TZVP def2-TZVP/J RIJCOSX Grid5 FinalGrid6 TightSCF
%maxcore 1000
%tddft
  maxdim 300
  nroots 50
end
```

**NTO 分析**：
```orca
%tddft
  nroots 50
  maxdim 300
  DoNTO true
  NTOStates 1, 2, 3
end
```

**TDDFT 激发态几何优化**：
```orca
! BP86 def2-SVP TightSCF Opt Keepdens
%tddft
  Nroots 2
  MaxDim 20
  IRoot 2        # 优化第 2 个激发态
end
```

### 6.5 ROCIS / ROCIS-DFT 光谱（5.7）

```orca
! B3LYP def2-TZVP RIJCOSX
%rocis
  NRoots 10
  DoTDA true
end
```

### 6.6 EOM-CCSD 激发态（5.9）

```orca
! DLPNO-CCSD def2-TZVP def2-TZVP/C
%eom
  NRoots 5
  DoEOM true
end
```

### 6.7 STEOM-CCSD 激发态（5.10）

```orca
! DLPNO-STEOM-CCSD def2-TZVP def2-TZVP/C
%steom
  NRoots 10
  NSingles 5
  NTriples 5
end
```

### 6.8 光谱模拟与绘制（5.14）

```orca
! B3LYP def2-TZVP RIJCOSX TightSCF
%tddft
  nroots 30
  DoSpec true          # 生成光谱数据
  DoCDspec true        # 生成 CD 光谱
end
```

### 6.9 振动光谱（5.18）

```orca
! B3LYP def2-TZVP Freq D3BJ
# 输出包含 IR 强度
```

**Raman 光谱**：
```orca
! B3LYP def2-TZVP Freq Raman D3BJ
```

**共振 Raman**（需 `orca_asa`）：
```orca
%rr
  Excitation 1
  Homodecouple true
end
```

### 6.10 VPT2 非谐分析（5.19）

```orca
! B3LYP def2-TZVP Freq D3BJ
%vpt2
  Anharm true
end
```

### 6.11 电性质（5.20）[pdf_70]

```orca
! B3LYP def2-TZVP
%elprop
  Dipole true          # 偶极矩
  Quadrupole true      # 四极矩
  Polar 1              # 极化率（CP-SCF）
  Polar 2              # 超极化率
end
```

### 6.12 NMR 参数（5.21）[pdf_70]

```orca
! B3LYP pcSseg-2 TightSCF
%epnmr
  NMR true
  Shieldings true      # 化学位移
  SpinSpin true        # 自旋-自旋耦合常数 J
end
```

### 6.13 EPR 参数（5.24）[pdf_70]

```orca
! UKS BP86 EPR-III TightSCF Grid5 FinalGrid6
%epnmr
  GTensor true         # g 张量
  HFC true             # 超精细耦合
  ZFS true             # 零场分裂
end
```

### 6.14 Mössbauer 参数（5.27）

```orca
! B3LYF CP(PPP) TightSCF
%epnmr
  Mössbauer true
  Quadrupole true
end
```

### 6.15 自旋-轨道耦合（5.28）

```orca
%rel
  SOC true             # 开启 SOC
  SOCType 1            # 1=SI-SOC, 2=RI-SOC
end
```

### 6.16 破缺态与交换耦合（5.29）

```orca
! UKS B3LYP def2-TZVP
%scf
  BrokenSym 1, 1       # 两个片段各 1 个未配对电子
  FlipSpin true        # 翻转自旋
end
```

### 6.17 交换耦合分解（5.30）

```orca
! UKS B3LYP def2-TZVP
%scf
  BrokenSym 1, 1
  FlipSpin true
end
%exchange
  Anderson true
end
```

### 6.18 ETS-NOCV / EDA-NOCV（5.37）

```orca
! B3LYP def2-TZVP
%ets
  Fragment 1 "frag1.gbw"
  Fragment 2 "frag2.gbw"
end
```

### 6.19 LED 局域能量分解（5.38）

```orca
! DLPNO-CCSD(T) def2-TZVP def2-TZVP/C
%mdci
  LED true
end
```

### 6.20 HFLD 方法（5.39）

```orca
! HFLD def2-TZVP
```

### 6.21 ADLD 原子色散分解（5.40）

```orca
! B3LYP D3BJ def2-TZVP ADLD
```

---

## 七、输入文件完整示例

### 7.1 水分子单点能 [pdf_14]
```orca
! HF DEF2-SVP
*xyz 0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
*
```

### 7.2 DFT 几何优化 [pdf_57]
```orca
! B3LYP def2-TZVP D3BJ Opt TightSCF
*xyz 0 1
C    0.0000    0.0000    0.0000
O    1.2000    0.0000    0.0000
H    0.0000    0.0000    1.0800
H    0.0000    1.0800    0.0000
*
```

### 7.3 DLPNO-CCSD(T) 单点能
```orca
! DLPNO-CCSD(T) cc-pVTZ cc-pVTZ/C TightSCF
*xyz 0 1
O    0.0000    0.0000    0.1200
H    0.0000    0.7500   -0.4800
H    0.0000   -0.7500   -0.4800
*
```

### 7.4 TDDFT 激发态计算 [pdf_72]
```orca
! B3LYP def2-TZVP def2-TZVP/J RIJCOSX Grid5 FinalGrid6 TightSCF
%maxcore 1000
%tddft
  nroots 20
  maxdim 200
  DoNTO true
  NTOStates 1,2,3
end
*xyz 0 1
C    0.0000    0.0000    0.0000
O    1.2000    0.0000    0.0000
*
```

### 7.5 CASSCF 计算 [pdf_65]
```orca
! RHF def2-SVP
%casscf
  nel    6
  norb   6
  mult   1
  nroots 3
  nevpt2 true
end
*xyz 0 1
C    0.0000    0.0000    0.0000
C    0.0000    0.0000    1.3500
H    0.0000    1.0200    0.6000
H    0.0000   -1.0200    0.6000
*
```

### 7.6 NMR 计算 [pdf_70]
```orca
! B3LYP pcSseg-2 TightSCF
%epnmr
  NMR true
  Shieldings true
  SpinSpin true
end
*xyz 0 1
C    0.0000    0.0000    0.0000
O    1.2000    0.0000    0.0000
*
```

### 7.7 COSMO 溶剂模型 [pdf_63]
```orca
! B3LYP def2-TZVP Opt COSMO(water)
%cosmo
  epsilon 78.4         # 水的介电常数
  Refrac 1.33          # 溶剂折射率
end
*xyz 0 1
coordinates
*
```

### 7.8 SMD 溶剂模型 [pdf_63]
```orca
! B3LYP def2-TZVP Opt SMD(water)
*xyz 0 1
coordinates
*
```

### 7.9 相对论计算（ZORA）[pdf_69]
```orca
! B3LYP ZORA-def2-TZVP SARC/J TightSCF
%rel
  ZORA true
end
*xyz 0 1
coordinates
*
```

### 7.10 相对论计算（DKH2）
```orca
! B3LYP DKH-def2-TZVP TightSCF
%rel
  DKH true
  DKH_order 2
end
*xyz 0 1
coordinates
*
```

### 7.11 相对论计算（X2C）
```orca
! B3LYP x2c-TZVPall TightSCF
%rel
  X2C true
end
*xyz 0 1
coordinates
*
```

### 7.12 多步作业（$new_job）[pdf_59]
```orca
! B3LYP def2-TZVP Opt
%base "opt"
*xyz 0 1
coordinates
*
$new_job
! B3LYP def2-TZVP Freq
%base "freq"
%moinp "opt.gbw"
*xyzfile 0 1 opt.xyz
```

### 7.13 破缺态 DFT [pdf_67]
```orca
! UKS B3LYP def2-TZVP
%scf
  BrokenSym 5, 1       # 片段1有5个α电子，片段2有1个α电子
  FlipSpin true
end
*xyz 0 1
coordinates
*
```

### 7.14 EPR 参数计算 [pdf_70]
```orca
! UKS BP86 EPR-III TightSCF Grid5 FinalGrid6
%epnmr
  GTensor true
  HFC true
  ZFS true
end
*xyz 0 1
coordinates
*
```

### 7.15 极化率计算 [pdf_70]
```orca
! B3LYP def2-TZVP TightSCF
%elprop
  Polar 1              # 极化率
  Polar 2              # 超极化率（可选，贵）
end
*xyz 0 1
coordinates
*
```

---

## 八、RI 近似与辅助基组

### 8.1 RI 近似类型 [pdf_71]

| 关键词 | 说明 | 所需辅助基组 |
|--------|------|-------------|
| `RI-J` | 仅 Coulomb 积分（纯 GGA 常用） | AuxJ（如 def2/J） |
| `RIJONX` | RI-J + 无近似 Exchange | AuxJ |
| `RIJK` | Coulomb + Exchange 均 RI | AuxJK（如 def2/JK） |
| `RIJCOSX` | RI-J + COSX 数值 Exchange | AuxJ |
| `RI-MP2` | MP2 相关积分 RI | AuxC（如 def2/C） |

### 8.2 辅助基组命名规则 [pdf_71]

| 类型 | 示例 | 说明 |
|------|------|------|
| AuxJ | `def2/J`、`cc-pVTZ/J` | Coulomb 拟合 | 
| AuxJK | `def2/JK`、`cc-pVTZ/JK` | Coulomb+Exchange 拟合 |
| AuxC | `def2/C`、`cc-pVTZ/C` | MP2 相关拟合 |
| AuxCABS | `cc-pVTZ-F12-CABS` | F12 辅助基组 |

### 8.3 推荐组合

```orca
# GGA DFT 优化（最快）
! RI BP86 def2-SVP def2-SVP/J D3BJ Opt

# 杂化 DFT 单点（大体系推荐）
! B3LYP RIJCOSX def2-TZVP def2-TZVP/J D3BJ

# 杂化 DFT 单点（小体系高精度）
! B3LYP RIJK def2-TZVP def2-TZVP/JK D3BJ

# DLPNO-CCSD(T) 单点
! DLPNO-CCSD(T) def2-TZVP def2-TZVP/C
```

---

## 九、实用工具与可视化

### 9.1 orca_plot [pdf_72]

绘图工具，用于生成轨道、密度、差分密度等 Cube 文件。

```bash
orca_plot job.gbw -i
```

**交互菜单**：
1. 选择输出格式（Cube 等）
2. 设置网格间隔（如 80）
3. 选择绘制类型（轨道、密度、差分密度、跃迁密度等）
4. 输入所需状态/轨道索引
5. 生成 `.cube` 文件（可用 VMD 或 Chemcraft 可视化）

**TDDFT 差分密度示例**：
```bash
orca_plot job.gbw -i
# 选择格式 → 设置网格 → 选选项 7 → 输入 job.cis 文件 → 选择态
```

### 9.2 orca_mapspc [pdf_72]

光谱绘制工具：
```bash
orca_mapspc job.out -eV -x500 -o spectrum.dat
```

### 9.3 orca_vpt2

非谐振动分析：
```bash
orca_vpt2 job.out
```

### 9.4 orca_2json

将输出文件转为 JSON 格式 [pdf_14]：
```bash
orca_2json job.out > job.json
```

### 9.5 其他实用程序

| 程序 | 用途 |
|------|------|
| `orca_2mkl` | 转换为 Molden 格式 |
| `orca_asa` | 共振 Raman 与光谱形状模拟 |
| `orca_hess2grad` | Hessian 转梯度 |
| `orca_thermo` | 热化学分析 |

---

## 十、ORCA Python 接口（OPI）（第8章）

### 8.1 OPI 基础

```python
from orca import *

# 创建计算
calc = orca_calc("input.inp")

# 运行
calc.run()

# 读取结果
energy = calc.get_energy()
```

### 8.2 Compound 工作流

```python
from orca_compound import *

# 定义化合物
mol = Compound("water")
mol.add_atom("O", 0.0, 0.0, 0.12)
mol.add_atom("H", 0.0, 0.75, -0.48)
mol.add_atom("H", 0.0, -0.75, -0.48)

# 设置与运行
mol.set_method("B3LYP/def2-TZVP")
mol.optimize()
mol.frequency()
```

---

## 十一、SCF 收敛问题处理

### 11.1 常用收敛策略 [pdf_14]

```orca
%scf
  MaxIter 500               # 增加最大迭代次数
  TolE 1e-8                 # 能量收敛阈值
  TolRMSD 1e-7              # 均方根密度变化
  SOSCF                     # 使用 SOSCF 方法
    Start 0.002
    Switch 0.0001
  end
  DIIS
    MaxEq 15                # DIIS 最大方程数
  end
  Guess PModel              # 初始猜测模式
  MOInp "previous.gbw"      # 从旧文件读取轨道
end
```

### 11.2 初始猜测 [pdf_14]

```orca
%scf
  Guess PModel              # P-Model 猜测（默认）
  Guess HCore               # 芯哈密顿猜测
  MOInp "old.gbw"           # 从 gbw 文件读取轨道
end
```

### 11.3 常见错误 [pdf_14]

| 错误信息 | 解决方案 |
|----------|---------|
| `Missing geometry` | 未提供坐标块 |
| `RI-MP2 needs an AuxC basis` | 缺少 RI-MP2 辅助基组 |
| `Unknown identifier in X block` | 检查块输入是否忘记 `end` |
| SCF 不收敛 | 增大 MaxIter、使用 SOSCF、换初始猜测 |

---

## 十二、settings.ini / 关键参数速查

| 参数 | 设置位置 | 说明 |
|------|----------|------|
| `MaxCore` | 输入文件 | 每个核心的最大内存（MB） |
| `MaxIter` | `%scf` | 最大 SCF 迭代次数 |
| `TolE` | `%scf` | SCF 能量收敛阈值 |
| `TolRMSD` | `%scf` | SCF 密度收敛阈值 |
| `nprocs` | `%pal` | 并行进程数 |
| `Grid` / `FinalGrid` | 简单关键词 | DFT 网格精度 |
| `TightSCF` | 简单关键词 | 提高 SCF 收敛精度 |
| `TightOpt` | 简单关键词 | 提高优化收敛标准 |
| `TightPNO` | 简单关键词 | 提高 DLPNO 精度 |
| `NormalPNO` | 简单关键词 | DLPNO 默认精度 |
| `LoosePNO` | 简单关键词 | 降低 DLPNO 精度（快速） |
| `NoFrozenCore` | 简单关键词 | 关闭冻核近似 |
| `MOREAD` | 简单关键词 | 读取已有轨道 |
| `KeepDens` | TDDFT 块 | 保存激发态密度 |
| `Calc_Hess` | `%geom` | 计算精确 Hessian |
| `NumHess` | `%geom` | 数值 Hessian |
| `InHess` | `%geom` | 读取已有 Hessian |

---

## 十三、实用技巧

### 13.1 多步作业（$new_job）[pdf_59]

```orca
! B3LYP def2-TZVP Opt
%base "step1"
*xyz 0 1
...
*
$new_job
! B3LYP def2-TZVP Freq
%base "step2"
%moinp "step1.gbw"
*xyzfile 0 1 step1.xyz
```

### 13.2 从旧作业读取轨道 [pdf_14]

```orca
%scf
  MOInp "previous.gbw"
end
```

### 13.3 DFT 网格设置 [pdf_57]

```orca
! B3LYP def2-TZVP Grid5 FinalGrid6 TightSCF
# Grid5 = 计算网格, FinalGrid6 = 最终网格（更高精度）
```

### 13.4 内存设置 [pdf_72]

```orca
%maxcore 2000    # 每个核心 2000 MB
```

### 13.5 冻核近似 [pdf_64]

默认开启（MP2/CC 等），关闭：
```orca
! NoFrozenCore
```

### 13.6 片段指定 [pdf_14]

```orca
%frag
  Group 1 {0-5}      # 原子 0 到 5 为片段 1
  Group 2 {6-10}     # 原子 6 到 10 为片段 2
end
```

### 13.7 对称性 [pdf_55]

```orca
! UseSym                # 自动检测点群
# 支持 D2h 及以下点群
```

### 13.8 输出控制 [pdf_54]

```orca
%output
  Print[P_All] 0        # 关闭所有额外打印
  Print[P_Mulliken] 1   # 开启 Mulliken 布居
  Print[P_Loewdin] 1    # 开启 Löwdin 布居
  Print[P_Basis] 1      # 打印基组信息
end
```

### 13.9 文件重命名脚本 [pdf_68]

```bash
# 批量重命名输入文件
rename b3lyp mp2 *.inp
# 将含 b3lyp 的文件名替换为 mp2
```

### 13.10 监测输出 [pdf_14]

```bash
tail -f job.out    # Linux/Mac 实时查看输出
```

---

## 十四、快速学习路径

1. **阅读第 1 章**：了解 ORCA 能做什么，完成第一个计算（Hello water!）
2. **阅读第 2 章 2.1~2.5**：掌握输入文件结构、坐标输入、基本设置、并行运行
3. **阅读第 4 章**：学习几何优化、频率、过渡态等结构相关计算
4. **阅读第 3 章**：了解各种模型化学方法（DFT、MP2、CC、CASSCF）
5. **阅读第 5 章**：根据需要学习光谱与性质计算
6. **参考 ORCA Input Library**：浏览实际示例，快速上手
7. **阅读 ORCA 手册相关章节**：深入了解理论背景

---

## 十五、参考文献

- ORCA 6.1 Manual（orca.pdf, 2025-11-05）
- Setting up ORCA - ORCA Input Library（pdf_53）
- General Input - ORCA Input Library（pdf_54）
- Geometry input - ORCA Input Library（pdf_55）
- DFT calculations - ORCA Input Library（pdf_59）
- MP2 & MP3 - ORCA Input Library（pdf_64）
- CASSCF calculations - ORCA Input Library（pdf_65）
- TDDFT - ORCA Input Library（pdf_72）
- Molecular properties - ORCA Input Library（pdf_70）
- Geometry optimizations - ORCA Input Library（pdf_57）
- 官方网站：https://orcaforum.kofo.mpg.de