# 量子化学理论推导与数学建模（完整技能手册）

> 本文档基于密度泛函理论、Hartree-Fock 理论、多体微扰理论、耦合簇理论、CASSCF、TDDFT 等核心方法的官方教材与经典文献整理，涵盖从基本假设到完整数学推导的**全部理论模块**。

---

## 一、预备知识

### 1.1 多电子 Schrödinger 方程 [pdf_98]

$$ \hat{H}\Psi = E\Psi $$

在 **Born-Oppenheimer 近似**下，固定原子核，电子哈密顿量为 [pdf_98]：

$$ \hat{H} \equiv -\frac{1}{2}\sum_{i=1}^{N}\nabla_{i}^{2} - \sum_{i=1}^{N}\sum_{I=1}^{M}\frac{Z_{I}}{|\mathbf{r}_{i}-\mathbf{R}_{I}|} + \sum_{i<j}^{N}\frac{1}{|\mathbf{r}_{i}-\mathbf{r}_{j}|} $$

或简写为：

$$ \hat{H} = \hat{T} + \hat{V}_{\text{ne}} + \hat{V}_{\text{ee}} $$

其中 $\hat{T}$ 为动能算符，$\hat{V}_{\text{ne}}$ 为核-电子吸引势，$\hat{V}_{\text{ee}}$ 为电子-电子排斥势。

### 1.2 电子密度

**电子密度**定义为 $N$ 电子波函数模平方对除一个电子外所有坐标的积分 [pdf_88]：

$$ n(\mathbf{r}) = N\int |\Psi(\mathbf{r}, \mathbf{r}_2, \dots, \mathbf{r}_N)|^2 d\mathbf{r}_2 d\mathbf{r}_3 \cdots d\mathbf{r}_N \qquad (1) $$

电子密度的基本性质 [pdf_100]：

- 全空间积分等于电子总数：$\int n(\mathbf{r}) d^3r = N$
- 在核位置有尖点：$n(\mathbf{r}) \sim \exp(-2Z_a|\mathbf{r}-\mathbf{R}_a|)$ [pdf_95]
- 概率解释：$\int_U n(\mathbf{r}) d^3r$ 为在区域 $U$ 中找到的电子的期望数

### 1.3 Slater 行列式

反对称化的多电子波函数表示为 **Slater 行列式** [pdf_36]：

$$ \Psi_{\text{SD}}(1,2,\dots,N) = \frac{1}{\sqrt{N!}} \begin{vmatrix}
\phi_1(1) & \phi_2(1) & \cdots & \phi_N(1) \\
\phi_1(2) & \phi_2(2) & \cdots & \phi_N(2) \\
\vdots & \vdots & \ddots & \vdots \\
\phi_1(N) & \phi_2(N) & \cdots & \phi_N(N)
\end{vmatrix} $$

其中 $\phi_i$ 为自旋轨道，$\phi_i(x) = \psi_i(\mathbf{r})\sigma_i(s)$。

### 1.4 变分原理 [pdf_36]

对于任意满足边界条件的尝试波函数 $\Psi_{\text{trial}}$，其能量期望值总是大于或等于真实基态能量：

$$ E[\Psi_{\text{trial}}] = \frac{\langle\Psi_{\text{trial}}|\hat{H}|\Psi_{\text{trial}}\rangle}{\langle\Psi_{\text{trial}}|\Psi_{\text{trial}}\rangle} \ge E_0 \qquad (2) $$

等号仅当 $\Psi_{\text{trial}} = \Psi_0$ 时成立。

---

## 二、Hartree-Fock 理论

### 2.1 单电子积分的期望值 [pdf_36]

在 Slater 行列式波函数下，单电子算符 $\hat{O}_1 = \sum_i \hat{h}(i)$ 的期望值为：

$$ \langle\Psi_{\text{SD}}|\hat{O}_1|\Psi_{\text{SD}}\rangle = \sum_{i=1}^{N} \langle\phi_i|\hat{h}|\phi_i\rangle \qquad (3) $$

### 2.2 双电子积分的期望值 [pdf_107]

双电子算符 $\hat{O}_2 = \sum_{i<j} \hat{g}(i,j)$ 的期望值：

$$ \langle\Psi_{\text{SD}}|\hat{O}_2|\Psi_{\text{SD}}\rangle = \sum_{i<j}^{N} \left[ \langle\phi_i\phi_j|\hat{g}|\phi_i\phi_j\rangle - \langle\phi_i\phi_j|\hat{g}|\phi_j\phi_i\rangle \right] \qquad (4) $$

对于电子排斥算符 $\hat{g}(1,2) = 1/r_{12}$，两项分别为 **Coulomb 积分** $J_{ij}$ 和 **Exchange 积分** $K_{ij}$ [pdf_107]：

$$ J_{ij} = \iint \phi_i^*(1)\phi_i(1)\frac{1}{r_{12}}\phi_j^*(2)\phi_j(2) d\mathbf{r}_1 d\mathbf{r}_2 \qquad (5) $$

$$ K_{ij} = \iint \phi_i^*(1)\phi_j(1)\frac{1}{r_{12}}\phi_j^*(2)\phi_i(2) d\mathbf{r}_1 d\mathbf{r}_2 \qquad (6) $$

### 2.3 HF 总能量 [pdf_36]

对于闭壳层体系，HF 总能量为：

$$ E_{\text{HF}} = 2\sum_{i=1}^{N/2} h_{ii} + \sum_{i,j=1}^{N/2} (2J_{ij} - K_{ij}) \qquad (7) $$

其中 $h_{ii} = \langle\phi_i|\hat{h}|\phi_i\rangle$，$\hat{h} = -\frac{1}{2}\nabla^2 - \sum_I\frac{Z_I}{|\mathbf{r}-\mathbf{R}_I|}$。

### 2.4 HF 方程的推导 [pdf_40]

**目标**：在轨道正交归一约束 $\langle\phi_i|\phi_j\rangle = \delta_{ij}$ 下，求能量泛函 $E[\{\phi_i\}]$ 对轨道的极小值。

引入 Lagrange 乘子 $\varepsilon_{ij}$（约束条件），构造泛函：

$$ \mathcal{L}[\{\phi_i\}] = E_{\text{HF}}[\{\phi_i\}] - \sum_{i,j} \varepsilon_{ij}(\langle\phi_i|\phi_j\rangle - \delta_{ij}) \qquad (8) $$

对 $\phi_i^*$ 进行变分 $\delta\mathcal{L}/\delta\phi_i^* = 0$，得到 **Hartree-Fock 方程** [pdf_40]：

$$ \hat{f}^{\text{HF}} \phi_i = \varepsilon_i \phi_i \qquad (9) $$

其中 **Fock 算符**定义为 [pdf_40]：

$$ \hat{f}^{\text{HF}} := \hat{T} + \hat{V}_{\text{Ne}} + \sum_{j\in[N]} \hat{J}^{(j)} - \sum_{j\in[N]} \hat{K}^{(j)} \qquad (10) $$

**Coulomb 算符**和 **Exchange 算符**的作用为 [pdf_40]：

$$ \hat{J}^{(j)}\phi_i(x_1) = \int dx_2 \frac{|\phi_j(x_2)|^2}{r_{12}} \phi_i(x_1) \qquad (11) $$

$$ \hat{K}^{(j)}\phi_i(x_1) = \int dx_2 \frac{\phi_j^*(x_2)\phi_i(x_2)}{r_{12}} \phi_j(x_1) \qquad (12) $$

### 2.5 Roothaan-Hall 方程 [pdf_35]

将分子轨道展开为原子轨道的线性组合（LCAO-MO）[pdf_103]：

$$ \phi_i = \sum_{\mu=1}^{M} C_{\mu i} \chi_\mu \qquad (13) $$

代入 HF 方程，左乘 $\chi_\nu^*$ 并积分，得到 **Roothaan-Hall 方程** [pdf_35]：

$$ \mathbf{F} \mathbf{C} = \mathbf{S} \mathbf{C} \boldsymbol{\varepsilon} \qquad (14) $$

其中 **Fock 矩阵元**为 [pdf_107]：

$$ F_{\mu\nu} = \langle\chi_\mu|-\frac{1}{2}\nabla^2|\chi_\nu\rangle - \sum_I Z_I\langle\chi_\mu|\frac{1}{r_I}|\chi_\nu\rangle + \sum_{\lambda\sigma} P_{\lambda\sigma}\left[(\mu\nu|\lambda\sigma) - \frac{1}{2}(\mu\lambda|\nu\sigma)\right] \qquad (15) $$

**重叠矩阵**：$S_{\mu\nu} = \langle\chi_\mu|\chi_\nu\rangle$

**密度矩阵**：$P_{\lambda\sigma} = 2\sum_{i=1}^{N/2} C_{\lambda i}^* C_{\sigma i}$

**双电子积分**：$(\mu\nu|\lambda\sigma) = \iint \chi_\mu^*(1)\chi_\nu(1)\frac{1}{r_{12}}\chi_\lambda^*(2)\chi_\sigma(2) d\mathbf{r}_1 d\mathbf{r}_2$

### 2.6 SCF 自洽场迭代过程 [pdf_116]

Roothaan-Hall 方程是**伪本征值问题**（Fock 矩阵依赖于自身解），必须通过迭代求解：

1. **指定系统**：原子核位置、原子序数、电子数、基组
2. **计算积分**：$S_{\mu\nu}$、$H_{\mu\nu}$、$(\mu\nu|\lambda\sigma)$
3. **对角化 $S$**：$S \rightarrow X = S^{-1/2}$（正交化变换矩阵）
4. **猜测初始密度矩阵** $P$（如用 Hückel 或半经验方法）
5. **构造 Fock 矩阵**：$F_{\mu\nu} = H_{\mu\nu} + \sum_{\lambda\sigma} P_{\lambda\sigma}[(\mu\nu|\lambda\sigma) - \frac{1}{2}(\mu\lambda|\nu\sigma)]$
6. **变换**：$F' = X^\dagger F X$
7. **对角化**：$F' C' = C' \varepsilon$，得 $C = X C'$
8. **新密度矩阵**：$P_{\mu\nu}^{\text{new}} = 2\sum_{i=1}^{N/2} C_{\mu i} C_{\nu i}^*$
9. **收敛检查**：$\|P^{\text{new}} - P\| < \text{tol}$？是 → 收敛；否 → 回到步骤 5

### 2.7 Koopmans 定理

正则 HF 轨道的轨道能量 $\varepsilon_i$ 的物理意义 [pdf_34]：

$$ \varepsilon_i = \langle\phi_i|\hat{f}|\phi_i\rangle = h_{ii} + \sum_{j}(J_{ij} - K_{ij}) \qquad (16) $$

- **HOMO 能量** $-\varepsilon_{\text{HOMO}}$ ≈ **电离能**（Koopmans 定理）
- **LUMO 能量** $-\varepsilon_{\text{LUMO}}$ ≈ **电子亲和能**（近似）

---

## 三、密度泛函理论（DFT）

### 3.1 Hohenberg-Kohn 定理 [pdf_99]

**定理一（存在性）**：对于相互作用多粒子体系，基态电子密度 $n_0(\mathbf{r})$ 唯一地确定外势 $V_{\text{ext}}(\mathbf{r})$（最多相差一个常数）[pdf_93]。

**Proof（反证法）**[pdf_99]：

假设存在两个不同的外势 $v_a(\mathbf{r})$ 和 $v_b(\mathbf{r})$ 对应相同的基态密度 $n_0(\mathbf{r})$。它们对应的哈密顿量分别为 $\hat{H}_a$ 和 $\hat{H}_b$，基态波函数分别为 $\Psi_a$ 和 $\Psi_b$（$\Psi_a \neq \Psi_b$），基态能量分别为 $E_a^0$ 和 $E_b^0$。

由变分原理，以 $\Psi_b$ 作为 $\hat{H}_a$ 的尝试波函数：

$$ E_a^0 < \langle\Psi_b|\hat{H}_a|\Psi_b\rangle = \langle\Psi_b|\hat{H}_b|\Psi_b\rangle + \langle\Psi_b|(\hat{H}_a - \hat{H}_b)|\Psi_b\rangle $$
$$ = E_b^0 + \langle\Psi_b|(\hat{v}_a - \hat{v}_b)|\Psi_b\rangle $$

由于 $\hat{v}_a - \hat{v}_b$ 为单电子算符：

$$ E_a^0 < E_b^0 + \int [v_a(\mathbf{r}) - v_b(\mathbf{r})] n_0(\mathbf{r}) d\mathbf{r} \qquad (17) $$

同理，交换 a 和 b：

$$ E_b^0 < E_a^0 + \int [v_b(\mathbf{r}) - v_a(\mathbf{r})] n_0(\mathbf{r}) d\mathbf{r} \qquad (18) $$

将 (17) 和 (18) 相加：

$$ E_a^0 + E_b^0 < E_a^0 + E_b^0 \qquad (19) $$

矛盾。故假设不成立，即不同的外势不能给出相同的基态密度。**证毕**。

**定理二（变分原理）**：对于任意满足 $\int n(\mathbf{r}) d\mathbf{r} = N$ 的尝试密度 $\tilde{n}(\mathbf{r})$，有 [pdf_99]：

$$ E[\tilde{n}] \ge E[n_0] \qquad (20) $$

即能量泛函在真实的基态密度处取极小值。

### 3.2 Kohn-Sham 方程 [pdf_88]

Kohn-Sham 方法将相互作用的多电子系统映射到一个**辅助的无相互作用系统**，使其产生相同的电子密度 [pdf_102]。

**Kohn-Sham 能量泛函** [pdf_104]：

$$ E_{\text{KS}}[n] = T_s[n] + \int v_{\text{ext}}(\mathbf{r}) n(\mathbf{r}) d\mathbf{r} + E_H[n] + E_{xc}[n] \qquad (21) $$

其中：
- $T_s[n] = -\frac{1}{2}\sum_{i=1}^{N}\int \phi_i^*(\mathbf{r})\nabla^2\phi_i(\mathbf{r}) d\mathbf{r}$：无相互作用动能
- $E_H[n] = \frac{1}{2}\iint \frac{n(\mathbf{r})n(\mathbf{r}')}{|\mathbf{r}-\mathbf{r}'|} d\mathbf{r} d\mathbf{r}'$：Hartree 能
- $E_{xc}[n]$：交换-关联能（**包含所有未知的多体效应**）

**Kohn-Sham 方程** [pdf_102]：

对 $\phi_i^*$ 变分，得到单粒子方程：

$$ \left[-\frac{1}{2}\nabla^2 + v_{\text{KS}}(\mathbf{r})\right] \phi_i(\mathbf{r}) = \varepsilon_i \phi_i(\mathbf{r}) \qquad (22) $$

**Kohn-Sham 有效势** [pdf_102]：

$$ v_{\text{KS}}(\mathbf{r}) = v_{\text{ext}}(\mathbf{r}) + v_H(\mathbf{r}) + v_{xc}(\mathbf{r}) = v_{\text{ext}}(\mathbf{r}) + \int\frac{n(\mathbf{r}')}{|\mathbf{r}-\mathbf{r}'|} d\mathbf{r}' + \frac{\delta E_{xc}[n]}{\delta n(\mathbf{r})} \qquad (23) $$

**电子密度**由 KS 轨道重建 [pdf_102]：

$$ n(\mathbf{r}) = \sum_{i=1}^{N} |\phi_i(\mathbf{r})|^2 \qquad (24) $$

**KS 总能表达式** [pdf_102]：

$$ E_{\text{KS}} = \sum_{i=1}^{N} \varepsilon_i - E_H[n] + E_{xc}[n] - \int v_{xc}(\mathbf{r}) n(\mathbf{r}) d\mathbf{r} \qquad (25) $$

### 3.3 KS 自洽求解流程 [pdf_99]

1. 猜测初始电子密度 $n^{(0)}(\mathbf{r})$（如原子密度叠加）
2. 构造 KS 有效势：$v_{\text{KS}}[n^{(0)}] = v_{\text{ext}} + v_H[n^{(0)}] + v_{xc}[n^{(0)}]$
3. 求解 KS 方程：$[-\frac{1}{2}\nabla^2 + v_{\text{KS}}]\phi_i^{(1)} = \varepsilon_i^{(1)}\phi_i^{(1)}$
4. 计算新密度：$n^{(1)}(\mathbf{r}) = \sum_i |\phi_i^{(1)}(\mathbf{r})|^2$
5. 若 $\|n^{(1)} - n^{(0)}\| < \text{tol}$，收敛；否则回到步骤 2

### 3.4 交换-关联泛函近似

**局域密度近似（LDA）** [pdf_102]：

$$ E_{xc}^{\text{LDA}}[n] = \int n(\mathbf{r}) \varepsilon_{xc}^{\text{unif}}(n(\mathbf{r})) d\mathbf{r} \qquad (26) $$

其中 $\varepsilon_{xc}^{\text{unif}}(n)$ 为均匀电子气的交换-关联能密度。

**广义梯度近似（GGA）** [pdf_101]：

$$ E_{xc}^{\text{GGA}}[n] = \int n(\mathbf{r}) \varepsilon_{xc}^{\text{GGA}}(n(\mathbf{r}), \nabla n(\mathbf{r})) d\mathbf{r} \qquad (27) $$

**杂化泛函**（如 B3LYP）[pdf_57]：

$$ E_{xc}^{\text{hybrid}} = a_0 E_x^{\text{exact}} + (1-a_0)E_x^{\text{GGA}} + a_c E_c^{\text{GGA}} \qquad (28) $$

### 3.5 电子密度的基本量 [pdf_1]

电子密度（由占据轨道构造）[pdf_1]：

$$ \rho(\mathbf{r}) = \sum_i \eta_i |\varphi_i(\mathbf{r})|^2 = \sum_i \eta_i \left|\sum_{\mu} C_{\mu,i}\chi_{\mu}(\mathbf{r})\right|^2 \qquad (29) $$

其中 $\eta_i$ 为轨道占据数，$\chi_{\mu}$ 为基函数。

---

## 四、Møller-Plesset 微扰理论

### 4.1 Rayleigh-Schrödinger 微扰论 [pdf_118]

将哈密顿量分解为可精确求解的 $\hat{H}_0$ 和微扰 $\hat{V}$：

$$ \hat{H} = \hat{H}_0 + \lambda\hat{V} \qquad (30) $$

**Møller-Plesset 分割**：取 $\hat{H}_0 = \sum_i \hat{f}(i)$（Fock 算符之和），$\hat{V} = \hat{H} - \hat{H}_0$。

波函数和能量按 $\lambda$ 展开 [pdf_117]：

$$ \Psi = \Psi^{(0)} + \lambda\Psi^{(1)} + \lambda^2\Psi^{(2)} + \cdots \qquad (31) $$

$$ E = E^{(0)} + \lambda E^{(1)} + \lambda^2 E^{(2)} + \cdots \qquad (32) $$

### 4.2 MP2 能量修正 [pdf_35]

一阶能量修正：$E^{(1)} = \langle\Psi^{(0)}|\hat{V}|\Psi^{(0)}\rangle$，即 HF 能量中的电子相关部分。

**MP2 二阶能量修正**（核心公式）[pdf_35]：

$$ E^{(2)} = \frac{1}{4} \sum_{ij}^{\text{occ}} \sum_{ab}^{\text{vir}} \frac{|\langle ij||ab\rangle|^2}{\varepsilon_i + \varepsilon_j - \varepsilon_a - \varepsilon_b} \qquad (33) $$

其中 $\langle ij||ab\rangle = \langle ij|ab\rangle - \langle ij|ba\rangle$ 为反对称化的双电子积分，$\varepsilon_i$ 为轨道能量。

### 4.3 MP2 计算步骤

1. 运行 HF 计算，得到轨道和轨道能量 $\{\varepsilon_i\}$
2. 计算双电子积分 $\langle ij||ab\rangle$（占据-虚轨道之间）
3. 按公式 (33) 求和，得到 $E^{(2)}$
4. 总能量：$E_{\text{MP2}} = E_{\text{HF}} + E^{(2)}$

### 4.4 高阶 MP 理论 [pdf_117]

| 阶数 | 包含的激发 | 计算复杂度 | 说明 |
|------|-----------|:---------:|------|
| MP2 | 双激发 D | $O(N^5)$ | 最常用，约80%相关能 |
| MP3 | 双激发 D | $O(N^6)$ | 改进不大 |
| MP4(SDQ) | SDQ | $O(N^6)$ | 无三重激发 |
| MP4(SDTQ) | SDTQ | $O(N^7)$ | 三重激发耗时 |
| MP5 | ~SDTQ | $O(N^8)$~$O(N^9)$ | 极少使用 |

### 4.5 MP 理论的性质

- **大小一致性**（size-extensive）：✓ [pdf_82]
- **变分性**（variational）：✗（MP2 能量可能低于精确值）[pdf_82]
- **收敛性**：对单参考占优的体系收敛好；对强相关体系可能发散 [pdf_82]

---

## 五、耦合簇理论（Coupled Cluster）

### 5.1 指数 Ansatz [pdf_107]

耦合簇波函数定义为：

$$ \Psi_{\text{CC}} = e^{\hat{T}} \Psi_{\text{HF}} \qquad (34) $$

其中**簇算符** $\hat{T}$ 为 [pdf_107]：

$$ \hat{T} = \hat{T}_1 + \hat{T}_2 + \hat{T}_3 + \cdots \qquad (35) $$

**激发算符**的具体形式 [pdf_107]：

$$ \hat{T}_1 \Psi_{\text{HF}} = \sum_{i}^{\text{occ}}\sum_{a}^{\text{vir}} t_i^a \Psi_i^a \qquad (36) $$

$$ \hat{T}_2 \Psi_{\text{HF}} = \sum_{i<j}^{\text{occ}}\sum_{a<b}^{\text{vir}} t_{ij}^{ab} \Psi_{ij}^{ab} \qquad (37) $$

其中 $t_i^a$、$t_{ij}^{ab}$ 为**簇振幅**（cluster amplitudes）。

### 5.2 指数展开 [pdf_34]

指数算符的展开产生所有可能的激发：

$$ e^{\hat{T}} = 1 + \hat{T} + \frac{1}{2!}\hat{T}^2 + \frac{1}{3!}\hat{T}^3 + \cdots \qquad (38) $$

例如 CCD（仅含 $\hat{T}_2$）[pdf_107]：

$$ \Psi_{\text{CCD}} = \left(1 + \hat{T}_2 + \frac{1}{2!}\hat{T}_2^2 + \frac{1}{3!}\hat{T}_2^3 + \cdots\right) \Psi_{\text{HF}} \qquad (39) $$

### 5.3 CCSD 方程 [pdf_118]

对于 CCSD（$\hat{T} = \hat{T}_1 + \hat{T}_2$），代入 Schrödinger 方程：

$$ \hat{H} e^{\hat{T}} \Psi_{\text{HF}} = E e^{\hat{T}} \Psi_{\text{HF}} \qquad (40) $$

左乘 $e^{-\hat{T}}$：

$$ e^{-\hat{T}} \hat{H} e^{\hat{T}} \Psi_{\text{HF}} = E \Psi_{\text{HF}} \qquad (41) $$

利用 Baker-Campbell-Hausdorff 展开：

$$ e^{-\hat{T}} \hat{H} e^{\hat{T}} = \hat{H} + [\hat{H}, \hat{T}] + \frac{1}{2!}[[\hat{H}, \hat{T}], \hat{T}] + \frac{1}{3!}[[[\hat{H}, \hat{T}], \hat{T}], \hat{T}] + \frac{1}{4!}[[[[\hat{H}, \hat{T}], \hat{T}], \hat{T}], \hat{T}] \qquad (42) $$

由于 $\hat{H}$ 最多含双电子算符，此展开在四重对易后终止。

**投影方程**（确定振幅）[pdf_118]：

$$ \langle\Psi_i^a| e^{-\hat{T}} \hat{H} e^{\hat{T}} |\Psi_{\text{HF}}\rangle = 0 \qquad (43) $$

$$ \langle\Psi_{ij}^{ab}| e^{-\hat{T}} \hat{H} e^{\hat{T}} |\Psi_{\text{HF}}\rangle = 0 \qquad (44) $$

**CCSD 能量**：

$$ E_{\text{CCSD}} = \langle\Psi_{\text{HF}}| e^{-\hat{T}} \hat{H} e^{\hat{T}} |\Psi_{\text{HF}}\rangle \qquad (45) $$

### 5.4 CCSD(T) 方法 [pdf_118]

CCSD(T) 在 CCSD 基础上**微扰地**加入三重激发校正：

$$ E_{\text{CCSD(T)}} = E_{\text{CCSD}} + E_{[T]} \qquad (46) $$

其中 $E_{[T]}$ 是三重激发通过微扰理论计算的校正项，是目前最成功的单参考方法之一。

### 5.5 耦合簇理论的性质

- **大小一致性**：✓（指数 Ansatz 保证）
- **变分性**：✗
- **对单参考优占体系**：高精度
- **对强相关体系**：需要多参考 CC

### 5.6 各方法比较

| 方法 | 计算复杂度 | 相关能比例 | 典型应用 |
|------|:---------:|:---------:|----------|
| MP2 | $O(N^5)$ | ~80% | 中等精度，大体系 |
| MP3 | $O(N^6)$ | ~85% | 较少使用 |
| MP4 | $O(N^7)$ | ~95% | 中等精度 |
| CCSD | $O(N^6)$ | ~95% | 高精度 |
| CCSD(T) | $O(N^7)$ | ~99% | **金标准** |
| CCSDT | $O(N^8)$ | ~99.5% | 极高精度 |

---

## 六、多组态自洽场（MCSCF/CASSCF）

### 6.1 多组态波函数 [pdf_115]

MCSCF 波函数是多个 Slater 行列式的线性组合，且**同时优化轨道和 CI 系数**：

$$ \Psi_{\text{MCSCF}} = \sum_{I} C_I \Phi_I \qquad (47) $$

其中 $\Phi_I$ 为不同的组态函数（Slater 行列式），$C_I$ 为 CI 系数。

### 6.2 CASSCF 方法 [pdf_34]

**完全活性空间 SCF（CASSCF）** 是 MCSCF 的特例，将轨道分为三个空间 [pdf_34]：

- **非活性（inactive）轨道**：始终双占据
- **活性（active）轨道**：在活性空间内做**全 CI**（所有可能的激发）
- **虚（virtual）轨道**：始终空占据

**CASSCF 波函数**：$|\Psi_{\text{CASSCF}}\rangle = \sum_{i \in \text{CAS}} C_i |\Phi_i\rangle$

其中求和遍历所有在活性空间轨道中分配 $N_{\text{act}}$ 个电子的方式。

### 6.3 CASSCF 记号 [pdf_76]

$$ \text{CASSCF}(n, m) $$

其中 $n$ 为活性电子数，$m$ 为活性轨道数。

**CAS 空间大小**（全 CI 组态数）：

$$ \dim = \sum_{S_z} \frac{(2S+1)(m!)^2}{(n/2 - S + 1)!(n/2 + S + 1)!(m - n/2 + S)!(m - n/2 - S - 1)!} \qquad (48) $$

### 6.4 CASSCF 优化过程

1. 初始猜测轨道（通常为 HF 轨道）
2. 构建活性空间内的全 CI 矩阵
3. 求解 CI 本征值问题，得到 $C_I$
4. 计算轨道梯度（旋转轨道）
5. 更新轨道（通常使用二次收敛方法）
6. 收敛否？否 → 回到步骤 2

### 6.5 RASSCF [pdf_34]

CASSCF 的扩展，将活性空间再分为三个区域：

- **RAS1**：最多允许 $N_{\text{hole}}$ 个空穴
- **RAS2**：全 CI（类似 CAS）
- **RAS3**：最多允许 $N_{\text{part}}$ 个粒子

### 6.6 NEVPT2 校正 [pdf_65]

在 CASSCF 基础上加入二阶微扰校正（**n-electron valence perturbation theory**）：

$$ E_{\text{NEVPT2}} = E_{\text{CASSCF}} + E^{(2)} \qquad (49) $$

---

## 七、组态相互作用（CI）方法

### 7.1 CI 波函数展开 [pdf_36]

CI 波函数将 HF 参考行列式加上所有可能的激发行列式 [pdf_36]：

$$ \Psi_{\text{CI}} = c_0 \Psi_{\text{HF}} + \sum_{i}^{\text{occ}}\sum_{a}^{\text{vir}} c_i^a \Psi_i^a + \sum_{i<j}^{\text{occ}}\sum_{a<b}^{\text{vir}} c_{ij}^{ab} \Psi_{ij}^{ab} + \cdots \qquad (50) $$

### 7.2 截断 CI

| 方法 | 包含的激发 | 说明 |
|------|-----------|------|
| CIS | 单激发 S | 用于激发态 |
| CID | 双激发 D | 大小不一致 |
| CISD | 单+双激发 SD | 常用，但非大小一致 |
| CISDT | SDT | 较高精度 |
| CISDTQ | SDTQ | 接近 FCI |
| FCI | 全部 | 精确解（指数复杂度） |

### 7.3 大小一致性问题

CI 截断后**不满足大小一致性**（size-inconsistency）。例如，CISD 用于两个分离的 He 原子时，能量误差与体系大小不成比例。

**Davidson 校正**（近似修正大小不一致性）：

$$ \Delta E_{\text{Davidson}} = (1 - c_0^2)(E_{\text{CISD}} - E_{\text{HF}}) \qquad (51) $$

---

## 八、含时密度泛函理论（TDDFT）

### 8.1 Runge-Gross 定理 [pdf_41]

**定理**：对于给定初始态 $\Psi(t_0)$，时间相关的电子密度 $n(\mathbf{r}, t)$ 唯一地确定时间相关的外势 $v_{\text{ext}}(\mathbf{r}, t)$（最多差一个时间函数）。

这为含时 DFT 奠定了严格的数学基础，将时间相关量子力学问题从波函数空间映射到密度空间。

### 8.2 时间相关 KS 方程 [pdf_41]

基于 Runge-Gross 定理，构建时间相关 KS 方程：

$$ i\frac{\partial}{\partial t} \phi_j(\mathbf{r}, t) = \left[-\frac{1}{2}\nabla^2 + v_{s}[\rho](\mathbf{r}, t)\right] \phi_j(\mathbf{r}, t) \qquad (52) $$

其中 KS 势为：

$$ v_s[\rho](\mathbf{r}, t) = v_{\text{ext}}(\mathbf{r}, t) + \int\frac{n(\mathbf{r}', t)}{|\mathbf{r}-\mathbf{r}'|} d\mathbf{r}' + v_{xc}[\rho](\mathbf{r}, t) \qquad (53) $$

### 8.3 线性响应理论 [pdf_41]

当外加微扰 $\delta v_{\text{ext}}(\mathbf{r}, \omega)$ 很弱时，密度的线性响应为：

$$ \delta n(\mathbf{r}, \omega) = \int \chi(\mathbf{r}, \mathbf{r}', \omega) \delta v_{\text{ext}}(\mathbf{r}', \omega) d\mathbf{r}' \qquad (54) $$

**KS 响应函数**：

$$ \chi_s(\mathbf{r}, \mathbf{r}', \omega) = \sum_{i,a} \left[\frac{\phi_i^*(\mathbf{r})\phi_a(\mathbf{r})\phi_a^*(\mathbf{r}')\phi_i(\mathbf{r}')}{\omega - (\varepsilon_a - \varepsilon_i) + i\eta} - \frac{\phi_i(\mathbf{r})\phi_a^*(\mathbf{r})\phi_a(\mathbf{r}')\phi_i^*(\mathbf{r}')}{\omega + (\varepsilon_a - \varepsilon_i) + i\eta}\right] \qquad (55) $$

### 8.4 Casida 方程 [pdf_15]

激发能量 $\Omega_I$ 由以下本征值方程给出：

$$ \sum_{j,b} \left[\delta_{ij}\delta_{ab}(\varepsilon_a - \varepsilon_i)^2 + 2\sqrt{\varepsilon_a - \varepsilon_i} K_{ia,jb}(\Omega_I) \sqrt{\varepsilon_b - \varepsilon_j}\right] F_{jb}^I = \Omega_I^2 F_{ia}^I \qquad (56) $$

其中耦合矩阵 $K$ 为：

$$ K_{ia,jb}(\omega) = \iint \phi_i^*(\mathbf{r})\phi_a(\mathbf{r})\left[\frac{1}{|\mathbf{r}-\mathbf{r}'|} + f_{xc}(\mathbf{r}, \mathbf{r}', \omega)\right] \phi_j^*(\mathbf{r}')\phi_b(\mathbf{r}') d\mathbf{r} d\mathbf{r}' \qquad (57) $$

**交换-关联核**：$f_{xc}(\mathbf{r}, \mathbf{r}', \omega) = \frac{\delta v_{xc}[n](\mathbf{r}, \omega)}{\delta n(\mathbf{r}', \omega)}$

### 8.5 绝热近似（ALDA）[pdf_93]

最常用的近似，将时间相关的 $v_{xc}$ 用静态 LDA 近似替代：

$$ f_{xc}^{\text{ALDA}}(\mathbf{r}, \mathbf{r}', t - t') = \delta(t - t')\delta(\mathbf{r} - \mathbf{r}')\left.\frac{dv_{xc}^{\text{LDA}}(n)}{dn}\right|_{n = n(\mathbf{r})} \qquad (58) $$

### 8.6 TDDFT 的适用范围 [pdf_41]

| 适用 | 不适用 |
|------|--------|
| 价层激发 | 里德堡激发（需正确渐近势） |
| 低能激发 | 电荷转移激发（需长程校正泛函） |
| 单激发为主 | 双激发或多激发 |
| 中等大小体系 | 大体系（计算成本高） |

---

## 九、波函数分析理论

### 9.1 Mulliken 布居分析 [pdf_107]

密度矩阵 $P_{\mu\nu} = \sum_{i}^{\text{occ}} c_{\mu i} c_{\nu i}^*$，重叠矩阵 $S_{\mu\nu} = \langle\chi_\mu|\chi_\nu\rangle$。

**Mulliken 布居数** [pdf_107]：

$$ N = \text{Tr}(\mathbf{PS}) = \sum_{\mu} (\mathbf{PS})_{\mu\mu} \qquad (59) $$

**原子电荷** [pdf_107]：

$$ q_A = Z_A - \sum_{\mu \in A} (\mathbf{PS})_{\mu\mu} \qquad (60) $$

**缺陷**：对基组有强烈依赖性，大基组下结果不稳定。

### 9.2 Löwdin 布居分析 [pdf_107]

先对基组进行对称正交化：

$$ \chi_\mu' = \sum_\nu S_{\mu\nu}^{-1/2} \chi_\nu \qquad (61) $$

再在正交化后的基组上进行布居分析，基组依赖性有所改善。

### 9.3 自然布居分析（NPA）[pdf_1]

NPA 通过占据数加权对称正交化，构造**自然原子轨道（NAO）** [pdf_1]：

**NAO 分类** [pdf_1]：
1. **Core NAO**：描述内层核心密度，占据数接近整数
2. **Valence NAO**：描述价层密度，占据数高
3. **Rydberg NAO**：描述极化与离域，占据数很低

**自然键轨道（NBO）**：在 NAO 基础上，通过寻找高占据的局域化轨道，重建 Lewis 结构图像 [pdf_48]。

### 9.4 电子定域化函数（ELF）[pdf_1]

ELF 基于**Pauli 排斥导致的动能密度**定义 [pdf_1]：

$$ \text{ELF}(\mathbf{r}) = \frac{1}{1 + [D(\mathbf{r})/D_0(\mathbf{r})]^2} \qquad (62) $$

其中 [pdf_1]：

$$ D(\mathbf{r}) = \frac{1}{2}\sum_i \eta_i |\nabla\varphi_i(\mathbf{r})|^2 - \frac{1}{8}\left[\frac{|\nabla\rho_\alpha(\mathbf{r})|^2}{\rho_\alpha(\mathbf{r})} + \frac{|\nabla\rho_\beta(\mathbf{r})|^2}{\rho_\beta(\mathbf{r})}\right] \qquad (63) $$

$$ D_0(\mathbf{r}) = \frac{3}{10}(6\pi^2)^{2/3}[\rho_\alpha(\mathbf{r})^{5/3} + \rho_\beta(\mathbf{r})^{5/3}] \qquad (64) $$

**物理意义** [pdf_1]：
- $D(\mathbf{r}) = \tau_s(\mathbf{r}) - \tau_w(\mathbf{r})$：**Pauli 动能密度**（电子定域造成的动能升高）
- $D_0(\mathbf{r}) = \tau_{TF}(\mathbf{r})$：**Thomas-Fermi 动能密度**（均匀电子气）
- ELF 值域 [0, 1]：大值 → 电子高度定域（共价键、孤对电子、内层）

**闭壳层简化** [pdf_1]（$\rho_\alpha = \rho_\beta = \rho/2$）：

$$ D(\mathbf{r}) = \frac{1}{2}\sum_i\eta_i|\nabla\varphi_i(\mathbf{r})|^2 - \frac{1}{8}\frac{|\nabla\rho(\mathbf{r})|^2}{\rho(\mathbf{r})} $$

$$ D_0(\mathbf{r}) = \frac{3}{10}(3\pi^2)^{2/3}\rho(\mathbf{r})^{5/3} $$

### 9.5 定域化轨道定位函数（LOL）[pdf_1]

LOL 与 ELF 密切相关，定义 [pdf_1]：

$$ \text{LOL}(\mathbf{r}) = \frac{\tau(\mathbf{r})}{1 + \tau(\mathbf{r})}, \quad \tau(\mathbf{r}) = \frac{D_0(\mathbf{r})}{(1/2)\sum_i\eta_i|\nabla\varphi_i(\mathbf{r})|^2} \qquad (65) $$

LOL 值域同样为 [0, 1]，与 ELF 定性相似但可解释为定域化轨道的观点。

### 9.6 自然轨道（Natural Orbitals）[pdf_81]

**自然轨道**是**对角化一阶约化密度矩阵**得到的轨道：

$$ \gamma(\mathbf{r}, \mathbf{r}') = \sum_i \eta_i \phi_i(\mathbf{r}) \phi_i^*(\mathbf{r}') \qquad (66) $$

其中 $\eta_i$ 为**占据数**（occupation numbers），$0 \le \eta_i \le 2$。

**ANO（Atomic Natural Orbitals）** [pdf_81]：从原子的相关计算（如 CISD）得到的自然轨道，用于构造高质量收缩基组。

---

## 十、QTAIM 拓扑分析理论

### 10.1 电子密度的临界点 [pdf_1]

电子密度 $\rho(\mathbf{r})$ 的**临界点（CP）** 满足 $\nabla\rho(\mathbf{r}_c) = 0$。

由 Hessian 矩阵的特征值分类：

$$ \mathbf{H}(\mathbf{r}_c) = \begin{pmatrix}
\partial^2\rho/\partial x^2 & \partial^2\rho/\partial x\partial y & \partial^2\rho/\partial x\partial z \\
\partial^2\rho/\partial y\partial x & \partial^2\rho/\partial y^2 & \partial^2\rho/\partial y\partial z \\
\partial^2\rho/\partial z\partial x & \partial^2\rho/\partial z\partial y & \partial^2\rho/\partial z^2
\end{pmatrix}_{\mathbf{r}=\mathbf{r}_c} $$

**CP 分类（rank, signature）**：

| 类型 | 符号 | 特征值 | 说明 |
|------|:----:|--------|------|
| (3, -3) | 核 CP | 三个负 | 原子核位置（极大值） |
| (3, -1) | 键 CP | 两负一正 | 化学键 |
| (3, +1) | 环 CP | 一负两正 | 环状结构 |
| (3, +3) | 笼 CP | 三个正 | 笼状结构（极小值） |

### 10.2 键径与键级

**键径**（bond path）：从键 CP 出发沿梯度下降方向连接两个核的路径。

**AIM 键级**（delocalization index）：

$$ \delta(A, B) = 2\sum_{i,j} S_{ij}(A) S_{ij}(B) \qquad (67) $$

其中 $S_{ij}(A) = \int_A \phi_i(\mathbf{r})\phi_j(\mathbf{r}) d\mathbf{r}$ 为轨道在原子 A 区域的积分。

---

## 十一、能量分解分析

### 11.1 ETS-NOCV 能量分解 [pdf_1]

将总相互作用能分解为：

$$ \Delta E_{\text{total}} = \Delta E_{\text{elstat}} + \Delta E_{\text{Pauli}} + \Delta E_{\text{orb}} + \Delta E_{\text{disp}} \qquad (68) $$

其中：
- $\Delta E_{\text{elstat}}$：静电相互作用
- $\Delta E_{\text{Pauli}}$：Pauli 排斥
- $\Delta E_{\text{orb}}$：轨道相互作用（电荷转移、极化）
- $\Delta E_{\text{disp}}$：色散校正

### 11.2 LED 分析（局域能量分解）

基于 DLPNO-CCSD(T) 的局域能量分解，可定量分析分子间相互作用中各个区域的贡献。

---

## 十二、相对论效应 [pdf_104]

### 12.1 标量相对论效应

**ZORA（零级正则近似）**：

$$ \hat{H}_{\text{ZORA}} = \sum_i \frac{\mathbf{p}_i^2}{2m_e - \frac{\mathbf{p}_i^2}{2m_e c^2}V(\mathbf{r}_i)} + V(\mathbf{r}_i) \qquad (69) $$

**DKH（Douglas-Kroll-Hess）**：通过多次酉变换解耦 Dirac 方程。

**X2C（精确两分量）**：直接从 Dirac 方程的精确解构造两分量哈密顿量。

### 12.2 自旋-轨道耦合

SOC 效应在重元素体系中至关重要，可通过以下方式处理：

$$ \hat{H}_{\text{SOC}} = \sum_i \frac{1}{2m_e^2 c^2} \frac{1}{r_i} \frac{dV(r_i)}{dr_i} \hat{\mathbf{l}}_i \cdot \hat{\mathbf{s}}_i \qquad (70) $$

---

## 十三、快速参考

### 13.1 核心方程速查

| 方程 | 名称 | 编号 |
|------|------|:----:|
| $(-\frac{1}{2}\nabla^2 + v_{\text{KS}})\phi_i = \varepsilon_i\phi_i$ | Kohn-Sham 方程 | (22) |
| $\hat{f}^{\text{HF}}\phi_i = \varepsilon_i\phi_i$ | Hartree-Fock 方程 | (9) |
| $\mathbf{F}\mathbf{C} = \mathbf{S}\mathbf{C}\boldsymbol{\varepsilon}$ | Roothaan-Hall 方程 | (14) |
| $\Psi_{\text{CC}} = e^{\hat{T}}\Psi_{\text{HF}}$ | 耦合簇指数 Ansatz | (34) |
| $E^{(2)} = \frac{1}{4}\sum_{ij}\sum_{ab}\frac{|\langle ij||ab\rangle|^2}{\varepsilon_i+\varepsilon_j-\varepsilon_a-\varepsilon_b}$ | MP2 能量 | (33) |
| $\text{ELF}(\mathbf{r}) = 1/[1+(D/D_0)^2]$ | 电子定域化函数 | (62) |
| $\rho(\mathbf{r}) = \sum_i \eta_i |\varphi_i(\mathbf{r})|^2$ | 电子密度 | (29) |

### 13.2 各方法精度对比

| 方法 | 相关能 | 大小一致 | 变分 | 计算复杂度 |
|------|:-----:|:--------:|:----:|:---------:|
| HF | 0% | ✓ | ✓ | $O(N^4)$ |
| MP2 | ~80% | ✓ | ✗ | $O(N^5)$ |
| CCSD | ~95% | ✓ | ✗ | $O(N^6)$ |
| CCSD(T) | ~99% | ✓ | ✗ | $O(N^7)$ |
| CISD | ~90% | ✗ | ✓ | $O(N^6)$ |
| CASSCF | 活性空间内全 | ✓ | ✓ | 指数级 |
| FCI | 100% | ✓ | ✓ | 指数级 |

### 13.3 关键记号

| 符号 | 含义 |
|------|------|
| $n(\mathbf{r})$ 或 $\rho(\mathbf{r})$ | 电子密度 |
| $\phi_i$ | 自旋轨道 / KS 轨道 |
| $\chi_\mu$ | 基函数（原子轨道） |
| $C_{\mu i}$ | 分子轨道展开系数 |
| $P_{\mu\nu}$ | 密度矩阵 |
| $S_{\mu\nu}$ | 重叠矩阵 |
| $F_{\mu\nu}$ | Fock 矩阵 |
| $\hat{T}$ | 簇算符 |
| $t_{ij}^{ab}$ | 簇振幅 |
| $\varepsilon_i$ | 轨道能量 |
| $E_{xc}$ | 交换-关联能 |
| $v_{xc}$ | 交换-关联势 |

---

## 十四、参考文献

- An Introduction to DFT（pdf_88）
- A Verbose Note on Density Functional Theory（pdf_40）
- DFT for Beginners（pdf_99）
- dftintro.pdf（pdf_100）
- DFT.pdf（pdf_102）
- DFT Fundamentals and Applications（pdf_101）
- Electronic Structure: Basic Theory and Practical Methods, 2e, Richard Martin（pdf_93）
- DFT: An Advanced Course（pdf_104）
- A Primer in Density Functional Theory（pdf_41）
- ESTC 电子结构理论与计算（pdf_107）
- TheoreticalChemistry_full_2018（pdf_103）
- Introduction to Computational Chemistry, 2e, Jensen（pdf_34）
- Computational Chemistry, 4e, Lewars（pdf_36）
- Computational Organic Chemistry, 2e, Bachrach（pdf_35）
- Molecular Electronic-Structure Theory, Helgaker（pdf_118）
- Multiwfn 手册（pdf_1）
- 关于高斯入门的小小指南（pdf_75）
- David Sholl - Density Functional Theory: A Practical Introduction（pdf_8）
- A Chemist's Guide to Density Functional Theory（pdf_15）
- Time-Dependent Density Functional Theory（pdf_41）
- Basis Sets for Computational Chemistry（pdf_113）