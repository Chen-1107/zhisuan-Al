# MIT 终极知识技能手册（全学科覆盖）

> 本文档基于 MIT OpenCourseWare 多门课程讲义内容综合整理，涵盖数学、物理学、计算机科学、电力电子、化学、材料科学、生物工程、非线性动力学、弦理论等学科的核心知识体系。

---

## 第一篇：数学基础

### 第一章 线性代数（18.06, Gilbert Strang）[pdf_339]

#### 1.1 线性方程组求解

**核心问题**：$n$ 个方程 $n$ 个未知数的线性方程组求解 [pdf_339]。

**矩阵形式**：$\mathbf{A}\mathbf{x} = \mathbf{b}$

**消元法（Gaussian Elimination）**：
- 主元（pivot）→ 消去 → 回代
- 三种初等行变换：交换、倍乘、加减
- 消元失败：主元为零 → 交换行（若仍为零则无解或无穷多解）

**矩阵分解**：$\mathbf{A} = \mathbf{LU}$（L 为单位下三角，U 为上三角）

#### 1.2 向量空间与子空间

**四大基本子空间**：
- 列空间 $C(\mathbf{A})$：所有列的线性组合
- 零空间 $N(\mathbf{A})$：$\mathbf{A}\mathbf{x} = \mathbf{0}$ 的解空间
- 行空间 $C(\mathbf{A}^T)$：所有行的线性组合
- 左零空间 $N(\mathbf{A}^T)$：$\mathbf{A}^T\mathbf{y} = \mathbf{0}$ 的解空间

**维数定理**：$\dim C(\mathbf{A}) = \dim C(\mathbf{A}^T) = r$，$\dim N(\mathbf{A}) = n - r$，$\dim N(\mathbf{A}^T) = m - r$

#### 1.3 正交性与最小二乘

**正交投影**：$\mathbf{p} = \mathbf{A}(\mathbf{A}^T\mathbf{A})^{-1}\mathbf{A}^T\mathbf{b}$

**Gram-Schmidt 正交化**：将向量 $\{\mathbf{a}_1, \mathbf{a}_2, \dots, \mathbf{a}_n\}$ 化为正交基。

**QR 分解**：$\mathbf{A} = \mathbf{QR}$（Q 正交，R 上三角）

#### 1.4 特征值与特征向量

$\mathbf{A}\mathbf{x} = \lambda\mathbf{x}$

**特征值性质**：
- $\det(\mathbf{A} - \lambda\mathbf{I}) = 0$
- $\text{tr}(\mathbf{A}) = \sum \lambda_i$，$\det(\mathbf{A}) = \prod \lambda_i$

**对称矩阵**：实对称矩阵的特征值均为实数，特征向量相互正交。

**对角化**：$\mathbf{A} = \mathbf{S}\boldsymbol{\Lambda}\mathbf{S}^{-1}$（$\mathbf{S}$ 为特征向量矩阵）

**奇异值分解（SVD）**：$\mathbf{A} = \mathbf{U}\boldsymbol{\Sigma}\mathbf{V}^T$

---

### 第二章 微分方程（18.03）[pdf_311][pdf_318]

#### 2.1 一阶微分方程

**分离变量**：$\frac{dy}{dx} = \frac{g(x)}{h(y)} \Rightarrow \int h(y) dy = \int g(x) dx$

**一阶线性**：$y' + p(x)y = q(x)$，积分因子 $\mu = e^{\int p dx}$

**恰当方程**：$M(x,y) + N(x,y)y' = 0$，$\frac{\partial M}{\partial y} = \frac{\partial N}{\partial x}$

#### 2.2 二阶线性微分方程

$ay'' + by' + cy = 0$，特征方程 $a\lambda^2 + b\lambda + c = 0$

- 两实根：$y = C_1e^{\lambda_1 x} + C_2e^{\lambda_2 x}$
- 重根：$y = (C_1 + C_2x)e^{\lambda x}$
- 共轭复根 $\alpha \pm i\beta$：$y = e^{\alpha x}(C_1\cos\beta x + C_2\sin\beta x)$

#### 2.3 Laplace 变换

$\mathcal{L}\{f(t)\} = \int_0^{\infty} e^{-st}f(t) dt$

**关键性质**：
- $\mathcal{L}\{f'(t)\} = sF(s) - f(0)$
- $\mathcal{L}\{f''(t)\} = s^2F(s) - sf(0) - f'(0)$

#### 2.4 傅里叶级数

周期函数 $f(t) = \frac{a_0}{2} + \sum_{n=1}^{\infty} (a_n\cos n\omega t + b_n\sin n\omega t)$

**系数公式**：
- $a_n = \frac{2}{T}\int_0^T f(t)\cos n\omega t \, dt$
- $b_n = \frac{2}{T}\int_0^T f(t)\sin n\omega t \, dt$

---

### 第三章 多变量微积分（18.02）[pdf_312][pdf_317]

**梯度**：$\nabla f = \left(\frac{\partial f}{\partial x}, \frac{\partial f}{\partial y}, \frac{\partial f}{\partial z}\right)$

**散度**：$\nabla \cdot \mathbf{F} = \frac{\partial F_x}{\partial x} + \frac{\partial F_y}{\partial y} + \frac{\partial F_z}{\partial z}$

**旋度**：$\nabla \times \mathbf{F} = \begin{vmatrix} \mathbf{i} & \mathbf{j} & \mathbf{k} \\ \partial_x & \partial_y & \partial_z \\ F_x & F_y & F_z \end{vmatrix}$

**核心定理**：
- **Green 定理**：$\oint_C \mathbf{F} \cdot d\mathbf{r} = \iint_R (\nabla \times \mathbf{F}) \cdot \mathbf{k} \, dA$
- **Stokes 定理**：$\oint_{\partial S} \mathbf{F} \cdot d\mathbf{r} = \iint_S (\nabla \times \mathbf{F}) \cdot d\mathbf{S}$
- **Gauss 散度定理**：$\oiint_{\partial V} \mathbf{F} \cdot d\mathbf{S} = \iiint_V (\nabla \cdot \mathbf{F}) \, dV$

---

### 第四章 数学归纳法与证明技巧（6.1200J/18.062J）[pdf_281][pdf_329]

**命题逻辑**：谓词（Predicate）、集合、蕴含、反证法 [pdf_281]

**归纳法（Induction）** [pdf_329]：
- 基础步骤：$P(0)$ 成立
- 归纳步骤：$P(k) \Rightarrow P(k+1)$

**强归纳法（Strong Induction）** [pdf_303]：
- 假设 $P(0), P(1), \dots, P(k)$ 均成立，证明 $P(k+1)$

**鸽巢原理（Pigeonhole Principle）**[pdf_271]：若 $n$ 个物品放入 $m$ 个盒子且 $n > m$，则至少一个盒子含至少两个物品。

**容斥原理（Inclusion-Exclusion）**[pdf_271]：
$$|A \cup B \cup C| = |A| + |B| + |C| - |A\cap B| - |A\cap C| - |B\cap C| + |A\cap B\cap C|$$

---

### 第五章 组合数学与计数（6.1200J/18.062J）[pdf_286][pdf_297]

**乘法法则**：$n_1 \times n_2 \times \cdots \times n_k$

**加法法则**：$n_1 + n_2 + \cdots + n_k$

**排列**：$P(n,k) = \frac{n!}{(n-k)!}$

**组合**：$C(n,k) = \binom{n}{k} = \frac{n!}{k!(n-k)!}$

**双射法则**：若两个集合间存在一一对应，则元素个数相等。

---

### 第六章 数论与图论（6.1200J/18.062J）[pdf_345][pdf_269][pdf_360]

**欧几里得算法**：$\gcd(a,b) = \gcd(b, a \bmod b)$ [pdf_345]

**扩展欧几里得算法**：$\gcd(a,b) = xa + yb$

**图的基本概念** [pdf_269]：
- 简单图、完全图、二分图
- 图着色：图着色数 $\chi(G)$
- 四色定理：任何平面图可四着色

**树** [pdf_360]：
- 连通且无环
- 含 $n-1$ 条边
- 任意两顶点间唯一路径

---

### 第七章 概率论（6.1200J/18.062J）[pdf_335][pdf_336][pdf_355]

**条件概率**：$P(A|B) = \frac{P(A \cap B)}{P(B)}$ [pdf_355]

**贝叶斯规则**：$P(A|B) = \frac{P(B|A)P(A)}{P(B)}$

**期望的线性性**：$E[X + Y] = E[X] + E[Y]$（无论变量是否独立）[pdf_335]

**随机变量**：分布函数、二项分布、期望、方差 [pdf_336]

---

### 第八章 算法与递归（6.100L / 6.1200J）[pdf_268][pdf_354][pdf_387]

**排序算法复杂度** [pdf_268]：

| 算法 | 最坏情况 | 平均情况 | 最佳情况 |
|------|:--------:|:--------:|:--------:|
| 冒泡排序 | $O(n^2)$ | $O(n^2)$ | $O(n)$ |
| 选择排序 | $O(n^2)$ | $O(n^2)$ | $O(n^2)$ |
| 归并排序 | $O(n\log n)$ | $O(n\log n)$ | $O(n\log n)$ |
| 快速排序 | $O(n^2)$ | $O(n\log n)$ | $O(n\log n)$ |

**主定理（Master Theorem）**[pdf_387]：
递推公式 $T(n) = aT(n/b) + f(n)$：
1. 若 $f(n) = O(n^{\log_b a - \epsilon})$，则 $T(n) = \Theta(n^{\log_b a})$
2. 若 $f(n) = \Theta(n^{\log_b a})$，则 $T(n) = \Theta(n^{\log_b a}\log n)$
3. 若 $f(n) = \Omega(n^{\log_b a + \epsilon})$ 且 $af(n/b) \le cf(n)$，则 $T(n) = \Theta(f(n))$

---

### 第九章 高等数学专题

#### 9.1 李群与李代数（18.745）[pdf_357][pdf_358]

**李群**：连续群，其元素由有限个实参数连续标记。

**李代数**：李群在单位元处的切空间，带 Lie 括号运算 $[X,Y]$。

**基本性质**：
- 反对称：$[X,Y] = -[Y,X]$
- Jacobi 恒等式：$[X,[Y,Z]] + [Y,[Z,X]] + [Z,[X,Y]] = 0$

#### 9.2 代数拓扑（18.905/18.906）[pdf_265][pdf_362]

**同伦论**：研究映射的可连续变形分类。

**基本群** $\pi_1(X)$：基于路径的同伦类构成的群。

**同调论**：用 Abel 群序列 $H_n(X)$ 刻画空间的拓扑结构。

**谱序列**：从已知信息逐层逼近未知同调/同伦群的工具 [pdf_265]。

---

## 第二篇：物理学

### 第十章 狭义相对论（8.033, Max Tegmark）[pdf_326][pdf_381][pdf_341]

#### 10.1 洛伦兹变换

**4-向量形式**：$x^\mu = (ct, \mathbf{r})$

**洛伦兹变换**（沿 $x$ 方向，速度为 $v$）：
$$t' = \gamma(t - \frac{v}{c^2}x), \quad x' = \gamma(x - vt), \quad y' = y, \quad z' = z$$

其中 $\gamma = \frac{1}{\sqrt{1 - v^2/c^2}}$。

**不变量**：$ds^2 = c^2dt^2 - dx^2 - dy^2 - dz^2$

#### 10.2 相对论动力学

**4-动量**：$p^\mu = m_0 u^\mu = (E/c, \mathbf{p})$

**质能关系**：$E = mc^2 = \gamma m_0 c^2$ [pdf_341]

**动量和能量**：$E^2 = p^2c^2 + m_0^2c^4$

**动能**：$K = E - m_0c^2 = (\gamma - 1)m_0c^2$

#### 10.3 相对论效应

- 时间膨胀：$\Delta t = \gamma \Delta \tau$
- 长度收缩：$L = L_0/\gamma$
- 速度合成：$u' = \frac{u + v}{1 + uv/c^2}$

---

### 第十一章 广义相对论与宇宙学（8.033）[pdf_363][pdf_352][pdf_369]

#### 11.1 度规与弯曲时空

**Schwarzschild 度规** [pdf_352]（描述球对称质量为 $M$ 的时空）：
$$ds^2 = \left(1 - \frac{2GM}{c^2r}\right)c^2dt^2 - \left(1 - \frac{2GM}{c^2r}\right)^{-1}dr^2 - r^2d\Omega^2$$

**黑洞**：在 $r_s = \frac{2GM}{c^2}$（Schwarzschild 半径）处存在事件视界。

#### 11.2 FRW 宇宙学 [pdf_369]

**FRW 度规**：$ds^2 = c^2dt^2 - a(t)^2\left[\frac{dr^2}{1-kr^2} + r^2d\Omega^2\right]$

**弗里德曼方程**：$\left(\frac{\dot{a}}{a}\right)^2 = \frac{8\pi G}{3}\rho - \frac{kc^2}{a^2} + \frac{\Lambda c^2}{3}$

**宇宙学参数**：
- $\Omega_m = \frac{8\pi G\rho_0}{3H_0^2}$（物质密度参数）
- $\Omega_k = -\frac{kc^2}{a_0^2H_0^2}$（曲率参数）
- $\Omega_\Lambda = \frac{\Lambda c^2}{3H_0^2}$（暗能量参数）
- $\Omega_m + \Omega_k + \Omega_\Lambda = 1$

---

### 第十二章 量子力学（8.321）[pdf_374][pdf_382]

**Schrödinger 方程**：$i\hbar\frac{\partial}{\partial t}|\Psi\rangle = \hat{H}|\Psi\rangle$

**算符与对易**：$[\hat{x}, \hat{p}] = i\hbar$

**不确定性原理**：$\Delta x \Delta p \ge \frac{\hbar}{2}$

**角动量**：$[L_i, L_j] = i\hbar\epsilon_{ijk}L_k$

**量子力学公设**：
1. 状态由 Hilbert 空间中的向量 $|\Psi\rangle$ 描述
2. 可观测量对应 Hermitian 算符
3. 测量结果为本征值，概率由 $|\langle\lambda|\Psi\rangle|^2$ 给出

---

### 第十三章 量子场论（8.324）[pdf_343][pdf_349][pdf_351][pdf_356]

**Lagrangian 场论**：$\mathcal{L} = \frac{1}{2}(\partial_\mu\phi)(\partial^\mu\phi) - \frac{1}{2}m^2\phi^2 - \frac{\lambda}{4!}\phi^4$

**规范原理**：局部对称性 $\Rightarrow$ 规范场（如 QED 中的 $U(1)$ 规范场）

**重整化**：吸收发散到物理参数中，得到有限可预言性 [pdf_349]

**Feynman 图**：微扰展开的图示表示，每条线对应传播子，每个顶点对应相互作用。

**QED Lagrangian**：$\mathcal{L} = \bar{\psi}(i\gamma^\mu D_\mu - m)\psi - \frac{1}{4}F_{\mu\nu}F^{\mu\nu}$

---

### 第十四章 弦论与 AdS/CFT 对偶（8.821）[pdf_301][pdf_361][pdf_366]

**AdS/CFT 对应** [pdf_301]：$d+1$ 维反 de Sitter 时空中的引力理论等价于 $d$ 维边界上的共形场论。

**关键思想** [pdf_366]：
- 强耦合场论 $\leftrightarrow$ 弱耦合引力
- 大 $N$ 极限 $\leftrightarrow$ 经典引力
- 体-边界对应：体场 $\phi$ 的边界值 $\phi_0$ 是边界算符 $\mathcal{O}$ 的源

**D-branes** [pdf_321]：弦论中的高维膜状物体，既是引力源又是规范理论的载体。

**t'Hooft 计数** [pdf_361]：$1/N$ 展开 = 拓扑展开（球面 = 树图，环面 = 单圈等）

---

### 第十五章 软-共线有效理论 SCET（8.851）[pdf_276][pdf_43]

**SCET** 是描述含共线（collinear）和超软（soft/ultrasoft）模式的 QCD 有效场论，用于处理高能喷注物理。

**幂计数**：共线动量 $p_c^\mu \sim (Q, \lambda^2, \lambda)$，超软 $p_s^\mu \sim (Q\lambda^2, Q\lambda^2, Q\lambda^2)$

**Wilson 线**：$W_n(x) = \mathcal{P}\exp\left(ig\int_{-\infty}^0 ds\, n\cdot A(ns + x)\right)$

**因子化**：截面可分解为硬函数 × 喷注函数 × 部分子分布函数。

---

### 第十六章 非线性动力学与混沌（12.006J/18.353J/2.050J）[pdf_278][pdf_330][pdf_331][pdf_337][pdf_340][pdf_347][pdf_353]

#### 16.1 一维系统 [pdf_331]

**固定点与稳定性**：一维系统 $\dot{x} = f(x)$，固定点 $f(x^*) = 0$。
- 线性稳定性：$f'(x^*) < 0$ 稳定，$f'(x^*) > 0$ 不稳定

**分岔（Bifurcation）**[pdf_331]：
- 鞍点分岔（Saddle-Node）：$f(x) = r - x^2$
- 跨临界分岔（Transcritical）：$f(x) = rx - x^2$
- 叉式分岔（Pitchfork）：$f(x) = rx - x^3$

#### 16.2 二维系统 [pdf_330]

**单摆方程**：$\ddot{\theta} + \sin\theta = 0$

**相图**：以 $(x, \dot{x})$ 为坐标刻画系统演化。

**线性系统分类**：$\mathbf{\dot{x}} = \mathbf{Ax}$
- 特征值 $\lambda_1, \lambda_2$：节点（同号实根）、鞍点（异号实根）、螺旋点（复共轭）、中心（纯虚）

**Lyapunov 指数** $\lambda$ [pdf_280]：量化系统对初始条件的敏感性，$\lambda > 0$ 为混沌。

#### 16.3 混沌与奇异吸引子 [pdf_337][pdf_353]

**Lorenz 方程** [pdf_337]：
$$\dot{x} = \sigma(y - x), \quad \dot{y} = x(\rho - z) - y, \quad \dot{z} = xy - \beta z$$

**Rössler 吸引子** [pdf_353]：$\dot{x} = -y - z, \quad \dot{y} = x + ay, \quad \dot{z} = b + z(x - c)$

**Poincaré 截面** [pdf_340]：将连续流降低为离散映射，便于分析混沌。

**Hénon 映射** [pdf_347]：$x_{n+1} = 1 - ax_n^2 + y_n, \quad y_{n+1} = bx_n$

---

## 第三篇：计算机科学

### 第十七章 Python 编程基础（6.100L）[pdf_348][pdf_334][pdf_273]

**基本数据类型**：整数、浮点数、字符串、布尔值 [pdf_348]

**控制结构**：`if/elif/else`、`while` 循环、`for` 循环、`break` [pdf_334][pdf_273]

**列表与元组**：可变性区别、列表推导式、索引、切片 [pdf_307][pdf_316]

**字符串操作**：索引、切片、`in` 操作符、格式化输出 [pdf_273]

**函数**：定义、参数、返回值、作用域、默认参数、lambda 函数 [pdf_277][pdf_292]

**面向对象编程** [pdf_275][pdf_359]：
- 类（class）、属性、方法
- 继承（inheritance）、Dunder 方法
- 封装与抽象

**异常处理** [pdf_263]：`try/except/else/finally`、`assert`、防御性编程

**递归** [pdf_294]：斐波那契、分治、递归树

---

### 第十八章 算法复杂度与数据结构（6.100L）[pdf_354][pdf_268]

**算法效率度量**：
- 计时、计数操作、大 O 表示法
- 增长率：常数 $O(1)$ → 对数 $O(\log n)$ → 线性 $O(n)$ → $O(n\log n)$ → 平方 $O(n^2)$ → 指数 $O(2^n)$

**哈希表与字典** [pdf_279]：$O(1)$ 平均查找时间，哈希冲突解决

**排序算法**：冒泡、选择、归并、快速排序 [pdf_268]

**搜索算法**：二分搜索 $O(\log n)$ vs 线性搜索 $O(n)$

---

### 第十九章 计算机科学数学（6.1200J/18.062J）[pdf_281]

**哥德尔不完备定理** [pdf_281]：任何一致的公理系统要么不完备（存在真而不可证的命题），要么不一致。

**模运算**：$a \equiv b \pmod{n}$，加法/乘法保持模运算封闭 [pdf_345]

**加密基础**：RSA 加密中的模指数运算

---

### 第二十章 机器学习算法（18.409）[pdf_309]

**非负矩阵分解（NMF）**：$\mathbf{V} \approx \mathbf{WH}$，其中所有元素非负

**主题建模**：从文本中提取潜在主题

**张量分解**：高阶数据的低秩分解

**稀疏恢复**：从少量测量中重建稀疏信号（压缩感知）

**字典学习**：学习数据的稀疏表示

**矩阵补全**：从部分观测恢复完整矩阵（推荐系统）

---

## 第四篇：电力电子（6.622/6.334）

### 第二十一章 DC/DC 变换器 [pdf_259][pdf_267][pdf_288]

**基本开关单元** [pdf_267]：由开关和二极管组成，通过开关动作实现电压转换。

**降压（Buck）变换器** [pdf_259]：$V_{out} = D \cdot V_{in}$（$D$ 为占空比）

**升压（Boost）变换器**：$V_{out} = V_{in} / (1 - D)$

**连续导通模式（CCM）** vs **不连续导通模式（DCM）**[pdf_283]：
- CCM：电感电流始终为正
- DCM：电感电流在周期内降至零
- DCM 下转换比与负载相关

**纹波比** [pdf_260]：$\text{Ripple} = \frac{\Delta I}{I}$，用于元件应力计算

### 第二十二章 隔离型变换器 [pdf_302]

**反激式（Flyback）**：利用变压器存储磁能，低成本
**正激式（Forward）**：变压器不存储能量，需磁芯复位
**磁芯复位**：通过第三绕组或谐振实现 [pdf_302]

### 第二十三章 磁学基础 [pdf_261][pdf_282]

**磁路模型**：$\mathcal{F} = \mathcal{R}\Phi$（磁动势 = 磁阻 × 磁通）

**安培定律**：$\oint \mathbf{H} \cdot d\mathbf{l} = \sum I$

**法拉第定律**：$V = N\frac{d\Phi}{dt}$

**损耗** [pdf_282]：
- 铜损（绕组损耗）：直流电阻 + 趋肤效应 + 邻近效应
- 磁芯损耗：磁滞损耗 + 涡流损耗
- Steinmetz 模型：$P_{core} = k f^\alpha B^\beta$

### 第二十四章 逆变器与整流器 [pdf_266][pdf_272][pdf_153][pdf_289]

**单相电压源逆变器（VSI）**[pdf_266]：桥式结构 + 滤波

**PWM 调制** [pdf_153]：正弦-三角比较，消除谐波

**多电平逆变器** [pdf_153]：中性点钳位（NPC）、飞跨电容型

**三相整流** [pdf_272]：六脉冲、十二脉冲整流，谐波降低

### 第二十五章 谐振变换与 EMI [pdf_284][pdf_274]

**谐振电路** [pdf_284]：品质因数 $Q$、谐振频率 $\omega_0 = 1/\sqrt{LC}$

**EMI 滤波器** [pdf_274]：LISN、L 型滤波器、阻尼设计

**开关电容转换器** [pdf_314]：SSL 和 FSL 极限模型

---

## 第五篇：化学与材料科学

### 第二十六章 化学科学原理（5.111）[pdf_216][pdf_221][pdf_233][pdf_228]

**原子结构** [pdf_221]：电子发现、Bohr 模型、量子力学原子模型

**化学平衡** [pdf_233]：反应商 $Q$ 与平衡常数 $K$，勒夏特列原理

**酸碱滴定** [pdf_219]：滴定曲线、缓冲区、等价点

**氧化还原与电化学** [pdf_228]：半反应、电池电位、Nernst 方程

### 第二十七章 固体化学（3.091）[pdf_249][pdf_256][pdf_306]

**周期表** [pdf_256]：元素周期律、电子构型、离子化能

**Lewis 结构与共价键** [pdf_256]：八隅体规则、共振结构

**Miller 指数** [pdf_249]：晶面表示法 $(hkl)$

**X 射线衍射** [pdf_249]：Bragg 定律 $n\lambda = 2d\sin\theta$

### 第二十八章 聚合物物理（3.063）[pdf_293]

**软物质**：聚合物、液晶、胶体

**分子结构**：链段、交联、缠结

**热力学**：Flory-Huggins 理论、玻璃化转变

---

## 第六篇：化学与生物工程

### 第二十九章 化学反应工程（10.37）[pdf_257][pdf_285][pdf_304][pdf_395]

**间歇式反应器** [pdf_285]：物料衡算 $dC_A/dt = -r_A$，一级反应 $\ln(C_{A0}/C_A) = kt$

**酶催化与米氏方程** [pdf_304]：$v = \frac{V_{\max}[S]}{K_m + [S]}$

**Monod 方程** [pdf_304]：微生物生长 $\mu = \mu_{\max}\frac{S}{K_s + S}$

**反应机理** [pdf_257]：速率限制步骤、Boltzmann 分布、中间体

### 第三十章 生物转化过程设计 [pdf_300]

**设计流程**：催化剂筛选 → 基线建立 → 优化 → 交付

**关键因素**：动力学、操作限制、反应器配置

---

## 第七篇：数值方法（10.34）[pdf_325]

### 第三十一章 数值方法总览

| 方法 | 应用 | 复杂度 |
|------|------|:------:|
| Newton-Raphson | 非线性方程求根 | 二次收敛 |
| Runge-Kutta | ODE 初值问题 | 4 阶 |
| 有限差分 | PDE 离散化 | 取决于网格 |
| 蒙特卡罗 | 高维积分、随机模拟 | $O(1/\sqrt{N})$ |
| 有限元 | 偏微分方程 | 取决于网格 |

---

## 第八篇：全面索引

### 学科-课程对照表

| 学科 | MIT 课程号 | 讲师 | 主要资料 |
|------|:----------:|------|----------|
| 线性代数 | 18.06 | Gilbert Strang | pdf_339 |
| 微分方程 | 18.03 | — | pdf_311, pdf_318, pdf_332 |
| 多变量微积分 | 18.02 | — | pdf_312, pdf_317 |
| 计算机科学数学 | 6.1200J/18.062J | Z. Abel, B. Chapman, E. Demaine | pdf_269, pdf_271, pdf_281, pdf_286, pdf_287, pdf_297, pdf_303, pdf_329, pdf_335, pdf_336, pdf_345, pdf_355, pdf_360, pdf_387, pdf_389 |
| Python 编程 | 6.100L | Ana Bell | pdf_263, pdf_268, pdf_273, pdf_275, pdf_277, pdf_279, pdf_292, pdf_294, pdf_298, pdf_299, pdf_307, pdf_316, pdf_327, pdf_334, pdf_348, pdf_354, pdf_359 |
| 机器学习算法 | 18.409 | Ankur Moitra | pdf_309 |
| 量子复杂度 | 6.845 | — | pdf_371, pdf_394 |
| 相对论 | 8.033 | Max Tegmark | pdf_326, pdf_341, pdf_350, pdf_363, pdf_369, pdf_381 |
| 量子力学 | 8.321 | — | pdf_374, pdf_382 |
| 量子场论 | 8.324 | Hong Liu | pdf_343, pdf_349, pdf_351, pdf_356 |
| 弦论/AdS/CFT | 8.821 | McGreevy | pdf_301, pdf_305, pdf_320, pdf_321, pdf_344, pdf_361, pdf_364, pdf_366, pdf_379, pdf_383, pdf_384, pdf_386, pdf_388, pdf_390, pdf_392 |
| SCET | 8.851 | Iain W. Stewart | pdf_276, pdf_43 |
| 非线性动力学 | 12.006J/18.353J/2.050J | D.H. Rothman | pdf_278, pdf_280, pdf_330, pdf_331, pdf_337, pdf_340, pdf_347, pdf_353 |
| 电力电子 | 6.622/6.334 | — | pdf_153, pdf_259, pdf_260, pdf_261, pdf_262, pdf_264, pdf_266, pdf_267, pdf_272, pdf_274, pdf_282, pdf_283, pdf_284, pdf_288, pdf_289, pdf_290, pdf_291, pdf_295, pdf_296, pdf_302, pdf_308, pdf_314, pdf_333, pdf_385, pdf_396, pdf_398 |
| 化学科学原理 | 5.111 | Drennan & Taylor | pdf_216, pdf_219, pdf_221, pdf_228, pdf_233, pdf_250, pdf_254, pdf_255 |
| 固体化学 | 3.091 | — | pdf_249, pdf_256, pdf_306 |
| 聚合物物理 | 3.063 | — | pdf_293 |
| 化学反应工程 | 10.37 | K. Dane Wittrup | pdf_257, pdf_285, pdf_304, pdf_395 |
| 数值方法 | 10.34 | — | pdf_325 |
| 李群与李代数 | 18.745 | — | pdf_357, pdf_358 |
| 代数拓扑 | 18.905/18.906 | Haynes Miller | pdf_265, pdf_362, pdf_365, pdf_367, pdf_368, pdf_372, pdf_373, pdf_375, pdf_376, pdf_377, pdf_378 |
| 生物化学 | 5.07SC | — | pdf_179 |
| 光伏学 | 2.627 | — | pdf_323 |
| 中子相互作用 | 22.106 | — | pdf_328 |

---

### 核心公式速查

| 公式 | 领域 | 来源 |
|------|------|------|
| $\mathbf{Ax} = \mathbf{b}$ | 线性代数 | 18.06 |
| $\mathbf{A} = \mathbf{LU} = \mathbf{QR} = \mathbf{U\Sigma V}^T$ | 矩阵分解 | 18.06 |
| $\det(\mathbf{A} - \lambda\mathbf{I}) = 0$ | 特征值 | 18.06 |
| $\oint_C \mathbf{F} \cdot d\mathbf{r} = \iint_S (\nabla \times \mathbf{F}) \cdot d\mathbf{S}$ | 矢量微积分 | 18.02 |
| $E = \gamma m_0c^2$ | 相对论 | 8.033 |
| $ds^2 = (1 - \frac{2GM}{c^2r})c^2dt^2 - (1 - \frac{2GM}{c^2r})^{-1}dr^2$ | 广义相对论 | 8.033 |
| $(\frac{\dot{a}}{a})^2 = \frac{8\pi G}{3}\rho - \frac{kc^2}{a^2} + \frac{\Lambda c^2}{3}$ | 宇宙学 | 8.033 |
| $i\hbar\frac{\partial}{\partial t}\|\Psi\rangle = \hat{H}\|\Psi\rangle$ | 量子力学 | 8.321 |
| $\mathcal{L}_{QED} = \bar{\psi}(i\gamma^\mu D_\mu - m)\psi - \frac{1}{4}F_{\mu\nu}F^{\mu\nu}$ | 量子场论 | 8.324 |
| $\dot{x} = \sigma(y-x), \dot{y} = x(\rho-z)-y, \dot{z}=xy-\beta z$ | 混沌理论 | 12.006 |
| $V_{out} = D \cdot V_{in}$ (Buck) | 电力电子 | 6.622 |
| $v = V_{\max}[S]/(K_m + [S])$ | 酶动力学 | 10.37 |
| $\gcd(a,b) = \gcd(b, a\bmod b)$ | 数论 | 6.1200 |
| $\binom{n}{k} = \frac{n!}{k!(n-k)!}$ | 组合数学 | 6.1200 |
| $P(A\|B) = P(B\|A)P(A)/P(B)$ | 概率论 | 6.1200 |
| $\text{Master Theorem}: T(n) = aT(n/b) + f(n)$ | 算法 | 6.1200 |

---

> **数据来源**：本文档基于 MIT OpenCourseWare 上述各课程官方讲义整理，所有引用均标注了对应的 pdf_media_id。各课程材料的版权归 MIT 及各自讲师所有，基于 Creative Commons 许可发布。