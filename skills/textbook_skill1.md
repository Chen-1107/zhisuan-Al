# 综合教科书知识技能手册（Textbook_Skill）

> 本文档综合知识库中**除 MIT 讲义与 DFT 软件使用说明书外**的全部教科书内容，涵盖量子化学、物理化学、有机化学、无机化学、计算化学、光谱学、数学方法等学科的核心知识与理论体系。

---

## 第一篇：量子化学

### 第一章 量子化学基础（McQuarrie 2e / Levine 7e / Cooksy 等）

#### 1.1 量子力学的诞生 [pdf_27]

**黑体辐射**：Planck 假设 $E = h\nu$，能量量子化概念诞生 [pdf_27]。

**光电效应**：Einstein 光量子假说 $E = h\nu = \Phi + K_{\max}$，证实光既具有波动性又具有粒子性。

**氢原子光谱**：Balmer 公式 $\frac{1}{\lambda} = R_H\left(\frac{1}{n_1^2} - \frac{1}{n_2^2}\right)$，Bohr 模型 $E_n = -\frac{m_e e^4}{8\varepsilon_0^2 h^2}\frac{Z^2}{n^2}$。

**de Broglie 波**：$\lambda = \frac{h}{p} = \frac{h}{mv}$，一切物质都具有波粒二象性。

**Heisenberg 不确定性原理**：$\Delta x \Delta p \ge \frac{\hbar}{2}$，$\Delta E \Delta t \ge \frac{\hbar}{2}$。

#### 1.2 Schrödinger 方程 [pdf_24]

**时间相关 Schrödinger 方程**：
$$i\hbar\frac{\partial\Psi}{\partial t} = -\frac{\hbar^2}{2m}\nabla^2\Psi + V\Psi$$

**时间无关 Schrödinger 方程**：
$$-\frac{\hbar^2}{2m}\frac{d^2\psi}{dx^2} + V\psi = E\psi$$

**波函数的统计解释**（Born 规则）：$|\Psi(\mathbf{r}, t)|^2 d\mathbf{r}$ 为在 $\mathbf{r}$ 处找到粒子的概率。

**波函数的标准条件**：单值、连续、平方可积。

#### 1.3 一维势箱 [pdf_27]

$$E_n = \frac{n^2 h^2}{8ma^2}, \quad \psi_n(x) = \sqrt{\frac{2}{a}}\sin\frac{n\pi x}{a}$$

**零点能**：$E_1 = \frac{h^2}{8ma^2} \neq 0$，与 Heisenberg 不确定性原理一致。

**节点定理**：$n$ 能级波函数有 $n-1$ 个节点。

#### 1.4 量子力学公设 [pdf_24]

**公设 1（状态）**：系统的状态由 Hilbert 空间中的归一化态矢量（或波函数）描述。

**公设 2（可观测量）**：每个可观测量对应一个 Hermitian 算符。

**公设 3（测量）**：测量结果只能为算符的本征值，测量后系统坍缩到对应本征态。

**公设 4（时间演化）**：状态由含时 Schrödinger 方程演化。

**公设 5（全同粒子）**：波函数对于玻色子对称，对于费米子反对称。

#### 1.5 谐振子 [pdf_27]

$$V(x) = \frac{1}{2}kx^2 = \frac{1}{2}m\omega^2 x^2$$

$$E_n = \left(n + \frac{1}{2}\right)\hbar\omega, \quad n = 0, 1, 2, \dots$$

**零点能**：$E_0 = \frac{1}{2}\hbar\omega$，与不确定性原理一致。

#### 1.6 角动量 [pdf_27]

**轨道角动量算符**：$\hat{\mathbf{l}} = \hat{\mathbf{r}} \times \hat{\mathbf{p}}$

**对易关系**：$[l_x, l_y] = i\hbar l_z$（循环置换）

**本征值**：$l^2 = l(l+1)\hbar^2$，$l_z = m\hbar$，其中 $l = 0, 1, 2, \dots$，$m = -l, -l+1, \dots, l$

#### 1.7 氢原子与类氢原子 [pdf_24][pdf_27]

**径向方程解**：$R_{nl}(r) = -\left[\frac{2Z}{na_0}\right]^{3/2}\frac{(n-l-1)!}{2n[(n+l)!]^3}\right]^{1/2} e^{-\rho/2}\rho^l L_{n+l}^{2l+1}(\rho)$

**能量**：$E_n = -\frac{Z^2}{n^2}\frac{m_e e^4}{8\varepsilon_0^2 h^2} = -\frac{Z^2}{n^2} \times 13.6\ \text{eV}$

**轨道角动量**：$|l| = \sqrt{l(l+1)}\hbar$，$l = 0, 1, 2, \dots, n-1$

**磁量子数**：$m = -l, -l+1, \dots, l$

#### 1.8 多电子原子与 Pauli 原理 [pdf_7]

**电子自旋**：$S = \frac{1}{2}$，$S_z = \pm\frac{1}{2}\hbar$

**Pauli 不相容原理**：两个电子不能占据完全相同的自旋轨道。

**Slater 行列式**：反对称化的多电子波函数。

**交换能**：同自旋电子间的 Fermi 孔效应。

#### 1.9 变分法与微扰论 [pdf_32]

**变分原理**：$E_0 \le \frac{\langle\Psi|\hat{H}|\Psi\rangle}{\langle\Psi|\Psi\rangle}$

**线性变分法**：$\Psi = \sum_{i} c_i \phi_i \Rightarrow \mathbf{Hc} = E\mathbf{Sc}$

**非简并微扰论**：$E_n^{(2)} = \sum_{k \neq n} \frac{|\langle\psi_k^{(0)}|\hat{H}'|\psi_n^{(0)}\rangle|^2}{E_n^{(0)} - E_k^{(0)}}$

---

### 第二章 分子电子结构（Levine 7e / Cooksy / Jensen）

#### 2.1 Born-Oppenheimer 近似 [pdf_7]

由于原子核质量远大于电子质量，可将核运动与电子运动分离：

$$E_{\text{total}} = E_{\text{elec}} + E_{\text{nuc}}$$

#### 2.2 氢分子离子 H₂⁺ [pdf_7]

**LCAO-MO 近似**：$\psi = c_1\phi_A + c_2\phi_B$

**成键轨道与反键轨道**：
- 成键轨道：$\sigma_{1s} = \frac{1}{\sqrt{2(1+S)}}(\phi_A + \phi_B)$，能量降低
- 反键轨道：$\sigma_{1s}^* = \frac{1}{\sqrt{2(1-S)}}(\phi_A - \phi_B)$，能量升高

#### 2.3 分子轨道理论 [pdf_7][pdf_32]

**价键（VB）理论**：以原子轨道重叠形成化学键，强调电子配对。

**分子轨道（MO）理论**：电子在整个分子中离域，轨道为原子轨道的线性组合。

**σ 键与 π 键**：σ 键沿键轴方向成键，π 键垂直于键轴方向成键。

#### 2.4 群论与分子对称性 [pdf_24][pdf_48]

**对称元素**：旋转轴、镜面、反演中心、旋转反射轴

**点群分类**：$C_{2v}$、$C_{2h}$、$D_{2h}$、$D_{3h}$、$T_d$、$O_h$、$I_h$ 等

**特征标表**：用于判断轨道对称性、光谱选择定则、分子轨道构建。

**投影算符**：$\hat{P}^{(j)} = \frac{l_j}{h}\sum_R \chi^{(j)}(R)^* \hat{R}$

#### 2.5 分子光谱 [pdf_7][pdf_24]

**转动光谱**：微波区，$E_J = J(J+1)\frac{\hbar^2}{2I}$

**振动光谱**：红外区，$E_n = (n + \frac{1}{2})\hbar\omega$

**电子光谱**：UV-Vis 区，Franck-Condon 原理

**选择定则**：$\Delta J = \pm 1$（转动），$\Delta n = \pm 1$（谐振子），$\Delta S = 0$，$\Delta \Lambda = 0, \pm 1$（电子跃迁）

---

## 第二篇：物理化学

### 第三章 热力学基础（Levine 6e / Atkins 12e / Cooksy）

#### 3.1 气体性质 [pdf_427]

**理想气体状态方程**：$PV = nRT$

**van der Waals 方程**：$\left(P + \frac{n^2a}{V^2}\right)(V - nb) = nRT$

**实际气体的临界现象**：临界温度 $T_c$、临界压力 $P_c$、临界体积 $V_c$

#### 3.2 热力学第一定律 [pdf_7][pdf_427]

**内能**：$\Delta U = q + w$

**焓**：$H = U + PV$，$\Delta H = q_P$

**热容**：$C_V = \left(\frac{\partial U}{\partial T}\right)_V$，$C_P = \left(\frac{\partial H}{\partial T}\right)_P$

**可逆过程与不可逆过程**：最大功在可逆过程中获得。

#### 3.3 热力学第二定律 [pdf_7][pdf_427]

**熵**：$dS = \frac{dq_{\text{rev}}}{T}$，$\Delta S = \int \frac{dq_{\text{rev}}}{T}$

**Clausius 不等式**：$dS \ge \frac{dq}{T}$

**热力学第三定律**：完美晶体的熵在 $T \to 0\ \text{K}$ 时趋于零。

#### 3.4 自由能与化学势 [pdf_7]

**Gibbs 自由能**：$G = H - TS$，$\Delta G = \Delta H - T\Delta S$

**化学势**：$\mu_i = \left(\frac{\partial G}{\partial n_i}\right)_{T, P, n_{j \neq i}}$

**化学平衡条件**：$\sum_i \nu_i \mu_i = 0$

**热力学平衡常数**：$\Delta G^\circ = -RT\ln K$

#### 3.5 相平衡 [pdf_7][pdf_427]

**Clapeyron 方程**：$\frac{dP}{dT} = \frac{\Delta S}{\Delta V}$

**Clausius-Clapeyron 方程**：$\ln\frac{P_2}{P_1} = -\frac{\Delta H_{\text{vap}}}{R}\left(\frac{1}{T_2} - \frac{1}{T_1}\right)$

**相律**：$F = C - P + 2$

#### 3.6 溶液与混合物 [pdf_7][pdf_427]

**Raoult 定律**：$P_A = x_A P_A^*$

**Henry 定律**：$P_A = K_A x_A$

**理想溶液**：$\Delta H_{\text{mix}} = 0$，$\Delta V_{\text{mix}} = 0$

**活度与活度系数**：$\mu_i = \mu_i^\circ + RT\ln a_i$

---

### 第四章 统计力学（Levine 6e / Cooksy / Atkins 12e）

#### 4.1 系综理论 [pdf_7]

**微正则系综**：孤立系统，$E$、$V$、$N$ 固定。

**正则系综**：与热浴接触，$T$、$V$、$N$ 固定。

**巨正则系综**：与热浴和粒子库接触，$T$、$V$、$\mu$ 固定。

**Boltzmann 分布**：$p_i = \frac{e^{-\beta E_i}}{Z}$，$Z = \sum_i e^{-\beta E_i}$

#### 4.2 配分函数 [pdf_7][pdf_39]

**平动配分函数**：$q_t = \left(\frac{2\pi mkT}{h^2}\right)^{3/2}V$

**转动配分函数**（双原子分子）：$q_r = \frac{T}{\sigma\Theta_r}$，$\Theta_r = \frac{h^2}{8\pi^2 Ik}$

**振动配分函数**：$q_v = \frac{e^{-\beta h\nu/2}}{1 - e^{-\beta h\nu}}$

**电子配分函数**：$q_e = \sum_i g_i e^{-\beta\varepsilon_i}$

#### 4.3 热力学函数与配分函数的关系 [pdf_7]

$$U = kT^2\left(\frac{\partial \ln Q}{\partial T}\right)_V, \quad S = k\ln Q + \frac{U}{T}$$

$$A = -kT\ln Q, \quad P = kT\left(\frac{\partial \ln Q}{\partial V}\right)_T$$

#### 4.4 理想气体热力学 [pdf_7]

**Rigid-Rotor Harmonic-Oscillator (RRHO) 近似**：用于计算理想气体的热力学性质。

**等容热容**：$C_V = \left(\frac{\partial U}{\partial T}\right)_V$，单原子气体 $C_V = \frac{3}{2}R$

**熵的统计力学表达**：$S = R\ln Q + RT\left(\frac{\partial \ln Q}{\partial T}\right)_V$

---

### 第五章 化学动力学（Levine 6e / Atkins 12e / Cooksy）

#### 5.1 反应速率 [pdf_7][pdf_427]

**速率定律**：$r = -\frac{d[A]}{dt} = k[A]^m[B]^n$

**一级反应**：$[A] = [A]_0 e^{-kt}$，半衰期 $t_{1/2} = \frac{\ln 2}{k}$

**二级反应**：$\frac{1}{[A]} = \frac{1}{[A]_0} + kt$，$t_{1/2} = \frac{1}{k[A]_0}$

**Arrhenius 方程**：$k = Ae^{-E_a/RT}$，$\ln k = \ln A - \frac{E_a}{RT}$

#### 5.2 过渡态理论 [pdf_7][pdf_34]

**Eyring 方程**：$k = \frac{k_B T}{h} e^{-\Delta G^\dagger/RT} = \frac{k_B T}{h} e^{\Delta S^\dagger/R} e^{-\Delta H^\dagger/RT}$

**热力学参数**：$\Delta H^\dagger$、$\Delta S^\dagger$、$\Delta G^\dagger$ 决定反应速率。

#### 5.3 复杂反应机理 [pdf_7]

**可逆反应**、**平行反应**、**连续反应**、**链反应**、**酶催化反应**（Michaelis-Menten 动力学）

**稳态近似**：中间体浓度近似为常数。

**速率决定步骤**：最慢的一步决定总反应速率。

---

## 第三篇：有机化学

### 第六章 有机化学基础（Loudon & Parise 6e / Karty 3e）

#### 6.1 化学键与分子结构 [pdf_47]

**Lewis 结构**：八隅体规则、形式电荷、共振结构

**VSEPR 理论**：通过电子对排斥预测分子几何构型

**杂化轨道**：$sp^3$（四面体）、$sp^2$（平面三角形）、$sp$（直线形）

**分子轨道理论**：σ 键、π 键、共轭体系

#### 6.2 酸碱性 [pdf_47]

**Brønsted-Lowry 酸碱**：$pK_a$ 值，$K_a = [\text{H}^+][\text{A}^-]/[\text{HA}]$

**Lewis 酸碱**：电子对给体与受体

**影响酸性的因素**：电负性、原子大小、共振效应、诱导效应、杂化

#### 6.3 立体化学 [pdf_47]

**手性**：$R/S$ 标记法，对映体、非对映体、内消旋化合物

**旋光性**：$[\alpha]_D = \frac{\alpha}{c \cdot l}$（比旋光度）

**Fischer 投影式**、**Newman 投影式**、**构象分析**

#### 6.4 官能团反应 [pdf_47][pdf_51]

| 官能团 | 典型反应 | 产物 |
|--------|----------|------|
| 烯烃 | 亲电加成 | 卤代烷/醇 |
| 炔烃 | 亲电加成/氧化 | 二卤代物/羧酸 |
| 芳香烃 | 亲电取代 | 取代苯 |
| 卤代烷 | 亲核取代/$E2$ 消除 | 醇/醚/烯烃 |
| 醇 | 氧化/酯化 | 醛酮/酯 |
| 醛酮 | 亲核加成 | 醇/缩醛 |
| 羧酸 | 酯化/还原 | 酯/醇 |
| 胺 | 酰化/烷基化 | 酰胺/季铵盐 |

#### 6.5 反应机理 [pdf_47][pdf_51]

**$S_N1$ 与 $S_N2$**：单分子 vs 双分子亲核取代，立体化学差异

**$E1$ 与 $E2$**：消除反应机理，Zaitsev 规则

**亲电芳香取代**：硝化、卤化、磺化、Friedel-Crafts 烷基化/酰基化

**醛醇缩合**：C—C 键形成的关键反应

**Diels-Alder 反应**：[4+2] 环加成，协同反应

#### 6.6 有机合成 [pdf_47][pdf_51]

**保护基策略**：OH 保护（TMS、THP）、NH 保护（Boc、Fmoc）、CO 保护（缩醛）

**逆合成分析**：从目标分子反推至易得原料

**合成规划**：官能团转化、碳骨架构建、立体化学控制

---

### 第七章 有机反应机理深入（Vogel & Houk Workbook）

#### 7.1 热化学与反应平衡 [pdf_213]

**反应焓变**：$\Delta H = \sum \Delta H_f^\circ(\text{产物}) - \sum \Delta H_f^\circ(\text{反应物})$

**Bond dissociation energy (BDE)**：$D_0 = \Delta H$ 对应键均裂

#### 7.2 周环反应 [pdf_213]

**Woodward-Hoffmann 规则**：同面/异面规则，$\pi$ 电子数决定反应类型

**电环化反应**：热允许（$4n$ 对旋，$4n+2$ 顺旋）

**$\sigma$ 迁移重排**：Cope 重排、Claisen 重排

#### 7.3 过渡金属催化 [pdf_213]

**交叉偶联反应**：Suzuki、Heck、Negishi、Stille 反应

**催化循环**：氧化加成 → 转金属化 → 还原消除

---

## 第四篇：无机化学

### 第八章 无机化学原理（Housecroft 5e / Jordan 2024）

#### 8.1 原子结构与周期律 [pdf_48]

**原子轨道**：$s$、$p$、$d$、$f$，电子构型与洪特规则

**周期律**：原子半径、电离能、电子亲和能、电负性（Pauling 标度、Mulliken 标度、Allred-Rochow 标度）的周期性趋势

**有效核电荷**：$Z_{\text{eff}} = Z - \sigma$（Slater 规则）

#### 8.2 化学键 [pdf_48]

**离子键**：Lattice energy（Born-Haber 循环）、Fajans 规则

**共价键**：Lewis 结构、共振、形式电荷

**金属键**：自由电子模型、能带理论

**分子间作用力**：van der Waals 力、氢键、偶极-偶极作用

#### 8.3 配位化学 [pdf_48][pdf_33]

**配体与配位数**：单齿、螯合、多齿配体，配位数为 2~12

**晶体场理论（CFT）**：$d$ 轨道在八面体场中分裂为 $t_{2g}$ 和 $e_g$，$\Delta_{\text{oct}}$

**配体场理论（LFT）**：结合 MO 理论的更完整描述

**光谱化学序列**：$\text{I}^- < \text{Br}^- < \text{Cl}^- < \text{F}^- < \text{OH}^- < \text{H}_2\text{O} < \text{NH}_3 < \text{en} < \text{NO}_2^- < \text{CN}^- < \text{CO}$

**磁性与颜色**：高自旋 vs 低自旋，d-d 跃迁决定颜色

#### 8.4 主族元素 [pdf_48]

**氢**：同位素、氢化物分类（离子型、共价型、金属型）

**s 区元素**：碱金属与碱土金属，对角线关系

**p 区元素**：B 族、C 族、N 族、O 族、卤素、稀有气体

**惰性电子对效应**：重 p 区元素低价态更稳定

#### 8.5 过渡金属与有机金属 [pdf_48][pdf_33]

**18 电子规则**：稳定有机金属配合物的电子计数

**金属羰基配合物**：CO 的 σ 给体和 π 反键作用

**金属有机催化**：Wacker 过程、氢化、羰基化

#### 8.6 固体结构 [pdf_48]

**晶体结构类型**：NaCl（岩盐）、CsCl、ZnS（闪锌矿/纤锌矿）、CaF₂、钙钛矿

**缺陷**：Schottky 缺陷、Frenkel 缺陷、杂质

**能带理论**：导体、半导体、绝缘体

---

## 第五篇：计算化学

### 第九章 计算化学基础（Jensen 2e / Lewars 4e / Young）

#### 9.1 方法分类 [pdf_36][pdf_34][pdf_82]

| 方法类型 | 理论基础 | 计算成本 | 精度 |
|----------|----------|:--------:|:----:|
| 分子力学（MM） | 经典力场 | 极低 | 结构合理 |
| 半经验 | 近似 HF + 参数化 | 低 | 定性~半定量 |
| HF（从头算） | 第一性原理 | 中 | 定性 |
| DFT | Hohenberg-Kohn 定理 | 中 | 半定量~定量 |
| 后 HF（MP2, CCSD, CI） | 电子相关 | 高~极高 | 定量~精确 |

#### 9.2 分子力学 [pdf_36][pdf_34]

**力场形式**：$E = \sum K_r(r - r_0)^2 + \sum K_\theta(\theta - \theta_0)^2 + \sum \frac{V_n}{2}[1 + \cos(n\phi - \gamma)] + \sum \left[\frac{A_{ij}}{r_{ij}^{12}} - \frac{B_{ij}}{r_{ij}^6} + \frac{q_i q_j}{4\pi\varepsilon_0 r_{ij}}\right]$

**常用力场**：AMBER、CHARMM、OPLS、UFF、MMFF

#### 9.3 半经验方法 [pdf_82][pdf_117][pdf_107]

**Hückel 方法**：$\pi$ 电子体系的简化处理

**扩展 Hückel（EHT）**：考虑所有价电子

**CNDO/INDO/NDDO**：逐步减少近似，CNDO 忽略微分重叠，NDDO 更精确

**MNDO/AM1/PM3/PM6**：参数化的 NDDO 方法，适合大体系

**ZINDO**：专为光谱设计

#### 9.4 从头算与电子相关 [pdf_34][pdf_36]

**HF 极限**：在无限基组下 HF 的能量极限，仍缺失电子相关能

**CI（组态相互作用）**：$\Psi_{\text{CI}} = c_0\Psi_{\text{HF}} + \sum c_i^a\Psi_i^a + \sum c_{ij}^{ab}\Psi_{ij}^{ab} + \cdots$

**MP2/MP4（Møller-Plesset 微扰）**：在 HF 基础上微扰加入电子相关

**CCSD/CCSD(T)（耦合簇）**：$\Psi_{\text{CC}} = e^{\hat{T}}\Psi_{\text{HF}}$，CCSD(T) 为"金标准"

#### 9.5 密度泛函理论 DFT [pdf_34][pdf_36][pdf_82]

**Hohenberg-Kohn 定理**：基态电子密度唯一确定所有性质

**Kohn-Sham 方程**：$[-\frac{1}{2}\nabla^2 + v_{\text{ext}}(\mathbf{r}) + v_H(\mathbf{r}) + v_{xc}(\mathbf{r})]\phi_i = \varepsilon_i\phi_i$

**交换-关联泛函**：
- LDA：$E_{xc}^{\text{LDA}}[n] = \int n(\mathbf{r})\varepsilon_{xc}(n) d\mathbf{r}$
- GGA：$E_{xc}^{\text{GGA}}[n] = \int f(n(\mathbf{r}), \nabla n(\mathbf{r})) d\mathbf{r}$
- 杂化泛函：B3LYP、PBE0、ωB97X-D

#### 9.6 基组理论 [pdf_81][pdf_432][pdf_433][pdf_434][pdf_435][pdf_436][pdf_437][pdf_438][pdf_439][pdf_440]

**STO（Slater 型轨道）**：$\chi_{nlm}(r, \theta, \phi) = N r^{n-1} e^{-\zeta r} Y_{lm}(\theta, \phi)$，物理上更准确但积分计算困难 [pdf_81]

**GTO（Gaussian 型轨道）**：$\chi_{nlm}(r, \theta, \phi) = N r^{n-1} e^{-\alpha r^2} Y_{lm}(\theta, \phi)$，积分计算快但形状偏差 [pdf_81]

**收缩基组**：多个 GTO 线性组合拟合 STO 的形状 [pdf_432]

**常用基组系列** [pdf_432][pdf_437]：

| 基组类型 | 示例 | 特点 |
|----------|------|------|
| 最小基组 | STO-3G | 每原子一个基函数，速度最快 |
| 分裂价基 | 3-21G, 6-31G | 价层分裂，提高灵活性 |
| 极化函数 | 6-31G(d), 6-31G(d,p) | 添加 d/p 函数，描述键变形 |
| 弥散函数 | 6-31+G(d) | 添加扩散函数，适合阴离子/弱相互作用 |
| 相关一致基组 | cc-pVDZ, cc-pVTZ, cc-pVQZ | Dunning 系列，系统收敛 |
| 赝势基组 | LANL2DZ, Stuttgart | 重元素内层用赝势，价层用基组 |

**基组选择原则** [pdf_432]：平衡精度与效率，避免基组重叠误差（BSSE），可用 Counterpoise 校正。

---

## 第六篇：光谱学

### 第十章 光谱学方法（Fleming 7e / Handbook of Spectroscopic Data / NMR and Chemistry 4e）

#### 10.1 质谱（MS）[pdf_142][pdf_51]

**基本原理**：分子电离 → 碎片离子 → 质荷比（$m/z$）分析

**分子离子峰**：确定分子量

**碎裂模式**：$\alpha$ 裂解、McLafferty 重排、RDA 裂解

**高分辨质谱**：精确原子量确定分子式

#### 10.2 紫外-可见光谱（UV-Vis）[pdf_142][pdf_26]

**Beer-Lambert 定律**：$A = \varepsilon c l$

**电子跃迁类型**：$\sigma \to \sigma^*$、$n \to \sigma^*$、$\pi \to \pi^*$、$n \to \pi^*$

**发色团**：C=C、C=O、苯环、共轭体系

**Woodward-Fieser 规则**：预测共轭二烯/烯酮的最大吸收波长

#### 10.3 红外光谱（IR）[pdf_142][pdf_26][pdf_48]

**振动模式**：伸缩振动、弯曲振动、摇摆振动、扭转振动

**特征官能团频率** [pdf_142]：

| 官能团 | 波数 (cm⁻¹) | 强度 |
|--------|:-----------:|:----:|
| O—H（自由） | 3580~3650 | 中，尖 |
| O—H（氢键） | 3200~3550 | 强，宽 |
| N—H | 3300~3500 | 中 |
| C—H | 2850~3000 | 强 |
| C≡C / C≡N | 2100~2260 | 中~弱 |
| C=O | 1650~1800 | 强 |
| C=C | 1600~1680 | 中 |
| C—O | 1050~1300 | 强 |

#### 10.4 核磁共振谱（NMR）[pdf_142][pdf_51][pdf_441][pdf_26]

**理论基础**：自旋量子数 $I = 1/2$ 的核（$^1$H、$^{13}$C、$^{19}$F、$^{31}$P）在外磁场中能级分裂 [pdf_441]

**Larmor 频率**：$\nu_0 = \frac{\gamma B_0}{2\pi}$

**化学位移** $\delta$：$\delta = \frac{\nu_{\text{样品}} - \nu_{\text{参考}}}{\nu_{\text{参考}}} \times 10^6$（ppm）

**$^1$H NMR 化学位移范围** [pdf_142]：

| 质子类型 | $\delta$ (ppm) |
|----------|:--------------:|
| 烷基 R—CH₃ | 0.8~1.5 |
| R—CH₂— | 1.2~1.7 |
| 烯基 C=C—H | 4.5~6.5 |
| 芳基 Ar—H | 6.5~8.5 |
| 醛基 HC=O | 9~10 |
| 羧基 COOH | 10~13 |

**自旋-自旋耦合**：$n+1$ 规则，耦合常数 $J$（Hz）

**$^{13}$C NMR**：$\delta$ 范围 0~220 ppm，DEPT 实验区分 CH₃/CH₂/CH/季碳

**二维 NMR**：COSY（$^1$H-$^1$H 相关）、HSQC/HMQC（$^1$H-$^{13}$C 直接相关）、HMBC（$^1$H-$^{13}$C 远程相关）

#### 10.5 综合谱图解析步骤 [pdf_142][pdf_26]

1. MS → 分子量 + 分子式
2. IR → 官能团判定
3. $^{13}$C NMR → 碳原子种类与数目
4. $^1$H NMR → 质子种类、数目、邻位关系
5. 2D NMR → 碳-氢连接关系确认
6. UV-Vis → 共轭体系确认

---

### 第十一章 光谱学数据手册（Handbook of Spectroscopic Data / Lange's Handbook）

#### 11.1 UV-Vis 经验规则 [pdf_26]

**Woodward-Fieser 规则（共轭二烯）**：母体系统 217 nm，每增加一个共轭双键 +30 nm，环外双键 +5 nm，烷基取代 +5 nm 等。

**Scott 规则（芳香羰基）**：对位取代基对 $\lambda_{\max}$ 的影响

#### 11.2 IR 光谱速查 [pdf_26]

**C=O 伸缩振动范围**：醛 1725~1740，酮 1710~1720，酯 1735~1750，酰胺 1650~1690，羧酸 1700~1725

**N—H 弯曲振动**：伯胺 $\delta_{\text{NH}}$ 1590~1650 cm⁻¹

#### 11.3 物理化学数据 [pdf_431]

**Lange's Handbook of Chemistry** [pdf_431] 提供：
- 元素性质（原子量、电离能、电负性）
- 有机化合物物理常数
- 热力学数据（$\Delta H_f^\circ$、$S^\circ$、$C_p$）
- 电极电势系列
- 光谱数据表

---

## 第七篇：数学方法

### 第十二章 数学物理方法（Mathematical Methods for Physicists）

#### 12.1 矢量与张量 [pdf_405]

**矢量微积分**：梯度、散度、旋度、Laplacian

**坐标变换**：Cartesian、柱坐标、球坐标

**张量运算**：Einstein 求和约定，变换规则

#### 12.2 微分方程 [pdf_405]

**常微分方程**：一阶、二阶线性 ODE，级数解法

**Sturm-Liouville 理论**：本征值问题，正交函数系

**特殊函数**：Legendre 多项式、Laguerre 多项式、Hermite 多项式、Bessel 函数、球谐函数

#### 12.3 Fourier 与 Laplace 变换 [pdf_405]

**Fourier 级数**：$f(x) = \frac{a_0}{2} + \sum_{n=1}^{\infty} (a_n\cos nx + b_n\sin nx)$

**Fourier 变换**：$\mathcal{F}\{f(x)\} = \int_{-\infty}^{\infty} f(x) e^{-ikx} dx$

**Laplace 变换**：$\mathcal{L}\{f(t)\} = \int_0^{\infty} f(t) e^{-st} dt$

#### 12.4 线性代数 [pdf_405]

**矩阵本征值问题**：$\mathbf{Ax} = \lambda\mathbf{x}$

**Hermitian 矩阵**：本征值为实数，本征向量正交

**酉变换与对角化**：$\mathbf{U}^\dagger\mathbf{AU} = \boldsymbol{\Lambda}$

---

## 第八篇：综合附录

### 附录一：教科书速查表

| 教科书 | 作者 | 版本 | 核心领域 | 库编号 |
|--------|------|:----:|----------|:------:|
| Quantum Chemistry | McQuarrie | 2e | 量子化学 | pdf_27 |
| Quantum Chemistry | Levine | 7e | 量子化学 | pdf_32 |
| Physical Chemistry: Quantum Chemistry and Molecular Interactions | Cooksy | 1e | 物理化学/量子化学 | pdf_24, pdf_23 |
| Physical Chemistry: Thermodynamics, Statistical Mechanics, Kinetics | Cooksy | 1e | 物理化学/统计力学 | pdf_39 |
| Physical Chemistry | Levine | 6e | 物理化学（全科） | pdf_7 |
| Atkins' Physical Chemistry | Atkins, de Paula, Keeler | 12e | 物理化学（全科） | pdf_427 |
| Organic Chemistry | Loudon, Parise | 6e | 有机化学 | pdf_47 |
| Organic Chemistry: Principles and Mechanisms | Karty | 3e | 有机化学 | pdf_51 |
| Organic Chemistry Workbook | Vogel, Houk | 1e | 有机反应机理 | pdf_213 |
| Inorganic Chemistry | Housecroft, Sharpe | 5e | 无机化学 | pdf_48 |
| Principles of Inorganic Chemistry | Jordan | 2024 | 无机化学 | pdf_33 |
| Electrons, Atoms, and Molecules in Inorganic Chemistry | Stephanos, Addison | 1e | 理论无机化学 | pdf_425 |
| Introduction to Computational Chemistry | Jensen | 2e | 计算化学 | pdf_34, pdf_429 |
| Computational Chemistry | Lewars | 4e | 计算化学 | pdf_36 |
| Computational Chemistry: A Practical Guide | Young | 1e | 计算化学 | pdf_82, pdf_117 |
| Spectroscopic Methods in Organic Chemistry | Fleming | 7e | 光谱学 | pdf_142 |
| A Handbook of Spectroscopic Data | — | 2009 | 光谱数据 | pdf_26 |
| NMR and Chemistry | Akitt, Mann | 4e | NMR 光谱 | pdf_441 |
| Mathematical Methods for Physicists | — | 1e | 数学物理 | pdf_405 |
| Lange's Handbook of Chemistry | Speight | 16e | 化学数据手册 | pdf_431 |

### 附录二：核心公式索引

| 公式 | 领域 | 来源 |
|------|------|------|
| $i\hbar\frac{\partial\Psi}{\partial t} = \hat{H}\Psi$ | 量子力学 | 所有量子化学教材 |
| $E_n = -\frac{Z^2}{n^2} \times 13.6\ \text{eV}$ | 氢原子 | pdf_27, pdf_32, pdf_24 |
| $\Delta G^\circ = -RT\ln K$ | 热力学 | pdf_7, pdf_427 |
| $k = \frac{k_B T}{h} e^{-\Delta G^\dagger/RT}$ | 过渡态理论 | pdf_7, pdf_34 |
| $S = k\ln Q$ | 统计力学 | pdf_7, pdf_39 |
| $[-\frac{1}{2}\nabla^2 + v_{\text{KS}}]\phi_i = \varepsilon_i\phi_i$ | DFT | pdf_34, pdf_36 |
| $\Psi_{\text{CC}} = e^{\hat{T}}\Psi_{\text{HF}}$ | 耦合簇 | pdf_34, pdf_36 |
| $A = \varepsilon c l$ | 光谱学 | pdf_142 |
| $\delta = \frac{\nu - \nu_{\text{ref}}}{\nu_{\text{ref}}} \times 10^6$ | NMR | pdf_142, pdf_441 |
| $E = K_r(r-r_0)^2 + K_\theta(\theta-\theta_0)^2 + \cdots$ | 分子力学 | pdf_34, pdf_36 |

### 附录三：学科交叉索引

- **分子轨道理论** → 量子化学（Levine Ch.14-15） + 有机化学（Karty Ch.1） + 无机化学（Housecroft Ch.5-6）
- **热力学** → 物理化学（Levine Ch.1-6） + 无机化学（Jordan Ch.5） + 计算化学（Jensen Ch.13）
- **光谱学** → 物理化学（Levine Ch.20） + 有机化学（Karty Ch.15-17） + 无机化学（Housecroft Ch.4）
- **对称性与群论** → 量子化学（Cooksy Ch.6） + 无机化学（Housecroft Ch.5） + 物理化学（Levine Ch.20）
- **统计力学** → 物理化学（Levine Ch.21） + 物理化学（Cooksy Ch.2-4） + 计算化学（Jensen Ch.13）
- **基组选择** → 计算化学（Jensen Ch.5） + 量子化学（Levine Ch.17） + 所有计算化学基组资料

---

> **数据来源**：本文档综合知识库中上述全部教科书内容整理而成，所有引用均标注了对应的 media_id。各教科书的版权归原作者及出版社所有。