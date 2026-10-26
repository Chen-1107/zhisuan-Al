# Gaussian 使用技能手册（完整指令版）

> 本文档基于 Gaussian 09/16 官方手册及《额外赠送高斯量子化学手册》、Gaussian 09 中文使用手册等资料整理，涵盖安装配置、输入文件语法、**全部计算类型与方法模块**的关键词指令与操作示例。

---

## 一、Gaussian 简介

**Gaussian** 是量子化学领域最广泛使用的计算化学软件包之一，由 John Pople 等人开发 [pdf_76]。支持：

- 分子能量与结构优化
- 过渡态搜索与反应路径分析
- 频率分析与热化学计算
- 激发态计算（CIS、TDDFT、CASSCF、EOM-CCSD、SAC-CI）
- 溶剂效应模拟（PCM、CPCM、IEFPCM、SMD、Onsager）
- 多尺度 ONIOM 方法
- 分子性质（NMR、EPR、极化率、振动光谱）
- 周期性体系

- **官方网站**：http://www.gaussian.com [pdf_74]
- **Gaussian 09 引用**：Gaussian 09, Revision A.02, M. J. Frisch et al. [pdf_74]
- **Gaussian 16 引用**：Gaussian 16, Revision C.01, M. J. Frisch et al.

---

## 二、安装与配置

### 2.1 Linux 版本安装 [pdf_75]

```bash
# 解压安装包
tar -xjvf G16-A03-AVX2.tbz

# 假设解压路径为 /home/user/g16
# 修改 ~/.bashrc，添加：
export g16root=/home/user
export GAUSS_SCRDIR=/home/user/g16/scratch
source $g16root/g16/bsd/g16.profile

# 使配置生效
source ~/.bashrc

# 设置权限
cd /home/user/g16
chmod 750 -R *

# 创建 scratch 临时目录
mkdir /home/user/g16/scratch
```

### 2.2 Windows 版本安装 [pdf_75]

1. 双击 `setup.exe`，按提示安装
2. **不要**将 Gaussian 安装到中文路径
3. 安装顺序：先 Gaussian，再 GaussView
4. Win10 下建议以管理员权限运行
5. 32bit 版本有内存限制（%Mem ≤ 1.5GB）和 CPU 核心限制（%NProc ≤ 4）

### 2.3 运行 Gaussian [pdf_76]

```bash
# Linux（Unix 风格）
g09 < input.com > output.log
g16 < input.gjf > output.log

# 后台运行
nohup g16 < input.gjf > output.log &

# 多核并行
g16 < input.gjf > output.log -p=8
```

### 2.4 环境变量 [pdf_74]

| 变量 | 说明 |
|------|------|
| `g16root` | Gaussian 安装目录的父目录 |
| `GAUSS_SCRDIR` | 临时文件目录（重要！） |
| `GAUSS_EXEDIR` | 可执行文件路径 |

---

## 三、输入文件结构

### 3.1 输入文件格式 [pdf_76]

Gaussian 输入文件由以下部分组成，各部分之间用**空行**分隔：

```
1. Route Section（# 开头）   ← 指定计算方法、基组、任务类型
2. Title Section              ← 任务标题（不能有空行）
3. Charge and Spin Multiplicity
4. Molecular Geometry
                              ← 空行
5. Optional additional sections
```

**Windows 扩展名**：`.gjf`，**Linux 扩展名**：`.com` [pdf_76]

### 3.2 Route Section 语法 [pdf_77]

```
# [p] method/basis [keyword=(opt1,opt2)] [keyword2=(opt1,opt2)]
```

- 以 `#` 开头
- `#p`：打印更多输出信息
- 关键词可任意顺序
- 最多 5 行
- 以空行结束

**示例**：
```
#p B3LYP/6-31G(d) Opt Freq
#p MP2/cc-pVTZ Counterpoise=2
#p ONIOM(B3LYP/6-31G(d):UFF) Opt
```

### 3.3 分子结构输入 [pdf_76]

**笛卡尔坐标（默认）**：
```
#p B3LYP/6-31G(d) Opt

Water molecule

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

**Z-矩阵（内坐标）**：
```
#p HF/6-31G(d) Opt

Water

0 1
O
H    1   r1
H    1   r2    2   a1

r1=0.96
r2=0.96
a1=105.0
```

### 3.4 多步计算（--Link1--）[pdf_76]

使用 `--Link1--` 连接多个计算步骤：

```
%Chk=water
#p B3LYP/6-31G(d) Opt

Step 1: Optimization

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973

--Link1--
%Chk=water
%NoSave
#p B3LYP/6-31G(d) Freq Geom=Check Guess=Read

Step 2: Frequency
0 1

```

### 3.5 控制指令 [pdf_74]

| 指令 | 说明 | 示例 |
|------|------|------|
| `%Mem=1GB` | 设置内存 | `%Mem=16GB` |
| `%NProcShared=8` | 共享内存并行 | `%NProcShared=32` |
| `%NProcLinda=4` | 分布式并行 | `%NProcLinda=4` |
| `%Chk=filename` | 检查点文件名 | `%Chk=water.chk` |
| `%NoSave` | 不保存检查点 | 最后一步使用 |
| `%Int=MaxDisk=10GB` | 积分文件大小限制 | `%Int=MaxDisk=20GB` |

### 3.6 输出文件信息 [pdf_76]

| 信息 | 在输出文件中查找 |
|------|-----------------|
| 标准坐标 | `Standard Orientation` |
| 能量 | `SCF Done: E(RHF)= -113.863697598 A.U.` |
| MP2 能量 | `EUMP2= -0.11416665769315D+03` |
| 偶极矩 | `Dipole moment` |
| 电荷分布 | `Total atomic charges`（Mulliken） |
| 分子轨道 | `Molecular Orbital Coefficients` |
| 频率 | `Frequencies ---` |
| IR 强度 | `IR Intensities ---` |
| 热力学 | `Thermochemistry` |

### 3.7 方法可用性速查 [pdf_74]

| 方法 | SP/Scan | Opt/Force | Freq | IRC | ADMP | Polar | Stable | ONIOM | SCRF | PBC |
|------|:-------:|:---------:|:----:|:---:|:----:|:-----:|:------:|:-----:|:----:|:---:|
| 分子力学 | ★ | ★ | ★ | | | | | ★ | ★ | |
| AM1/PM3/PM6 | ★ | ★ | ★ | ★ | | ★ | ★ | ★ | ★ | |
| HF | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ |
| DFT | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ | ★ |
| CASSCF | ★ | ★ | ★ | ★ | | ★ | | ★ | ★ | |
| MP2 | ★ | ★ | ★ | ★ | ★ | ★ | | ★ | ★ | |
| MP3/MP4(SDQ) | ★ | ★ | **num** | ★ | | RLPM | | ★ | ★ | |
| MP4(SDTQ)/MP5 | ★ | **num** | | | | | | ★ | | |
| QCISD/CCD/CCSD | ★ | ★ | **num** | ★ | | RLPM | | ★ | ★ | |
| QCISD(T)/(TQ) | ★ | **num** | | | | | | ★ | | |
| BD | ★ | ★ | | | | | | ★ | ★ | |
| CBS/Gn/W1 | ★ | | | | | | | | | |
| CIS | ★ | ★ | ★ | ★ | | ★ | | ★ | ★ | |
| TD | ★ | ★ | **num** | ★ | | | | ★ | ★ | |
| EOM | ★ | **num** | | | | | | ★ | ★ | |
| SAC-CI | ★ | | | | | | | ★ | ★ | |

> **说明**：num = 数值频率；RLPM = 只能做数值 polar；★ = 支持

---

## 四、计算类型（Job Types）

### 4.1 单点能计算（SP）[pdf_76]

**默认任务类型**，如不指定则自动执行 SP。

```gjf
#p B3LYP/6-31G(d)

Single point energy of water

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

### 4.2 几何优化（Opt）[pdf_76]

```gjf
#p B3LYP/6-31G(d) Opt

Water optimization

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

**Opt 选项** [pdf_76]：

| 选项 | 说明 |
|------|------|
| `Opt=Tight` | 提高收敛标准 |
| `Opt=VeryTight` | 更高收敛标准 |
| `Opt=ReadFC` | 读取力常数 |
| `Opt=CalcFC` | 计算初始 Hessian |
| `Opt=CalcAll` | 每一步都计算 Hessian |
| `Opt=TS` | 过渡态搜索（需 + Freq） |
| `Opt=QST2` | 通过反应物和产物找过渡态 |
| `Opt=QST3` | 通过反应物、产物和猜测找过渡态 |
| `Opt=Z-Matrix` | 使用内坐标优化 |
| `Opt=ModRedundant` | 自定义冗余坐标 |
| `Opt=AddRedundant` | 打印指定键长/键角 |
| `Opt(maxcyc=N)` | 最大迭代步数 |
| `Opt=Restart` | 重启优化 |

**收敛标准**（默认）[pdf_76]：
- 最大力 < 0.00045
- 均方根力 < 0.0003
- 最大位移 < 0.0018
- 均方根位移 < 0.0012

### 4.3 频率计算（Freq）[pdf_76]

```gjf
#p B3LYP/6-31G(d) Opt Freq

Water optimization and frequency

0 1
coordinates
```

**Freq 选项**：

| 选项 | 说明 |
|------|------|
| `Freq=Raman` | 计算拉曼强度 |
| `Freq=VCD` | 计算振动圆二色谱 |
| `Freq=HPmodes` | 高精度打印简正模式 |
| `Freq=ReadFC` | 从检查点读取力常数 |
| `Freq=Numer` | 数值频率（用于 MP4/CCSD 等） |
| `Freq=Noraman` | 不计算拉曼 |

**频率校正因子** [pdf_76]：

| 方法 | 频率校正因子 | ZPE 校正因子 |
|------|:-----------:|:------------:|
| HF | 0.8929 | 0.8929 |
| B3LYP | 0.9613 | 0.9806 |
| MP2 | 0.9434 | 0.9670 |

### 4.4 IRC 计算（IRC）[pdf_76]

```gjf
#p B3LYP/6-31G(d) IRC

IRC calculation

0 1
(Transition state coordinates)
```

**IRC 选项**：

| 选项 | 说明 |
|------|------|
| `IRC(Forward)` | 仅正向 |
| `IRC(Reverse)` | 仅反向 |
| `IRC(Calcfc)` | 计算力常数 |
| `IRC(MaxPoints=N)` | 最大步数（默认 6） |
| `IRC(StepSize=N)` | 步长（默认 10，单位 0.01 amu¹/²·Bohr） |
| `IRC(RCFC)` | 从检查点读取力常数 |
| `IRC=ReadFC` | 读取力常数 |
| `IRC(Downhill)` | 沿能量下降方向 |

### 4.5 势能面扫描（Scan）[pdf_76]

```gjf
#p HF/6-31G(d) Scan

CH PES Scan

0 2
C
H 1 R

R 0.5 40 0.04
```

**变量格式**：`name 初始值 [点数 步长]`

### 4.6 波函数稳定性（Stable）[pdf_76]

```gjf
#p UHF/6-31G(d) Stable

Test wavefunction stability

0 1
O2 coordinates
```

**Stable 选项**：
- `Stable=Opt`：自动优化到稳定波函数
- `Stable`：仅测试

### 4.7 极化率与超极化率（Polar）[pdf_74]

```gjf
#p B3LYP/6-31+G(d) Polar

Polarizability

0 1
coordinates
```

### 4.8 分子体积（Volume）[pdf_74]

```gjf
#p HF/6-31G(d) Volume

Molecular volume

0 1
coordinates
```

### 4.9 力计算（Force）[pdf_74]

```gjf
#p B3LYP/6-31G(d) Force

Atomic forces

0 1
coordinates
```

---

## 五、理论方法（第6章）

### 5.1 Hartree-Fock（HF）[pdf_76]

```gjf
#p HF/6-31G(d) Opt
#p RHF/6-31G(d)        # 限制性闭壳层
#p UHF/6-31G(d)        # 非限制性开壳层
#p ROHF/6-31G(d)       # 限制性开壳层
```

### 5.2 密度泛函方法（DFT）[pdf_76]

**常用泛函**：

| 关键词 | 说明 |
|--------|------|
| `B3LYP` | 最常用的杂化泛函（VWN5） |
| `B3LYP` | Gaussian 默认 VWN3（B3LYP 风格） |
| `B3PW91` | B3PW91 杂化泛函 |
| `BP86` | 纯 GGA 泛函 |
| `BLYP` | 纯 GGA 泛函 |
| `PBE1PBE` | PBE0 杂化泛函 |
| `M06-2X` | 明尼苏达杂化泛函 |
| `CAM-B3LYP` | 长程校正杂化泛函 |
| `wB97XD` | 长程校正+色散 |
| `LSDA` | 局域自旋密度近似 |
| `SVWN` | 局域泛函（Slater+VWN） |
| `SVWN5` | 局域泛函（Slater+VWN5） |
| `HSEH1PBE` | 屏蔽杂化泛函 |
| `LC-wPBE` | 长程校正泛函 |
| `BMK` | 杂化 meta-GGA |
| `TPSSTPSS` | meta-GGA |
| `BVP86` | 纯 GGA |
| `PBEh1PBE` | 杂化 PBE |
| `HCTH` | 梯度修正泛函 |
| `THCTHHYB` | 杂化 HCTH |
| `O3LYP` | 杂化泛函 |

**泛函的 B3LYP 风格差异** [pdf_57]：
```gjf
#p B3LYP/6-31G(d)               # ORCA 风格（VWN5）
#p B3LYP/G/6-31G(d)             # Gaussian 风格（VWN3）
```

### 5.3 后 SCF 方法

**MP2** [pdf_76]：
```gjf
#p MP2/6-31G(d) Opt
#p MP2(full)/6-31G(d)           # 全电子相关
#p MP2(FC)/6-31G(d)             # 冻核近似（默认）
#p UMP2/6-31G(d)                # 非限制性 MP2
#p ROMP2/6-31G(d)               # 限制性开壳层 MP2
```

**MP3 / MP4**：
```gjf
#p MP3/6-31G(d)
#p MP4/6-31G(d)                 # MP4(SDTQ)
#p MP4(SDQ)/6-31G(d)           # 仅 SDQ 项
```

**耦合簇**：
```gjf
#p CCSD/6-31G(d)
#p CCSD(T)/6-31G(d)             # 包含三激发校正
#p CCD/6-31G(d)
#p QCISD/6-31G(d)               # 二次 CI
#p QCISD(T)/6-31G(d)            # QCISD + 三激发校正
```

**BD（Brueckner 耦合簇）**：
```gjf
#p BD/6-31G(d)
#p BD(T)/6-31G(d)
```

### 5.4 CASSCF 方法 [pdf_76]

```gjf
#p CASSCF(4,6)/6-31G(d) Opt

#p CASSCF(10,10)/STO-3G
```

**CASSCF 语法**：`CASSCF(n,m)`，n=活性电子数，m=活性轨道数

### 5.5 半经验方法 [pdf_76]

```gjf
#p AM1 Opt
#p PM3 Opt
#p PM6
#p MNDO
#p MINDO3
#p PDDG
#p PM3MM
#p ZINDO                         # ZINDO 激发态
#p CNDO
#p INDO
```

### 5.6 复合方法（高精度能量）[pdf_74]

| 关键词 | 说明 | MAD（kJ/mol） |
|--------|------|:------------:|
| `G1` | Gaussian-1 理论 | 6.3 |
| `G2` | Gaussian-2 理论 | 6.2 |
| `G2MP2` | G2(MP2) 修改版 | 6.3 |
| `G3` | Gaussian-3 理论 | 4.3 |
| `G3MP2` | G3(MP2) 简化版 | - |
| `G3B3` | G3 使用 B3LYP 结构和频率 | - |
| `G3MP2B3` | G3MP2 使用 B3LYP 结构 | - |
| `G4` | Gaussian-4 理论 | 3.4 |
| `G4MP2` | G4(MP2) 简化版 | 4.1 |
| `CBS-4` | CBS 方法（最低级） | 8.8 |
| `CBS-Q` | CBS-Q 方法 | 4.2 |
| `CBS-QB3` | CBS-QB3 方法 | 4.0 |
| `CBS-APNO` | CBS-APNO 方法 | 2.1 |
| `W1U` | Weizmann-1 方法 | 最高精度 |

**G1 方法步骤** [pdf_76]：
1. HF/6-31G(d) 优化 + 频率（ZPE，校正因子 0.8929）
2. MP2(Full)/6-31G(d) 重新优化
3. MP4/6-311G(d,p) 基态能量
4. MP4/6-311+G(d,p) 弥散校正
5. MP4/6-311G(2df,p) 极化校正
6. QCISD(T)/6-311G(d,p) 高精度相关校正
7. 经验校正

**G4 方法步骤** [pdf_35]：
1. B3LYP/6-31G(2df,p) 优化 + 频率
2. HF/6-311+G(3df,2p) 能量
3. MP2/6-311+G(3df,2p) 能量
4. MP4/6-31G(2df,p) 高极化校正
5. CCSD(T)/6-31G(d) 高精度相关校正
6. MP2(full)/G3LargeXP 大基组校正
7. HF 极限外推
8. 自旋-轨道耦合 + 经验校正

**使用示例**：
```gjf
#p G4 Opt=Tight

G4 calculation

0 1
coordinates

--Link1--
#p G4(ReadIso) Geom=AllCheck

0 1
300.0 1.0
```

### 5.7 分子力学方法 [pdf_74]

| 关键词 | 说明 |
|--------|------|
| `UFF` | 通用力场 |
| `Dreiding` | Dreiding 力场 |
| `AMBER` | AMBER 力场 |

---

## 六、基组（Basis Sets）（第5章）

### 6.1 常用基组 [pdf_76]

| 基组 | 类型 | 说明 |
|------|------|------|
| `STO-3G` | 最小基组 | 最快速，定量精度差 |
| `3-21G` | 分裂基组 | 中等精度 |
| `6-31G(d)` | 低极化基组 | 常用，Pople 基组 |
| `6-31G(d,p)` | 极化基组 | 含氢原子极化 |
| `6-311G(d,p)` | 三分裂基组 | 更高精度 |
| `6-31+G(d)` | 弥散基组 | 阴离子、激发态 |
| `6-311+G(d,p)` | 三分裂+弥散 | 常用 |
| `6-311++G(2df,2p)` | 高精度 | 复合方法 |
| `cc-pVDZ` | Dunning 相关一致基组 | |
| `cc-pVTZ` | 三zeta 相关一致 | |
| `cc-pVQZ` | 四zeta 相关一致 | |
| `aug-cc-pVTZ` | 弥散相关一致 | |
| `LANL2DZ` | 有效核势（ECP） | 过渡金属 |
| `SDD` | Stuttgart ECP | 过渡金属 |
| `CEP-121G` | 紧致有效势 | 重元素 |
| `DGDZVP` | DGauss 基组 | |
| `EPR-III` | EPR 专用基组 | 超精细耦合 |
| `pcSseg-2` | NMR 专用基组 | 化学位移 |

### 6.2 自定义基组（Gen）[pdf_74]

```gjf
#p B3LYP/Gen Opt

Mixed basis set

0 1
coordinates

C 0
6-31G(d)
****
H 0
6-31G
****
Fe 0
LANL2DZ
****

Fe 0
LANL2DZ
```

### 6.3 有效核势（ECP）[pdf_74]

```gjf
#p B3LYP/GenECP Opt

coordinates

C O H 0
6-31G(d)
****
Mo 0
LANL2DZ
****

Mo 0
LANL2DZ
```

---

## 七、分子性质计算

### 7.1 布居分析（Pop）[pdf_76]

| 关键词 | 说明 |
|--------|------|
| `Pop=Reg` | 默认，打印最高5个HOMO和最低5个LUMO |
| `Pop=Full` | 打印所有轨道 |
| `Pop=NBO` | NBO 分析 |
| `Pop=NBODel` | NBO 删除分析 |
| `Pop=MKS` | 打印 Mulliken 布居 |
| `Pop=Min` | 最小布居打印 |
| `Pop=NCS` | 自然连接化学屏蔽 |
| `Pop=NCSDiag` | NCS 对角化 |
| `Pop=NCSAll` | 所有 NCS 信息 |

### 7.2 NBO 分析 [pdf_74]

```gjf
#p B3LYP/6-31G(d) Pop=NBO

NBO analysis

0 1
coordinates
```

### 7.3 AIM 分析 [pdf_76]

```gjf
#p B3LYP/6-31G(d) AIM=All

Atoms in Molecules

0 1
coordinates
```

**AIM 选项**：
- `AIM=All`：所有 AIM 性质
- `AIM=BondOrders`：键级计算

### 7.4 NMR 计算 [pdf_76]

```gjf
#p B3LYP/6-311+G(2d,p) NMR

NMR chemical shifts

0 1
coordinates
```

**NMR 两步法示例**：
```
%Chk=NMR
#p B3LYP/6-31G(d) Opt

Optimization

0 1
coordinates

--Link1--
%Chk=NMR
%NoSave
#p B3LYP/6-311+G(2d,p) NMR Geom=Check Guess=Read

NMR calculation

0 1
```

**NMR 选项**：
- `NMR=Shielding`：化学位移
- `NMR=SpinSpin`：自旋-自旋耦合 J
- `NMR=CSGT`：CSGT 方法
- `NMR=GIAO`：GIAO 方法（默认）
- `NMR=Suscep`：磁化率

### 7.5 极化率与超极化率 [pdf_74]

```gjf
#p B3LYP/6-31+G(d) Polar

Polarizability

0 1
coordinates
```

### 7.6 电子密度与静电势 [pdf_76]

```gjf
#p B3LYP/6-31G(d) Density

Electron density

0 1
coordinates
```

**Density 选项**：
- `Density=Current`：当前密度
- `Density=Checkpoint`：从检查点提取

### 7.7 振动光谱（IR/Raman）[pdf_76]

```gjf
#p B3LYP/6-31G(d) Opt Freq

IR spectrum

0 1
coordinates

#p B3LYP/6-31G(d) Freq=Raman

Raman spectrum

0 1
coordinates
```

### 7.8 VCD 计算

```gjf
#p B3LYP/6-31G(d) Freq=VCD

VCD spectrum

0 1
coordinates
```

---

## 八、激发态计算（第9章）

### 8.1 CIS 方法（CI-Singles）[pdf_76]

```gjf
#p CIS(6-6)/6-31+G(d)           # 6个HOMO，6个LUMO
#p CIS(NStates=10)/6-31+G(d)    # 10个激发态
#p CIS(Root=1)/6-31+G(d)        # 指定根
#p CIS(50-50)/6-31+G(d)         # 50个轨道窗口
```

**CIS 激发态优化**：
```
%Chk=es_form
#p CIS/6-31+G(d)

Formaldehyde Excited States

0 1
coordinates

--Link1--
%Chk=es_form
%NoSave
#p CIS(Root=1,Read)/6-31+G(d) Opt Freq Geom=Check Guess=Read

Excited state optimization

0 1
```

### 8.2 TDDFT 方法 [pdf_74]

```gjf
#p B3LYP/6-31+G(d) TD(NStates=10)

TDDFT excited states

0 1
coordinates
```

**TDDFT 选项**：
- `TD(NStates=N)`：计算前 N 个态
- `TD(Singlets)`：仅单重态
- `TD(Triplets)`：仅三重态
- `TD(Read)`：从检查点读取

### 8.3 SAC-CI 方法 [pdf_74]

```gjf
#p SAC-CI/6-31G(d)

SAC-CI excited states

0 1
coordinates
```

### 8.4 EOM-CCSD 方法 [pdf_74]

```gjf
#p EOM-CCSD/6-31G(d)

EOM-CCSD excited states

0 1
coordinates
```

### 8.5 ZINDO 方法 [pdf_74]

```gjf
#p ZINDO(NStates=40)

ZINDO semi-empirical excited states

0 1
coordinates
```

---

## 九、溶剂效应（第10章）

### 9.1 SCRF 方法总览 [pdf_76]

| 关键词 | 模型 | 说明 |
|--------|------|------|
| `SCRF=Onsager` | Onsager 偶极反应场 | 最简模型 |
| `SCRF=PCM` | PCM | 极化连续模型（默认） |
| `SCRF=CPCM` | 导体 PCM | 更稳定 |
| `SCRF=IEFPCM` | 积分方程形式 PCM | 推荐 |
| `SCRF=IPCM` | 等密度 PCM | |
| `SCRF=SCIPCM` | 自洽等密度 PCM | |
| `SCRF=SMD` | SMD 模型 | 结合非静电项 |
| `SCRF=COSMO` | COSMO | 类导体屏蔽 |
| `SCRF=Dipole` | 偶极反应场 | |

### 9.2 PCM 溶剂计算 [pdf_76]

```gjf
#p B3LYP/6-31G(d) SCRF=(PCM,Solvent=Water)

PCM calculation

0 1
coordinates
```

**常用溶剂参数**：

| 溶剂 | 介电常数 ε |
|------|:----------:|
| 水（Water） | 78.39 |
| 环己烷（Cyclohexane） | 2.02 |
| 苯（Benzene） | 2.28 |
| 乙醇（Ethanol） | 24.55 |
| 甲醇（Methanol） | 32.61 |
| 二氯甲烷（Dichloromethane） | 8.93 |
| 乙腈（Acetonitrile） | 35.69 |
| 氯仿（Chloroform） | 4.71 |
| 四氢呋喃（THF） | 7.43 |
| 二甲亚砜（DMSO） | 46.83 |
| 甲苯（Toluene） | 2.38 |

### 9.3 SMD 溶剂模型 [pdf_74]

```gjf
#p B3LYP/6-31G(d) SCRF=(SMD,Solvent=Water)

SMD calculation

0 1
coordinates
```

### 9.4 Onsager 模型自定义 [pdf_76]

```gjf
#p B3LYP/6-31G(d) SCRF=Dipole Volume

Onsager model

0 1
coordinates

2.92 2.0
```

### 9.5 自定义溶剂 [pdf_74]

```gjf
#p B3LYP/6-31G(d) SCRF=(PCM,Read)

Custom solvent

0 1
coordinates

eps=35.69
rsolv=1.83
```

---

## 十、ONIOM 多尺度方法

### 10.1 ONIOM 基本语法 [pdf_74]

```gjf
#p ONIOM(B3LYP/6-31G(d):UFF) Opt

ONIOM two-layer calculation

0 1
coordinates (with connectivity)
```

### 10.2 ONIOM 层数 [pdf_74]

**两层**：`ONIOM(high:low)`
**三层**：`ONIOM(high:medium:low)`

**示例**：
```gjf
#p ONIOM(B3LYP/6-31G(d):UFF)=Embed Opt

ONIOM with embedding

0 1
coordinates
```

### 10.3 ONIOM 选项 [pdf_74]

| 选项 | 说明 |
|------|------|
| `=Embed` | 力学嵌入 |
| `=Scale=N` | 比例因子 |
| `=NoCompress` | 不压缩参数 |
| `=Connect` | 使用连接原子 |

### 10.4 ONIOM 溶剂 [pdf_74]

```gjf
#p ONIOM(B3LYP/6-31G(d):UFF) SCRF(ONIOMPCM=C,Read) Opt

ONIOM with PCM solvent

0 1
coordinates
```

---

## 十一、典型操作示例

### 11.1 水分子单点能 [pdf_76]
```gjf
#p HF/6-31G(d)

Water single point energy

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

### 11.2 DFT 几何优化
```gjf
#p B3LYP/6-31G(d) Opt

Water optimization

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

### 11.3 优化+频率
```gjf
#p B3LYP/6-31G(d) Opt Freq

Water optimization and frequency

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973
```

### 11.4 过渡态搜索（QST2）[pdf_76]
```gjf
#p UHF/6-31G(d) Opt=QST2

H3CO --> H2COH Transition State

0,2
Coordinates for H3CO

0,2
Coordinates for H2COH
```

### 11.5 IRC 计算 [pdf_76]
```gjf
#p B3LYP/6-31G(d) IRC=(RCFC,StepSize=30,MaxPoints=15) SCF=QC

IRC calculation

0 1
(TS coordinates)
```

### 11.6 TDDFT 激发态
```gjf
#p B3LYP/6-31+G(d) TD(NStates=10)

TDDFT excited states

0 1
coordinates
```

### 11.7 NMR 化学位移 [pdf_76]
```
%Chk=NMR
#p B3LYP/6-31G(d) Opt

Optimization

0 1
coordinates

--Link1--
%Chk=NMR
%NoSave
#p B3LYP/6-311+G(2d,p) NMR Geom=Check Guess=Read

NMR

0 1
```

### 11.8 溶剂效应
```gjf
#p B3LYP/6-31G(d) SCRF=(PCM,Solvent=Water) Opt

Optimization in water

0 1
coordinates
```

### 11.9 势能面扫描 [pdf_76]
```gjf
#p HF/6-31G(d) Scan

CH Scan

0 2
C
H 1 R

R 0.5 40 0.04
```

### 11.10 高精度 G4 能量
```gjf
#p G4 Opt=Tight

G4 calculation

0 1
coordinates
```

### 11.11 波函数稳定性测试 [pdf_76]
```gjf
#p RHF/6-31G(d) Stable=Opt

Stability test

0 1
O2 coordinates
```

### 11.12 多步优化+频率+NMR
```
%Chk=water
#p B3LYP/6-31G(d) Opt Freq

Step 1: Opt+Freq

0 1
O    0.0000    0.0000    0.0626
H   -0.7920    0.0000   -0.4973
H    0.7920    0.0000   -0.4973

--Link1--
%Chk=water
%NoSave
#p B3LYP/6-311+G(2d,p) NMR Geom=Check Guess=Read

Step 2: NMR

0 1
```

### 11.13 激发态优化 [pdf_76]
```
%Chk=es_form
#p CIS/6-31+G(d)

Excited state calculation

0 1
coordinates

--Link1--
%Chk=es_form
%NoSave
#p CIS(Root=1,Read)/6-31+G(d) Opt Freq Geom=Check Guess=Read

Excited state geometry optimization

0 1
```

### 11.14 等构反应焓变 [pdf_76]
```gjf
#p B3LYP/6-311+G(3df,2p)//B3LYP/6-31G(d)

Isodesmic reaction energy

0 1
coordinates
```

### 11.15 双杂化方法（B2-PLYP 风格）
```gjf
#p B2PLYP/def2-TZVP Opt

Double hybrid DFT

0 1
coordinates
```

---

## 十二、SCF 收敛问题处理

### 12.1 常用收敛策略 [pdf_76]

```gjf
#p B3LYP/6-31G(d) SCF=Tight

#p B3LYP/6-31G(d) SCF=(Tight,NoVarAcc)

#p B3LYP/6-31G(d) SCF=(Tight,QC)
```

### 12.2 SCF 选项 [pdf_74]

| 选项 | 说明 |
|------|------|
| `SCF=Tight` | 高精度收敛 |
| `SCF=VeryTight` | 极高精度 |
| `SCF=QC` | 二次收敛（推荐难收敛体系） |
| `SCF=XQC` | 扩展二次收敛 |
| `SCF=Conventional` | 传统 SCF |
| `SCF=Direct` | 直接 SCF（默认） |
| `SCF=NoVarAcc` | 不变精度（避免积分近似） |
| `SCF=NoSymm` | 关闭对称性 |
| `SCF=MaxCycle=N` | 最大迭代次数 |
| `SCF=DM` | 阻尼法 |
| `SCF=DSymm` | 密度对称化 |

### 12.3 初始猜测 [pdf_74]

| 选项 | 说明 |
|------|------|
| `Guess=Read` | 从检查点读取 |
| `Guess=Huckel` | Hückel 猜测 |
| `Guess=Alter` | 交换轨道顺序 |
| `Guess=Only` | 仅打印猜测波函数 |
| `Guess=Fragment=N` | 片段组合猜测 |

---

## 十三、实用工具

### 13.1 formchk（检查点转文本）
```bash
formchk water.chk water.fch
```

### 13.2 cubegen（生成 Cube 文件）
```bash
cubegen 0 Density=SCF water.fch density.cub 0

cubegen 0 MO=HOMO water.fch homo.cub 0
# Gaussian 09 中轨道编号从 1 开始
```

### 13.3 freqchk（频率分析）
```bash
freqchk water.chk
```

### 13.4 GaussView
- 可视化分子结构、轨道、等值面
- 生成输入文件
- 分析输出文件

### 13.5 从输出文件提取信息 [pdf_76]

```bash
# 提取能量
grep "SCF Done" output.log

# 提取优化后的坐标
grep -A 5 "Standard orientation" output.log

# 提取频率
grep "Frequencies" output.log

# 提取 IR 强度
grep "IR Intensities" output.log

# 提取热力学数据
grep -A 10 "Thermochemistry" output.log

# 提取偶极矩
grep "Dipole moment" output.log
```

---

## 十四、关键参数速查

| 参数 | 设置位置 | 说明 |
|------|----------|------|
| `%Mem` | 文件开头 | 内存分配 |
| `%NProcShared` | 文件开头 | 共享内存并行核数 |
| `%NProcLinda` | 文件开头 | Linda 分布式并行 |
| `%Chk` | 文件开头 | 检查点文件名 |
| `%NoSave` | 文件开头 | 不保存检查点 |
| `SCF=Tight` | Route Section | 高精度 SCF |
| `SCF=QC` | Route Section | 二次收敛 SCF |
| `Int=UltraFine` | Route Section | 超精细积分网格 |
| `Int=Grid=UltraFine` | Route Section | 超精细 DFT 网格 |
| `Opt=Tight` | Route Section | 高精度优化 |
| `Opt=CalcFC` | Route Section | 计算初始 Hessian |
| `5D 7F` | Route Section | 使用 5 个 d 轨道 / 7 个 f 轨道 |
| `NoSymm` | Route Section | 关闭对称性 |
| `IOP(9/40=4)` | Route Section | 打印所有组态系数 |

---

## 十五、常见问题与排查

### 15.1 常见错误 [pdf_75]

| 错误信息 | 解决方案 |
|----------|---------|
| 收敛失败 | 增加 MaxCycle，使用 SCF=QC，检查初始结构 |
| 虚频 | 检查是否为过渡态，或优化未达极小值 |
| 内存不足 | 增大 %Mem，或使用直接 SCF |
| 磁盘空间不足 | 设置 GAUSS_SCRDIR 到足够空间的分区 |
| L101 错误 | 输入文件格式问题，检查各部分空行 |
| Link 9999 错误 | 检查几何结构是否合理 |

### 15.2 优化建议 [pdf_76]

- 先用半经验方法或小基组粗优化，再提高精度
- 难收敛体系：使用 `Opt=CalcFC` 或 `Opt=ReadFC`
- 大体系：使用 ONIOM 方法
- 阴离子/激发态：添加弥散函数

### 15.3 频率校正 [pdf_76]

HF 方法频率有~10-12% 系统误差，乘以校正因子 0.8929 与实验值吻合。

---

## 十六、快速学习路径

1. **阅读第 1 章**：了解计算化学基本概念
2. **阅读第 2 章**：单点能计算，理解输出文件
3. **阅读第 3 章**：几何优化与过渡态搜索
4. **阅读第 4 章**：频率分析与热化学
5. **阅读第 5 章**：基组选择
6. **阅读第 6 章**：理论方法选择
7. **阅读第 7 章**：高精度复合方法
8. **阅读第 8 章**：反应性研究
9. **阅读第 9 章**：激发态计算
10. **阅读第 10 章**：溶剂效应

---

## 十七、参考文献

- Gaussian 09 中文使用手册（pdf_74）
- 额外赠送高斯量子化学手册（pdf_76）
- 关于高斯入门的小小指南 V1.2 by paramecium86（pdf_75）
- Gaussian 09 使用指南说明书（pdf_77）
- Gaussian 官方网站：http://www.gaussian.com